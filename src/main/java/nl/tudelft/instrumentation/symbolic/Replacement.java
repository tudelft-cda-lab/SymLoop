
package nl.tudelft.instrumentation.symbolic;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.*;

class Replacement {
    public final String name;
    public final Sort sort;
    public final int start;
    public final int added;
    public final int stop;

    public Replacement(String name, Sort s, int start, int added, int stop) {
        this.name = name;
        this.sort = s;
        this.start = start;
        this.added = added;
        this.stop = stop;
    }

    public BoolExpr applyTo(BoolExpr expr) {
        return this.applyTo(expr, 0);
    }

    public static BoolExpr applyAllTo(List<Replacement> replacements, BoolExpr e) {
        for (Replacement r : replacements) {
            e = r.applyTo(e);
        }
        return e;
    }

    public List<Expr> getAllExprs(int amount) {
        List<Expr> exprs = new ArrayList<>();
        for (int a = getIndexAfter(0)+1; a <= getIndexAfter(amount); a++) {
            exprs.add(getExprFor(a));
        }
        return exprs;
    }

    public BoolExpr applyTo(BoolExpr expr, int amount) {
        // Loop backwards to prevent repeated subsitution
        for (int i = this.start; i >= this.stop; i--) {
            int base = i + (this.added * amount);
            expr = (BoolExpr) expr.substitute(
                    getExprFor(base),
                    getExprFor(base + this.added));
        }
        return expr;
    }

    private Expr getExprFor(int index) {
        return PathTracker.ctx.mkConst(
                PathTracker.ctx.mkSymbol(
                        SymbolicExecutionLab.getVarName(this.name, index)),
                this.sort);
    }

    public BoolExpr isSelfLoopExpr() {
        return PathTracker.ctx.mkEq(getExprAfter(0), getExprAfter(1));
    }

    public int getIndexAfter(int amount) {
        return this.start + (this.added * amount);
    }

    public Expr getExprAfter(int index) {
        return getExprFor(getIndexAfter(index));
    }

    public String getName() {
        return name;
    }
}
