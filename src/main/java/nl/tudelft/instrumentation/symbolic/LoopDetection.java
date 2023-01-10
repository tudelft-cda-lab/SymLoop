
package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.microsoft.z3.*;

public class LoopDetection {

    private SortedSet<String> foundLoops = new TreeSet<>();
    private SortedSet<String> selfLoops = new TreeSet<>();
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
            return true;
        }
        return false;
    }

    String getAmountQualifier(int min, int max){
        if (max == -1) {
            if(min <= 0) {
                return "*";
            }
            if (min == 1) {
                return "+";
            }
            return String.format("{%d,}", min);
        }
        return String.format("{%d,%d}", min, max);
    }

    Pattern getSelfLoopPattern(String input, int lastN, int minAmount, int maxAmount) {
        int loopIndex = input.length() - lastN;
        String basePart = input.substring(0, loopIndex);
        String loopPart = input.substring(loopIndex);
        String amountQuantifier = getAmountQualifier(minAmount, maxAmount);
        // The ^ makes sure the start is matched, even when using FIND
        String regex = String.format("^(%s)(?:%s)%s", basePart, loopPart, amountQuantifier);
        if (lastN > 1) {
            String end = "";
            for (int i = lastN-2; i >= 0; i--) {
                end = String.format("(%c%s)?", loopPart.charAt(i), end);
            }
            regex += end;
        }
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(input);
        if (minAmount == 1) {
            assert m.matches();
        }
        System.out.println(p);
        return p;
    }

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

        SymbolicExecutionLab.shouldSolve = true;
        if (currentPattern != null) {
            Matcher m = currentPattern.matcher(INPUT);
            boolean isFullMatch = m.matches();
            if (isFullMatch) {
                // System.out.printf("'%s' is still part of current pattern '%s'\n", INPUT, currentPattern);
                // Set it to true on the last input symbol
                SymbolicExecutionLab.shouldSolve = SymbolicExecutionLab.isLastCharacter();
                return false;
            } else {
                // System.out.printf("'%s' not part of current pattern '%s' %d\n", INPUT, currentPattern, INPUT.length());
                currentPattern = null;
            }
        }
        if (isSelfLooping(INPUT)) {
            return true;
        }
        int depth = Math.min(Settings.getInstance().MAX_LOOP_DETECTION_DEPTH + 1, history.getNumberOfSaves());

        for (; lastNSaves <= depth; lastNSaves++) {
            List<Replacement> replacements = history.getReplacementsForLastSaves(lastNSaves);
            BoolExpr loopModel = history.getConstraint(lastNSaves);
            BoolExpr extended = Replacement.applyAllTo(replacements, loopModel);
            BoolExpr selfLoopExpr = history.getSelfLoopExpr(lastNSaves);
            if (lastNSaves > 1 && PathTracker.solve(selfLoopExpr, false, false)) {
                // System.out.println(String.format("EXISTING SELF LOOP: %s, saves: %d", INPUT, lastNSaves));
                currentPattern = getSelfLoopPattern(SymbolicExecutionLab.processedInput, lastNSaves - 1, 1, -1);
                selfLoopPatterns.add(currentPattern);
                return true;
            }

            // If already checked or there is no loop
            if (replacements.size() == 0
                    // || !alreadyChecked.add(lastNSaves + '-' +
                    // SymbolicExecutionLab.processedInput)
                    || !PathTracker.solve(extended, false, false)) {
                continue;
            }

            foundLoops.add(SymbolicExecutionLab.processedInput);

            if (isSelfLoop(replacements, extended)) {
                currentPattern = getSelfLoopPattern(SymbolicExecutionLab.processedInput, lastNSaves - 1, 2, -1);
                if (selfLoops.add(SymbolicExecutionLab.processedInput)) {
                    selfLoopPatterns.add(currentPattern);
                    // SymbolicExecutionLab.printfRed("SELF LOOP DETECTED for %s over %d\n", SymbolicExecutionLab.processedInput, lastNSaves - 1);
                }
                return false;
            }

            String output = replacements.stream().map(Replacement::getName).collect(Collectors.joining(", "));

            SymbolicExecutionLab.printfRed(
                    "loop detected over %d iterations with vars %s: on input '%s'. \n", lastNSaves - 1, output,
                    SymbolicExecutionLab.processedInput);

            extended = history.getExtendedConstraint(lastNSaves, replacements);
            if (isFiniteLoop(extended, replacements)) {
                return false;
            }

            currentPattern = getSelfLoopPattern(SymbolicExecutionLab.processedInput, lastNSaves - 1, 1, Settings.getInstance().LOOP_UNROLLING_AMOUNT-1);
            if (!isLooping(INPUT, loopPatterns)) {
                loopPatterns.add(currentPattern);
            }
            // printLoops();
            return false;
        }
        return false;
    }

    void printLoops() {
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
    }

    boolean isFiniteLoop(BoolExpr extended, List<Replacement> replacements) {
        final int LOOP_UNROLLING_AMOUNT = Settings.getInstance().LOOP_UNROLLING_AMOUNT;
        OptimizingSolver solver = PathTracker.solver;
        solver.push();

        List<BoolExpr> loop = new ArrayList<>();
        loop.add(extended);
        for (int i = 1; i < LOOP_UNROLLING_AMOUNT; i += 1) {
            extended = Replacement.applyAllTo(replacements, extended, i);
            loop.add(extended);
        }
        solver.add(loop.toArray(BoolExpr[]::new));

        Status status = solver.check();
        if(status != Status.SATISFIABLE) {
            solver.pop();
        }
        if (status == Status.UNSATISFIABLE) {
            // SymbolicExecutionLab.printfGreen("loop ends with %s, after %d iterations on model %s\n", status,
            //         i, extended);
            //         //
            // TODO: Create a constraint that goes up to the number of times that it is possible to go through the loop.
            return true;
        } else if (status == Status.UNKNOWN) {
            SymbolicExecutionLab.printfYellow("Solver exited with status: %s\n", status);
            return true;
        }
        assert status == Status.SATISFIABLE;

        List<BoolExpr> onLoop = new ArrayList<>();
        ArithExpr numberOfTimesTheLoopExecutes = (ArithExpr) ctx.mkConst("custom_loop_number_" + (currentLoopNumber++),
                ctx.getIntSort());
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
        PathTracker.addToBranches(oneOfTheLoop);
        return false;
    }
}
