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

    @Override
    public CustomExpr substitute(CustomExpr[] from, CustomExpr[] to) {
        for(int i = 0; i < from.length; i ++) {
            if (this.equals(from[i])) {
                return to[i];
            }
        }
        return this;
    }

    public boolean equals(Object other) {
        if (super.equals(other) && other instanceof NamedCustomExpr) {
            NamedCustomExpr that = (NamedCustomExpr) other;
            return this.name.equals(that.name);
        }
        return false;
    }

}
