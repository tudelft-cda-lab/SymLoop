package nl.tudelft.instrumentation.symbolic.exprs;

/**
 * 
 */

public class NamedCustomExpr extends CustomExpr {

    public final String name;

    public NamedCustomExpr(String name, ExprType type) {
        super(type);
        this.name = name;
    }

}

