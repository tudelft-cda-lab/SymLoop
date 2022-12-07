
package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import java.util.stream.Collectors;

import com.microsoft.z3.*;

public class LoopDetection {

    private List<String> foundLoops = new ArrayList<>();
    private List<String> selfLoops = new ArrayList<>();
    private HashSet<String> alreadyChecked = new HashSet<>();
    private Context ctx = PathTracker.ctx;

    private ConstraintHistory history = new ConstraintHistory();

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
        history.reset();
    }

    void assignToVariable(String name, Expr value) {
        history.assignToVariable(name, value);
    }

    boolean isConstant(Expr value) {
        return value.isNumeral();
    }

    void nextInput(BoolExpr inputConstraint) {
        history.nextInput(inputConstraint);
    }

    void addToLoopModel(BoolExpr condition) {
        history.addToLoopModel(condition);
    }

    boolean isSelfLoop(List<Replacement> replacements, BoolExpr extended) {
        BoolExpr all = ctx.mkTrue();
        for (Replacement r : replacements) {
            all = ctx.mkAnd(r.isSelfLoopExpr(), all);
        }
        if (PathTracker.solve(ctx.mkAnd(all, extended), false, false)) {
            return true;
        }
        return false;
    }

    // private Set<BoolExpr> getConstants(List<Replacement> replacements) {
    // List<Expr> constantVariables = new ArrayList<Expr>();
    // List<Expr> constantValues = new ArrayList<Expr>();
    // for (Entry<String, List<Expr>> entry : variables.entrySet()) {
    // List<Expr> assigns = entry.getValue();
    // String name = entry.getKey();
    // int lastLength = getLastVariableLength(name);
    // Sort s = assigns.get(0).getSort();
    // for (int i = lastLength - 1; i < assigns.size(); i++) {
    // Expr e = assigns.get(i);
    // if (isConstant(e)) {
    // constantVariables.add(ctx.mkConst(ctx.mkSymbol(SymbolicExecutionLab.getVarName(name,
    // i)), s));
    // constantValues.add(e);
    // }
    // }
    // }

    // Expr[] constantVariablesArray = constantVariables.toArray(Expr[]::new);
    // Expr[] constantValueArray = constantValues.toArray(Expr[]::new);

    // Set<BoolExpr> baseConstraints = loopModelList.stream().map(e -> {
    // return (BoolExpr) e.substitute(constantVariablesArray, constantValueArray);
    // }).map(e -> {
    // BoolExpr n = e;
    // for (Replacement r : replacements) {
    // n = r.applyTo(n);
    // }
    // if (!n.equals(e)) {
    // return n;
    // } else {
    // return null;
    // }
    // }).filter(Objects::nonNull).collect(Collectors.toSet());
    // return baseConstraints;
    // }

    boolean isIterationLooping() {
        history.save();
        // Due to saving first, the last save is empty, so we go back 2 saves.
        int lastNSaves = 1;
        if (history.getNumberOfSaves() < lastNSaves) {
            // Not enough data to detect loops
            history.save();
            return false;
        }
        int MAX_LOOP_DETECTION_DEPTH = 1;
        int depth = Math.min(MAX_LOOP_DETECTION_DEPTH + 1, history.getNumberOfSaves());

        for (; lastNSaves <= depth; lastNSaves++) {
            List<Replacement> replacements = history.getReplacementsForLastSaves(lastNSaves);
            BoolExpr loopModel = history.getConstraint(lastNSaves);
            BoolExpr extended = Replacement.applyAllTo(replacements, loopModel);

            // If already checked or there is no loop
            if (replacements.size() == 0
                    // || !alreadyChecked.add(lastNSaves + '-' + SymbolicExecutionLab.processedInput)
                    || !PathTracker.solve(extended, false, false)) {
                continue;
            }

            foundLoops.add(SymbolicExecutionLab.processedInput);
            foundLoops.sort(String::compareTo);

            if (isSelfLoop(replacements, extended)) {
                SymbolicExecutionLab.printfRed("SELF LOOP DETECTED for %s\n", SymbolicExecutionLab.processedInput);
                selfLoops.add(SymbolicExecutionLab.processedInput);
                return true;
            }

            String output = replacements.stream().map(Replacement::getName).collect(Collectors.joining(", "));

            SymbolicExecutionLab.printfRed(
                    "loop detected over %d iterations with vars %s: on input '%s'. \n", lastNSaves - 1, output,
                    SymbolicExecutionLab.processedInput);

            extended = history.getExtendedConstraint(lastNSaves, replacements);
            if (isFiniteLoop(extended, replacements)) {
                return false;
            }

            for (String s : foundLoops) {
                SymbolicExecutionLab.printfBlue("%s\n", s);
            }
            // selfLoops.add(SymbolicExecutionLab.processedInput);
            return false;
        }
        return false;
    }

    boolean isFiniteLoop(BoolExpr extended, List<Replacement> replacements) {
        final int AMOUNT = 5;
        Solver solver = ctx.mkSolver();
        solver.add(PathTracker.z3model);
        solver.add(PathTracker.z3branches);
        solver.add(extended);
        List<BoolExpr> loop = new ArrayList<>();
        loop.add(extended);
        for (int i = 1; i < AMOUNT; i += 1) {
            for (Replacement r : replacements) {
                extended = r.applyTo(extended, i);
            }
            solver.add(extended);
            loop.add(extended);
            Status status = solver.check();
            if (status == Status.UNSATISFIABLE) {
                SymbolicExecutionLab.printfGreen("loop ends with %s, after %d iterations on model %s\n", status,
                        i, extended);
                return true;
            } else if (status == Status.UNKNOWN) {
                SymbolicExecutionLab.printfRed("Solver exited with status: %s\n", status);
                System.exit(1);
            }
        }

        List<BoolExpr> onLoop = new ArrayList<>();
        Expr n = ctx.mkConst("n", ctx.getIntSort());
        for (int i = 0; i < AMOUNT; i += 1) {
            List<BoolExpr> thisIteration = new ArrayList<>();
            for (Replacement r : replacements) {
                thisIteration.add(ctx.mkEq(r.getExprAfter(i), r.getExprAfter(AMOUNT + 1)));
                if (i != 0) {
                    this.history.assignToVariable(r.getName(), null);
                }
            }
            thisIteration.add(ctx.mkEq(n, ctx.mkInt(i)));
            onLoop.add(history.mkAnd(thisIteration));
        }
        for (Replacement r : replacements) {
            String name = r.getName();
            String newName = SymbolicExecutionLab.getVarName(name, r.getIndexAfter(0));
            while (SymbolicExecutionLab.nameCounts.get(name) != r.getIndexAfter(AMOUNT + 1) + 1) {
                newName = SymbolicExecutionLab.createVarName(name);
            }
            // for(String name: SymbolicExecutionLab.vars.keySet()) {
            // System.out.printf("%s: %s\n", name,
            // SymbolicExecutionLab.vars.get(name).z3var);
            MyVar v = SymbolicExecutionLab.vars.get(name);
            SymbolicExecutionLab.assign(v, name, ctx.mkConst(ctx.mkSymbol(newName), v.z3var.getSort()),
                    v.z3var.getSort());
            // }
        }
        BoolExpr oneOfTheLoop = history.mkOr(onLoop);
        System.out.println(oneOfTheLoop);
        history.save();
        history.resetNumberOfSave();
        solver.add(oneOfTheLoop);
        assert solver.check() == Status.SATISFIABLE;
        Model model = solver.getModel();
        // System.out.println(solver.getModel());
        System.out.println(model.evaluate(n, true));
        PathTracker.addToBranches(oneOfTheLoop);
        PathTracker.addToBranches(history.mkAnd(loop));
        PathTracker.printModel = true;
        // TODO replace all the occurences of the current variable in MYVARS
        return false;
    }
}
