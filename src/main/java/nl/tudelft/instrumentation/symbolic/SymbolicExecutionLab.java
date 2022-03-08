package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import com.microsoft.z3.*;

import nl.tudelft.instrumentation.fuzzing.BranchVisitedTracker;
import nl.tudelft.instrumentation.fuzzing.DistanceTracker;

import java.io.FileWriter;
import java.io.IOException;

/**
 * You should write your solution using this class.
 */
public class SymbolicExecutionLab {

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
    static int traceLength = 10;
    static PriorityQueue<NextTrace> nextTraces = new PriorityQueue<>();
    static BranchVisitedTracker branchTracker = new BranchVisitedTracker();
    static Map<Integer, Integer> impossibleBranchesPathLengths = new HashMap<>();
    static Map<Integer, Set<Long>> stateEstimates = new HashMap<>();

    static BranchVisitedTracker triedBranches = new BranchVisitedTracker();

    private static int currentLineNumber = 0;
    private static int pathLength = 0;
    private static long pathEstimate = 0L;

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
        // Call the solver
        // PathTracker.z3modelz3model = c.mkAnd(c.mkEq(z3var, value),
        // PathTracker.z3model);
        Context c = PathTracker.ctx;
        currentLineNumber = line_nr;
        pathLength += 1;
        pathEstimate += line_nr;

        // Check if the state is not visited (sum of path length)
        branchTracker.visit(line_nr, value);
        if (!stateEstimates.containsKey(line_nr)) {
            stateEstimates.put(line_nr, new HashSet<>());
        } else {
        }
        Set<Long> stateMap = stateEstimates.get(line_nr);
        if(!branchTracker.hasVisitedBoth(line_nr)){
        if (!stateMap.contains(pathEstimate)) {
            stateMap.add(pathEstimate);
        // if (!branchTracker.hasVisited(line_nr, !value) && (!triedBranches.hasVisited(line_nr, !value))) { // ||
            // impossibleBranchesPathLengths.getOrDefault(line_nr,
            // Integer.MAX_VALUE) < pathLength)) {
            // if (!branchTracker.hasVisited(line_nr, !value) &&
            // (!triedBranches.hasVisited(line_nr, !value) ||
            // impossibleBranchesPathLengths.getOrDefault(line_nr, Integer.MAX_VALUE) >=
            // pathLength)) {
            // if (!branchTracker.hasVisited(line_nr, !value)){ //&&
            // (!triedBranches.hasVisited(line_nr, !value) ||
            // impossibleBranchesPathLengths.getOrDefault(line_nr, Integer.MAX_VALUE) >=
            // pathLength)) {
            // System.out.printf("line %d, value: %b, trace: %s\n", line_nr, value,
            // currentTrace);
            PathTracker.solve(c.mkEq(condition.z3var, c.mkBool(!value)), false);
            // boolean solutionFoundSelf = PathTracker.solve(c.mkEq(condition.z3var, c.mkBool(value)), false);
            // System.out.printf("Now trying to solve %d, %b, new: %b, pathLength:
            // %d\n",line_nr, !value, solutionFound,
            // impossibleBranchesPathLengths.getOrDefault(line_nr, Integer.MAX_VALUE));
            // impossibleBranchesPathLengths.put(line_nr, pathLength);
            triedBranches.visit(line_nr, !value);

            // c.mkOr(c.mkEq(condition.z3var, c.mkBool(value)), c.mkEq(condition.z3var,
            // c.mkBool(value)));
            // System.exit(-1);
            // }
        } else {
            // stateMap.add(pathEstimate);
            // System.out.printf("Skipping, %d\n", line_nr);
        }
        }
        BoolExpr temp = c.mkEq(condition.z3var, c.mkBool(value));
        PathTracker.z3branches = c.mkAnd(temp, PathTracker.z3branches);
    }

    static void newSatisfiableInput(LinkedList<String> new_inputs) {
        // Hurray! found a new branch using these new inputs!
        LinkedList<String> temp = new LinkedList<String>();
        for (String s : new_inputs) {
            temp.add(s.replaceAll("\"", ""));
        }
        // TODO: add a random input at the end
        temp.add(PathTracker.inputSymbols[r.nextInt(PathTracker.inputSymbols.length)]);
        nextTraces.add(new NextTrace(temp, currentLineNumber, pathLength));
        System.out.printf("New satisfiable input: %s\n", temp);
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
        pathEstimate = 0L;
    }


    static void run() {
        initialize(PathTracker.inputSymbols);
        nextTraces.add(new NextTrace(currentTrace, currentLineNumber, pathLength));
        initialize(PathTracker.inputSymbols);
        nextTraces.add(new NextTrace(currentTrace, currentLineNumber, pathLength));
        // System.out.println(PathTracker.ctx);
        // System.out.println(PathTracker.inputs);
        // Place here your code to guide your fuzzer with its search using Symbolic
        // Execution.
        while (!isFinished) {
            // Do things!
            try {
                reset();
                if (nextTraces.isEmpty()) {
                    System.exit(0);
                    // initialize(PathTracker.inputSymbols);
                } else {
                    NextTrace trace = nextTraces.poll();
                    System.out.printf("now doing %d, %d\n", trace.getLineNr(), trace.pathLength());
                    currentTrace = trace.trace;
                }
                PathTracker.runNextFuzzedSequence(currentTrace.toArray(new String[0]));
                // System.in.read();
                // System.out.println("Woohoo, looping!");
                System.out.printf("Visited: %d out of %d, #nextTraces: %d\n", branchTracker.numVisited(),
                        branchTracker.totalBranches(), nextTraces.size());
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // catch (IOException e) {
            // // TODO Auto-generated catch block
            // e.printStackTrace();
            // }
        }
    }

    public static void output(String out) {
        if (!out.contains("Current state")) {
            System.out.println(out);
        }
    }

}
