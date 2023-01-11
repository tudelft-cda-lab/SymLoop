package nl.tudelft.instrumentation.symbolic;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.microsoft.z3.*;

import nl.tudelft.instrumentation.fuzzing.BranchVisitedTracker;
import nl.tudelft.instrumentation.symbolic.OptimizingSolver.DataPoint;
import nl.tudelft.instrumentation.symbolic.SolverInterface.SolvingForType;

/**
 * You should write your solution using this class.
 */
public class SymbolicExecutionLab {

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

    static Set<String> errorTraces = new HashSet<>();

    private static int currentLineNumber = 0;
    private static boolean currentValue;

    private static HashSet<String> alreadySolvedBranches = new HashSet<>();
    private static HashSet<String> alreadyFoundTraces = new HashSet<>();

    static int firstBranchLineNr = -1;
    static int inputInIndex = 0;
    static String processedInput = "";

    static String path = "";
    static boolean skip = false;
    static boolean changed = false;
    static LoopDetection loopDetector = new LoopDetection();

    static HashMap<String, Integer> nameCounts = new HashMap<String, Integer>();

    private static MutableGraph<String> graph = GraphBuilder.directed().allowsSelfLoops(true).build();
    private static Map<String, String> edges = new HashMap<String, String>();

    private static String pathString = "Start";

    public static HashMap<String, MyVar> vars = new HashMap<>();

    public static boolean shouldSolve = true;

    public static int numberOfLoopsInPathConstraint = 0;

    private static BufferedWriter solverTimesWriter;

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

    static MyVar createVar(String name, Expr value, Sort s) {
        Context c = PathTracker.ctx;
        /**
         * Create var, assign value and add to path constraint.
         * We show how to do it for creating new symbols, please
         * add similar steps to the functions below in order to
         * obtain a path constraint.
         */
        loopDetector.assignToVariable(name, value);
        Expr z3var = c.mkConst(c.mkSymbol(createVarName(name)), s);
        PathTracker.addToModel(c.mkEq(z3var, value));
        // loopModel = c.mkAnd(c.mkEq(z3var, value), loopModel);
        MyVar myVar = new MyVar(z3var, name);
        vars.put(name, myVar);
        return myVar;
    }

    private static String input = "";
    private static String output = "";
    private static List<String> fullTrace = new ArrayList<>();
    private static List<String> fullTraces = new ArrayList<>();
    private static boolean invalid;

    static void addToFullTrace() {
        if (!input.isEmpty() && !invalid) {
            fullTrace.add(String.format("%s/%s", input, output));
        }
    }

    static MyVar createInput(String name, Expr value, Sort s) {
        if (!skip && loopDetector.isIterationLooping()) {
            printfRed("STOPPING AT %s\n", processedInput);
            skip = true;
        }
        addToFullTrace();
        input = value.toString().replaceAll("\"", "");
        output = "";
        // Create an input var, these should be free variables!
        // Do not add it to the model
        Context c = PathTracker.ctx;
        Expr intermediate = c.mkConst(c.mkSymbol(createVarName(name)), s);
        // intermediate = c.mkEq(intermediate, value);
        MyVar input = new MyVar(intermediate, name);
        vars.put(name, input);
        PathTracker.inputs.add(input);

        // restrict inputs to the valid input symbols found in PathTracker.inputSymbols
        BoolExpr[] temp = new BoolExpr[PathTracker.inputSymbols.length];
        for (int i = 0; i < PathTracker.inputSymbols.length; i++) {
            temp[i] = c.mkEq(c.mkString(PathTracker.inputSymbols[i]), intermediate);
        }

        loopDetector.assignToVariable(name, intermediate);
        PathTracker.addToModel(c.mkOr(temp));
        // loopModel = PathTracker.ctx.mkTrue();
        // loopModel = c.mkAnd(c.mkOr(temp), loopModel);
        loopDetector.nextInput(c.mkOr(temp), name);
        return input;
    }

    static MyVar createBoolExpr(BoolExpr var, String operator) {
        // Any unary expression (!)
        if (operator.equals("!")) {
            return new MyVar(PathTracker.ctx.mkNot(var));
        }
        throw new IllegalArgumentException(String.format("unary operator: %s not implement", operator));
    }

    static MyVar createBoolExpr(BoolExpr left_var, BoolExpr right_var, String operator) {
        // Any binary expression (&, &&, |, ||)
        if (operator.equals("&&") || operator.equals("&")) {
            return new MyVar(PathTracker.ctx.mkAnd(left_var, right_var));
        } else if (operator.equals("||") || operator.equals("|")) {
            return new MyVar(PathTracker.ctx.mkOr(left_var, right_var));
        }
        throw new IllegalArgumentException(String.format("binary operator: %s not implement", operator));
    }

