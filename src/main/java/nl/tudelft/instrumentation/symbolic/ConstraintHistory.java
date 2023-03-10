
package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.Pair;

import com.microsoft.z3.*;

import nl.tudelft.instrumentation.symbolic.exprs.ConstantCustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExprOp;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExprOp.Operation;

public class ConstraintHistory {

    private HashMap<String, List<CustomExpr>> variables = new HashMap<>();
    private HashMap<String, List<Integer>> lastVariables = new HashMap<>();

    private List<List<CustomExpr>> loopModelList = new ArrayList<>();
    private List<List<Boolean>> isAssignment = new ArrayList<>();
    private int numberOfSaves = 0;

    private HashSet<String> existing = new HashSet<>();

    public int getNumberOfSaves() {
        return numberOfSaves;
    }

    public ConstraintHistory() {
    }

    public CustomExpr mkAnd(List<CustomExpr> exprs) {
        return CustomExprOp.mkAnd(exprs.toArray(CustomExpr[]::new));
    }

    public CustomExpr mkOr(List<CustomExpr> exprs) {
        return CustomExprOp.mkOr(exprs.toArray(CustomExpr[]::new));
    }

    public Map<String, Integer> getVariables() {
        Map<String, Integer> vars = new HashMap<>();
        for (Entry<String, List<CustomExpr>> e : variables.entrySet()) {
            vars.put(e.getKey(), e.getValue().size());
        }
        return vars;
    }

    public CustomExpr getExtendedConstraint(int lastNSaves, List<Replacement> replacements) {
        assert lastNSaves <= loopModelList.size();
        List<CustomExpr> expressions = new ArrayList<>();
        int amountOfSaves = loopModelList.size();
        for (int i = amountOfSaves - lastNSaves; i < amountOfSaves; i++) {
            List<CustomExpr> allConstraints = loopModelList.get(i);
            List<CustomExpr> filtered = new ArrayList<>();
            for (CustomExpr e : allConstraints) {
                CustomExpr changed = e;
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

    public CustomExpr getConstraint(int lastNSaves) {
        assert lastNSaves <= loopModelList.size();
        List<CustomExpr> expressions = new ArrayList<>();
        int amountOfSaves = loopModelList.size();
        for (int i = amountOfSaves - lastNSaves; i < amountOfSaves; i++) {
            expressions.add(mkAnd(loopModelList.get(i)));
        }
        return mkAnd(expressions);
    }

    public Pair<CustomExpr, CustomExpr> getSeperateAssignAndBranches(int lastNSaves) {
        assert lastNSaves <= loopModelList.size();
        List<CustomExpr> branches = new ArrayList<>();
        List<CustomExpr> assigns = new ArrayList<>();
        int amountOfSaves = loopModelList.size();
        for (int i = amountOfSaves - lastNSaves; i < amountOfSaves; i++) {
            List<CustomExpr> c = loopModelList.get(i);
            List<Boolean> a = isAssignment.get(i);
            for(int j = 0; j < c.size(); j++){
                if (a.get(j)) {
                    assigns.add(c.get(j));
                } else {
                    branches.add(c.get(j));
                }
            }
        }
        return Pair.of(mkAnd(assigns), mkAnd(branches));
    }

    private int getLastVariableLength(String name, int lastN) {
        List<Integer> list = lastVariables.get(name);
        assert lastN <= list.size();
        return list.get(list.size() - lastN);
    }

    public void save() {
        loopModelList.add(new ArrayList<>());
        isAssignment.add(new ArrayList<>());
        for (String name : variables.keySet()) {
            lastVariables.get(name).add(variables.get(name).size());
        }
        this.numberOfSaves++;
        // existing.clear();
    }

    public void resetNumberOfSave() {
        numberOfSaves = 0;
    }

    public CustomExpr getSelfLoopExpr(int amountOfSaves) {
        List<CustomExpr> constraints = new ArrayList<>();
        for (String name : variables.keySet()) {
            List<CustomExpr> assigns = variables.get(name);
            int lastLength = getLastVariableLength(name, amountOfSaves);
            if (lastLength == 0) {
                return ConstantCustomExpr.fromBool(false);
            }
            constraints.add(CustomExprOp.mkEq(assigns.get(lastLength - 1), assigns.get(assigns.size() - 1)));
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
        isAssignment.clear();
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

    void nextInput(CustomExpr inputConstraint) {
        addToLoopModel(inputConstraint, true);
    }

    private void addToLoopModelList(CustomExpr condition, boolean isAssign) {
        // getLast(loopModelList).add(condition);
        if (Settings.getInstance().UNFOLD_AND && condition instanceof CustomExprOp) {
            CustomExprOp c = (CustomExprOp) condition;
            if (c.op == Operation.AND) {
                for (CustomExpr arg : c.args) {
                    CustomExpr v = arg;
                    addToLoopModelList(v, isAssign);
                }
                return;
            }

        }
        getLast(loopModelList).add(condition);
        getLast(isAssignment).add(isAssign);
    }

    void addToLoopModel(CustomExpr condition, boolean isAssign) {
        addToLoopModelList(condition, isAssign);
    }

}
