package nl.tudelft.instrumentation.symbolic;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Status;

import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;

interface SolverInterface {

    public static enum SolvingForType {
        BRANCH('B'),
        IS_LOOP('L'),
        IS_REATING_LOOP('R'),
        IS_SELF_LOOP('S');

        final char c;

        SolvingForType(char c) {
            this.c  = c;
        }
    }

    public abstract void add(CustomExpr expr);

    public abstract Status check(SolvingForType type);

    public abstract Model getModel();

    public abstract void pop();

    public abstract void push();

    public abstract void reset();

}
