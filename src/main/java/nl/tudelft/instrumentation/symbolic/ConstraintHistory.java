
package nl.tudelft.instrumentation.symbolic;

import java.util.*;

import com.microsoft.z3.*;

public class ConstraintHistory {

    static class Variable {
        public final String name;
        public final Sort sort;

        public Variable(String name, Sort sort) {
            this.name = name;
            this.sort = sort;
        }
    }

    private HashMap<String, List<Expr>> variables = new HashMap<>();
    private HashMap<String, List<Integer>> lastVariables = new HashMap<>();
    // private List<BoolExpr> loopModel = new ArrayList<>();
    private final Context ctx = PathTracker.ctx;

    private List<List<BoolExpr>> loopModelList = new ArrayList<>();
    private int numberOfSaves = 0;

    public int getNumberOfSaves() {
        return numberOfSaves;
    }

    public ConstraintHistory() {

    }

    public BoolExpr mkAnd(List<BoolExpr> exprs) {
        return ctx.mkAnd(exprs.toArray(BoolExpr[]::new));
    }

    public BoolExpr getConstraint(int lastNSaves) {
        SymbolicExecutionLab.printfRed("%d, %d\n", lastNSaves, loopModelList.size());
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
        // loopModel.add(PathTracker.ctx.mkFalse());
        loopModelList.add(new ArrayList<>());
        for (String name : variables.keySet()) {
            lastVariables.get(name).add(variables.get(name).size());
        }
        this.numberOfSaves++;
    }

    public List<Replacement> getReplacementsForLastSaves(int amountOfSaves) {
        List<Replacement> replacements = new ArrayList<Replacement>();
        for (String name : variables.keySet()) {
            List<Expr> assigns = variables.get(name);
            int lastLength = getLastVariableLength(name, amountOfSaves);
            int added = assigns.size() - lastLength;
            if (added > 0) {
                Sort s = assigns.get(0).getSort();
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
        this.numberOfSaves=0;
        // loopModel.clear();
        // loopModel.add(PathTracker.ctx.mkTrue());
    }

    void assignToVariable(String name, Expr value) {
        if (this.variables.containsKey(name)) {
            // System.out.printf("Assign to %s = %s\n",name, value);
            this.variables.get(name).add(value);
        } else {
            // System.out.printf("New assign to %s = %s\n",name, value);
            List<Expr> initial = new ArrayList<Expr>();
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
        if ((!condition.isConst()) && condition.isAnd()) {
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
