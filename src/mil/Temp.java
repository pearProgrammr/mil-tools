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

public class Temp extends Atom {

  private String id;

  private Type type;

  /** Default constructor. */
  public Temp(String id, Type type) {
    this.id = id;
    this.type = type;
  }

  public static final Temp[] noTemps = new Temp[0];

  private static int count = 0;

  public Temp(Type type) {
    this("t" + count++, type);
  }

  public Temp() {
    this(null);
  }

  /** Generate a printable description of this atom. */
  public String toString() {
    return id;
  }

  /** Test for an occurrence of this variable in the given array of atoms. */
  public boolean occursIn(Atom[] as) {
    for (int i = 0; i < as.length; i++) {
      if (as[i] == this) {
        return true;
      }
    }
    return false;
  }

  /** Build a new array that combines the elements from the left array with those from the right. */
  public static Temp[] append(Temp[] left, Temp[] right) {
    int l = left.length;
    int r = right.length;
    Temp[] n = new Temp[l + r];
    for (int i = 0; i < l; i++) {
      n[i] = left[i];
    }
    for (int i = 0; i < r; i++) {
      n[l + i] = right[i];
    }
    return n;
  }

  /** Create a list of new variables of a given length. */
  public static Temp[] makeTemps(int n) {
    Temp[] vs = new Temp[n];
    for (int i = 0; i < n; i++) {
      vs[i] = new Temp();
    }
    return vs;
  }

  boolean isIn(Temps vs) {
    return Temps.isIn(this, vs);
  }

  /**
   * Add this atom as an argument variable to the given list; only local variables and temporaries
   * are treated as argument variables because wildcards are ignored and all other atoms can be
   * accessed as constants.
   */
  public Temps add(Temps vs) {
    return Temps.add(this, vs);
  }

  public Temps removeFrom(Temps vs) {
    return Temps.remove(this, vs);
  }

  public TempSubst mapsTo(Atom a, TempSubst s) {
    return new TempSubst(this, a, s);
  }

  /** Apply a TempSubst to this Atom. */
  public Atom apply(TempSubst s) {
    return TempSubst.apply(this, s);
  }

  /** Return a type for an instantiated version of this item when used as Atom (input operand). */
  public Type instantiate() {
    return type;
  }

  /**
   * Set this variable's type to be a fresh type variable. Used to initialize the type field of a
   * Temp (output operand).
   */
  public Type freshType(Tyvar tyvar) {
    return type = new TVar(tyvar);
  }

  /** Generate code to copy the data for this atom into the specified frame slot. */
  void copyTo(int dst, MachineBuilder builder) {
    builder.copy(builder.lookup(this), dst);
  }

  /** Generate code to load the data for this atom into the value register. */
  void load(MachineBuilder builder) {
    builder.load(builder.lookup(this));
  }

  /**
   * Return the frame slot corresponding to this atom, or (-1) if there is no associated frame slot.
   */
  int frameSlot(MachineBuilder builder) {
    return builder.lookup(this);
  }

  /**
   * Generate code that will load the value of a global atom into a frame slot; if the atom is a
   * Temp instead, then we do not generate any code.
   */
  void copyGlobalTo(int dst, MachineBuilder builder) {
    /* do nothing */
  }

  /** Return true if none of the variables in the given array are live. */
  static boolean noneLive(Temp[] vs) {
    for (int i = 0; i < vs.length; i++) {
      if (vs[i].isLive()) {
        return false;
      }
    }
    return true;
  }

  boolean isLive() {
    return !id.equals("_");
  }

  Temp notLive() {
    return new Temp("_", type);
  }

  public Facts kills(Facts facts) {
    return Facts.kills(this, facts);
  }

  /**
   * Add a fact v = t to the specified list; a fact like this cannot be included if v appears in t.
   * (We don't expect the latter condition to occur often/ever, but should be careful, just in
   * case.)
   */
  public Facts addFact(Tail t, Facts facts) {
    return (t.isRepeatable() && !t.contains(this)) ? new Facts(this, t, facts) : facts;
  }

  public Facts addCfunFact(Cfun cf, Facts facts) {
    return new Facts(this, new DataAlloc(cf), facts);
  }

  public Tail lookupFact(Facts facts) {
    return Facts.lookupFact(this, facts);
  }

  /**
   * Add a fact to indicate that we know which constructor will be used for this Atom, but not what
   * its arguments will be. (Unless the constructor has zero arity.)
   */
  Facts addFact(Cfun cf, Facts facts) {
    Call call = new DataAlloc(cf);
    return addFact((cf.getArity() == 0 ? call.withArgs(Atom.noAtoms) : call), facts);
  }

  /**
   * Test to determine if this Atom is known to hold a specific function value, represented by a
   * ClosAlloc/closure allocator, according to the given set of facts.
   */
  ClosAlloc lookForClosAlloc(Facts facts) {
    Tail t = Facts.lookupFact(this, facts);
    return t == null ? null : t.lookForClosAlloc();
  }

  /**
   * Test to determine if this Atom is known to hold a specific data value represented by a
   * DataAlloc allocator, according to the given set of facts.
   */
  DataAlloc lookForDataAlloc(Facts facts) {
    Tail t = Facts.lookupFact(this, facts);
    return t == null ? null : t.lookForDataAlloc();
  }

  /** Test to see if two atoms are the same upto alpha renaming. */
  boolean alphaAtom(Temps thisvars, Atom that, Temps thatvars) {
    return that.alphaTemp(thatvars, this, thisvars);
  }

