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
import compiler.BuiltinPosition;
import compiler.Failure;
import compiler.Handler;
import compiler.Position;
import core.*;
import java.io.PrintWriter;

public class ClosureDefn extends Defn {

  private String id;

  private Temp[] params;

  private Temp[] args;

  private Tail tail;

  /** Default constructor. */
  public ClosureDefn(Position pos, String id, Temp[] params, Temp[] args, Tail tail) {
    super(pos);
    this.id = id;
    this.params = params;
    this.args = args;
    this.tail = tail;
  }

  private static int count = 0;

  public ClosureDefn(Position pos, Temp[] params, Temp[] args, Tail tail) {
    this(pos, "k" + count++, params, args, tail);
  }

  public ClosureDefn(Position pos, Temp[] args, Tail tail) {
    this(pos, (Temp[]) null, args, tail);
  }

  void setParams(Temp[] params) {
    this.params = params;
  }

  private AllocType declared;

  private AllocType defining;

  /** Get the declared type, or null if no type has been set. */
  public AllocType getDeclared() {
    return declared;
  }

  /** Set the declared type. */
  public void setDeclared(AllocType declared) {
    this.declared = declared;
  }

  /** Return the identifier that is associated with this definition. */
  public String getId() {
    return id;
  }

  public String toString() {
    return id;
  }

  /** Find the list of Defns that this Defn depends on. */
  public Defns dependencies() {
    return tail.dependencies(null);
  }

  String dotAttrs() {
    return "style=filled, fillcolor=salmon";
  }

  void displayDefn(PrintWriter out) {
    if (declared != null) {
      out.println(id + " :: " + declared);
    }

    Call.dump(out, id, "{", params, "} ");
    Atom.displayTuple(out, args);
    out.print(" = ");
    tail.displayln(out);
  }

  AllocType instantiate() {
    return (declared != null) ? declared.instantiate() : defining;
  }

  /**
   * Set the initial type for this definition by instantiating the declared type, if present, or
   * using type variables to create a suitable skeleton. Also sets the types of bound variables.
   */
  void setInitialType() throws Failure {
    // Pick new names for the parameters:
    Temp[] oldps = params;
    params = Temp.makeTemps(oldps.length);

    // Pick new names for the arguments:
    Temp[] oldas = args;
    args = Temp.makeTemps(oldas.length);

    // Update the tail with new temporary names:
    tail = tail.forceApply(TempSubst.extend(oldps, params, TempSubst.extend(oldas, args, null)));

    // Set initial types for temporaries:
    Type[] stored = Type.freshTypes(params);
    Type dom = Type.tuple(Type.freshTypes(args));
    rng = new TVar(Tyvar.tuple);
    Type result = Type.milfun(dom, rng);
    if (declared == null) {
      defining = new AllocType(stored, result);
    } else {
      defining = declared.instantiate();
      defining.storedUnifiesWith(pos, stored);
      defining.resultUnifiesWith(pos, result);
    }
  }

  /**
   * Record the expected type of value that is generated by the tail associated with this closure
   * definition.
   */
  private Type rng;

  /** Type check the body of this definition. */
  void checkBody(Handler handler) throws Failure {
    try {
      checkBody(pos);
    } catch (Failure f) {
      // We can recover from a type error in this definition (at least for long enough to type
      // check other definitions) if the types are all declared (and there is a handler).
      if (allTypesDeclared() && handler != null) {
        handler.report(f); // Of course, we still need to report the failure
        defining = null; // Mark this definition as having failed to check
      } else {
        throw f;
      }
    }
  }