    static MyVar createIntExpr(IntExpr var, String operator) {
        // Any unary expression (+, -)
        if (operator.equals("+")) {
            return new MyVar(var);
        } else if (operator.equals("-")) {
            return new MyVar(PathTracker.ctx.mkUnaryMinus(var));
        }

        throw new IllegalArgumentException(String.format("unary int expression: %s not implement", operator));
    }

    static MyVar createIntExpr(IntExpr left_var, IntExpr right_var, String operator) {
        // Any binary expression (+, -, /, etc)
        Context ctx = PathTracker.ctx;
        if (operator.equals("==")) {
            return new MyVar(PathTracker.ctx.mkEq(left_var, right_var));
        } else if (operator.equals("<=")) {
            return new MyVar(PathTracker.ctx.mkLe(left_var, right_var));
        } else if (operator.equals(">=")) {
            return new MyVar(PathTracker.ctx.mkGe(left_var, right_var));
        } else if (operator.equals("<")) {
            return new MyVar(PathTracker.ctx.mkLt(left_var, right_var));
        } else if (operator.equals(">")) {
            return new MyVar(PathTracker.ctx.mkGt(left_var, right_var));
        } else if (operator.equals("*")) {
            return new MyVar(PathTracker.ctx.mkMul(left_var, right_var));
        } else if (operator.equals("-")) {
            return new MyVar(PathTracker.ctx.mkSub(left_var, right_var));
        } else if (operator.equals("+")) {
            return new MyVar(PathTracker.ctx.mkAdd(left_var, right_var));
        } else if (operator.equals("%")) {
            ArithExpr mod = PathTracker.ctx.mkMod(left_var, right_var);
            if (Settings.getInstance().CORRECT_INTEGER_MODEL) {
                return new MyVar(
                        PathTracker.ctx.mkITE(
                                PathTracker.ctx.mkOr(
                                        PathTracker.ctx.mkGe(left_var,
                                                (ArithExpr) PathTracker.ctx.mkInt(0)),
                                        PathTracker.ctx.mkEq(mod,
                                                (ArithExpr) PathTracker.ctx.mkInt(0))),

                                mod,
                                PathTracker.ctx.mkSub(mod, right_var)));
            } else {
                return new MyVar(mod);
            }
        } else if (operator.equals("/")) {
            if (Settings.getInstance().CORRECT_INTEGER_MODEL) {
                return new MyVar(ctx.mkMul(
                        ctx.mkDiv(
                                mkAbs(ctx, left_var),
                                mkAbs(ctx, right_var)),
                        mkSign(ctx, left_var),
                        mkSign(ctx, right_var)));
            } else {
                return new MyVar(ctx.mkDiv(left_var, right_var));
            }
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

    static MyVar createStringExpr(SeqExpr left_var, SeqExpr right_var, String operator) {
        // We only support String.equals
        // return new MyVar(PathTracker.ctx.mkFalse());
        if (operator.equals("==")) {
            return new MyVar(PathTracker.ctx.mkEq(left_var, right_var));

        }
        throw new IllegalArgumentException(String.format("string operator: %s not implement", operator));
    }

    static void assign(MyVar var, String name, Expr value, Sort s) {
        // All variable assignments, use single static assignment
        Context c = PathTracker.ctx;
        Expr z3var = c.mkConst(c.mkSymbol(createVarName(name)), s);
        loopDetector.assignToVariable(name, value);
        var.z3var = z3var;
        PathTracker.addToModel(c.mkEq(z3var, value));
        loopDetector.addToLoopModel(c.mkEq(z3var, value));
    }

    static void saveTraces() {
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("traces.dat")))) {
            out.write(String.format("%d %d\n", fullTraces.size(), PathTracker.inputSymbols.length));
            for (String t : fullTraces) {
                out.write(t);
            }
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void saveSolverTimes() {
        if (solverTimesWriter == null) {
            try {
                solverTimesWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("solvertimes.csv")));
                solverTimesWriter.write("TYPE\tNS\tTRACE_LEN\tLOOPS\n");
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            for (DataPoint d : OptimizingSolver.solverTimes) {
                solverTimesWriter.write(
                        String.format("%c\t%d\t%d\t%d\n", d.type.c, d.timeInNs, d.traceLength, d.numberOfLoops));
            }
            solverTimesWriter.flush();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        OptimizingSolver.solverTimes.clear();
    }

    static void saveGraph(boolean always) {
        if (changed || always) {
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("graph.dot")))) {
                out.write("digraph {\nrankdir=TB\n");
                for (EndpointPair<String> e : graph.edges()) {
                    out.write(String.format("%s-> %s [ label=\"%s\"];\n", e.source(), e.target(),
                            edges.get(e.source() + e.target()).replace("\"", "'")));
                    out.newLine();
                }
                out.write("}\n");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static void encounteredNewBranch(MyVar condition, boolean value, int line_nr) {
        path += String.format("%d:%b\n", line_nr, value);
        if (firstBranchLineNr == -1) {
            firstBranchLineNr = line_nr;
        }
        if (firstBranchLineNr == line_nr) {
            assert inputInIndex < currentTrace.size();
            // System.out.printf("inputInIndex: %d\n", inputInIndex);
            processedInput += currentTrace.get(inputInIndex);
            inputInIndex++;
        }

        String newPathString = String.format("%s_%d", processedInput, line_nr);
        String from = String.valueOf(currentLineNumber);
        String to = String.valueOf(line_nr);
        if (graph.putEdge(from, to)) {
            changed = true;
            edges.put(from + to, condition.z3var.toString());
        }

        currentLineNumber = line_nr;
        currentValue = value;
        branchTracker.visit(line_nr, value);
        currentBranchTracker.visit(line_nr, value);
        pathString = newPathString;
        if (skip) {
            return;
        }
        Context c = PathTracker.ctx;
        if (
        // currentTrace.size() <= processedInput.length() &&
        shouldSolve &&
                alreadySolvedBranches.add(pathString)) {
            // Call the solver
            PathTracker.solve(c.mkEq(condition.z3var, c.mkBool(!value)), SolvingForType.BRANCH, false, true);
        }
        BoolExpr branchCondition = (BoolExpr) condition.z3var;
        if (!value) {
            branchCondition = c.mkNot(branchCondition);
        }
        PathTracker.addToBranches(branchCondition);
        loopDetector.addToLoopModel(branchCondition);
    }

    static boolean isStillUsefull(Iterable<String> input) {
        String s = String.join("", input);
        if (errorTraces.contains(s)) {
            return false;
        }
        for (String e : errorTraces) {
            if (s.startsWith(e)) {
                return false;
            }
        }
        if (loopDetector.isSelfLooping(s)) {
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
                        String.join(" ", currentTrace) + "\n" + path + "\n" + output, !currentValue));
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
        pathString = "Start";
        nameCounts.clear();
        System.gc();
        currentLineNumber = 0;
        inputInIndex = 0;
        processedInput = "";
        currentBranchTracker.clear();
        skip = false;
        changed = false;
        path = "";
        invalid = false;
        shouldSolve = true;
        numberOfLoopsInPathConstraint = 0;

        /// OUTPUT generation
        output = "";
        input = "";
        fullTrace.clear();
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
                printfGreen(path);
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

    static boolean timeLimitReached() {
        Settings settings = Settings.getInstance();
        if (settings.MAX_TIME_S == -1) {
            return false;
        }
        long elapsed = (System.currentTimeMillis() - START_TIME);
        return elapsed > (settings.MAX_TIME_S * 1000);
    }

    static void run(String[] args) {
        Settings.create(args);
        initialize(PathTracker.inputSymbols);
        nextTraces.add(new NextTrace(currentTrace, currentLineNumber, "<initial>", false));
        while (!isFinished && !isEmpty() && !timeLimitReached()) {
            reset();
            NextTrace trace = getNext();
            runNext(trace);
            isFinished = branchTracker.visitedAll();
        }
        if (timeLimitReached()) {
            printfYellow("TIME LIMIT REACHED\n");
        }
        if (branchTracker.visitedAll()) {
            printfGreen("All paths visited, exiting now\n");
        }
        printFinalStatus();
        saveSolverTimes();
        // saveTraces();
        // saveGraph(true);
        System.exit(0);
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
            addToFullTrace();
            fullTraces.add(String.format("1 %d %s\n", fullTrace.size(), String.join(" ", fullTrace)));
            // saveTraces();
            // saveGraph(false);
            printStatus();
        }
    }

    public static void printStatus() {
        System.out.printf("Visited: %d out of %d, #errors: %d, #nextTraces: %d, #backlog: %d, Errors:\n%s\n",
                branchTracker.numVisited(),
                branchTracker.totalBranches(), errorTracker.amount(), nextTraces.size(), backLog.size(),
                errorTracker.summary());
    }

    private static void printFinalStatus() {
        printStatus();
        System.out.println(errorTracker.summary());
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
            invalid = true;
            output += "INVALID";
        } else {
            output += out.strip();
        }
    }

}
