package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import com.microsoft.z3.*;

import nl.tudelft.instrumentation.fuzzing.BranchVisitedTracker;

/**
 * You should write your solution using this class.
 */
public class SymbolicExecutionLab {

    static String[] coverageSet = new String[]{
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
        int pathLength;

        static Comparator<NextTrace> comparator = Comparator.comparing(NextTrace::pathLength)
                .thenComparing(NextTrace::getLineNr);

        public NextTrace(List<String> trace, int linenr, int pathLength) {
            this.trace = trace;
            this.linenr = linenr;
            this.pathLength = pathLength;
        }

        public int getLineNr() {
            return linenr;
        }

        public int pathLength() {
            return pathLength;
        }

        @Override
        public int compareTo(NextTrace other) {
            return comparator.compare(this, other);
        }

    }

    static Random r = new Random();
    static Boolean isFinished = false;
    static List<String> currentTrace;
    static int traceLength = 1;
    static PriorityQueue<NextTrace> nextTraces = new PriorityQueue<>();
    static PriorityQueue<NextTrace> backLog = new PriorityQueue<>();
    static BranchVisitedTracker branchTracker = new BranchVisitedTracker();
    static Map<Integer, Integer> impossibleBranchesPathLengths = new HashMap<>();
    static ErrorTracker errorTracker = new ErrorTracker();
    static long startTime = System.currentTimeMillis();

    private static int currentLineNumber = 0;
    private static int pathLength = 0;

    private static HashSet<String> alreadySolvedBranches = new HashSet<>();
    private static HashSet<String> alreadyFoundTraces = new HashSet<>();

    static int firstBranchLineNr = -1;
    static int inputInIndex = 0;
    static String proccessedInput = "";

    static void initialize(String[] inputSymbols) {
        // Initialise a random trace from the input symbols of the problem.
        currentTrace = generateRandomTrace(inputSymbols);
    }

    static MyVar createVar(String name, Expr value, Sort s) {
        Context c = PathTracker.ctx;
        /**
         * Create var, assign value and add to path constraint.
         * We show how to do it for creating new symbols, please
         * add similar steps to the functions below in order to
         * obtain a path constraint.
         */
        Expr z3var = c.mkConst(c.mkSymbol(name + "_" + PathTracker.z3counter++), s);
        PathTracker.z3model = c.mkAnd(c.mkEq(z3var, value), PathTracker.z3model);
        return new MyVar(z3var, name);
    }

    static MyVar createInput(String name, Expr value, Sort s) {
        // Create an input var, these should be free variables!
        // Do not add it to the model
        Context c = PathTracker.ctx;
        Expr intermediate = c.mkConst(c.mkSymbol(name + "_" + PathTracker.z3counter++), s);
        // intermediate = c.mkEq(intermediate, value);
        MyVar input = new MyVar(intermediate, name);
        PathTracker.inputs.add(input);

        // restrict inputs to the valid input symbols found in PathTracker.inputSymbols
        BoolExpr temp = c.mkFalse();
        for (int i = 0; i < PathTracker.inputSymbols.length; i++) {
            temp = c.mkOr(temp, c.mkEq(c.mkString(PathTracker.inputSymbols[i]), intermediate));
        }
        PathTracker.z3model = c.mkAnd(temp, PathTracker.z3model);

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
            return new MyVar(PathTracker.ctx.mkMod(left_var, right_var));
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
        Expr z3var = c.mkConst(c.mkSymbol(name + "_" + PathTracker.z3counter++), s);
        // Update variable Z3 in assignment
        var.z3var = z3var;
        PathTracker.z3model = c.mkAnd(c.mkEq(z3var, value), PathTracker.z3model);
    }

    static void encounteredNewBranch(MyVar condition, boolean value, int line_nr) {
        if(firstBranchLineNr == -1) {
            firstBranchLineNr = line_nr;
        }
        if(firstBranchLineNr==line_nr) {
            assert inputInIndex < currentTrace.size();
            proccessedInput += currentTrace.get(inputInIndex);
            inputInIndex++;
        }

        Context c = PathTracker.ctx;
        currentLineNumber = line_nr;
        pathLength += 1;
        branchTracker.visit(line_nr, value);
        String pathString = String.format("%d-%s", line_nr, proccessedInput);
        if (alreadySolvedBranches.add(pathString)){
            // Call the solver
            PathTracker.solve(c.mkEq(condition.z3var, c.mkBool(!value)), false);
        }
        BoolExpr branchCondition = c.mkEq(condition.z3var, c.mkBool(value));
        PathTracker.z3branches = c.mkAnd(branchCondition, PathTracker.z3branches);
    }

