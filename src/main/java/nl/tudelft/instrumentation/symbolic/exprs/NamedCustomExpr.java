package nl.tudelft.instrumentation.symbolic.exprs;

import java.util.Map;

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
    public CustomExpr substitute(Map<String, String> changes) {
        if (changes.containsKey(name)) {
            return new NamedCustomExpr(changes.get(name), type);
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