  void checkBody(Position pos) throws Failure {
    tail.inferType(pos).unify(pos, rng);
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
    // !       debug.Log.println("Generalizing definition for: " + getId());
    if (defining != null) {
      TVars gens = defining.tvars();
      generics = TVar.generics(gens, null);
      // !           debug.Log.println("generics: " + showGenerics());
      AllocType inferred = defining.generalize(generics);
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

  void findAmbigTVars(Handler handler, TVars gens) {
    String extras = TVars.listAmbigTVars(tail.tvars(gens), gens);
    if (extras != null) {
      // TODO: do we need to apply a skeleton() method to defining?
      // handler.report(new Failure(pos,  ...));
      debug.Log.println( // TODO: replace this call with the handler above ...
          "Closure definition \""
              + id
              + "\" used at type "
              + defining
              + " with ambiguous type variables "
              + extras);
    }
  }

  /** First pass code generation: produce code for top-level definitions. */
  void generateMain(Handler handler, MachineBuilder builder) {
    /* skip these definitions on first pass */
  }

  /** Second pass code generation: produce code for block and closure definitions. */
  void generateFunctions(MachineBuilder builder) {
    builder.resetFrame();
    builder.setAddr(this, builder.getNextAddr());
    builder.extend(args, 0);
    int o = args.length;
    builder.extend(params, o);
    for (int i = 0; i < params.length; i++) {
      builder.sel(i, o++);
    }
    tail.generateTailCode(builder, o);
  }

  /** Stores the list of closure definitions that have been derived from this definition. */
  private ClosureDefns derived = null;

  public ClosureDefn deriveWithKnownCons(Call[] calls) {
    // !System.out.println("Looking for derived ClosureDefn with Known Cons ");
    // !Call.dump(calls);
    // !System.out.println(" for the Block");
    // !this.displayDefn();
    // !System.out.println();
    // Look to see if we have already derived a suitable version of this ClosureDefn:
    for (ClosureDefns cs = derived; cs != null; cs = cs.next) {
      if (cs.head.hasKnownCons(calls)) {
        // !System.out.println("Found an existing, suitably specialized occurrence of this
        // ClosureDefn");
        // Return pointer to previous occurrence, or decline the request to specialize
        // if the original closure definition already has the requested allocator pattern.
        return (this == cs.head) ? null : cs.head;
      }
    }

    // !System.out.println("Generating a new closure definition");
    // Given this closure definition, this{params} [args] = t, we want to be able to replace a
    // closure allocation
    // for this and a set of known constructors specified by calls[] with corresponding allocations
    // for a
    // specialized closure constructor, k, that is defined by:
    //    k{newparams} [newargs] = b[newparams++newargs]
    //    b[newparams++newargs]  = ... initializers for calls ...
    //                             newtail

    // newargs provides fresh names for args to avoid naming conflicts:
    Temp[] newargs = Temp.makeTemps(args.length);

    // make the new closure definition; the params and tail will be filled in later:
    ClosureDefn k = new ClosureDefnWithKnownCons(/*pos*/ null, newargs, null, calls);
    derived = new ClosureDefns(k, derived);

    // We pick temporary variables for new parameters:
    Temp[][] tss = Call.makeTempsFor(calls);

    // Combine old parameters and new temporaries to calculate newparams:
    if (tss == null) {
      k.params = params; // TODO: safe to reuse params, or should we make a copy?
      k.derived = new ClosureDefns(k, k.derived);
    } else {
      k.params = mergeParams(tss, params);
    }

    // Concatenate k.params and newargs to find parameters for b:
    Temp[] bparams = Temp.append(k.params, newargs);

    // Generate the code for the body of b using a suitably renamed version of tail:
    Tail newtail = tail.apply(TempSubst.extend(args, newargs, null));
    Code bcode = addInitializers(calls, params, tss, new Done(newtail));

    // Make the definition for the new block b:
    Block b = new Block(BuiltinPosition.position, bparams, bcode); // TODO: diff position?

    // Fill in the tail for k:
    k.tail = new BlockCall(b, bparams);

    return k;
  }

  boolean hasKnownCons(Call[] calls) {
    return false;
  }

  /** Apply inlining. */
  public void inlining() {
    // !  System.out.println("==================================");
    // !  System.out.println("Going to try inlining on:");
    // !  displayDefn();
    // !  System.out.println();
    tail = tail.inlineTail();
    // !  System.out.println("And the result is:");
    // !  displayDefn();
    // !  System.out.println();
  }

  void liftAllocators() {
    tail = tail.liftStaticAllocator();
  }

  /**
   * A bitmap that identifies the used arguments of this definition. The base case, with no used
   * arguments, can be represented by a null array. Otherwise, it will be a non null array, the same
   * length as the list of parameters, with true in positions corresponding to arguments that are
   * known to be used and false in all other positions.
   */
  private boolean[] usedArgs = null;

  /**
   * Counts the total number of used arguments in this definition; this should match the number of
   * true values in the usedArgs array.
   */
  private int numUsedArgs = 0;

  /** Reset the bitmap and count for the used arguments of this definition, where relevant. */
  void clearUsedArgsInfo() {
    usedArgs = null;
    numUsedArgs = 0;
  }

  /**
   * Count the number of unused arguments for this definition using the current unusedArgs
   * information for any other items that it references.
   */
  int countUnusedArgs() {
    return countUnusedArgs(params);
  }

  /**
   * Count the number of unused arguments for this definition. A zero count indicates that all
   * arguments are used.
   */
  int countUnusedArgs(Temp[] dst) {
    int unused = dst.length - numUsedArgs; // count # of unused args
    if (unused > 0) { // skip if no unused args
      usedVars = usedVars(); // find vars used in body
      for (int i = 0; i < dst.length; i++) { // scan argument list
        if (usedArgs == null || !usedArgs[i]) { // skip if already known to be used
          if (dst[i].isIn(usedVars) && !duplicated(i, dst)) {
            if (usedArgs == null) { // initialize usedArgs for first use
              usedArgs = new boolean[dst.length];
            }
            usedArgs[i] = true; // mark this argument as used
            numUsedArgs++; // update counts
            unused--;
          }
        }
      }
    }
    return unused;
  }

  private Temps usedVars;

  /**
   * A utility function that returns true if the variable at position i in the given array also
   * appears in some earlier position in the array. (If this condition applies, then we can mark the
   * later occurrence as unused; there is no need to pass the same variable twice.)
   */
  private static boolean duplicated(int i, Temp[] dst) {
    // Did this variable appear in an earlier position?
    for (int j = 0; j < i; j++) {
      if (dst[j] == dst[i]) {
        return true;
      }
    }
    return false;
  }

  /**
   * Find the list of variables that are used in this definition. Variables that are mentioned in
   * BlockCalls or ClosAllocs are only included if the corresponding flag in usedArgs is set.
   */
  Temps usedVars() {
    return tail.usedVars(null);
  }

  /**
   * Find the list of variables that are used in a call to this definition, taking account of the
   * usedArgs setting so that we only include variables appearing in argument positions that are
   * known to be used.
   */
  Temps usedVars(Atom[] args, Temps vs) {
    if (usedArgs != null) { // ignore this call if no args are used
      for (int i = 0; i < args.length; i++) {
        if (usedArgs[i]) { // ignore this argument if the flag is not set
          vs = args[i].add(vs);
        }
      }
    }
    return vs;
  }

  /**
   * Use information about which and how many argument positions are used to trim down an array of
   * destinations (specifically, the formal parameters of a Block or a ClosureDefn).
   */
  Temp[] removeUnusedTemps(Temp[] dsts) {
    // !   System.out.println("In " + getId() + ": numUsedArgs=" + numUsedArgs + ", dsts.length=" +
    // dsts.length);
    if (numUsedArgs < dsts.length) { // Found some new, unused args
      Temp[] newTemps = new Temp[numUsedArgs];
      int j = 0;
      for (int i = 0; i < dsts.length; i++) {
        if (usedArgs != null && usedArgs[i]) {
          newTemps[j++] = dsts[i];
        } else {
          MILProgram.report("removing unused argument " + dsts[i] + " from " + getId());
        }
      }
      return newTemps;
    }
    return dsts; // No newly discovered unused arguments
  }

  /**
   * Update an argument list by removing unused arguments, or return null if no change is required.
   */
  Atom[] removeUnusedArgs(Atom[] args) {
    if (numUsedArgs < args.length) { // Only rewrite if we have found some new unused arguments
      Atom[] newArgs = new Atom[numUsedArgs];
      int j = 0;
      for (int i = 0; i < args.length; i++) {
        if ((usedArgs != null && usedArgs[i])) {
          newArgs[j++] = args[i];
        }
      }
      return newArgs;
    }
    return null; // The argument list should not be changed
  }

  /** Rewrite this program to remove unused arguments in block calls. */
  void removeUnusedArgs() {
    if (numUsedArgs < params.length) {
      MILProgram.report(
          "Rewrote closure definition "
              + getId()
              + " to eliminate "
              + (params.length - numUsedArgs)
              + " unused fields");
      params = removeUnusedTemps(params); // remove unused stored parameters
      if (declared != null) {
        declared = declared.removeStored(numUsedArgs, usedArgs);
      }
    }
    tail = tail.removeUnusedArgs(); // update calls in tail
  }

  public void flow() {
    // TODO: find examples that exercise the next line ...
    tail = tail.rewriteTail(null /* facts */);
    tail.liveness(null /*facts*/);
  }

  /**
   * Compute a Tail that gives the result of entering this closure given the arguments that are
   * stored in the closure (sargs) and the extra function arguments (fargs) that prompted us to
   * enter this closure in the first place.
   */
  Tail withArgs(Atom[] sargs, Atom[] fargs) {
    return tail.forceApply(TempSubst.extend(args, fargs, TempSubst.extend(params, sargs, null)));
  }

  /**
   * Compute an integer summary for a fragment of MIL code with the key property that alpha
   * equivalent program fragments have the same summary value.
   */
  int summary() {
    return id.hashCode();
  }

  /**
   * Compute a summary for this definition (if it is a block or top-level) and then look for a
   * previously encountered item with the same code in the given table. Return true if a duplicate
   * was found.
   */
  boolean summarizeDefns(Blocks[] blocks, TopLevels[] topLevels) {
    return false;
  }

  void eliminateDuplicates() {
    tail.eliminateDuplicates();
  }

  void collect() {
    tail.collect();
  }

  void collect(TypeSet set) {
    declared = declared.canonAllocType(set);
    defining = defining.canonAllocType(set); // TODO: is this needed?  Could it be null?
    Atom.collect(params, set);
    Atom.collect(args, set);
    tail.collect(set);
  }

  /** Update all declared types with canonical versions. */
  void canonDeclared(MILSpec spec) {
    declared = declared.canonAllocType(spec);
  }

  /** Apply constructor function simplifications to this program. */
  void cfunSimplify() {
    tail = tail.removeNewtypeCfun();
  }

  void printlnSig(PrintWriter out) {
    out.println(id + " :: " + declared);
  }

  /** Test to determine if this is an appropriate definition to match the given type. */
  ClosureDefn isClosureDefnOfType(AllocType inst) {
    return declared.alphaEquiv(inst) ? this : null;
  }

  ClosureDefn(ClosureDefn k) {
    this(k.pos, null, null);
  }

  /**
   * Fill in the body of this definition as a specialized version of the given closure definition.
   */
  void specialize(MILSpec spec, ClosureDefn korig) {
    TVarSubst s = korig.declared.specializingSubst(korig.generics, this.declared);
    debug.Log.println(
        "ClosureDefn specialize: "
            + korig.getId()
            + " :: "
            + korig.declared
            + "  ~~>  "
            + this.getId()
            + " :: "
            + this.declared
            + ", generics="
            + korig.showGenerics()
            + ", substitution="
            + s);
    this.params = Temp.specialize(s, korig.params);
    this.args = Temp.specialize(s, korig.args);
    SpecEnv env = new SpecEnv(korig.params, this.params, new SpecEnv(korig.args, this.args, null));
    this.tail = korig.tail.specializeTail(spec, s, env);
  }

  /**
   * Generate a specialized version of an entry point. This requires a monomorphic definition (to
   * ensure that the required specialization is uniquely determined, and to allow the specialized
   * version to share the same name as the original.
   */
  Defn specializeEntry(MILSpec spec) throws Failure {
    if (declared.isQuantified()) {
      throw new PolymorphicEntrypointFailure("closure definition", this);
    }
    ClosureDefn k = spec.specializedClosureDefn(this, declared);
    k.id = this.id; // use the same name as in the original program
    return k;
  }

  void repTransform(Handler handler, RepTypeSet set) {
    Temp[][] npss = Temp.reps(params); // analyze params
    RepEnv env = Temp.extend(params, npss, null); // environment for params
    params = Temp.repParams(params, npss);
    Temp[][] nass = Temp.reps(args); // analyze args
    env = Temp.extend(args, nass, env); // add environment for args
    args = Temp.repParams(args, nass);
    tail = tail.repTransform(set, env);
    declared = declared.canonAllocType(set);
  }

  /**
   * Perform scope analysis on a closure definition, creating new temporaries for each of the
   * (stored) parameters and input arguments, and checking that all of the identifiers in the given
   * tail have a corresponding binding.
   */
  public void inScopeOf(Handler handler, MILEnv milenv, String[] ids, String[] args, CodeExp cexp)
      throws Failure {
    this.params = Temp.makeTemps(ids.length);
    this.args = Temp.makeTemps(args.length);
    this.tail = cexp.toTail(handler, milenv, ids, this.params, args, this.args);
  }

  void addExport(MILEnv exports) {
    exports.addClosureDefn(id, this);
  }

  /**
   * Find the arguments that are needed to enter this definition. The arguments for each block or
   * closure are computed the first time that we visit the definition, but are returned directly for
   * each subsequent call.
   */
  Temp[] addArgs() throws Failure {
    // !     System.out.println("In ClosureDefn " + getId());
    if (params == null) { // compute stored params on first visit
      Temps as = tail.addArgs(null);
      for (int i = 0; i < args.length; i++) {
        as = args[i].removeFrom(as);
      }
      params = Temps.toArray(as);
    }
    return params;
  }

  /** Returns the LLVM type for value that is returned by a function. */
  llvm.Type retType(TypeMap tm) {
    return declared.resultType().retType(tm);
  }

  llvm.Type closurePtrType(TypeMap tm) {
    return tm.toLLVM(declared.resultType());
  }

  llvm.Type codePtrType(TypeMap tm) {
    return closurePtrType(tm).codePtrType();
  }

  /**
   * Calculate the type of a structure describing the layout of a closure for a specific definition.
   */
  llvm.Type closureLayoutTypeCalc(TypeMap tm) {
    return declared.closureLayoutTypeCalc(tm);
  }

  void countCalls() {
    /* no non-tail calls here */
  }

  /**
   * Identify the set of blocks that should be included in the function that is generated for this
   * definition. A block call in the tail for a TopLevel is considered a regular call (it will
   * likely be called from the initialization code), but a block call in the tail for a ClosureDefn
   * is considered a genuine tail call. For a Block, we only search for the components of the
   * corresponding function if the block is the target of a call.
   */
  Blocks identifyBlocks() {
    return tail.identifyBlocks(this, null);
  }

  /** Return a string label that can be used to identify this node. */
  String label() {
    return "clos_" + id;
  }

  CFG makeCFG() {
    ClosureDefnCFG cfg = new ClosureDefnCFG(this);
    cfg.initCFG();
    return cfg;
  }

  /** Find the CFG successors for this definition. */
  Label[] findSuccs(CFG cfg, Node src) {
    return tail.findSuccs(cfg, src);
  }

  llvm.Local[] formals(TypeMap tm, VarMap dvm) {
    llvm.Local[] fs = new llvm.Local[1 + args.length]; // Closure pointer + arguments
    fs[0] = dvm.reg(closurePtrType(tm));
    for (int i = 0; i < args.length; i++) {
      fs[1 + i] = dvm.lookup(tm, args[i]);
    }
    return fs;
  }

  llvm.Code toLLVM(TypeMap tm, DefnVarMap vm, llvm.Local clo, Label[] succs) {
    // Generate code for the tail portion of this closure definition:
    llvm.Code code =
        new llvm.CodeComment(
            "body of closure starts here", vm.loadGlobals(tail.toLLVM(tm, vm, null, succs)));

    if (params.length == 0) { // load closure parameters from memory
      return code;
    } else {
      llvm.Type ptrt = tm.closureLayoutType(this).ptr(); // type identifies components of closure
      llvm.Local ptr = vm.reg(ptrt); // holds a pointer to the closure object
      for (int n = params.length; --n >= 0; ) { // extract stored parameters
        llvm.Local pptr =
            vm.reg(params[n].lookupType(tm).ptr()); // holds pointer to stored parameter
        code =
            new llvm.Op(
                pptr,
                new llvm.Getelementptr(ptr, new llvm.Int(0), new llvm.Int(n + 1)),
                new llvm.Op(vm.lookup(tm, params[n]), new llvm.Load(pptr), code));
      }
      return new llvm.CodeComment(
          "load stored values from closure", new llvm.Op(ptr, new llvm.Bitcast(clo, ptrt), code));
    }
  }

  llvm.Global closureGlobalCalc(TypeMap tm) {
    return new llvm.Global(codePtrType(tm), label());
  }
}