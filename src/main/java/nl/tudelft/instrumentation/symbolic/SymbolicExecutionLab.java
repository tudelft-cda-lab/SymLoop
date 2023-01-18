package nl.tudelft.instrumentation.symbolic;

import java.util.*;

import com.microsoft.z3.*;

import nl.tudelft.instrumentation.fuzzing.BranchVisitedTracker;
import nl.tudelft.instrumentation.symbolic.SolverInterface.SolvingForType;
import nl.tudelft.instrumentation.symbolic.exprs.ConstantCustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExprOp;
import nl.tudelft.instrumentation.symbolic.exprs.NamedCustomExpr;

/**
 * You should write your solution using this class.
 */
public class SymbolicExecutionLab {

    static final int PRINT_EVERY = 2000;
    static final int INITIAL_TRACE_LENGTH = 1;
    static Random r = new Random(1);
    static Boolean isFinished = false;
    static List<String> currentTrace;
    static PriorityQueue<NextTrace> nextTraces = new PriorityQueue<>();
    static PriorityQueue<NextTrace> backLog = new PriorityQueue<>();
    static BranchVisitedTracker branchTracker = new BranchVisitedTracker();
    static BranchVisitedTracker currentBranchTracker = new BranchVisitedTracker();
    static ErrorTracker errorTracker = new ErrorTracker();
    static final long START_TIME = System.currentTimeMillis();
    static long lastPrint = System.currentTimeMillis();
    static boolean printThisRun = true;

    static Set<String> errorTraces = new HashSet<>();

    private static int currentLineNumber = 0;
    private static boolean currentValue;

    private static HashSet<String> alreadySolvedBranches = new HashSet<>();
    private static HashSet<String> alreadyFoundTraces = new HashSet<>();

    static int inputInIndex = 0;
    static String processedInput = "";

    static boolean skip = false;
    static LoopDetection loopDetector = new LoopDetection();

    static HashMap<String, Integer> nameCounts = new HashMap<String, Integer>();

    public static HashMap<String, MyVar> vars = new HashMap<>();

    public static boolean shouldSolve = true;

    public static int numberOfLoopsInPathConstraint = 0;

    private static Profiling profiler = new Profiling();

    static void initialize(String[] inputSymbols) {
        // Initialise a random trace from the input symbols of the problem.
        String[] initial = Settings.getInstance().INITIAL_TRACE;
        if (initial == null) {
            currentTrace = generateRandomTrace(inputSymbols);
        } else {
            currentTrace = Arrays.asList(initial);
        }
    }

    static boolean isLastCharacter() {
        return processedInput.length() >= currentTrace.size() - 2;
    }

    static String createVarName(String name) {
        Integer count = nameCounts.getOrDefault(name, 0);
        // System.out.printf("createVarName: %s, %d\n", name, count);
        nameCounts.put(name, count + 1);
        return getVarName(name, count);
    }

    static String getVarName(String name, int index) {
        return name + "_" + index;
    }

    static MyVar createVar(String name, CustomExpr expr) {
        // System.out.printf("create var (%s): %s\n",name, expr.toZ3());
        /**
         * Create var, assign value and add to path constraint.
         * We show how to do it for creating new symbols, please
         * add similar steps to the functions below in order to
         * obtain a path constraint.
         */
        loopDetector.assignToVariable(name, expr);
        String newName = createVarName(name);
        CustomExpr var = new NamedCustomExpr(newName, expr.type);

        PathTracker.addToModel(CustomExprOp.mkEq(var, expr));
        MyVar myVar = new MyVar(name, var);
        vars.put(name, myVar);
        return myVar;
    }

    static MyVar createInput(String name, ConstantCustomExpr expr) {
        if (!skip && loopDetector.isIterationLooping()) {
            printfRed("STOPPING AT %s\n", processedInput);
            skip = true;
        }
        // Create an input var, these should be free variables!
        // Do not add it to the model
        CustomExpr intermediate = new NamedCustomExpr(createVarName(name), expr.type);
        // intermediate = c.mkEq(intermediate, value);
        MyVar input = new MyVar(name, intermediate);
        vars.put(name, input);
        PathTracker.inputs.add(input);

        // restrict inputs to the valid input symbols found in PathTracker.inputSymbols
        CustomExpr[] temp = new CustomExpr[PathTracker.inputSymbols.length];
        for (int i = 0; i < PathTracker.inputSymbols.length; i++) {
            temp[i] = CustomExprOp.mkEq(ConstantCustomExpr.fromString(PathTracker.inputSymbols[i]), intermediate);
        }

        loopDetector.assignToVariable(name, intermediate);
        CustomExpr orred = CustomExprOp.mkOr(temp);
        PathTracker.addToModel(orred);
        // loopModel = PathTracker.ctx.mkTrue();
        // loopModel = c.mkAnd(c.mkOr(temp), loopModel);
        loopDetector.nextInput(orred, name);

        assert inputInIndex < currentTrace.size();
        // System.out.printf("inputInIndex: %d\n", inputInIndex);
        String next = currentTrace.get(inputInIndex);
        assert next.equals(expr.value);
        processedInput += next;
        inputInIndex++;

        return input;
    }

