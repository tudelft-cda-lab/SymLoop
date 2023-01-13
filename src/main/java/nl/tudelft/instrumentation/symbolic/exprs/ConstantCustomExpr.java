package nl.tudelft.instrumentation.symbolic.exprs;

import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

import nl.tudelft.instrumentation.symbolic.PathTracker;

/**
 * 
 */

public class ConstantCustomExpr extends CustomExpr {

    public Object value;

    private ConstantCustomExpr(ExprType type, Object value) {
        super(type);
        this.value = value;
    }

    public static ConstantCustomExpr fromBool(boolean value) {
        return new ConstantCustomExpr(ExprType.BOOL, value);
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
                return ctx.mkString((String) value);
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
    public CustomExpr substitute(CustomExpr[] from, CustomExpr[] to) {
        for(int i = 0; i < from.length; i ++) {
            if (this.equals(from[i])) {
                return to[i];
            }
        }
        return this;
    }

}
