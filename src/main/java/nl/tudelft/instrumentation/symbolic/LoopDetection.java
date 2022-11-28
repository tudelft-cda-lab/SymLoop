
package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import com.microsoft.z3.*;


public class LoopDetection {

    static class Replacement {
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

        public BoolExpr applyTo(BoolExpr expr, int amount) {
            // Loop backwards to prevent repeated subsitution
            Context ctx = PathTracker.ctx;
            for (int i = this.start; i >= this.stop; i--) {
                int base = i + (this.added * amount);
                expr = (BoolExpr) expr.substitute(
                        ctx.mkConst(ctx.mkSymbol(SymbolicExecutionLab.getVarName(this.name, base)), this.sort),
                        ctx.mkConst(ctx.mkSymbol(SymbolicExecutionLab.getVarName(this.name, base + this.added)), this.sort));

            }
            return expr;
        }

    }


    private HashSet<String> alreadyFoundLoops = new HashSet<>();
    private HashMap<String, List<Expr>> variables = new HashMap<String, List<Expr>>();
    private HashMap<String, Integer> lastVariables = new HashMap<String, Integer>();
    private BoolExpr loopModel = PathTracker.ctx.mkFalse();
    private Context ctx = PathTracker.ctx;

    public LoopDetection() {
    }

    public void reset() {
        loopModel = PathTracker.ctx.mkFalse();
        variables.clear();
        lastVariables.clear();
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
            this.lastVariables.put(name, 0);
        }
    }

    void nextInput(BoolExpr inputConstraint) {
        loopModel = inputConstraint;
    }


    void addToLoopModel(BoolExpr condition) {
        loopModel = ctx.mkAnd(condition, loopModel);
    }

    void onLoopDone() {
        // System.out.printf("loopmodel: %d %s\n", inputInIndex, loopModel);
        String output = "";
        boolean isLoop = false;
        BoolExpr extended = loopModel;

        // String, Name, I, I2, Sort;
        List<Replacement> replacements = new ArrayList<Replacement>();

        // if (processedInput.length() > 1) {
            for (String name : variables.keySet()) {
                List<Expr> assigns = variables.get(name);
                Integer lastLength = lastVariables.get(name);
                int added = assigns.size() - lastLength;
                // System.out.printf("%s: %d, now: %d\n", name, lastLength, assigns.size());
                if (added > 0) {
                    output += String.format("%s, ", name);
                    isLoop = true;
                    Replacement r = new Replacement(
                            name, assigns.get(0).getSort(),
                            assigns.size() - 1, added, lastLength-1);
                    replacements.add(r);
                    extended = r.applyTo(extended);
                    lastVariables.put(name, assigns.size());
                }
            }
        // }

        if (isLoop && PathTracker.solve(extended, false, false) && alreadyFoundLoops.add(SymbolicExecutionLab.processedInput)) {
            // System.out.printf("loop detected on '%s' for %s: %s\n\nEXTENDED: %s\n",
            // processedInput, output, loopModel, extended);
            // printfYellow("loopmodel: %s\n", loopModel);
            // printfRed("loop detected for %s: on input '%s'\nextended: %s\n", output, processedInput, extended);
            SymbolicExecutionLab.printfRed("loop detected with vars %s: on input '%s'\n", output, SymbolicExecutionLab.processedInput);
            BoolExpr full = extended;
            List<BoolExpr> l = new ArrayList<BoolExpr>();
            for (int i = 1; i < 100; i += 1) {
                l.add(extended);
                for (Replacement r : replacements) {
                    extended = r.applyTo(extended, i);
                }
                // System.out.printf("%s\n", extended);
                full = PathTracker.ctx.mkAnd(extended, full);
                if(PathTracker.solve(full, false, false)) {
                } else {
                    SymbolicExecutionLab.printfGreen("loop ends after %d iterations\n", i);
                    break;
                }
            }

            for (String s : alreadyFoundLoops) {
                SymbolicExecutionLab.printfBlue("%s\n", s);
            }
        }

    }
}
