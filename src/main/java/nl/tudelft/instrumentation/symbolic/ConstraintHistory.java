
package nl.tudelft.instrumentation.symbolic;

import java.util.*;

import com.microsoft.z3.*;

import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;

public class ConstraintHistory {

    private HashMap<String, List<CustomExpr>> variables = new HashMap<>();
    private HashMap<String, List<Integer>> lastVariables = new HashMap<>();
    private final Context ctx = PathTracker.ctx;

    private List<List<BoolExpr>> loopModelList = new ArrayList<>();
    private int numberOfSaves = 0;

    private HashSet<String> existing = new HashSet<>();

    public int getNumberOfSaves() {
        return numberOfSaves;
    }

    public ConstraintHistory() {
    }

    public BoolExpr mkAnd(List<BoolExpr> exprs) {
        return ctx.mkAnd(exprs.toArray(BoolExpr[]::new));
    }

    public BoolExpr mkOr(List<BoolExpr> exprs) {
        return ctx.mkOr(exprs.toArray(BoolExpr[]::new));
    }

    public BoolExpr getExtendedConstraint(int lastNSaves, List<Replacement> replacements) {
        assert lastNSaves <= loopModelList.size();
        List<BoolExpr> expressions = new ArrayList<>();
        int amountOfSaves = loopModelList.size();
        for (int i = amountOfSaves - lastNSaves; i < amountOfSaves; i++) {
            List<BoolExpr> allConstraints = loopModelList.get(i);
            List<BoolExpr> filtered = new ArrayList<>();
            for (BoolExpr e : allConstraints) {
                BoolExpr changed = e;
                for (Replacement r : replacements) {
                    changed = r.applyTo(changed);
                }
                if (!e.equals(changed)) {
                    filtered.add(changed);
                }
            }
            expressions.add(mkAnd(filtered));
        }
        return mkAnd(expressions);
    }

    public BoolExpr getConstraint(int lastNSaves) {
        assert lastNSaves <= loopModelList.size();
        List<BoolExpr> expressions = new ArrayList<>();
        int amountOfSaves = loopModelList.size();
        for (int i = amountOfSaves - lastNSaves; i < amountOfSaves; i++) {
            expressions.add(mkAnd(loopModelList.get(i)));
        }
        return mkAnd(expressions);
    }

    private int getLastVariableLength(String name, int lastN) {
        List<Integer> list = lastVariables.get(name);
        assert lastN <= list.size();
        return list.get(list.size() - lastN);
    }

    public void save() {
        loopModelList.add(new ArrayList<>());
        for (String name : variables.keySet()) {
            lastVariables.get(name).add(variables.get(name).size());
        }
        this.numberOfSaves++;
        // existing.clear();
    }

    public void resetNumberOfSave() {
        numberOfSaves = 0;
    }

    public BoolExpr getSelfLoopExpr(int amountOfSaves) {
        List<BoolExpr> constraints = new ArrayList<>();
        for (String name : variables.keySet()) {
            List<CustomExpr> assigns = variables.get(name);
            int lastLength = getLastVariableLength(name, amountOfSaves);
            if (lastLength == 0) {
                return ctx.mkBool(false);
            }
            constraints.add(ctx.mkEq(assigns.get(lastLength-1).toZ3(), assigns.get(assigns.size() - 1).toZ3()));
        }
        return mkAnd(constraints);
    }

    public List<Replacement> getReplacementsForLastSaves(int amountOfSaves) {
        List<Replacement> replacements = new ArrayList<Replacement>();
        for (String name : variables.keySet()) {
            List<CustomExpr> assigns = variables.get(name);
            int lastLength = getLastVariableLength(name, amountOfSaves);
            int added = assigns.size() - lastLength;
            if (added > 0) {
                Sort s = assigns.get(0).type.toSort();
                Replacement r = new Replacement(
                        name, s,
                        assigns.size() - 1, added, lastLength - 1);
                replacements.add(r);
            }
        }
        return replacements;
    }

    public void reset() {
        variables.clear();
        lastVariables.clear();
        loopModelList.clear();
        this.numberOfSaves = 0;
        this.existing.clear();
    }

    void assignToVariable(String name, CustomExpr value) {
        if (this.variables.containsKey(name)) {
            this.variables.get(name).add(value);
        } else {
            List<CustomExpr> initial = new ArrayList<>();
            initial.add(value);
            this.variables.put(name, initial);
            List<Integer> l = new ArrayList<Integer>();
            l.add(0);
            this.lastVariables.put(name, l);
        }
    }

    boolean isConstant(Expr value) {
        return value.isNumeral();
    }

    private <T> T getLast(List<T> list) {
        return list.get(list.size() - 1);
    }

    void nextInput(BoolExpr inputConstraint) {
        addToLoopModel(inputConstraint);
    }

    private void addToLoopModelList(BoolExpr condition) {
        // getLast(loopModelList).add(condition);
        if (Settings.getInstance().UNFOLD_AND && (!condition.isConst()) && condition.isAnd()) {
            Expr[] args = condition.getArgs();
            for (Expr arg : args) {
                BoolExpr v = (BoolExpr) arg;
                addToLoopModelList(v);
            }

        } else {
            getLast(loopModelList).add(condition);
        }
    }

    void addToLoopModel(BoolExpr condition) {
        addToLoopModelList(condition);
    }

}
