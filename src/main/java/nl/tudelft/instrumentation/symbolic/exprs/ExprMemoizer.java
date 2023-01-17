package nl.tudelft.instrumentation.symbolic.exprs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import nl.tudelft.instrumentation.symbolic.InferringSolver;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExprOp.Operation;

/**
 * 
 */

public class ExprMemoizer {

    private Map<String, CustomExpr> memory = new HashMap<>();

    private List<List<String>> history = new ArrayList<>();
    private List<String> current = new ArrayList<>();

    private InferringSolver solver;

    public ExprMemoizer(InferringSolver s) {
        this.solver = s;
    }

    public void push() {
        history.add(current);
        current = new ArrayList<>();
    }

    public void pop() {
        memory.keySet().removeAll(current);
        assert history.size() > 0;
        current = history.remove(history.size() - 1);
    }

    private boolean addToMemory(String name, CustomExpr value) {
        if (!(value instanceof ConstantCustomExpr)) {
            return false;
        }
        if(memory.containsKey(name)) {
            CustomExpr e = memory.get(name);
            return !e.equals(value);
        }
        memory.put(name, value);
        current.add(name);
        // solver.add(CustomExprOp.mkEq(new NamedCustomExpr(name, value.type), value));
        return false;
    }

    public CustomExpr optimize(CustomExpr e) {
        return optimize(e, false);
    }

    public CustomExpr optimize(CustomExpr e, boolean remember) {
        if (e instanceof ConstantCustomExpr) {
            return e;
        } else if (e instanceof NamedCustomExpr) {
            NamedCustomExpr named = (NamedCustomExpr) e;
            return memory.getOrDefault(named.name, e);
        } else if (e instanceof CustomExprOp) {
            if (remember) {
                CustomExpr scanned = scanForEq(e);
                if(!scanned.equals(e)) {
                    return scanned;
                }
            }
            return eval((CustomExprOp) e);
        }
        assert false;
        return e;
    }

    public CustomExpr evalBinop(CustomExpr og, CustomExpr leftExpr, CustomExpr rightExpr, Operation op) {
        if (leftExpr instanceof ConstantCustomExpr && rightExpr instanceof ConstantCustomExpr) {
            ConstantCustomExpr l = ((ConstantCustomExpr) leftExpr);
            ConstantCustomExpr r = ((ConstantCustomExpr) rightExpr);
            if (op == Operation.EQ) {
                return ConstantCustomExpr.fromBool(l.value.equals(r.value));
            }
            // System.out.println(og.toZ3());
            int left = l.asInt();
            int right = r.asInt();
            switch (op) {
                case ADD:
                    return ConstantCustomExpr.fromInt(left + right);
                case DIV:
                    return ConstantCustomExpr.fromInt(left / right);
                case EQ:
                    return ConstantCustomExpr.fromBool(left == right);
                case GT:
                    return ConstantCustomExpr.fromBool(left > right);
                case GTE:
                    return ConstantCustomExpr.fromBool(left >= right);
                case LT:
                    return ConstantCustomExpr.fromBool(left < right);
                case LTE:
                    return ConstantCustomExpr.fromBool(left <= right);
                case MOD:
                    return ConstantCustomExpr.fromInt(left % right);
                case MUL:
                    return ConstantCustomExpr.fromInt(left * right);
                case SUB:
                    return ConstantCustomExpr.fromInt(left - right);
                default:
                    assert false;
                    return null;
            }
        } else {
            // System.out.printf("OP: %s, l: %s, r: %s.\n",op, leftExpr.toZ3(),
            // rightExpr.toZ3());
            return CustomExprOp.mkBinop(op, leftExpr, rightExpr);
        }
    }

