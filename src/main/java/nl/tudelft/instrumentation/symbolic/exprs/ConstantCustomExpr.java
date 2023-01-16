package nl.tudelft.instrumentation.symbolic.exprs;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

import nl.tudelft.instrumentation.symbolic.PathTracker;

/**
 * 
 */

public class ConstantCustomExpr extends CustomExpr {

    public static final ConstantCustomExpr TRUE = new ConstantCustomExpr(ExprType.BOOL, true);
    public static final ConstantCustomExpr FALSE = new ConstantCustomExpr(ExprType.BOOL, false);

    public static final Map<String, Expr> stringCache = new HashMap<>();
    public Object value;

    Expr createString(String value) {
        if (stringCache.containsKey(value)) {
            return stringCache.get(value);
        }
        Expr e = PathTracker.ctx.mkString((String) value);
        stringCache.put(value, e);
        return e;
    }

    private ConstantCustomExpr(ExprType type, Object value) {
        super(type);
        this.value = value;
    }

    public static ConstantCustomExpr fromBool(boolean value) {
        if (value) {
            return TRUE;
        }
        return FALSE;
    }

    public static ConstantCustomExpr fromInt(int value) {
        return new ConstantCustomExpr(ExprType.INT, value);
    }

    public static ConstantCustomExpr fromString(String value) {
        return new ConstantCustomExpr(ExprType.STRING, value);
    }

    @Override
    public Expr toZ3() {
        Context ctx = PathTracker.ctx;
        switch (type) {
            case BOOL:
                return ctx.mkBool((boolean) value);
            case INT:
                return ctx.mkInt((int) value);
            case STRING:
                return createString((String) value);
        }
        assert false;
        return null;
    }

    public boolean asBool() {
        assert this.type == ExprType.BOOL;
        return (boolean) this.value;
    }

    public int asInt() {
        assert this.type == ExprType.INT;
        return (int) this.value;
    }

    @Override
    public CustomExpr substitute(Map<String, String> changes) {
        return this;
    }

    public boolean equals(Object other) {
        if (super.equals(other) && other instanceof ConstantCustomExpr) {
            ConstantCustomExpr that = (ConstantCustomExpr) other;
            return this.value.equals(that.value);
        }
        return false;
    }

}
