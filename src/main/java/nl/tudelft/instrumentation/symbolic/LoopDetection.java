
package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.microsoft.z3.*;

import nl.tudelft.instrumentation.symbolic.SolverInterface.SolvingForType;
import nl.tudelft.instrumentation.symbolic.exprs.ConstantCustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExprOp;
import nl.tudelft.instrumentation.symbolic.exprs.ExprType;
import nl.tudelft.instrumentation.symbolic.exprs.NamedCustomExpr;

public class LoopDetection {

    private SortedSet<String> foundLoops = new TreeSet<>();
    private SortedSet<String> selfLoops = new TreeSet<>();
    private Context ctx = PathTracker.ctx;
    public String inputName = "unknown";

    protected ConstraintHistory history = new ConstraintHistory();
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

    public boolean containsLoop(String input) {
        return isLooping(input, loopPatterns);
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

    void assignToVariable(String name, CustomExpr value) {
        history.assignToVariable(name, value);
    }

    boolean isConstant(Expr value) {
        return value.isNumeral();
    }

    void nextInput(CustomExpr inputConstraint, String name) {
        inputName = name;
        history.nextInput(inputConstraint);
    }

    void addToLoopModel(CustomExpr condition, boolean isAssign) {
        history.addToLoopModel(condition, isAssign);
    }

    boolean isSelfLoop(List<Replacement> replacements, CustomExpr extended) {
        CustomExpr[] all = new CustomExpr[replacements.size()];
        for (int i = 0; i < replacements.size(); i++) {
            all[i] = replacements.get(i).isSelfLoopExpr();
        }
        if (PathTracker.solve(CustomExprOp.mkAnd(extended, CustomExprOp.mkAnd(all)), SolvingForType.IS_SELF_LOOP, false,
                false)) {
            return true;
        }
        return false;
    }

    String getAmountQualifier(int min, int max) {
        if (max == -1) {
            if (min <= 0) {
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
            for (int i = lastN - 2; i >= 0; i--) {
                end = String.format("(%c%s)?", loopPart.charAt(i), end);
            }
            regex += end;
        }
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(input);
        if (minAmount == 1) {
            assert m.matches();
        }
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

        System.out.println(SymbolicExecutionLab.processedInput);

        if (SymbolicExecutionLab.processedInput.equals(
                String.join("", SymbolicExecutionLab.currentTrace))) {
            System.out.println("CURRENTLY HAS PROCESSED EVERYTING");
        }

        if (SymbolicExecutionLab.isCreatingPaths && SymbolicExecutionLab.processedInputList.size() == SymbolicExecutionLab.save_at_input) {
            SymbolicExecutionLab.indexBefore = history.getNumberOfSaves();
            System.out.printf("CURRENTLY Right before end: %d\n", SymbolicExecutionLab.indexBefore);
            SymbolicExecutionLab.fromVarCounts.add(history.getVariables());
        }

        if (!SymbolicExecutionLab.shouldLoopCheck || SymbolicExecutionLab.isCreatingPaths) {
            return false;
        }

        SymbolicExecutionLab.shouldSolve = true;
        if (currentPattern != null) {
            Matcher m = currentPattern.matcher(INPUT);
            boolean isFullMatch = m.matches();
            if (isFullMatch) {
                System.out.printf("'%s' is still part of current pattern '%s'\n", INPUT,
                        currentPattern);
                // Set it to true on the last input symbol
                SymbolicExecutionLab.shouldSolve = SymbolicExecutionLab.isLastCharacter();
                return false;
            } else {
                System.out.printf("'%s' not part of current pattern '%s' %d\n", INPUT,
                        currentPattern, INPUT.length());
                currentPattern = null;
            }
        }
        if (isSelfLooping(INPUT)) {
            return true;
        }
        Settings s = Settings.getInstance();
        int depth = Math.min(s.MAX_LOOP_DETECTION_DEPTH + 1, history.getNumberOfSaves());

        if (s.VERIFY_LOOP) {
            lastNSaves = s.LOOP_TRACE.length + 1;
            depth = Math.min(depth, lastNSaves);
        }

        for (; lastNSaves <= depth; lastNSaves++) {
            List<Replacement> replacements = history.getReplacementsForLastSaves(lastNSaves);
            CustomExpr loopModel = history.getConstraint(lastNSaves);
            CustomExpr extended = Replacement.applyAllTo(replacements, loopModel);
            CustomExpr selfLoopExpr = history.getSelfLoopExpr(lastNSaves);
            if (lastNSaves > 1 && PathTracker.solve(selfLoopExpr, SolvingForType.IS_SELF_LOOP, false, false)) {
                currentPattern = getSelfLoopPattern(SymbolicExecutionLab.processedInput, lastNSaves - 1, 1, -1);
                System.out.println(String.format("EXISTING SELF LOOP: %s, saves: %d, pattern: %s", INPUT,
                        lastNSaves, currentPattern));
                selfLoopPatterns.add(currentPattern);
                return true;
            }

            // If already checked or there is no loop
            if (replacements.size() == 0
                    // || !alreadyChecked.add(lastNSaves + '-' +
                    // SymbolicExecutionLab.processedInput)
                    || !PathTracker.solve(extended, SolvingForType.IS_LOOP, false, false)) {
                continue;
            }

            foundLoops.add(SymbolicExecutionLab.processedInput);

            if (isSelfLoop(replacements, extended)) {
                currentPattern = getSelfLoopPattern(SymbolicExecutionLab.processedInput, lastNSaves - 1, 1, -1);
                if (selfLoops.add(SymbolicExecutionLab.processedInput)) {
                    selfLoopPatterns.add(currentPattern);
                    SymbolicExecutionLab.printfRed("SELF LOOP DETECTED for %s over %d\n",
                            SymbolicExecutionLab.processedInput, lastNSaves - 1);
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

            currentPattern = getSelfLoopPattern(SymbolicExecutionLab.processedInput, lastNSaves - 1, 1,
                    Settings.getInstance().LOOP_UNROLLING_AMOUNT - 1);
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

    boolean isFiniteLoop(CustomExpr extended, List<Replacement> replacements) {
        int LOOP_UNROLLING_AMOUNT = Settings.getInstance().LOOP_UNROLLING_AMOUNT;
        OptimizingSolver solver = PathTracker.solver;
        solver.push();

        List<CustomExpr> loop = new ArrayList<>();
        loop.add(extended);
        for (int i = 1; i < LOOP_UNROLLING_AMOUNT; i += 1) {
            extended = Replacement.applyAllTo(replacements, extended, i);
            loop.add(extended);
        }
        CustomExpr unrolledConstrained = CustomExprOp.mkAnd(loop.toArray(CustomExpr[]::new));
        solver.add(unrolledConstrained);

        Status status = solver.check(SolvingForType.IS_REATING_LOOP);
        if (status != Status.SATISFIABLE) {
            solver.pop();
        }
        if (status == Status.UNSATISFIABLE) {
            if (Settings.getInstance().DO_HALF_LOOPS) {
                solver.push();
                int maxPossible = 0;
                for (int i = 0; i < LOOP_UNROLLING_AMOUNT; i += 1) {
                    solver.add(loop.get(i));
                    status = solver.check(SolvingForType.IS_REATING_LOOP);
                    if (Status.SATISFIABLE != status) {
                        System.out.println();
                        LOOP_UNROLLING_AMOUNT = i - 1;
                        maxPossible = i - 1;
                        break;
                    }
                }
                solver.pop();
                solver.add(CustomExprOp.mkAnd(loop.subList(0, maxPossible).toArray(CustomExpr[]::new)));
                status = solver.check(SolvingForType.IS_REATING_LOOP);
                assert status == Status.SATISFIABLE;
            } else {
                return true;
            }

        } else if (status == Status.UNKNOWN) {
            SymbolicExecutionLab.printfYellow("Solver exited with status: %s\n", status);
            return true;
        }
        assert status == Status.SATISFIABLE;

        List<CustomExpr> onLoop = new ArrayList<>();
        CustomExpr numberOfTimesTheLoopExecutes = new NamedCustomExpr("custom_loop_number_" + (currentLoopNumber++),
                ExprType.INT);
        for (int i = 0; i < LOOP_UNROLLING_AMOUNT; i += 1) {
            List<CustomExpr> thisIteration = new ArrayList<>();
            for (Replacement r : replacements) {
                thisIteration.add(CustomExprOp.mkEq(r.getExprAfter(i), r.getExprAfter(LOOP_UNROLLING_AMOUNT + 1)));
            }
            thisIteration.add(CustomExprOp.mkEq(numberOfTimesTheLoopExecutes, ConstantCustomExpr.fromInt(i)));
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
            SymbolicExecutionLab.assign(v, varName, new NamedCustomExpr(newName, v.expr.type));
            if (r.name.equals(inputName)) {
                assert !onlyOneInputVariable;
                onlyOneInputVariable = true;
                PathTracker.loopIterations.put(myNVar, r);
            }
        }
        if (Settings.getInstance().MINIMIZE) {
            PathTracker.solver.minimize(numberOfTimesTheLoopExecutes.toArithExpr());
        }
        PathTracker.inputs.add(myNVar);
        CustomExpr oneOfTheLoop = history.mkOr(onLoop);
        history.save();
        history.resetNumberOfSave();
        PathTracker.addToBranches(oneOfTheLoop);
        SymbolicExecutionLab.numberOfLoopsInPathConstraint += 1;


        return false;
    }
}
