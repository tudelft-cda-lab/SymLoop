
package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import java.util.Map.Entry;
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
            SymbolicExecutionLab.printfRed("SELF LOOP DETECTED for %s\n", SymbolicExecutionLab.processedInput);
            selfLoops.add(SymbolicExecutionLab.processedInput);
            return true;
        }
        return false;
    }

    // private Set<BoolExpr> getConstants(List<Replacement> replacements) {
    //     List<Expr> constantVariables = new ArrayList<Expr>();
    //     List<Expr> constantValues = new ArrayList<Expr>();
    //     for (Entry<String, List<Expr>> entry : variables.entrySet()) {
    //         List<Expr> assigns = entry.getValue();
    //         String name = entry.getKey();
    //         int lastLength = getLastVariableLength(name);
    //         Sort s = assigns.get(0).getSort();
    //         for (int i = lastLength - 1; i < assigns.size(); i++) {
    //             Expr e = assigns.get(i);
    //             if (isConstant(e)) {
    //                 constantVariables.add(ctx.mkConst(ctx.mkSymbol(SymbolicExecutionLab.getVarName(name, i)), s));
    //                 constantValues.add(e);
    //             }
    //         }
    //     }

    //     Expr[] constantVariablesArray = constantVariables.toArray(Expr[]::new);
    //     Expr[] constantValueArray = constantValues.toArray(Expr[]::new);

    //     Set<BoolExpr> baseConstraints = loopModelList.stream().map(e -> {
    //         return (BoolExpr) e.substitute(constantVariablesArray, constantValueArray);
    //     }).map(e -> {
    //         BoolExpr n = e;
    //         for (Replacement r : replacements) {
    //             n = r.applyTo(n);
    //         }
    //         if (!n.equals(e)) {
    //             return n;
    //         } else {
    //             return null;
    //         }
    //     }).filter(Objects::nonNull).collect(Collectors.toSet());
    //     return baseConstraints;
    // }

    boolean isLoopDone() {
        if (SymbolicExecutionLab.skip) {
            return false;
        }
        int lastNSaves = 1;
        if (history.getNumberOfSaves() < lastNSaves) {
            history.save();
            return true;
        }
        BoolExpr loopModel = history.getConstraint(lastNSaves);
        BoolExpr extended = loopModel;
        List<Replacement> replacements = history.getReplacementsForLastSaves(lastNSaves);

        for (Replacement r : replacements) {
            extended = r.applyTo(extended);
        }

        history.save();

        if (replacements.size() > 0 && alreadyChecked.add(SymbolicExecutionLab.processedInput)
                && PathTracker.solve(extended, false, false)
                && !foundLoops.contains(SymbolicExecutionLab.processedInput)) {
            foundLoops.add(SymbolicExecutionLab.processedInput);
            foundLoops.sort(String::compareTo);

            if (isSelfLoop(replacements, extended)) {
                return false;
            }

            String output = replacements.stream().map(Replacement::getName).collect(Collectors.joining(", "));

            SymbolicExecutionLab.printfRed(
                    "loop detected with vars %s: on input '%s'. %d / %d constraints need updating\n", output,
                    SymbolicExecutionLab.processedInput,
                    -1, -1);
            SymbolicExecutionLab.printfBlue("loopModel: %s\n", loopModel);


            Solver solver = ctx.mkSolver();
            solver.add(PathTracker.z3model);
            solver.add(PathTracker.z3branches);
            solver.add(extended);
            for (int i = 1; i < 100; i += 1) {
                for (Replacement r : replacements) {
                    extended = r.applyTo(extended, i);
                }
                solver.add(extended);
                Status status = solver.check();
                if (status == Status.UNSATISFIABLE) {
                    SymbolicExecutionLab.printfGreen("loop ends with %s, after %d iterations on model %s\n", status, i, extended);
                } else if (status == Status.UNKNOWN){
                    SymbolicExecutionLab.printfRed("status: %s\n", status);
                    System.exit(1);
                }
            }

            for (String s : foundLoops) {
                SymbolicExecutionLab.printfBlue("%s\n", s);
            }
        }
        return true;
    }
}
