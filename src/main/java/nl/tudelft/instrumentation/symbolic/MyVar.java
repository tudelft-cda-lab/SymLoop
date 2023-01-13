package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import com.microsoft.z3.*;

import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;

import java.util.Random;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This class serves as a wrapper object for each of the java primitive
 * types that are used in the RERS problems. With this class, we can
 * transform and store each variable that is defined in a RERS problem
 * as a Z3 Expression. The Z3 expression can then be used in the construction
 * of a path constraint for the solver.
 *
 * @author Sicco Verwer
 */
public class MyVar {
    public CustomExpr expr;
    public Expr z3var; // the Z3 expression that will used in the construction of a path constraint.
    public String name = "v";

    /**
     * Create a new MyVar object from a Z3 expression that has already been given a name before.
     * @param v the Z3 expression.
     */
    MyVar(CustomExpr c){
        assert c != null;
        this.z3var = c.toZ3();
        this.expr = c;
    }

    /**
     * Create a new MyVar object from a new Z3 expression that has been created.
     * @param v the Z3 expression
     * @param n the name of the variable.
     */
    MyVar(String n, CustomExpr c){
        assert c != null;
        this.z3var = c.toZ3();
        this.name = n;
        this.expr = c;
    }

    public void assign(CustomExpr c){
        assert c != null;
        this.z3var = c.toZ3();
        this.expr = c;
    }

}