    static MyVar createBoolExpr(CustomExpr var, String operator) {
        // Any unary expression (!)
        if (operator.equals("!")) {
            CustomExpr c = CustomExprOp.mkNot(var);
            return new MyVar(c);
        }
        throw new IllegalArgumentException(String.format("unary operator: %s not implement", operator));
    }

    static MyVar createBoolExpr(CustomExpr left_var, CustomExpr right_var, String operator) {
        // Any binary expression (&, &&, |, ||)
        if (operator.equals("&&") || operator.equals("&")) {
            return new MyVar(CustomExprOp.mkAnd(left_var, right_var));
        } else if (operator.equals("||") || operator.equals("|")) {
            return new MyVar(CustomExprOp.mkOr(left_var, right_var));
        }
        throw new IllegalArgumentException(String.format("binary operator: %s not implement", operator));
    }

    static MyVar createIntExpr(CustomExpr var, String operator) {
        // Any unary expression (+, -)
        if (operator.equals("+")) {
            return new MyVar(var);
        } else if (operator.equals("-")) {
            return new MyVar(CustomExprOp.mkUnaryMinus(var));
        }

        throw new IllegalArgumentException(String.format("unary int expression: %s not implement", operator));
    }

    static MyVar createIntExpr(CustomExpr left_var, CustomExpr right_var, String operator) {
        // Any binary expression (+, -, /, etc)
        if (operator.equals("==")) {
            return new MyVar(CustomExprOp.mkEq(left_var, right_var));
        } else if (operator.equals("<=")) {
            return new MyVar(CustomExprOp.mkLe(left_var, right_var));
        } else if (operator.equals(">=")) {
            return new MyVar(CustomExprOp.mkGe(left_var, right_var));
        } else if (operator.equals("<")) {
            return new MyVar(CustomExprOp.mkLt(left_var, right_var));
        } else if (operator.equals(">")) {
            return new MyVar(CustomExprOp.mkGt(left_var, right_var));
        } else if (operator.equals("*")) {
            return new MyVar(CustomExprOp.mkMul(left_var, right_var));
        } else if (operator.equals("-")) {
            return new MyVar(CustomExprOp.mkSub(left_var, right_var));
        } else if (operator.equals("+")) {
            return new MyVar(CustomExprOp.mkAdd(left_var, right_var));
        } else if (operator.equals("%")) {
            return new MyVar(CustomExprOp.mkMod(left_var, right_var));
        } else if (operator.equals("/")) {
            return new MyVar(CustomExprOp.mkDiv(left_var, right_var));
        }
        throw new IllegalArgumentException(String.format("binary int expression: %s not implement", operator));
    }

    static ArithExpr mkAbs(Context ctx, ArithExpr a) {
        return (ArithExpr) ctx.mkITE(ctx.mkGe(a, ctx.mkInt(0)), a, ctx.mkUnaryMinus(a));
    }

    static ArithExpr mkSign(Context ctx, ArithExpr a) {
        return (ArithExpr) ctx.mkITE(
                ctx.mkEq(a, ctx.mkInt(0)),
                ctx.mkInt(0),
                ctx.mkITE(
                        ctx.mkGt(a, ctx.mkInt(0)),
                        ctx.mkInt(1),
                        ctx.mkInt(-1)));
    }

    static MyVar createStringExpr(CustomExpr left_var, CustomExpr right_var, String operator) {
        // We only support String.equals
        // return new MyVar(PathTracker.ctx.mkFalse());
        if (operator.equals("==")) {
            return new MyVar(CustomExprOp.mkEq(left_var, right_var));

        }
        throw new IllegalArgumentException(String.format("string operator: %s not implement", operator));
    }

    static void assign(MyVar var, String name, CustomExpr expr) {
        // System.out.printf("MyVar (%s): %s \n %s\n", name, var.z3var(), expr.toZ3());
        // All variable assignments, use single static assignment
        String newName = createVarName(name);
        CustomExpr customVar = new NamedCustomExpr(newName, expr.type);
        loopDetector.assignToVariable(name, expr);
        var.assign(customVar);
        CustomExpr eq = CustomExprOp.mkEq(customVar, expr);
        PathTracker.addToModel(eq);
        loopDetector.addToLoopModel(eq);
    }

