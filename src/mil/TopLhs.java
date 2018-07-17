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
import compiler.Failure;
import compiler.Handler;
import compiler.Position;
import core.*;
import java.io.PrintWriter;

class TopLhs {

  private String id;

  /** Default constructor. */
  TopLhs(String id) {
    this.id = id;
  }

  private static int count = 0;

  public TopLhs() {
    this("s" + count++);
  }

  private Scheme declared;

  private Type defining;

  /** Get the declared type, or null if no type has been set. */
  public Scheme getDeclared() {
    return declared;
  }

  /** Set the declared type. */
  public void setDeclared(Scheme declared) {
    this.declared = declared;
  }

  /** Return the identifier that is associated with this definition. */
  public String getId() {
    return id;
  }

  void displayDefn(PrintWriter out) {
    if (declared != null) {
      out.println(id + " :: " + declared);
    }
  }

  /** Return a type for an instantiated version of this item when used as Atom (input operand). */
  public Type instantiate() {
    return declared.instantiate();
  }

  Type setInitialType() {
    return defining = (declared != null) ? declared.instantiate() : new TVar(Tyvar.star);
  }

  boolean allTypesDeclared() {
    return declared != null;
  }

  /** Lists the generic type variables for this definition. */
  protected TVar[] generics = TVar.noTVars;

  /** Produce a printable description of the generic variables for this definition. */
  public String showGenerics() {
    return TVar.show(generics);
  }

  void generalizeType(Handler handler) throws Failure {
    // !   debug.Log.println("Generalizing definition for: " + getId());
    if (defining != null) {
      TVars gens = defining.tvars();
      generics = TVar.generics(gens, null);
      // !     debug.Log.println("generics: " + showGenerics());
      Scheme inferred = defining.generalize(generics);
      debug.Log.println("Inferred " + id + " :: " + inferred);
      if (declared == null) {
        declared = inferred;
      } else if (!declared.alphaEquiv(inferred)) {
        throw new Failure(
            "Declared type \""
                + declared
                + "\" for \""
                + id
                + "\" is more general than inferred type \""
                + inferred
                + "\"");
      }
      findAmbigTVars(handler, gens); // search for ambiguous type variables ...
    }
  }

  void findAmbigTVars(Handler handler, TVars gens) {}

  void collect(TypeSet set) {
    declared = declared.canonScheme(set);
    defining = defining.canonType(set);
  }

  /** Update all declared types with canonical versions. */
  void canonDeclared(MILSpec spec) {
    declared = declared.canonScheme(spec);
  }

  void printlnSig(PrintWriter out) {
    out.println(id + " :: " + declared);
  }

  /** Set the type of this specialized TopLhs to the appropriate instance of the defining type. */
  void specialize(TopLhs lorig, TVarSubst s) {
    this.declared = lorig.defining.apply(s);
  }

  /** Return a substitution that can instantiate this Lhs to the given type. */
  TVarSubst specializingSubst(Type inst) {
    return declared.specializingSubst(generics, inst);
  }

  static void copyIds(TopLhs[] newLhs, TopLhs[] oldLhs) {
    for (int i = 0; i < oldLhs.length; i++) {
      newLhs[i].id = oldLhs[i].id;
    }
  }

  static Type[][] reps(TopLhs[] lhs) {
    Type[][] reps = null;
    for (int i = 0; i < lhs.length; i++) {
      Type[] r = lhs[i].repCalc();
      if (r != null) {
        if (reps == null) {
          reps = new Type[lhs.length][];
        }
        reps[i] = r;
      }
    }
    return reps;
  }

  Type[] repCalc() {
    return declared.repCalc();
  }

  void repTransform(Handler handler, RepTypeSet set) {
    // TODO: not clear that this will work if there is a change of representation; see External
    // case.
    declared = declared.canonScheme(set);
  }

  void setDeclared(Handler handler, Position pos, Scheme scheme) {
    if (declared != null) {
      handler.report(new Failure(pos, "Multiple type annotations for \"" + id + "\""));
    }
    declared = scheme;
  }

  void addExport(MILEnv exports, TopDef t) {
    exports.addTop(id, t);
  }

  llvm.GlobalVarDefn globalVarDefn(TypeMap tm) {
    return new llvm.GlobalVarDefn(id, tm.toLLVM(defining).defaultValue());
  }

  Temp makeTemp() {
    return new Temp(defining);
  }
}
