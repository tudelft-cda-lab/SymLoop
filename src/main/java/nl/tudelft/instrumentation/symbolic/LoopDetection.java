
package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.collect.Streams;
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

        public String getName() {
            return name;
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

    boolean isSelfLoop(List<Replacement> replacements, BoolExpr extended) {
        BoolExpr all = ctx.mkTrue();
        for (Replacement r : replacements) {
            all = ctx.mkAnd(r.isSelfLoopExpr(), all);
        }
        if (PathTracker.solve(ctx.mkAnd(all, extended), false, false)) {
            SymbolicExecutionLab.printfRed("SELF LOOP DETECTED for %s\n", SymbolicExecutionLab.processedInput);
            selfLoops.add(SymbolicExecutionLab.processedInput);
            return true;
        }
        return false;
    }

    private Set<BoolExpr> getConstants(List<Replacement> replacements) {
        List<Expr> constantVariables = new ArrayList<Expr>();
        List<Expr> constantValues = new ArrayList<Expr>();
        for (Entry<String, List<Expr>> entry : variables.entrySet()) {
            List<Expr> assigns = entry.getValue();
            String name = entry.getKey();
            int lastLength = lastVariables.get(name);
            Sort s = assigns.get(0).getSort();
            for (int i = lastLength - 1; i < assigns.size(); i++) {
                Expr e = assigns.get(i);
                if (isConstant(e)) {
                    constantVariables.add(ctx.mkConst(ctx.mkSymbol(SymbolicExecutionLab.getVarName(name, i)), s));
                    constantValues.add(e);
                }
            }
        }

        Expr[] constantVariablesArray = constantVariables.toArray(Expr[]::new);
        Expr[] constantValueArray = constantValues.toArray(Expr[]::new);

        Set<BoolExpr> baseConstraints = loopModelList.stream().map(e -> {
            return (BoolExpr) e.substitute(constantVariablesArray, constantValueArray);
        }).map(e -> {
            BoolExpr n = e;
            for (Replacement r : replacements) {
                n = r.applyTo(n);
            }
            if (!n.equals(e)) {
                return n;
            } else {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
        return baseConstraints;
    }

    boolean isLoopDone() {
        if (SymbolicExecutionLab.skip) {
            return false;
        }
        BoolExpr extended = loopModel;
        List<Replacement> replacements = new ArrayList<Replacement>();

        for (String name : variables.keySet()) {
            List<Expr> assigns = variables.get(name);
            int lastLength = lastVariables.get(name);
            int added = assigns.size() - lastLength;
            if (added > 0) {
                Sort s = assigns.get(0).getSort();
                Replacement r = new Replacement(
                        name, s,
                        assigns.size() - 1, added, lastLength - 1);
                replacements.add(r);
                extended = r.applyTo(extended);
            }
        }
        updateLastVariables();

        if (replacements.size() > 0 && alreadyChecked.add(SymbolicExecutionLab.processedInput)
                && PathTracker.solve(extended, false, false)
                && !foundLoops.contains(SymbolicExecutionLab.processedInput)) {
            foundLoops.add(SymbolicExecutionLab.processedInput);
            foundLoops.sort(String::compareTo);

            if (isSelfLoop(replacements, extended)) {
                return false;
            }

            Set<BoolExpr> baseConstraints = getConstants(replacements);
            BoolExpr base = ctx.mkAnd(baseConstraints.toArray(BoolExpr[]::new));

            String output = replacements.stream().map(Replacement::getName).collect(Collectors.joining(", "));

            SymbolicExecutionLab.printfRed(
                    "loop detected with vars %s: on input '%s'. %d / %d constraints need updating\n", output,
                    SymbolicExecutionLab.processedInput,
                    baseConstraints.size(), loopModelList.size());

            // List<BoolExpr> baseConstraints = new ArrayList<BoolExpr>();
            // for (BoolExpr c : needsUpdatingExpr) {
            // for (Replacement r : replacements) {
            // c = r.applyTo(c);
            // }
            // baseConstraints.add(c);
            // }
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