    static void encounteredNewBranch(MyVar condition, boolean value, int line_nr) {
        String newPathString = String.format("%s_%d", processedInput, line_nr);
        currentLineNumber = line_nr;
        currentValue = value;
        branchTracker.visit(line_nr, value);
        currentBranchTracker.visit(line_nr, value);
        if (skip) {
            return;
        }
        if (
        // currentTrace.size() <= processedInput.length() &&
        shouldSolve &&
                alreadySolvedBranches.add(newPathString)) {
            // Call the solver
            PathTracker.solve(CustomExprOp.mkEq(ConstantCustomExpr.fromBool(!value), condition.expr),
                    SolvingForType.BRANCH, false, true);
        }
        CustomExpr branchCondition = condition.expr;
        if (!value) {
            branchCondition = CustomExprOp.mkNot(branchCondition);
        }
        PathTracker.addToBranches(branchCondition);
        loopDetector.addToLoopModel(branchCondition);
    }

    static boolean isStillUsefull(Iterable<String> input) {
        String s = String.join("", input);
        if (errorTraces.contains(s)) {
            // printfRed("contained in  errorTraces: %s\n", s);
            return false;
        }
        for (String e : errorTraces) {
            if (s.startsWith(e)) {
                // printfRed("startswith error: %s\n", e);
                return false;
            }
        }
        if (loopDetector.isSelfLooping(s)) {
            // printfRed("isSelfLooping: %s\n", s);
            return false;
        }
        return true;
    }

    static void newSatisfiableInput(LinkedList<String> new_inputs, String output) {
        // Hurray! found a new branch using these new inputs!
        LinkedList<String> temp = new LinkedList<String>();
        for (String s : new_inputs) {
            temp.add(s.replaceAll("\"", ""));
        }

        // Add a random input at the end to allow solving new paths
        String alreadyFound = String.join("", temp);
        if (!skip && alreadyFoundTraces.add(alreadyFound) && isStillUsefull(temp)) {
            // System.out.printf("New satisfiable input: %s\n", temp);
            temp.add(newRandomInputChar());
            // temp.add("A");
            String newInput = String.join("", temp);
            if (!loopDetector.isSelfLooping(newInput)) {
                add(new NextTrace(temp, currentLineNumber,
                        String.join(" ", currentTrace) + "\n" + output, !currentValue));
            } else {
                printfGreen("PART OF LOOP: %s\n", newInput);
            }
        }

    }

    static String newRandomInputChar() {
        return PathTracker.inputSymbols[r.nextInt(PathTracker.inputSymbols.length)];
    }

    /**
     * Method for fuzzing new inputs for a program.
     * 
     * @param inputSymbols the inputSymbols to fuzz from.
     * @return a fuzzed sequence
     */
    static List<String> fuzz(String[] inputSymbols) {
        /*
         * Add here your code for fuzzing a new sequence for the RERS problem.
         * You can guide your fuzzer to fuzz "smart" input sequences to cover
         * more branches using symbolic execution. Right now we just generate
         * a complete random sequence using the given input symbols. Please
         * change it to your own code.
         */
        return generateRandomTrace(inputSymbols);
    }

    /**
     * Generate a random trace from an array of symbols.
     * 
     * @param symbols the symbols from which a trace should be generated from.
     * @return a random trace that is generated from the given symbols.
     */
    static List<String> generateRandomTrace(String[] symbols) {
        ArrayList<String> trace = new ArrayList<>();
        for (int i = 0; i < INITIAL_TRACE_LENGTH; i++) {
            trace.add(symbols[r.nextInt(symbols.length)]);
        }
        return trace;
    }

    static void reset() {
        PathTracker.reset();
        loopDetector.reset();
        nameCounts.clear();
        System.gc();
        currentLineNumber = 0;
        inputInIndex = 0;
        processedInput = "";
        currentBranchTracker.clear();
        skip = false;
        shouldSolve = true;
        numberOfLoopsInPathConstraint = 0;
    }

    static boolean isEmpty() {
        return nextTraces.isEmpty() && backLog.isEmpty();
    }

    static void add(NextTrace trace) {
        if (branchTracker.hasVisitedBoth(trace.getLineNr())) {
            backLog.add(trace);
        } else {
            nextTraces.add(trace);
        }
    }

    static NextTrace getNext() {
        assert !isEmpty();
        if (nextTraces.isEmpty()) {
            return backLog.poll();
        } else {
            NextTrace trace = nextTraces.poll();
            if (branchTracker.hasVisitedBoth(trace.getLineNr())) {
                backLog.add(trace);
                return getNext();
            } else {
                return trace;
            }
        }
    }

