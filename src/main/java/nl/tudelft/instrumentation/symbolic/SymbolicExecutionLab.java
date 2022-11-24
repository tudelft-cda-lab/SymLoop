package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import com.microsoft.z3.*;

import nl.tudelft.instrumentation.fuzzing.BranchVisitedTracker;

/**
 * You should write your solution using this class.
 */
public class SymbolicExecutionLab {

    static String[] coverageSet = new String[] {
            "AFJFGFEEIBDGJBDEIBEIBJBCHACFFIIICBCGIDD",
            "BCDABFEGDEDDAJDJBFBFHABDDFAFBAFABJC",
            "IFIEJBDCADCCEAJHEFDIJHAIFGFHFDEDJABICDBBHF",
            "EBJHJDIGDDCEFDJGJIEJCJDCCHEEAJHGJ",
            "HIGBDFEGBDFDGBGFCIAGCDABGAICJHGEFGDJAJBJBBAACJFE",
            "AFJFDAFFADFEJAHIEBHJDFIABJCIJFHGDBIICIDCHGHBIFH",
            "GJCDGHEFCF",
            "BBFAFEBDDFJF",
            "ADIFCFHJJEDDJEJDGCAFBBHHGJHAJBC",
            "FADBAEHGIFEFIFAGJICEECDHHHEFAGJICGIBAJFJEAAD",
            "IFJIHIBFFADJFCDGIICDIFEEFDBHAIGECBFABGJ",
            "BIIFEIIHGHFAIFBACDICDFHCAAEGHAJGAEFIHFE",
            "FJEGAHHDJBGAD",
            "FJFDEBEDADDDJEICGJFJG",
            "IICGCCJBGFHCIBEEGFFEHHFJAIFCD",
            "DBDBFHJIGICBJGICDDJCGBADEBIHIFGBCJIDCCFBEAIJIBJA",
            "GDHFICBCJABBAFJFICCHAEEBACAGHGEIFBFJFDEFB",
            "JFHBHHHCHBBHDJFFGBJAIBCEIGCGDCEACCGFEIDABICIG",
            "BFBAGBAJDCEEEJEHEEECJHJBIHCJIDFIJEGHAIIIEC",
            "BDFBFJFBDBAADBDIBIGDHFBHIEDIBIDGHDAJCBJAEE",
            "GFDJFIIBAGEAIGDG",
            "GIJCCFJCABFADDGHDIGIIDAHHAFFGGFDHDFDIBFFAJFDD",
            "JFBHHIJEIGEJGGIEHIIFBJGGFB"
    };

    static class NextTrace implements Comparable<NextTrace> {
        List<String> trace;
        int linenr;
        boolean value;
        int pathLength;
        String from;

        static Comparator<NextTrace> comparator = Comparator.comparing(NextTrace::pathLength)
                .thenComparing(NextTrace::getLineNr);

        public NextTrace(List<String> trace, int linenr, int pathLength, String from, boolean value) {
            this.trace = trace;
            this.linenr = linenr;
            this.pathLength = pathLength;
            this.from = from;
            this.value = value;
        }

        public int getLineNr() {
            return linenr;
        }

        public int pathLength() {
            return pathLength;
        }

        public boolean getConditionValue() {
            return this.value;
        }

        @Override
        public int compareTo(NextTrace other) {
            return comparator.compare(this, other);
        }

    }

    static Random r = new Random(1);
    static Boolean isFinished = false;
    static List<String> currentTrace;
    static int traceLength = 1;
    static PriorityQueue<NextTrace> nextTraces = new PriorityQueue<>();
    static PriorityQueue<NextTrace> backLog = new PriorityQueue<>();
    static BranchVisitedTracker branchTracker = new BranchVisitedTracker();
    static BranchVisitedTracker currentBranchTracker = new BranchVisitedTracker();
    static Map<Integer, Integer> impossibleBranchesPathLengths = new HashMap<>();
    static ErrorTracker errorTracker = new ErrorTracker();
    static long startTime = System.currentTimeMillis();

    private static int currentLineNumber = 0;
    private static boolean currentValue;
    private static int pathLength = 0;

    private static HashSet<String> alreadySolvedBranches = new HashSet<>();
    private static HashSet<String> alreadyFoundTraces = new HashSet<>();
    private static HashSet<String> alreadyFoundLoops = new HashSet<>();

    static int firstBranchLineNr = -1;
    static int inputInIndex = 0;
    static String processedInput = "";

    static String path = "";
    static boolean skip = false;

    static HashMap<String, List<Expr>> variables = new HashMap<String, List<Expr>>();
    static HashMap<String, Integer> lastVariables = new HashMap<String, Integer>();

    private static HashMap<String, Integer> nameCounts = new HashMap<String, Integer>();

    private static BoolExpr loopModel = PathTracker.ctx.mkFalse();

    static void initialize(String[] inputSymbols) {
        // Initialise a random trace from the input symbols of the problem.
        currentTrace = generateRandomTrace(inputSymbols);
        loopModel = PathTracker.ctx.mkFalse();
    }

