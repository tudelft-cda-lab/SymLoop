
package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.microsoft.z3.*;

public class LoopDetection {

    private static final int LOOP_UNROLLING_AMOUNT = 100;
    private static final int MAX_LOOP_DETECTION_DEPTH = 3;

    private List<String> foundLoops = new ArrayList<>();
    private List<String> selfLoops = new ArrayList<>();
    private HashSet<String> alreadyChecked = new HashSet<>();
    private Context ctx = PathTracker.ctx;
    private String inputName = "unknown";

    private ConstraintHistory history = new ConstraintHistory();
    private int currentLoopNumber = 0;
    private Set<Pattern> selfLoopPatterns = new HashSet<>();
    private Set<Pattern> loopPatterns = new HashSet<>();
    private Pattern currentPattern;

    public LoopDetection() {
    }

    public boolean isLooping(String input, Iterable<Pattern> patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(input);
            if (m.find()) {
                // System.out.printf("loop: %s\n", loop);
                return true;
            }
        }
        return false;
    }

    public boolean isSelfLooping(String input) {
        return isLooping(input, selfLoopPatterns);
    }

    public void reset() {
        history.reset();
        PathTracker.loopIterations.clear();
        currentLoopNumber = 0;
        currentPattern = null;
    }

    void assignToVariable(String name, Expr value) {
        history.assignToVariable(name, value);
    }

    boolean isConstant(Expr value) {
        return value.isNumeral();
    }

    void nextInput(BoolExpr inputConstraint, String name) {
        inputName = name;
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
            System.out.println(all);
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
    //
    //
    Pattern getSelfLoopPattern(String input, int lastN) {
        int loopIndex = input.length() - lastN;
        String basePart = input.substring(0, loopIndex);
        String loopPart = input.substring(loopIndex);
        String regex = String.format("^%s(%s)+", basePart, loopPart);
        System.out.printf("%d, %d:'%s' + '%s', '%s'\n", lastN, loopIndex, basePart, loopPart, regex);
        if (lastN > 1) {
            // String end = "(<)?";
            // String end = "((<)(-)?)?";
            // String end = "(((<)(-)?)?)";
            String end = "";
            List<String> s = new ArrayList<>();
            for (int i = 1; i < lastN; i++) {
                s.add(String.format("(%s)", loopPart.substring(0, i)));
            }
            end = String.format("(%s)?", String.join("|", s));
            System.out.println(end);
            regex += end;
        }
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(input);
        assert m.matches();
        return p;
    }

    // String getRegexFor(String input, int lastN, int max_amount) {
    // int loopIndex = input.length() - lastN;
    // String basePart = input.substring(0, loopIndex);
    // String loopPart = input.substring(loopIndex);
    // String regex = String.format("%s(%s){0,%d}", basePart, loopPart, max_amount);
    // System.out.printf("%d, %d:'%s' + '%s', '%s'\n", lastN, loopIndex, basePart,
    // loopPart, regex);
    // Pattern p = Pattern.compile(regex);
    // System.exit(1);
    // return "";
    // // ,max_amount)
    // }

    boolean isIterationLooping() {
        final String INPUT = SymbolicExecutionLab.processedInput;
        history.save();
        // Due to saving first, the last save is empty, so we go back 2 saves.
        int lastNSaves = 1;
        if (history.getNumberOfSaves() < lastNSaves) {
            // Not enough data to detect loops
            history.save();
            return false;
        }

        if (currentPattern != null) {
            System.out.println(INPUT);
            Matcher m = currentPattern.matcher(INPUT);
            boolean isFullMatch = m.matches();
            if (isFullMatch) {
                System.out.printf("Still part of current pattern '%s'\n", currentPattern);
                return false;
            } else {
                System.out.printf("Not part of current pattern '%s' %d\n", currentPattern, INPUT.length());
            }
        }
        if (isSelfLooping(INPUT)) {
            return true;
        }
        int depth = Math.min(MAX_LOOP_DETECTION_DEPTH + 1, history.getNumberOfSaves());

        for (; lastNSaves <= depth; lastNSaves++) {
            List<Replacement> replacements = history.getReplacementsForLastSaves(lastNSaves);
            BoolExpr loopModel = history.getConstraint(lastNSaves);
            BoolExpr extended = Replacement.applyAllTo(replacements, loopModel);

            // If already checked or there is no loop
            if (replacements.size() == 0
                    // || !alreadyChecked.add(lastNSaves + '-' +
                    // SymbolicExecutionLab.processedInput)
                    || !PathTracker.solve(extended, false, false)) {
                continue;
            }

            foundLoops.add(SymbolicExecutionLab.processedInput);
            foundLoops.sort(String::compareTo);

            if (isSelfLoop(replacements, extended)) {
                SymbolicExecutionLab.printfRed("SELF LOOP DETECTED for %s over %d\n",
                        SymbolicExecutionLab.processedInput, lastNSaves - 1);
                selfLoops.add(SymbolicExecutionLab.processedInput);
                selfLoopPatterns.add(getSelfLoopPattern(SymbolicExecutionLab.processedInput, lastNSaves - 1));
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
            currentPattern = getSelfLoopPattern(SymbolicExecutionLab.processedInput, lastNSaves - 1);
            if (!isLooping(INPUT, loopPatterns)) {
                loopPatterns.add(currentPattern);
            }
            List<Pattern> s = new ArrayList<>(loopPatterns);
            Collections.sort(s, Comparator.comparing(Pattern::toString));
            for (Pattern p : s) {
                SymbolicExecutionLab.printfBlue("loop: %s\n", p);
            }

            s = new ArrayList<>(selfLoopPatterns);
            Collections.sort(s, Comparator.comparing(Pattern::toString));
            for (Pattern p : s) {
                SymbolicExecutionLab.printfBlue("selfloop: %s\n", p);
            }
            return false;
        }
        return false;
    }

    boolean isFiniteLoop(BoolExpr extended, List<Replacement> replacements) {
        Solver solver = ctx.mkSolver();
        solver.add(PathTracker.z3model);
        solver.add(PathTracker.z3branches);

        solver.add(extended);
        List<BoolExpr> loop = new ArrayList<>();
        loop.add(extended);
        for (int i = 1; i < LOOP_UNROLLING_AMOUNT; i += 1) {
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
        ArithExpr numberOfTimesTheLoopExecutes = (ArithExpr) ctx.mkConst("custom_loop_number_" + (currentLoopNumber++), ctx.getIntSort());
        for (int i = 0; i < LOOP_UNROLLING_AMOUNT; i += 1) {
            List<BoolExpr> thisIteration = new ArrayList<>();
            for (Replacement r : replacements) {
                thisIteration.add(ctx.mkEq(r.getExprAfter(i), r.getExprAfter(LOOP_UNROLLING_AMOUNT + 1)));
            }
            thisIteration.add(ctx.mkEq(numberOfTimesTheLoopExecutes, ctx.mkInt(i)));
            onLoop.add(history.mkAnd(thisIteration));
        }
        boolean onlyOneInputVariable = false;
        MyVar myNVar = new MyVar(numberOfTimesTheLoopExecutes);
        for (Replacement r : replacements) {
            String varName = r.getName();
            String newName = SymbolicExecutionLab.getVarName(varName, r.getIndexAfter(0));
            while (SymbolicExecutionLab.nameCounts.get(varName) < r.getIndexAfter(LOOP_UNROLLING_AMOUNT + 1) + 1) {
                newName = SymbolicExecutionLab.createVarName(varName);
                this.history.assignToVariable(varName, null);
            }
            MyVar v = SymbolicExecutionLab.vars.get(varName);
            SymbolicExecutionLab.assign(v, varName, ctx.mkConst(ctx.mkSymbol(newName), v.z3var.getSort()),
                    v.z3var.getSort());
            if (r.name.equals(inputName)) {
                assert !onlyOneInputVariable;
                onlyOneInputVariable = true;
                PathTracker.loopIterations.put(myNVar, r);
            }
        }

        PathTracker.solver.minimize(numberOfTimesTheLoopExecutes);
        PathTracker.inputs.add(myNVar);
        BoolExpr oneOfTheLoop = history.mkOr(onLoop);
        history.save();
        history.resetNumberOfSave();
        solver.add(oneOfTheLoop);
        assert solver.check() == Status.SATISFIABLE;
        PathTracker.addToBranches(oneOfTheLoop);
        PathTracker.addToBranches(history.mkAnd(loop));
        System.out.println("new input over: " + currentPattern);
        return false;
    }
}
