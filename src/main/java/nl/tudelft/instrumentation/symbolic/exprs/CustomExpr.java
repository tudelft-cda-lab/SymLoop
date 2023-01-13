package nl.tudelft.instrumentation.symbolic.exprs;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;

/**
 * 
 */

public abstract class CustomExpr {

    public ExprType type;

    public CustomExpr(ExprType type) {
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
}
