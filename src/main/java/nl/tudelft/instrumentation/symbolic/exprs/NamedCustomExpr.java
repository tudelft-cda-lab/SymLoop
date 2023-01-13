package nl.tudelft.instrumentation.symbolic.exprs;

import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

import nl.tudelft.instrumentation.symbolic.PathTracker;

/**
 * 
 */

public class NamedCustomExpr extends CustomExpr {

    public final String name;

    public NamedCustomExpr(String name, ExprType type) {
        super(type);
        this.name = name;
    }

    @Override
    public Expr toZ3() {
        Context ctx = PathTracker.ctx;
        return ctx.mkConst(name, this.type.toSort());
    }


}
