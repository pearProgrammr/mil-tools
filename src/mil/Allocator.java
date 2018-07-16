/*
    Copyright 2018 Mark P Jones, Portland State University

    This file is part of mil-tools.

    mil-tools is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    mil-tools is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with mil-tools.  If not, see <https://www.gnu.org/licenses/>.
*/
package mil;

import compiler.*;
import core.*;

public abstract class Allocator extends Call {

  /** Test to determine whether a given tail expression has no externally visible side effect. */
  public boolean hasNoEffect() {
    return true;
  }

  public Allocator isAllocator() {
    return this;
  }

  Tail liftStaticAllocator() {
    for (int i = 0; i < args.length; i++) {
      if (!args[i].isStatic()) {
        return this;
      }
    }
    // !System.out.print("The allocator "); this.dump();
    // !System.out.println(" is static.");
    TopLevel topLevel = new TopLevel(/*pos*/ null, new TopLhs(), this); // TODO: fix position.
    MILProgram.report("lifting static allocator to top-level " + topLevel);
    return new Return(new TopDef(topLevel, 0));
  }

  /**
   * Test to determine whether a given tail expression may be repeatable (i.e., whether the results
   * of a previous use of the same tail can be reused instead of repeating the tail). TODO: is there
   * a better name for this?
   */
  public boolean isRepeatable() {
    return true;
  }

  /**
   * Test to determine whether a given tail expression is pure (no externally visible side effects
   * and no dependence on other effects).
   */
  public boolean isPure() {
    return true;
  }

  public Tail lookupFact(TopLevel tl) {
    // !System.out.print("REGISTER ");
    // !this.dump();
    // !System.out.println(" for " + top);
    this.tl = tl;
    return this;
  }

  protected TopLevel tl = null;

  public TopLevel getTopLevel() {
    return tl;
  }

  /**
   * Calculate an array of static values for this tail, or null if none of the results produced by
   * this tail have statically known values. (Either because they are truly not statically known, or
   * because we choose not to compute static values for certain forms of Tail.) If there are
   * multiple results, only some of which are statically known, then the array that is returned will
   * be non-null, but will have null values in places where static values are not known.
   */
  llvm.Value[] staticValueCalc(TypeMap tm, llvm.Program prog) {
    llvm.Value[] comps = null; // lazily allocated array of components
    int n = args.length;
    if (n <= 0) {
      comps = new llvm.Value[1]; // leave space for the tag at index 0
    } else {
      llvm.Value v = args[0].staticValueCalc();
      if (v == null) {
        return null; // if any component is unknown, then so is the full allocator
      }
      comps = new llvm.Value[1 + n]; // again, allow for tag at index 0
      comps[1] = v;
      for (int i = 1; i < n; i++) {
        if ((comps[1 + i] = args[i].staticValueCalc()) == null) {
          return null;
        }
      }
    }
    return new llvm.Value[] {staticAlloc(tm, prog, comps)};
  }

  abstract llvm.Value staticAlloc(TypeMap tm, llvm.Program prog, llvm.Value[] vals);

  llvm.Value staticAlloc(
      llvm.Program prog, llvm.Value[] vals, llvm.Type layoutType, llvm.Type genPtrType) {
    // Create a private constant containing all the fields for this object:
    String layoutName = prog.freshName("layout");
    prog.add(new llvm.PrivConst(layoutName, new llvm.Struct(layoutType, vals)));
    llvm.Global layoutGlobal = new llvm.Global(layoutType.ptr(), layoutName);

    // Create an alias that casts the specific constructor to the general type for this object:
    String valueName = prog.freshName("val");
    prog.add(new llvm.Alias(valueName, new llvm.BitcastVal(layoutGlobal, genPtrType)));
    return new llvm.Global(genPtrType, valueName);
  }

  /** Generate LLVM code to execute this Tail with NO result from the right hand side of a Bind. */
  llvm.Code toLLVM(TypeMap tm, VarMap vm, TempSubst s, llvm.Code c) {
    debug.Internal.error("Allocator does not return void");
    return c;
  }

  /**
   * Generate code to allocate space for an object of type objt, set obj to point to the start of
   * the (uninitialized) memory, fill in the tag and fields, and then continue with the code in c.
   */
  llvm.Code alloc(
      TypeMap tm,
      VarMap vm,
      TempSubst s,
      llvm.Type objt,
      llvm.Local obj,
      llvm.Value tag,
      llvm.Code c) {
    // NOTE: The following steps build up the desired code in reverse order of execution

    // - Save the fields in the object allocated at the address in obj:
    int n = args.length;
    if (n > 0) {
      while (--n >= 0) {
        c = storeField(vm, s, obj, n + 1, args[n].toLLVM(tm, vm, s), c);
      }
      c = new llvm.CodeComment("initialize other fields", c);
    }

    // - Save the object tag:
    c = new llvm.CodeComment("set the tag", storeField(vm, s, obj, 0, tag, c));

    // - Allocate space for a new object:
    llvm.Local past = vm.reg(objt); // pointer to first address past a c object starting at 0
    llvm.Local size = vm.reg(llvm.Type.i32); // integer holding the size of a c object
    llvm.Local raw = vm.reg(llvm.Type.i8.ptr()); // raw pointer to allocated object
    llvm.Rhs call =
        new llvm.Call(
            raw.getType(), // a call to allocate memory
            new llvm.Global(llvm.Type.i8, "alloc"), // TODO: fix this Global reference!
            new llvm.Value[] {size});

    return new llvm.CodeComment(
        "calculate the number of bytes that we need to allocate",
        new llvm.Op(
            past,
            new llvm.Getelementptr(new llvm.Null(objt), new llvm.Int(1)),
            new llvm.Op(
                size,
                new llvm.PtrToInt(past, size.getType()),
                new llvm.CodeComment(
                    "allocate memory for the object",
                    new llvm.Op(raw, call, new llvm.Op(obj, new llvm.Bitcast(raw, objt), c))))));
  }

  /** Generate code to execute lhs[n] = v; c */
  static llvm.Code storeField(
      VarMap vm, TempSubst s, llvm.Value lhs, int n, llvm.Value v, llvm.Code c) {
    llvm.Local addr = vm.reg(v.getType().ptr());
    return new llvm.Op(
        addr,
        new llvm.Getelementptr(lhs, llvm.Int.ZERO, new llvm.Int(n)),
        new llvm.Store(v, addr, c));
  }
}