    static String createVarName(String name) {
        Integer count = nameCounts.getOrDefault(name, 0);
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
        assignToVariable(name, value);
        Expr z3var = c.mkConst(c.mkSymbol(createVarName(name)), s);
        PathTracker.z3model = c.mkAnd(c.mkEq(z3var, value), PathTracker.z3model);
        // loopModel = c.mkAnd(c.mkEq(z3var, value), loopModel);
        return new MyVar(z3var, name);
    }

    static void assignToVariable(String name, Expr value) {
        if (variables.containsKey(name)) {
            variables.get(name).add(value);
        } else {
            List<Expr> initial = new ArrayList<Expr>();
            initial.add(value);
            variables.put(name, initial);
            lastVariables.put(name, 1);
        }
    }

    static MyVar createInput(String name, Expr value, Sort s) {
        onLoopDone();
        // Create an input var, these should be free variables!
        // Do not add it to the model
        Context c = PathTracker.ctx;
        Expr intermediate = c.mkConst(c.mkSymbol(createVarName(name)), s);
        // intermediate = c.mkEq(intermediate, value);
        MyVar input = new MyVar(intermediate, name);
        PathTracker.inputs.add(input);

        // restrict inputs to the valid input symbols found in PathTracker.inputSymbols
        BoolExpr[] temp = new BoolExpr[PathTracker.inputSymbols.length];
        for (int i = 0; i < PathTracker.inputSymbols.length; i++) {
            temp[i] = c.mkEq(c.mkString(PathTracker.inputSymbols[i]), intermediate);
        }

        assignToVariable(name, intermediate);
        PathTracker.z3model = c.mkAnd(c.mkOr(temp), PathTracker.z3model);
        // loopModel = PathTracker.ctx.mkTrue();
        // loopModel = c.mkAnd(c.mkOr(temp), loopModel);
        loopModel = c.mkOr(temp);
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
            return new MyVar(
                    PathTracker.ctx.mkITE(
                            PathTracker.ctx.mkOr(
                                    PathTracker.ctx.mkGe(left_var,
                                            (ArithExpr) PathTracker.ctx.mkInt(0)),
                                    PathTracker.ctx.mkEq(mod,
                                            (ArithExpr) PathTracker.ctx.mkInt(0))),

                            mod,
                            PathTracker.ctx.mkSub(mod, right_var)));
        } else if (operator.equals("/")) {
            return new MyVar(PathTracker.ctx.mkDiv(left_var, right_var));
        }
        throw new IllegalArgumentException(String.format("binary int expression: %s not implement", operator));
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
        // Update variable Z3 in assignment
        variables.get(name).add(value);
        var.z3var = z3var;
        PathTracker.z3model = c.mkAnd(c.mkEq(z3var, value), PathTracker.z3model);
        loopModel = c.mkAnd(c.mkEq(z3var, value), loopModel);
    }

    static void onLoopDone() {
        if(skip){
            return;
        }
        Context ctx = PathTracker.ctx;
        // System.out.printf("loopmodel: %d %s\n", inputInIndex, loopModel);
        String output = "";
        boolean isLoop = false;
        BoolExpr extended = loopModel;
        if (processedInput.length() > 1) {
            for (String name : variables.keySet()) {
                List<Expr> assigns = variables.get(name);
                Integer lastLength = lastVariables.get(name);
                int added = assigns.size() - lastLength;
                if (added > 0) {
                    output += String.format("%s, ", name);
                    isLoop = true;
                    // Loop backwards to prevent repeated subsitution
                    for (int i = assigns.size() - 1; i >= lastLength - 1; i--) {
                        Expr e = assigns.get(i);
                        extended = (BoolExpr) extended.substitute(
                                ctx.mkConst(PathTracker.ctx.mkSymbol(getVarName(name, i)), e.getSort()),
                                ctx.mkConst(PathTracker.ctx.mkSymbol(getVarName(name, i + added)), e.getSort()));
                    }
                    lastVariables.put(name, assigns.size());
                }
            }
        }

        if (isLoop && alreadyFoundLoops.add(processedInput) && PathTracker.solve(extended, false, false)) {
            // System.out.printf("loop detected on '%s' for %s: %s\n\nEXTENDED: %s\n", processedInput, output, loopModel, extended);
            System.out.printf("loop detected for %s: %s\n", output, processedInput);
        }
    }

    static void encounteredNewBranch(MyVar condition, boolean value, int line_nr) {
        if(skip){
            return;
        }
        path += String.format("%d:%b\n", line_nr, value);
        if (firstBranchLineNr == -1) {
            firstBranchLineNr = line_nr;
        }
        if (firstBranchLineNr == line_nr) {
            assert inputInIndex < currentTrace.size();
            // System.out.printf("inputInIndex: %d\n", inputInIndex);
            // onLoopDone();
            processedInput += currentTrace.get(inputInIndex);
            inputInIndex++;
        }

        Context c = PathTracker.ctx;
        currentLineNumber = line_nr;
        currentValue = value;
        pathLength += 1;
        branchTracker.visit(line_nr, value);
        currentBranchTracker.visit(line_nr, value);
        String pathString = String.format("%d-%s", line_nr, processedInput);
        if (alreadySolvedBranches.add(pathString)) {
            // Call the solver
            PathTracker.solve(c.mkEq(condition.z3var, c.mkBool(!value)), false, true);
        }
        BoolExpr branchCondition = (BoolExpr) condition.z3var;
        if (!value) {
            branchCondition = c.mkNot(branchCondition);
        }
        PathTracker.z3branches = c.mkAnd(branchCondition, PathTracker.z3branches);
        loopModel = c.mkAnd(branchCondition, loopModel);
    }

    static void newSatisfiableInput(LinkedList<String> new_inputs, String output) {
        // Hurray! found a new branch using these new inputs!
        LinkedList<String> temp = new LinkedList<String>();
        for (String s : new_inputs) {
            temp.add(s.replaceAll("\"", ""));
        }

        // Add a random input at the end to allow solving new paths
        String alreadyFound = String.join("", temp);
        if (alreadyFoundTraces.add(alreadyFound)) {
            System.out.printf("New satisfiable input: %s\n", temp);
            temp.add(newRandomInputChar());
            // temp.add("A");
            add(new NextTrace(temp, currentLineNumber, pathLength,
                    String.join(" ", currentTrace) + "\n" + path + "\n" + output, !currentValue));
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
        for (int i = 0; i < traceLength; i++) {
            trace.add(symbols[r.nextInt(symbols.length)]);
        }
        return trace;
    }

    static void reset() {
        PathTracker.reset();
        nameCounts.clear();
        System.gc();
        pathLength = 0;
        currentLineNumber = 0;
        inputInIndex = 0;
        processedInput = "";
        currentBranchTracker.clear();
        variables.clear();
        lastVariables.clear();
        loopModel = PathTracker.ctx.mkFalse();
        skip = false;
        path = "";
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

    static void run() {
        initialize(PathTracker.inputSymbols);
        nextTraces.add(new NextTrace(currentTrace, currentLineNumber, pathLength, "<initial>", false));
        startTime = System.currentTimeMillis();
        // Place here your code to guide your fuzzer with its search using Symbolic
        // Execution.
        //
        // for(String s : coverageSet) {
        // System.out.printf("\t%s: \n", s);
        // String[] a = (String[]) s.split("");
        // // System.out.printf("%s,\n", Arrays.asList(a));
        // // nextTraces.add(new NextTrace(Arrays.asList(a), currentLineNumber,
        // pathLength));
        // }
        // for(String s : PathTracker.inputSymbols) {
        // nextTraces.add(new NextTrace(Arrays.asList(new String[]{s}),
        // currentLineNumber, pathLength));
        // }
        while (!isFinished) {
            try {
                reset();
                if (isEmpty()) {
                    System.out.println(errorTracker.getSet());
                    System.exit(0);
                } else {
                    NextTrace trace = getNext();
                    printfYellow("now doing line: %d, pathLength: %d, %s\n", trace.getLineNr(), trace.pathLength(),
                            trace.trace);
                    currentTrace = trace.trace;
                    PathTracker.runNextFuzzedSequence(currentTrace.toArray(new String[0]));
                    // Checking if the solver is actually right
                    if ((!currentBranchTracker.hasVisited(trace.getLineNr(), trace.getConditionValue()))
                            && trace.getLineNr() != 0) {
                        printfRed("SOLVER IS WRONG, did not discover the solvable branch, %s\n", trace.from);
                        printfGreen(path);
                        System.out.printf("%d TRUE: %b, FALSE: %b\n", trace.getLineNr(),
                                branchTracker.hasVisited(trace.getLineNr(), true),
                                branchTracker.hasVisited(trace.getLineNr(), false));
                        System.exit(-1);
                    }
                }
                // System.in.read();
                isFinished = branchTracker.visitedAll();
                System.out.printf("Visited: %d out of %d, #nextTraces: %d, #backlog: %d \n", branchTracker.numVisited(),
                        branchTracker.totalBranches(), nextTraces.size(), backLog.size());
                Thread.sleep(0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.exit(0);
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

    public static void output(String out) {
        if (errorTracker.add(out)) {
            // System.out.printf("%sFound new error, current amount is: %d.%s\n",
            // ANSI_GREEN, errorTracker.amount(), ANSI_RESET);
            long current = System.currentTimeMillis();
            long seconds = (current - startTime) / 1000;
            printfGreen("Found new error, current amount is\t%d\t. in \t%d\t seconds\n", errorTracker.amount(),
                    seconds);

        }
        if(out.contains("Invalid")) {
            skip = true;
            // System.out.println(out);
        }
    }

}
