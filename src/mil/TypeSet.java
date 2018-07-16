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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

public class TypeSet {

  /** A mapping from Tycons to lists of uses (i.e., types with that Tycon in head position). */
  private HashMap<Tycon, Types> tyconInstances = new HashMap();

  /** A mapping from other (singleton) type forms to lists of uses. */
  private HashMap<Type, Types> otherInstances = new HashMap();

  /**
   * A mapping from constant values (BigIntegers and Strings) to corresponding (canonical) TLits.
   */
  private HashMap<Object, TLit> litsToTypes = new HashMap();

  public void dump() {
    PrintWriter out = new PrintWriter(System.out);
    dump(out);
    out.flush();
  }

  public void dump(String name) {
    try {
      PrintWriter out = new PrintWriter(name);
      dump(out);
      out.close();
    } catch (IOException e) {
      System.out.println("Attempt to create TypeSet output in \"" + name + "\" failed");
    }
  }

  /** Print a listing of all the types that are stored in this TypeSet. (For debugging!) */
  public void dump(PrintWriter out) {
    out.println("Tycon uses: -----------------------------");
    for (Tycon tycon : tyconInstances.keySet()) {
      out.println("Tycon: " + tycon.getId());
      for (Types ts = tyconInstances.get(tycon); ts != null; ts = ts.next) {
        out.println("   " + ts.head);
      }
    }

    int count = 0;
    for (Type type : otherInstances.keySet()) {
      if (0 == count++) {
        out.println("Other uses: -----------------------------");
      } else {
        out.print(", ");
      }
      out.print(type.toString());
      out.print(" (");
      int num = 0;
      for (Types ts = otherInstances.get(type); ts != null; ts = ts.next) {
        if (0 != num++) {
          out.print(", ");
        }
        out.print(ts.head.toString());
      }
      out.print(")");
    }
    if (count > 0) {
      out.println();
    }

    count = 0;
    for (Object o : litsToTypes.keySet()) {
      if (0 == count++) {
        out.println("Type literals used: ---------------------");
      } else {
        out.print(", ");
      }
      out.print(litsToTypes.get(o).toString());
    }
    if (count > 0) {
      out.println();
    }

    out.println("-----------------------------------------");
  }

  /**
   * A stack of Type values, used to record type constructor arguments while traversing the Type
   * spines.
   */
  private Type[] stack = new Type[10];

  /** The index of the next unused slot in the stack. */
  private int sp = 0;

  /** Push a type on to the stack, expanding it if necessary. */
  protected void push(Type t) {
    if (sp >= stack.length) {
      Type[] nstack = new Type[2 * stack.length];
      for (int i = 0; i < stack.length; i++) {
        nstack[i] = stack[i];
      }
      stack = nstack;
    }
    stack[sp++] = t;
  }

  /** Return the type on the stack corresponding to argument n (the first argument is at n==1). */
  Type stackArg(int n) {
    return stack[sp - n];
  }

  /** Discard the specified number of entries from the top of the stack. We assume n<=sp. */
  protected void drop(int n) {
    sp -= n;
  }

  /**
   * Pop the specified number of arguments from the stack, and store them in a new array. We assume
   * n<=sp.
   */
  protected Type[] pop(int n) {
    if (n == 0) {
      return Type.noTypes;
    } else {
      Type[] ts = new Type[n];
      for (int i = 0; i < n; i++) {
        ts[i] = stack[--sp];
      }
      return ts;
    }
  }

  /**
   * Find a canonical type expression for a type that has Tycon h at its head and arguments given by
   * the top n types on the stack.
   */
  protected Type canon(Tycon h, int args) {
    Types ts = tyconInstances.get(h); // Find previous uses of this item
    Type t = findMatch(args, ts); // And search for a match
    if (t == null) {
      t = rebuild(h.asType(), args); // If none found, build a canonical representative
      tyconInstances.put(h, new Types(t, ts)); // Add it to the list
    }
    return t; // Return the (old or new) canonical representative
  }

  /**
   * Build a canonical type (for the first time that this particular type was found) by combining
   * the specified head type with a number of arguments from the stack.
   */
  private Type rebuild(Type t, int args) {
    for (; args > 0; args--) {
      t = new TAp(t, stack[--sp]);
    }
    return t;
  }

  /**
   * Scan the given list of Types for an entry with the specified number of arguments, each of which
   * matches the corresponding arguments on the top of the stack. If we find a match, then we remove
   * the arguments and return the canonical type.
   */
  private Type findMatch(int args, Types ts) {
    for (; ts != null; ts = ts.next) {
      if (ts.head.matches(this, args)) {
        sp -= args; // remove arguments
        return ts.head; // and return canonical version
      }
    }
    return null;
  }

  /**
   * Find a canonical type expression for a type that has a TVar or a TGen at its head and arguments
   * given by the top n types on the stack. (Any singleton type objects could be used as indices.)
   * We do not expect to encounter cases like this in monomorphic programs produced as a result of
   * specialization, by including support for them here allows us to use TypeSet values with
   * programs that make use of polymorphic types, and also allows the use of TypeSets for rewriting
   * types within type schemes.
   */
  Type canonOther(Type head, int args) {
    if (args == 0) { // If there are no arguments, then head is already
      return head; // a canonical representative
    } else {
      Types ts = otherInstances.get(head); // Find previous uses of this item
      Type t = findMatch(args, ts); // And search for a match
      if (t == null) {
        t = rebuild(head, args); // If none found, build a canonical representative
        otherInstances.put(head, new Types(t, ts)); // Add it to the list
      }
      return t; // Return the (old or new) canonical representative
    }
  }

  /**
   * Find a canonical TLit for a given Object value (either a BigInteger or a String). We assume
   * well-kinded inputs, implying, in particular that we will never encounter a TLit with any
   * arguments.
   */
  TLit canonLit(Object val, TLit n, int args) {
    if (args != 0) {
      debug.Internal.error("kind error: TLits should not have arguments");
    }
    TLit m = litsToTypes.get(val); // Look for a previous use of a TLit with this value
    if (m != null) { // And return it if found
      return m;
    }
    litsToTypes.put(val, n); // Or make it canonical if it's the first occurrence
    return n;
  }

  /**
   * A worker function for canonAllocType: Returns a new array containing canonical versions of the
   * types in ts, using the given set and tenv values.
   */
  Type[] canonTypes(Type[] ts, Type[] tenv) {
    Type[] us = new Type[ts.length];
    for (int i = 0; i < us.length; i++) {
      us[i] = ts[i].canonType(tenv, this);
    }
    return us;
  }

  private HashMap<DataName, DataName> remapDataNames = new HashMap();

  DataName getDataName(DataName dn) {
    return remapDataNames.get(dn);
  }

  void putDataName(DataName dn, DataName newDn) {
    remapDataNames.put(dn, newDn);
  }

  private HashMap<Prim, Prim> primMap = new HashMap();

  Prim getPrim(Prim p) {
    return primMap.get(p);
  }

  void putPrim(Prim p, Prim q) {
    primMap.put(p, q);
  }
}