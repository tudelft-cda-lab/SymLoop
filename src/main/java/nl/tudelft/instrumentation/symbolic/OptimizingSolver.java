package nl.tudelft.instrumentation.symbolic;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Optimize;
import com.microsoft.z3.Status;
import com.microsoft.z3.Optimize.Handle;

class OptimizingSolver implements SolverInterface {

    private Optimize solver;

    public OptimizingSolver() {
        this.solver = PathTracker.ctx.mkOptimize();
    }

    @Override
    public void add(BoolExpr... exprs) {
        solver.Add(exprs);

    }

    @Override
    public Status check() {
        return solver.Check();
    }

    @Override
    public Model getModel() {
        return solver.getModel();
    }

    @Override
    public void pop() {
        solver.Pop();
    }

    @Override
    public void push() {
        solver.Push();
    }

    @Override
    public void reset() {
        solver = PathTracker.ctx.mkOptimize();

    }

    public Handle minimize(Expr e) {
        return solver.MkMinimize(e);
    }
}
