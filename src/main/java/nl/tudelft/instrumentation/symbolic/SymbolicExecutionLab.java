package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import com.microsoft.z3.*;
import nl.tudelft.instrumentation.fuzzing.DistanceTracker;

import java.util.Random;
import java.io.FileWriter;
import java.io.IOException;

/**
 * You should write your solution using this class.
 */
public class SymbolicExecutionLab {

    static Random r = new Random();
    static Boolean isFinished = false;
    static List<String> currentTrace;
    static int traceLength = 10;
    static List<String> nextTrace = null;

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
        // Do bound it to a value
        // Do not add it to the model
        Context c = PathTracker.ctx;
        Expr intermediate = c.mkConst(c.mkSymbol(name + "_" + PathTracker.z3counter++), s);
        // intermediate = c.mkEq(intermediate, value);
        MyVar input = new MyVar(intermediate, name);
        PathTracker.inputs.add(input);
        // PathTracker.z3model = c.mkAnd(intermediate, PathTracker.z3model);
        return input;
        // TOCHECK
        // return new MyVar(c.mkTrue());
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

        if (nextTrace == null) {
            Context c = PathTracker.ctx;
            System.out.printf("line %d, value: %b, trace: %s\n", line_nr, value, currentTrace);
            PathTracker.solve(c.mkEq(condition.z3var, c.mkBool(!value)), true);

            BoolExpr temp = c.mkEq(condition.z3var, c.mkBool(value));
            // c.mkOr(c.mkEq(condition.z3var, c.mkBool(value)), c.mkEq(condition.z3var,
            // c.mkBool(value)));
            // System.exit(-1);
            PathTracker.z3branches = c.mkAnd(temp, PathTracker.z3branches);
        }
        // PathTrakcer.z3branches = c.mkEq(z3model, c.mkTrue())
    }

    static void newSatisfiableInput(LinkedList<String> new_inputs) {
        // Hurray! found a new branch using these new inputs!
        System.out.printf("New satisfiable input: %s\n", new_inputs);
        nextTrace = new_inputs;
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

    static void run() {
        initialize(PathTracker.inputSymbols);
        // System.out.println(PathTracker.ctx);
        // System.out.println(PathTracker.inputs);
        // Place here your code to guide your fuzzer with its search using Symbolic
        // Execution.
        while (!isFinished) {
            // Do things!
            try {
                PathTracker.reset();
                if (nextTrace == null) {
                    initialize(PathTracker.inputSymbols);
                } else {
                    currentTrace = nextTrace;
                    nextTrace = null;
                }
                PathTracker.runNextFuzzedSequence(currentTrace.toArray(new String[0]));
                System.in.read();
                Thread.sleep(10);
                // System.out.println("Woohoo, looping!");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public static void output(String out) {
        if (!out.contains("Current state")) {
            System.out.println(out);
        }
    }

}