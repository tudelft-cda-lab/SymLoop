package nl.tudelft.instrumentation.symbolic;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Model;
import com.microsoft.z3.Status;

import nl.tudelft.instrumentation.runner.CallableTraceRunner;
import nl.tudelft.instrumentation.symbolic.SolverInterface.SolvingForType;
import nl.tudelft.instrumentation.symbolic.exprs.ConstantCustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExprOp;
import nl.tudelft.instrumentation.symbolic.exprs.ExprType;

/**
 * This class is used for the symbolic execution lab.
 * 
 * @author Clinton Cao, Sicco Verwer
 */
public class PathTracker {
    public static HashMap<String, String> cfg = new HashMap<String, String>() {
        {
            put("model", "true");
            put("timeout", "10000");
        }
    };
    public static Context ctx = new Context(cfg);

    public static BoolExpr z3model = ctx.mkTrue();
    // private static BoolExpr z3branches = ctx.mkTrue();
    public static OptimizingSolver solver = new InferringSolver();
    // public static OptimizingSolver solver = new OptimizingSolver();

    static HashMap<MyVar, Replacement> loopIterations = new HashMap<>();

    public static LinkedList<MyVar> inputs = new LinkedList<MyVar>();
    // static ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
    static CallableTraceRunner<Void> problem;
    static String[] inputSymbols;

    static int lastLength = -1;

    /**
     * Used to reset the constraints and everything else of z3 before running the
     * next sequence.
     */
    public static void reset() {
        z3model = ctx.mkTrue();
        // z3branches = ctx.mkTrue();
        inputs.clear();
        solver.reset();
    }

    public static void addToBranches(CustomExpr expr) {
        // z3branches = ctx.mkAnd(expr, z3branches);
        solver.add(expr);
    }

    public static void addToModel(CustomExpr expr) {
        z3model = ctx.mkAnd(expr.toBoolExpr(), z3model);
        solver.add(expr);
    }

    /**
     * This method contains code that calls the Z3 solver and check whether it solve
     * the path
     * constraint that is constructed for the new branch. The solver will try to
     * find inputs
     * that can satisfy the path constraint. We can use these inputs to reach this
     * newly
     * discovered branch.
     * 
     * @param new_branch the branch that we have discovered and want to visit.
     * @param printModel boolean value that specifies whether the path constraint
     *                   should
     *                   be printed in the terminal or not.
     */
    public static boolean solve(CustomExpr new_branch, SolvingForType type, boolean printModel, boolean isInput) {
        OptimizingSolver s = solver;
        s.push();
        // Solver s = ctx.mkSolver();
        String output = "";
        // s.add(PathTracker.z3model);
        // s.add(PathTracker.z3branches);
        // s.push();
        s.add(new_branch);

        if (printModel) {
            System.out.print("Model: ");
            System.out.println(PathTracker.z3model);
            System.out.print("Branches: ");
            // System.out.println(PathTracker.z3branches);
            System.out.print("New branch: ");
            System.out.println(new_branch);
        }

        Status status = s.check(type);
        if (status == Status.SATISFIABLE) {
            Model m = s.getModel();
            output += m;
            LinkedList<String> new_inputs = new LinkedList<String>();
            for (MyVar v : PathTracker.inputs) {
                if (loopIterations.containsKey(v)) {
                    Replacement r = loopIterations.get(v);
                    String amountAsString = m.evaluate(v.z3var(), true).toString();
                    // SymbolicExecutionLab.printfBlue("loopVar %s: %s\n", v.z3var, amountAsString);
                    int amount = Integer.parseInt(amountAsString);
                    for (CustomExpr e : r.getAllExprs(amount)) {
                        String value = m.evaluate(e.toZ3(), true).toString();
                        new_inputs.add(value);
                    }
                } else {
                    new_inputs.add(m.evaluate(v.z3var(), true).toString());
                }
            }
            if (isInput) {
                SymbolicExecutionLab.newSatisfiableInput(new_inputs, output);
            }
            s.pop();
            return true;
        } else {
            if (status == Status.UNKNOWN) {
                System.out.println("STATUS OF THE SOLVER IS UNKNOWN: " + type.c);
                return false;
            }
            s.pop();
        }
        return false;
    }

