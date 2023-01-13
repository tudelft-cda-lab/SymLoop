package nl.tudelft.instrumentation.symbolic.exprs;

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
        return new ConstantCustomExpr(ExprType.BOOL,value);
    }

    public static ConstantCustomExpr fromInt(int value) {
        return new ConstantCustomExpr(ExprType.INT, value);
    }

    public static ConstantCustomExpr fromString(String value) {
        return new ConstantCustomExpr(ExprType.STRING, value);
    }

}

