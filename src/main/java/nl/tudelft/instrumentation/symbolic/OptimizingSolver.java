package nl.tudelft.instrumentation.symbolic;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Optimize;
import com.microsoft.z3.Status;
import com.microsoft.z3.Optimize.Handle;

import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;

class OptimizingSolver implements SolverInterface {

    public static List<DataPoint> solverTimes = new ArrayList<>();

    public static class DataPoint {
        final int traceLength;
        final int numberOfLoops;
        final long timeInNs;
        final SolvingForType type;

        public DataPoint(SolvingForType type, long timeInNs) {
            this.traceLength = SymbolicExecutionLab.processedInput.length();
            this.numberOfLoops = SymbolicExecutionLab.numberOfLoopsInPathConstraint;
            this.timeInNs = timeInNs;
            this.type = type;
        }
    }

    private Optimize solver;

    public OptimizingSolver() {
        this.solver = PathTracker.ctx.mkOptimize();
    }

    @Override
    public void add(CustomExpr expr) {
        solver.Add(expr.toBoolExpr());
    }

    @Override
    public Status check(SolvingForType type) {
        long start = System.nanoTime();
        // System.out.println(this.solver);
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
