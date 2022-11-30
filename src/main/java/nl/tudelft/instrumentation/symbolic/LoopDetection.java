
package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import com.microsoft.z3.*;

public class LoopDetection {

    static class Replacement {
        public final String name;
        public final Sort sort;
        public final int start;
        public final int added;
        public final int stop;

        public Replacement(String name, Sort s, int start, int added, int stop) {
            this.name = name;
            this.sort = s;
            this.start = start;
            this.added = added;
            this.stop = stop;
        }

        public BoolExpr applyTo(BoolExpr expr) {
            return this.applyTo(expr, 0);
        }

        public BoolExpr applyTo(BoolExpr expr, int amount) {
            // Loop backwards to prevent repeated subsitution
            for (int i = this.start; i >= this.stop; i--) {
                int base = i + (this.added * amount);
                expr = (BoolExpr) expr.substitute(
                        getExprFor(base),
                        getExprFor(base + this.added));
            }
            return expr;
        }

        public Expr getExprFor(int index) {
            return PathTracker.ctx.mkConst(
                    PathTracker.ctx.mkSymbol(
                            SymbolicExecutionLab.getVarName(this.name, index)),
                    this.sort);
        }

        public BoolExpr isSelfLoopExpr() {
            int i = this.start;
            Expr a = getExprFor(i);
            Expr b = getExprFor(i + this.added);
            return PathTracker.ctx.mkEq(a, b);
        }

        public int getIndexAfter(int amount) {
            return this.start + (this.added * amount);
        }
    }

    private List<String> foundLoops = new ArrayList<>();
    private List<String> selfLoops = new ArrayList<>();
    private HashSet<String> alreadyChecked = new HashSet<>();
    private HashMap<String, List<Expr>> variables = new HashMap<String, List<Expr>>();
    private HashMap<String, Integer> lastVariables = new HashMap<String, Integer>();
    private BoolExpr loopModel = PathTracker.ctx.mkFalse();
    private Context ctx = PathTracker.ctx;

    private List<BoolExpr> loopModelList = new ArrayList<BoolExpr>();

    public LoopDetection() {
    }

    public boolean isLooping(String input) {
        for (String loop : selfLoops) {
            if (input.startsWith(loop)) {
                return true;
            }
        }
        return false;
    }

    public void reset() {
        loopModel = PathTracker.ctx.mkFalse();
        variables.clear();
        lastVariables.clear();
        loopModelList.clear();
    }

    void assignToVariable(String name, Expr value) {
        if (this.variables.containsKey(name)) {
            // System.out.printf("Assign to %s = %s\n",name, value);
            this.variables.get(name).add(value);
        } else {
            // System.out.printf("New assign to %s = %s\n",name, value);
            List<Expr> initial = new ArrayList<Expr>();
            initial.add(value);
            this.variables.put(name, initial);
            this.lastVariables.put(name, 0);
        }
    }

    boolean isConstant(Expr value) {
        return value.isNumeral();
    }

    void nextInput(BoolExpr inputConstraint) {
        loopModel = inputConstraint;
        loopModelList.clear();
        addToLoopModel(inputConstraint);
    }

    void addToLoopModelList(BoolExpr condition) {
        if ((!condition.isConst()) && condition.isAnd()) {
            Expr[] args = condition.getArgs();
            for (Expr arg : args) {
                BoolExpr v = (BoolExpr) arg;
                addToLoopModelList(v);
            }

        } else {
            loopModelList.add(condition);
        }
    }

    void addToLoopModel(BoolExpr condition) {
        addToLoopModelList(condition);
        loopModel = ctx.mkAnd(condition, loopModel);
    }

    void updateLastVariables() {
        for (String name : variables.keySet()) {
            lastVariables.put(name, variables.get(name).size());
        }
    }

