
package nl.tudelft.instrumentation.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.z3.*;

import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExprOp;
import nl.tudelft.instrumentation.symbolic.exprs.ExprType;
import nl.tudelft.instrumentation.symbolic.exprs.NamedCustomExpr;

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

    public CustomExpr applyTo(CustomExpr expr) {
        return this.applyTo(expr, 0);
    }

    public static CustomExpr applyAllTo(List<Replacement> replacements, CustomExpr e) {
        return applyAllTo(replacements, e, 0);
    }

    public static CustomExpr applyAllTo(List<Replacement> replacements, CustomExpr e, int amount) {
        Map<String, String> changes = new HashMap<>();
        for (Replacement r : replacements) {
            r.addApplyTo(changes, amount);
        }
        return (CustomExpr) e.substitute(changes);
    }

    public List<CustomExpr> getAllExprs(int amount) {
        List<CustomExpr> exprs = new ArrayList<>();
        for (int a = getIndexAfter(0) + 1; a <= getIndexAfter(amount); a++) {
            exprs.add(getExprFor(a));
        }
        return exprs;
    }

    public void addApplyTo(Map<String, String> changes, int amount) {
        // Loop backwards to prevent repeated subsitution
        for (int i = this.start; i >= this.stop; i--) {
            int base = i + (this.added * amount);
            changes.put(getNameFor(base), getNameFor(base + this.added));
        }
    }

    public CustomExpr applyTo(CustomExpr expr, int amount) {
        // Loop backwards to prevent repeated subsitution
        for (int i = this.start; i >= this.stop; i--) {
            int base = i + (this.added * amount);
            expr = (CustomExpr) expr.substitute(
                    getNameFor(base),
                    getNameFor(base + this.added));
        }
        return expr;
    }

    private String getNameFor(int index) {
        return SymbolicExecutionLab.getVarName(this.name, index);
    }

    private CustomExpr getExprFor(int index) {
        return new NamedCustomExpr(getNameFor(index), ExprType.fromSort(this.sort));
    }

    public CustomExpr isSelfLoopExpr() {
        return CustomExprOp.mkEq(getExprAfter(0), getExprAfter(1));
    }

    public int getIndexAfter(int amount) {
        return this.start + (this.added * amount);
    }

    public CustomExpr getExprAfter(int index) {
        return getExprFor(getIndexAfter(index));
    }

    public String getName() {
        return name;
    }
}
