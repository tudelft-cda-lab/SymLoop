package nl.tudelft.instrumentation.symbolic.exprs;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;

/**
 * 
 */

public abstract class CustomExpr {

    public ExprType type;

    public CustomExpr(ExprType type) {
        assert type != null;
        this.type = type;
    }

    public abstract Expr toZ3();

    public IntExpr toArithExpr() {
        assert this.type == ExprType.INT;
        return (IntExpr) toZ3();
    }

    public BoolExpr toBoolExpr() {
        assert this.type == ExprType.BOOL;
        return (BoolExpr) toZ3();
    }

    public String toString() {
        return toZ3().toString();
    }

    public boolean equals(Object other) {
        if (other instanceof CustomExpr) {
            CustomExpr o = (CustomExpr) other;
            return o.type.equals(this.type);
        }
        return false;
    }

    public abstract CustomExpr substitute(Map<String, String> changes);

    public CustomExpr substitute(String from, String to) {
        Map<String, String> changes = new HashMap<>();
        changes.put(from, to);
        return substitute(changes);
    }

    public boolean isConst() {
        return this instanceof ConstantCustomExpr;
    }

    public ConstantCustomExpr asConst() {
        assert isConst();
        return (ConstantCustomExpr) this;
    }
}
