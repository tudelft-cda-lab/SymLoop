package nl.tudelft.instrumentation.symbolic;

import com.microsoft.z3.Model;
import com.microsoft.z3.Status;

import nl.tudelft.instrumentation.symbolic.exprs.ConstantCustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.ExprMemoizer;

/**
 * 
 */

public class InferringSolver extends OptimizingSolver {

    private ExprMemoizer memory;
    private boolean changed = true;
    private Status lastStatus = Status.UNKNOWN;

    public InferringSolver() {
        super();
        this.memory = new ExprMemoizer(this);
    }

    public void reset() {
        lastStatus = Status.UNKNOWN;
        changed = true;
        memory.reset();
        super.reset();
    }

    public void add(CustomExpr expr) {
        CustomExpr e;
        try {
            e = memory.optimize(expr, true);
        } catch(ArithmeticException exception) {
            SymbolicExecutionLab.printfRed("ArithmeticException\n");
            exception.printStackTrace(System.out);
            // exception.printStackTrace();
            lastStatus = Status.UNSATISFIABLE;
            changed = false;
            return;
        }
        // System.out.printf("expr:\n\t%s\noptimized is:\n\t%s\n", expr, e);
        if (e instanceof ConstantCustomExpr) {
            if (!e.asConst().asBool()) {
                lastStatus = Status.UNSATISFIABLE;
                changed = false;
            }
            return;
        }
        changed = true;
        super.add(e);
    }

    public void pop() {
        super.pop();
        memory.pop();

        this.lastStatus = Status.UNKNOWN;
        changed = true;
    }

    public void push() {
        super.push();
        memory.push();
    }

    public Status check(SolvingForType type) {
        if (!changed) {
            return lastStatus;
        }
        return super.check(type);
    }

}