    static void newSatisfiableInput(LinkedList<String> new_inputs) {
        // Hurray! found a new branch using these new inputs!
        LinkedList<String> temp = new LinkedList<String>();
        for (String s : new_inputs) {
            temp.add(s.replaceAll("\"", ""));
        }

        // Add a random input at the end to allow solving new paths
        String alreadyFound = String.join("", temp);
        if(alreadyFoundTraces.add(alreadyFound)){
            System.out.printf("New satisfiable input: %s\n", temp);
            temp.add(newRandomInputChar());
            add(new NextTrace(temp, currentLineNumber, pathLength));
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
        System.gc();
        pathLength = 0;
        currentLineNumber = 0;
        inputInIndex = 0;
        proccessedInput = "";
    }

    static boolean isEmpty() {
        return nextTraces.isEmpty() && backLog.isEmpty();
    }

    static void add(NextTrace trace) {
        if(branchTracker.hasVisitedBoth(trace.getLineNr())) {
            backLog.add(trace);
        } else {
            nextTraces.add(trace);
        }
    }

    static NextTrace getNext() {
        assert !isEmpty();
        if(nextTraces.isEmpty()) {
            return backLog.poll();
        } else {
            NextTrace trace = nextTraces.poll();
            if(branchTracker.hasVisitedBoth(trace.getLineNr())){
                backLog.add(trace);
                return getNext();
            } else {
                return trace;
            }
        }
    }

    static void run() {
        initialize(PathTracker.inputSymbols);
        nextTraces.add(new NextTrace(currentTrace, currentLineNumber, pathLength));
        startTime = System.currentTimeMillis();
        // Place here your code to guide your fuzzer with its search using Symbolic
        // Execution.
        //
        // for(String s : coverageSet) {
        //     System.out.printf("\t%s: \n", s);
        //     String[] a = (String[]) s.split("");
        //     // System.out.printf("%s,\n", Arrays.asList(a));
        //     // nextTraces.add(new NextTrace(Arrays.asList(a), currentLineNumber, pathLength));
        // }
        // for(String s : PathTracker.inputSymbols) {
        //     nextTraces.add(new NextTrace(Arrays.asList(new String[]{s}), currentLineNumber, pathLength));
        // }
        while (!isFinished) {
            try {
                reset();
                if (isEmpty()) {
                    System.out.println(errorTracker.getSet());
                    System.exit(0);
                } else {
                    NextTrace trace = getNext();
                    printfYellow("now doing line: %d, pathLength: %d, %s\n", trace.getLineNr(), trace.pathLength(), trace.trace);
                    currentTrace = trace.trace;
                    PathTracker.runNextFuzzedSequence(currentTrace.toArray(new String[0]));
                    // Potential improvement: add a contraint that the currentTrace should not be a solution in the future.
                    // Checking if the solver is actually right
                    if((!branchTracker.hasVisitedBoth(trace.getLineNr())) && trace.getLineNr() != 0){
                        printfRed("SOLVER IS WRONG, did not discover the solvable branch\n");
                        System.out.printf("TRUE: %b, FALSE: %b\n",branchTracker.hasVisited(trace.getLineNr(), true), branchTracker.hasVisited(trace.getLineNr(), false));
                        System.exit(-1);
                    }
                }
                // System.in.read();
                System.out.printf("Visited: %d out of %d, #nextTraces: %d, #backlog: %d \n", branchTracker.numVisited(),
                        branchTracker.totalBranches(), nextTraces.size(),backLog.size());
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
        if(errorTracker.add(out)) {
            // System.out.printf("%sFound new error, current amount is: %d.%s\n", ANSI_GREEN, errorTracker.amount(), ANSI_RESET);
            long current = System.currentTimeMillis();
            long seconds = (current-startTime)/1000;
            printfGreen("Found new error, current amount is\t%d\t. in \t%d\t seconds\n", errorTracker.amount(), seconds);

        }
    }

}