    boolean onLoopDone() {
        // System.out.printf("loopmodel: %d %s\n", inputInIndex, loopModel);
        if (SymbolicExecutionLab.skip) {
            return false;
        }
        // loopModel = ctx.mkAnd(loopModelList.toArray(BoolExpr[]::new));
        String output = "";
        boolean isLoop = false;
        BoolExpr extended = loopModel;
        // System.out.println("loopModel: " + loopModel);

        List<Replacement> replacements = new ArrayList<Replacement>();

        // boolean[] needsUpdating = new boolean[loopModelList.size()];
        List<Expr> constantVariables = new ArrayList<Expr>();
        List<Expr> constantValues = new ArrayList<Expr>();

        for (String name : variables.keySet()) {
            List<Expr> assigns = variables.get(name);
            Integer lastLength = lastVariables.get(name);
            int added = assigns.size() - lastLength;
            // System.out.printf("%s: %d, now: %d\n", name, lastLength, assigns.size());
            if (added > 0) {
                output += String.format("%s, ", name);
                isLoop = true;
                Sort s = assigns.get(0).getSort();
                Replacement r = new Replacement(
                        name, s,
                        assigns.size() - 1, added, lastLength - 1);
                replacements.add(r);
                extended = r.applyTo(extended);
                for (int i = r.start; i > r.stop; i--) {
                    if (isConstant(assigns.get(i))) {
                        // TODO: add to constant arrays
                        constantVariables.add(ctx.mkConst(ctx.mkSymbol(SymbolicExecutionLab.getVarName(name, i)), s));
                        constantValues.add(assigns.get(i));
                    }
                }
            }
        }
        updateLastVariables();

        // }

        if (isLoop && alreadyChecked.add(SymbolicExecutionLab.processedInput)
                && PathTracker.solve(extended, false, false)
                && !foundLoops.contains(SymbolicExecutionLab.processedInput)) {
            foundLoops.add(SymbolicExecutionLab.processedInput);
            foundLoops.sort(String::compareTo);

            BoolExpr all = ctx.mkTrue();
            for (Replacement r : replacements) {
                all = ctx.mkAnd(r.isSelfLoopExpr(), all);
            }
            if (PathTracker.solve(ctx.mkAnd(all, extended), false, false)) {
                SymbolicExecutionLab.printfRed("SELF LOOP DETECTED for %s\n", SymbolicExecutionLab.processedInput);
                selfLoops.add(SymbolicExecutionLab.processedInput);
                return false;
            }

            HashSet<BoolExpr> needsUpdatingExpr = new HashSet<BoolExpr>();
            Expr[] constantVariablesArray = constantVariables.toArray(Expr[]::new);
            Expr[] constantValueArray = constantValues.toArray(Expr[]::new);
            for (Replacement r : replacements) {
                for (int i = 0; i < loopModelList.size(); i++) {
                    BoolExpr e = loopModelList.get(i);
                    e = (BoolExpr) e.substitute(constantVariablesArray, constantValueArray);
                    BoolExpr applied = r.applyTo(e);
                    if (!e.equals(applied)) {
                        needsUpdatingExpr.add(e);
                    }
                }
            }

            SymbolicExecutionLab.printfRed(
                    "loop detected with vars %s: on input '%s'. %d / %d constraints need updating\n", output,
                    SymbolicExecutionLab.processedInput,
                    needsUpdatingExpr.size(), loopModelList.size());

            List<BoolExpr> baseConstraints = new ArrayList<BoolExpr>();
            for (BoolExpr c : needsUpdatingExpr) {
                for (Replacement r : replacements) {
                    c = r.applyTo(c);
                }
                baseConstraints.add(c);
            }
            BoolExpr base = ctx.mkAnd(baseConstraints.toArray(BoolExpr[]::new));
            // SymbolicExecutionLab.printfGreen("loopModel: %s\n", loopModel);
            // SymbolicExecutionLab.printfBlue("base: %s\n", base);
            // SymbolicExecutionLab.printfGreen("extended: %s\n", extended);
            System.out.printf("LEN: %d out of %d\n", baseConstraints.size(), loopModelList.size());
            // BoolExpr full = extended;//ctx.mkAnd(loopModel, base);
            extended = base;
            // List<BoolExpr> constraints = new ArrayList<BoolExpr>();
            Solver solver = ctx.mkSolver();
            solver.add(PathTracker.z3model);
            solver.add(PathTracker.z3branches);
            solver.add(extended);
            int nextSolve = 2;
            for (int i = 1; i < 100; i += 1) {
                // constraints.add(extended);
                for (Replacement r : replacements) {
                    extended = r.applyTo(extended, i);
                }
                // System.out.printf("%s\n", extended);
                // full = PathTracker.ctx.mkAnd(extended, full);
                solver.add(extended);
                // if (PathTracker.solve(full, false, false)) {
                if (i >= nextSolve) {
                    if (solver.check() == Status.SATISFIABLE) {
                        nextSolve *= 2;
                    } else {
                        SymbolicExecutionLab.printfGreen("loop ends after %d iterations\n", i);
                        System.exit(1);
                        break;
                    }
                }
            }
            updateLastVariables();

            for (String s : foundLoops) {
                SymbolicExecutionLab.printfBlue("%s\n", s);
            }
        }
        return true;
    }
}
