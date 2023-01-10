package nl.tudelft.instrumentation.symbolic;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;

class NormalSolver implements SolverInterface {

    private Solver solver;

    public NormalSolver() {
        this.solver = PathTracker.ctx.mkSolver();
    }

    @Override
    public void add(BoolExpr... exprs) {
        solver.add(exprs);

    }

    @Override
    public Status check(SolvingForType type) {
        return solver.check();
    }

    @Override
    public Model getModel() {
        return solver.getModel();
    }

    @Override
    public void pop() {
        solver.pop();
    }

    @Override
    public void push() {
        solver.push();
    }

    @Override
    public void reset() {
        solver = PathTracker.ctx.mkSolver();

    }

}
