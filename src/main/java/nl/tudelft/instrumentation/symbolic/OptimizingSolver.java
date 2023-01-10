package nl.tudelft.instrumentation.symbolic;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Optimize;
import com.microsoft.z3.Status;
import com.microsoft.z3.Optimize.Handle;

class OptimizingSolver implements SolverInterface {

    public static List<DataPoint> solverTimes = new ArrayList<>();
    public static class DataPoint {
        final int traceLength;
        final int numberOfLoops;
        final long timeInMs;
        final SolvingForType type;

        public DataPoint(SolvingForType type, long timeInMs) {
            this.traceLength = SymbolicExecutionLab.processedInput.length();
            this.numberOfLoops = SymbolicExecutionLab.numberOfLoopsInPathConstraint;
            this.timeInMs = timeInMs;
            this.type = type;
        }
    }

    private Optimize solver;

    public OptimizingSolver() {
        this.solver = PathTracker.ctx.mkOptimize();
    }

    @Override
    public void add(BoolExpr... exprs) {
        solver.Add(exprs);
    }

    @Override
    public Status check(SolvingForType type) {
        long start = System.nanoTime();
        Status s = solver.Check();
        long end = System.nanoTime();
        solverTimes.add(new DataPoint(type, end - start));
        return s;
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