    static void checkIfSolverIsRight(NextTrace trace) {
        // Checking if the solver is actually right
        if ((!currentBranchTracker.hasVisited(trace.getLineNr(), trace.getConditionValue()))
                && trace.getLineNr() != 0) {
            if (Settings.getInstance().CORRECT_INTEGER_MODEL) {
                printfRed("SOLVER IS WRONG, did not discover the solvable branch, %s\n", trace.from);
                System.out.printf("%d TRUE: %b, FALSE: %b\n", trace.getLineNr(),
                        currentBranchTracker.hasVisited(trace.getLineNr(), true),
                        currentBranchTracker.hasVisited(trace.getLineNr(), false));
                System.exit(-1);
            } else {
                printfRed(
                        "Current integer model is not sufficient, try enabling correct integer model using '-correct-integer-model', %s\n",
                        trace.from);
            }
        }
    }

    static long timeLeftMillis() {
        Settings settings = Settings.getInstance();
        if (settings.MAX_TIME_S == -1) {
            return 99999999999999999l;
        }
        long elapsed = (System.currentTimeMillis() - START_TIME);
        return (settings.MAX_TIME_S * 1000) - elapsed;
    }

    static boolean timeLimitReached() {
        return timeLeftMillis() < 0;
    }

    static void run(String[] args) {
        Settings s = Settings.create(args);
        System.out.println(s.parameters());
        initialize(PathTracker.inputSymbols);
        nextTraces.add(new NextTrace(currentTrace, currentLineNumber, "<initial>", false));
        while (!isFinished && !isEmpty() && !timeLimitReached()) {
            reset();
            NextTrace trace = getNext();
            runNext(trace);
            isFinished = branchTracker.visitedAll();
            printThisRun = shouldPrint();
        }
        if (timeLimitReached()) {
            printfYellow("TIME LIMIT REACHED\n");
        }
        if (branchTracker.visitedAll()) {
            printfGreen("All paths visited, exiting now\n");
        }
        printFinalStatus();
        profiler.saveSolverTimes();
        // saveTraces();
        // saveGraph(true);
        System.exit(0);
    }

    public static boolean shouldPrint() {
        long current = System.currentTimeMillis();
        if (current - lastPrint > PRINT_EVERY) {
            lastPrint = current;
            return true;
        }
        return false;
    }

    public static void runNext(NextTrace trace) {
        if (!isStillUsefull(trace.trace)) {
            return;
        }
        printfYellow("now doing line: %d, %b, %s\n", trace.getLineNr(), trace.getConditionValue(),
                trace.trace);
        currentTrace = trace.trace;
        boolean completed = PathTracker.runNextFuzzedSequence(currentTrace.toArray(new String[0]));
        if (!skip && completed) {
            loopDetector.isIterationLooping();
        }
        if (completed) {
            checkIfSolverIsRight(trace);
            // saveTraces();
            // saveGraph(false);
            if (printThisRun) {
                printStatus();
            }
        }
    }

    public static void printStatus() {
        System.out.printf("Visited: %d out of %d, #errors: %d, #nextTraces: %d, #backlog: %d, Errors:\n%s\n",
                branchTracker.numVisited(),
                branchTracker.totalBranches(), errorTracker.amount(), nextTraces.size(), backLog.size(),
                errorTracker.summary());
    }

    public static void printFinalStatus() {
        printStatus();
        // System.out.println(errorTracker.summary());
    }

    public static void printfGreen(String a, Object... args) {
        printfColor("\u001B[32m", a, args);
    }

    public static void printfRed(String a, Object... args) {
        printfColor("\u001B[31m", a, args);
    }

    public static void printfColor(String color, String a, Object... args) {
        System.out.print(color);
        System.out.printf(a, args);
        System.out.print("\033[0m");
    }

    public static void printfYellow(String a, Object... args) {
        printfColor("\u001B[33m", a, args);
    }

    public static void printfBlue(String a, Object... args) {
        printfColor("\u001B[34m", a, args);
    }

    public static void output(String out) {
        if (errorTracker.add(out)) {
            // System.out.printf("%sFound new error, current amount is: %d.%s\n",
            // ANSI_GREEN, errorTracker.amount(), ANSI_RESET);
            long current = System.currentTimeMillis();
            long seconds = (current - START_TIME) / 1000;
            printfGreen("Found new error '%s', current amount is\t%d\t. in \t%d\t seconds\n", out,
                    errorTracker.amount(),
                    seconds);

        }
        if (out.contains("Invalid")) {
            errorTraces.add(SymbolicExecutionLab.processedInput);
            skip = true;
            if (!errorTracker.isError(out) && !out.contains("Current state has no transition for this input!")) {
                System.out.println(out);
            }
        }
    }

}