  /** Test two items for alpha equivalence. */
  boolean alphaTemp(Temps thisvars, Temp that, Temps thatvars) {
    int thisidx = Temps.lookup(this, thisvars);
    int thatidx = Temps.lookup(that, thatvars);
    return (thisidx == thatidx && (thisidx >= 0 || this.sameAtom(that)));
  }

  /**
   * Update the information that we have recorded about a given formal parameter to reflect the use
   * of this actual parameter. The input, orig, will be one of: - null, indicating that no previous
   * information has been found - a specific Const or Top, indicating that this single value was
   * used in all previous calls - a special value, top, indicating that multiple distinct values
   * have been encountered in previous calls.
   */
  Atom update(Atom orig) {
    return Atom.top;
  }

  void collect(TypeSet set) {
    type = type.canonType(set);
  }

  Atom specializeAtom(MILSpec spec, TVarSubst s, SpecEnv env) {
    return SpecEnv.find(this, env);
  }

  /**
   * Generate a specialized version of this (lambda-bound, monomorphically typed) variable using the
   * same name and the instance of its type under the substitution s.
   */
  public Temp specializeTemp(TVarSubst s) {
    return new Temp(id, type.apply(s));
  }

  /** Generate a specialized list of variables from the given list. */
  static Temp[] specialize(TVarSubst s, Temp[] ds) {
    Temp[] nds = new Temp[ds.length];
    for (int i = 0; i < ds.length; i++) {
      nds[i] = ds[i].specializeTemp(s);
    }
    return nds;
  }

  Type[] repCalc() {
    return type.repCalc();
  }

  Atom[] repArg(RepTypeSet set, RepEnv env) {
    return (type.repCalc() == null) ? null : RepEnv.find(this, env);
  }

  /**
   * Analyze the given list of variables to determine if a change in representation is required. The
   * return result is either null if no change is needed, or else an array deps such that, for each
   * i, either reps[i]==null, meaning that no change is needed in the representation of parameter i,
   * or else reps[i] is an array of zero or more new variables that should be used instead of vs[i]
   * in the transformed program.
   */
  protected static Temp[][] reps(Temp[] vs) {
    Temp[][] reps = null;
    for (int i = 0; i < vs.length; i++) {
      Type[] r = vs[i].repCalc();
      if (r != null) {
        if (reps == null) {
          reps = new Temp[vs.length][];
        }
        reps[i] = Temp.makeTemps(r.length);
      }
    }
    return reps;
  }

  /**
   * Calculate an updated parameter list using the results of a previous call to reps() on that
   * list.
   */
  static Temp[] repParams(Temp[] vs, Temp[][] reps) {
    // Use the original parameter list if there are no changes:
    if (reps == null) {
      return vs;
    }

    // Figure out how long the new list of params should be:
    int len = 0;
    for (int i = 0; i < reps.length; i++) {
      len += (reps[i] == null ? 1 : reps[i].length);
    }

    // Make the new list of parameters:
    Temp[] nvs = new Temp[len];
    int j = 0;
    for (int i = 0; i < reps.length; i++) {
      if (reps[i] == null) {
        nvs[j++] = vs[i];
      } else {
        if (reps[i].length > 0) {
          Temp[] ts = reps[i];
          for (int k = 0; k < ts.length; k++) {
            nvs[j++] = ts[k];
          }
        }
      }
    }
    return nvs;
  }

  /**
   * Extend the given environment to reflect the replacement of certain parameters in vs with the
   * list of variables s specified in the result of a previous call to reps() on vs.
   */
  static RepEnv extend(Temp[] vs, Temp[][] reps, RepEnv env) {
    if (reps != null) {
      for (int i = 0; i < vs.length; i++) {
        if (reps[i] != null) {
          env = new RepEnv(vs[i], reps[i], env);
        }
      }
    }
    return env;
  }

  /**
   * Build a new array that contains the tail of the elements in the input array (i.e., a copy of
   * the input array with all but the 0th element. Assumes that the input array is nonempty!
   */
  public static Temp[] tail(Temp[] xs) {
    Temp[] ys = new Temp[xs.length - 1];
    for (int i = 1; i < xs.length; i++) {
      ys[i - 1] = xs[i];
    }
    return ys;
  }

  /** Make a copy of an array. */
  public static Temp[] clone(Temp[] xs) {
    Temp[] ys = new Temp[xs.length];
    for (int i = 0; i < xs.length; i++) {
      ys[i] = xs[i];
    }
    return ys;
  }

  /** Build a new array that combines the elements from each of the components of the input. */
  public static Temp[] concat(Temp[][] xss) {
    int len = 0;
    for (int i = 0; i < xss.length; i++) {
      len += (xss[i] == null) ? 0 : xss[i].length;
    }
    Temp[] ys = new Temp[len];
    for (int j = 0, i = 0; i < xss.length; i++) {
      if (xss[i] != null) {
        for (int k = 0; k < xss[i].length; k++) {
          ys[j++] = xss[i][k];
        }
      }
    }
    return ys;
  }

  Temp newParam() {
    return new Temp(this.type);
  }

  /** Find the LLVM type for this Temp value. */
  llvm.Type lookupType(TypeMap tm) {
    return tm.toLLVM(type);
  }

  /** Calculate an LLVM Value corresponding to a given MIL argument. */
  llvm.Value toLLVM(TypeMap tm, VarMap vm) {
    return vm.lookup(tm, this);
  }
}