package nl.tudelft.instrumentation.symbolic;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Status;

interface SolverInterface {
    public abstract void add(BoolExpr... exprs);

    public abstract Status check();

    public abstract Model getModel();

    public abstract void pop();

    public abstract void push();

    public abstract void reset();

}
