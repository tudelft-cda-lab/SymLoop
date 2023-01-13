package nl.tudelft.instrumentation.symbolic.exprs;

/**
 * 
 */

public class CustomExprOp extends CustomExpr {


    public enum Operation {
        ADD,
        SUB,
        EQ,
        LTE,
        GTE,
        LT,
        GT,
        AND,
        OR,
    }

    public final Operation op;
    public final CustomExpr[] args;

    private CustomExprOp(ExprType type, CustomExpr[] args, Operation op) {
        super(type);
        this.args = args;
        this.op = op;
    }

    public static CustomExprOp mkEq(CustomExpr a, CustomExpr b) {
        if(a.type != b.type) {
            throw new IllegalArgumentException("TYPES NOT EQUAL");
        }
        return new CustomExprOp(ExprType.BOOL, new CustomExpr[]{a, b}, Operation.EQ);
    }

    public static CustomExprOp mkAnd(CustomExpr... args) {
        for(CustomExpr e : args) {
            assertType(e, ExprType.BOOL);
        }
        return new CustomExprOp(ExprType.BOOL, args, Operation.AND);
    }

    public static CustomExprOp mkOr(CustomExpr... args) {
        for(CustomExpr e : args) {
            assertType(e, ExprType.BOOL);
        }
        return new CustomExprOp(ExprType.BOOL, args, Operation.OR);
    }

    public static CustomExprOp mkSub(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.INT, new CustomExpr[]{a, b}, Operation.SUB);
    }
    public static CustomExprOp mkAdd(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.INT, new CustomExpr[]{a, b}, Operation.SUB);
    }

    private static void assertType(CustomExpr e, ExprType t) {
        assert e.type == t;
    }

}