    // Making temporary variables, i.e., within if-conditions
    public static MyVar tempVar(boolean value) {
        return new MyVar(ConstantCustomExpr.fromBool(value));
    }

    public static MyVar tempVar(int value) {
        return new MyVar(ConstantCustomExpr.fromInt(value));
    }

    public static MyVar tempVar(String value) {
        return new MyVar(ConstantCustomExpr.fromString(value));
    }

    // Making new stored variables
    public static MyVar myVar(boolean value, String name) {
        return SymbolicExecutionLab.createVar(name, ConstantCustomExpr.fromBool(value));
    }

    public static MyVar myVar(int value, String name) {
        return SymbolicExecutionLab.createVar(name, ConstantCustomExpr.fromInt(value));
    }

    public static MyVar myVar(String value, String name) {
        return SymbolicExecutionLab.createVar(name, ConstantCustomExpr.fromString(value));
    }

    public static MyVar myVar(MyVar value, String name) {
        return SymbolicExecutionLab.createVar(name, value.expr);
    }

    // Making a new input variable
    public static MyVar myInputVar(String value, String name) {
        return SymbolicExecutionLab.createInput(name, ConstantCustomExpr.fromString(value));
    }

    // for assigning an array to a variable.
    public static MyVar[] myVar(MyVar[] value, String name) {
        MyVar[] vars = new MyVar[value.length];
        for (int i = 0; i < value.length; i++) {
            vars[i] = value[i];
        }
        return vars;
    }

    /**
     * Arrays are tricky, this is how we deal with them.
     * this assignment creates a reference and does not need new variables
     */
    public static MyVar[] myVar(MyVar[] value) {
        MyVar[] vars = new MyVar[value.length];
        for (int i = 0; i < value.length; i++) {
            vars[i] = value[i];
        }
        return vars;
    }

    /**
     * Main construction for creating the path constraint.
     * This part is for handling arithmetic and boolean logic.
     */
    public static MyVar unaryExpr(MyVar i, String operator) {
        if (i.expr.type == ExprType.BOOL) {
            return SymbolicExecutionLab.createBoolExpr(i.expr, operator);
        }
        if (i.expr.type == ExprType.INT) {
            return SymbolicExecutionLab.createIntExpr(i.expr, operator);
        }
        // if (i.z3var instanceof IntExpr || i.z3var instanceof ArithExpr) {
        // return SymbolicExecutionLab.createIntExpr((IntExpr) i.z3var, operator);
        // }
        assert false;
        return new MyVar(ConstantCustomExpr.fromBool(false));
    }

    public static MyVar binaryExpr(MyVar i, MyVar j, String operator) {
        if (i.expr.type == ExprType.BOOL) {
            return SymbolicExecutionLab.createBoolExpr(i.expr, j.expr, operator);
        }
        if (i.expr.type == ExprType.INT) {
            return SymbolicExecutionLab.createIntExpr(i.expr, j.expr, operator);
        }
        assert false;
        return new MyVar(ConstantCustomExpr.fromBool(false));
    }

    public static MyVar equals(MyVar i, MyVar j) {
        return SymbolicExecutionLab.createStringExpr(i.expr, j.expr, "==");
    }

    // We handle arrays, which needs an iterated if-then-else.
    public static MyVar arrayInd(MyVar[] name, MyVar index) {
        CustomExpr ite_expr = name[0].expr;
        for (int i = 1; i < name.length; i++) {
            ite_expr = CustomExprOp.mkITE(
                    CustomExprOp.mkEq(ConstantCustomExpr.fromInt(i), index.expr),
                    name[i].expr, ite_expr);
        }
        return new MyVar(ite_expr);
    }

    // We handle increments, forwarded to assignments.
    public static MyVar increment(MyVar i, String operator, boolean prefix) {
        if (prefix) {
            if (operator.equals("++"))
                myAssign(i, new MyVar(CustomExprOp.mkAdd(i.expr, ConstantCustomExpr.fromInt(1))), "=");
            if (operator.equals("--"))
                myAssign(i, new MyVar(CustomExprOp.mkAdd(i.expr, ConstantCustomExpr.fromInt(-1))), "=");
            return i;
        } else {
            MyVar old_var = new MyVar(i.expr);
            if (operator.equals("++"))
                myAssign(i, new MyVar(CustomExprOp.mkAdd(i.expr, ConstantCustomExpr.fromInt(1))), "=");
            if (operator.equals("--"))
                myAssign(i, new MyVar(CustomExprOp.mkAdd(i.expr, ConstantCustomExpr.fromInt(-1))), "=");
            return old_var;
        }
    }

