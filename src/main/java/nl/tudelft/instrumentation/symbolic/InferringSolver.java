package nl.tudelft.instrumentation.symbolic;

import com.microsoft.z3.Status;

import nl.tudelft.instrumentation.symbolic.exprs.ConstantCustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.ExprMemoizer;

/**
 * 
 */

public class InferringSolver extends OptimizingSolver {

    private ExprMemoizer memory;

    public InferringSolver() {
        super();
        this.memory = new ExprMemoizer(this);
    }

    public void reset() {
        memory.reset();
        super.reset();
    }

    public void add(CustomExpr expr) {
        CustomExpr e = memory.optimize(expr, true);
        // System.out.printf("expr:\n\t%s\noptimized is:\n\t%s\n", expr, e);
        if (e instanceof ConstantCustomExpr && e.asConst().asBool()) {
            return;
        }
        super.add(e);
    }

    public void pop() {
        super.pop();
        memory.pop();
    }

    public void push() {
        super.push();
        memory.push();
    }

    public Status check(SolvingForType type) {
        // System.out.println(memory);
        return super.check(type);
    }

}