    public CustomExpr handleOr(CustomExpr... args) {
        CustomExpr[] optimized = new CustomExpr[args.length];
        for (int i = 0; i < args.length; i++) {
            optimized[i] = optimize(args[i]);
            if (optimized[i] instanceof ConstantCustomExpr) {
                if (((ConstantCustomExpr) optimized[i]).asBool() == true) {
                    return ConstantCustomExpr.fromBool(true);
                }
            }
        }
        return CustomExprOp.mkOr(optimized);
    }

    public CustomExpr scanForEq(CustomExpr andExpr) {
        if (andExpr instanceof CustomExprOp) {
            CustomExprOp c = (CustomExprOp) andExpr;
            if (c.op.equals(Operation.AND)) {
                for (CustomExpr e : c.args) {
                    scanForEq(e);
                }
            } else if (c.op.equals(Operation.EQ)) {
                CustomExpr left = c.args[0];
                CustomExpr right = c.args[1];
                if (left instanceof NamedCustomExpr) {
                    right = optimize(right, true);
                    if (right instanceof ConstantCustomExpr) {
                        String name = ((NamedCustomExpr) left).name;
                        boolean conflicts = addToMemory(name, right);
                        if (conflicts) {
                            return ConstantCustomExpr.FALSE;
                        }
                    }
                }
            }
        }
        return andExpr;
    }

    public CustomExpr handleAnd(CustomExpr... args) {
        List<CustomExpr> optimized = new ArrayList<>(args.length);
        for (int i = 0; i < args.length; i++) {
            CustomExpr o = optimize(args[i]);
            if (o.isConst()) {
                if (!o.asConst().asBool()) {
                    return ConstantCustomExpr.fromBool(false);
                }
            } else {
                optimized.add(o);
            }
        }
        if (optimized.size() == 0) {
            return ConstantCustomExpr.fromBool(true);
        }
        return CustomExprOp.mkAnd(optimized.toArray(CustomExpr[]::new));
    }

    public CustomExpr handleNot(CustomExpr arg) {
        if (arg instanceof ConstantCustomExpr) {
            return ConstantCustomExpr.fromBool(!(boolean) ((ConstantCustomExpr) arg).value);
        } else {
            return CustomExprOp.mkNot(arg);
        }
    }

    public CustomExpr handleITE(CustomExpr condition, CustomExpr a, CustomExpr b) {
        CustomExpr c = optimize(condition);
        if (c instanceof ConstantCustomExpr) {
            if (((ConstantCustomExpr) c).asBool()) {
                return optimize(a);
            } else {
                return optimize(b);
            }
        }
        return CustomExprOp.mkITE(c, optimize(a), optimize(b));
    }

    public CustomExpr eval(CustomExprOp e) {
        switch (e.op) {
            case ADD:
            case DIV:
            case EQ:
            case GT:
            case GTE:
            case LT:
            case LTE:
            case MOD:
            case MUL:
            case SUB:
                assert e.args.length == 2;
                CustomExpr left = optimize(e.args[0]);
                CustomExpr right = optimize(e.args[1]);
                return evalBinop(e, left, right, e.op);
            case AND:
                return handleAnd(e.args);
            case ITE:
                return handleITE(e.args[0], e.args[1], e.args[2]);
            case NOT:
                return handleNot(optimize(e.args[0]));
            case OR:
                return handleOr(e.args);
            case UMIN:
                CustomExpr arg = optimize(e.args[0]);
                if (arg instanceof ConstantCustomExpr) {
                    return ConstantCustomExpr.fromInt(-(int) ((ConstantCustomExpr) arg).value);
                } else {
                    return CustomExprOp.mkUnaryMinus(arg);
                }
            default:
                break;
        }
        System.out.println(e.op);
        assert false;
        return e;
    }

    public void reset() {
        this.memory.clear();
        this.current.clear();
        this.history.clear();
    }

    public String toString() {
        String output = "";
        for (Entry<String, CustomExpr> e : memory.entrySet()) {
            output += String.format("%s = %s, ", e.getKey(), e.getValue());
        }
        return output;
    }

}
