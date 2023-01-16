package nl.tudelft.instrumentation.symbolic.exprs;

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
        return PathTracker.createConst(name, this.type.toSort());
    }

    @Override
    public CustomExpr substitute(String[] from, String[] to) {
        for(int i = 0; i < from.length; i ++) {
            if (this.name.equals(from[i])) {
                return new NamedCustomExpr(to[i], type);
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