    // We handle conditionals, which is an if-then-else.
    public static MyVar conditional(MyVar b, MyVar t, MyVar e) {
        return new MyVar(CustomExprOp.mkITE(b.expr, t.expr, e.expr));
    }

    // Assignment changes the z3var in a MyVar variable.
    public static void myAssign(MyVar target, MyVar value, String operator) {
        // first add or subtract if necessary
        CustomExpr new_value = value.expr;
        if (operator.equals("-="))
            new_value = CustomExprOp.mkSub(target.expr, value.expr);
        if (operator.equals("+="))
            new_value = CustomExprOp.mkAdd(target.expr, value.expr);

        SymbolicExecutionLab.assign(target, target.name, new_value);
    }

    // We handle arrays, again using if-then-else and call standard variable
    // assignment for all indices.
    public static void myAssign(MyVar[] name, MyVar index, MyVar value, String operator) {
        for (int i = 0; i < name.length; i++) {
            // Expr old_expr = name[i].z3var;
            CustomExpr old_expr = name[i].expr;
            // Expr new_value = value.z3var;
            CustomExpr new_value = value.expr;
            if (operator.equals("-="))
                new_value = CustomExprOp.mkSub(old_expr, value.expr);
            if (operator.equals("+="))
                new_value = CustomExprOp.mkAdd(old_expr, value.expr);

            SymbolicExecutionLab.assign(name[i], name[i].name, CustomExprOp
                    .mkITE(CustomExprOp.mkEq(ConstantCustomExpr.fromInt(i), index.expr), new_value, old_expr));
        }
    }

    // Direct assign for array references
    public static void myAssign(MyVar[] name1, MyVar[] name2, String operator) {
        for (int i = 0; i < name1.length; i++) {
            name1[i] = name2[i];
        }
    }

    /**
     * Converts an if-statement into a custom myIf-statement. This method is used to
     * call the encounteredNewBranch method which contains the logic for computing
     * the branch distance when a new branch has been found.
     * 
     * @param condition the condition of the if-statement.
     * @param value     the value of the condition.
     * @param line_nr   the line number of the if-statement.
     */
    public static void myIf(MyVar condition, boolean value, int line_nr) {
        SymbolicExecutionLab.encounteredNewBranch(condition, value, line_nr);
    }

    /**
     * Used to catch output from the standard out.
     * 
     * @param out the string that has been outputted in the standard out.
     */
    public static void output(String out) {
        SymbolicExecutionLab.output(out);
    }

    /**
     * Initialize and hand over control to FuzzingLab
     * 
     * @param eca The current problem instance
     * @param s   the input symbols of the problem
     */
    public static void run(String[] args, String[] s, CallableTraceRunner<Void> eca) {
        problem = eca;
        inputSymbols = s;
        SymbolicExecutionLab.run(args);
    }

    /**
     * This method is used for running the fuzzed input. It first assigns the
     * fuzzed sequence that needs to be run and then user a handler to
     * start running the sequence through the problem.
     * 
     * @param sequence the fuzzed sequence that needs top be run.
     */
    public static boolean runNextFuzzedSequence(String[] sequence) {
        problem.setSequence(sequence);
        final Future handler = executor.submit(problem);
        executor.schedule(() -> {
            handler.cancel(false);
        }, Settings.getInstance().MAX_RUNTIME_SINGLE_TRACE_S, TimeUnit.SECONDS);

        // Wait for it to be completed
        try {
            handler.get();
        } catch (CancellationException e) {
            SymbolicExecutionLab.printfYellow("TIMEOUT!");
            if (Settings.getInstance().STOP_ON_FIRST_TIMEOUT) {
                System.exit(-1);
            }
            return false;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        return true;
    }

}
