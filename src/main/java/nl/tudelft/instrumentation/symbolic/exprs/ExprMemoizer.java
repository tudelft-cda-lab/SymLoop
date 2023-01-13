package nl.tudelft.instrumentation.symbolic.exprs;

import java.util.HashMap;
import java.util.Map;

import nl.tudelft.instrumentation.symbolic.exprs.CustomExprOp.Operation;

/**
 * 
 */

public class ExprMemoizer {

    private Map<String, CustomExpr> memory = new HashMap<>();

    public ExprMemoizer() {
    }

    public CustomExpr addOptimized(String name, CustomExpr e) {
        // return e;
        // System.out.println("optimizing: " + name + ":" + e.toZ3().toString());
        CustomExpr optimized = optimize(e);
        // System.out.printf("%s optimized is \n", e.toZ3());
        // System.out.println(optimized.toZ3());
        memory.put(name, optimized);
        return optimized;
    }

    public CustomExpr[] optimizeAll(CustomExpr... e) {
        CustomExpr[] args = new CustomExpr[e.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = optimize(e[i]);
        }
        return args;
    }

    public CustomExpr optimize(CustomExpr e) {
        if (e instanceof ConstantCustomExpr) {
            return e;
        } else if (e instanceof NamedCustomExpr) {
            NamedCustomExpr named = (NamedCustomExpr) e;
            return memory.getOrDefault(named.name, e);
        } else if (e instanceof CustomExprOp) {
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

    public CustomExpr handleAnd(CustomExpr... args) {
        CustomExpr[] optimized = new CustomExpr[args.length];
        for (int i = 0; i < args.length; i++) {
            optimized[i] = optimize(args[i]);
            if (optimized[i] instanceof ConstantCustomExpr) {
                if (((ConstantCustomExpr) optimized[i]).asBool() == false) {
                    return ConstantCustomExpr.fromBool(false);
                }
            }
        }
        return CustomExprOp.mkAnd(optimized);
    }

    public CustomExpr handleNot(CustomExpr arg) {
        if (arg instanceof ConstantCustomExpr) {
            return ConstantCustomExpr.fromBool(!(boolean) ((ConstantCustomExpr) arg).value);
        } else {
            return CustomExprOp.mkNot(arg);
        }
    }

    public CustomExpr handleITE(CustomExpr condition, CustomExpr a, CustomExpr b)  {
        CustomExpr c = optimize(condition);
        if (c instanceof ConstantCustomExpr) {
            if(((ConstantCustomExpr) c).asBool()) {
                return a;
            } else {
                return b;
            }
        }
        // System.out.println(condition);
        // System.out.println(a +":"+ a.type + "opt: " + optimize(a).type);
        // System.out.println(b  +":"+ b.type + "opt: " + optimize(b).type);
        // System.out.println("\nasdf\n");
        return CustomExprOp.mkITE(c,  optimize(a), optimize(b));
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
        // assert false;
        return e;
    }

    public void reset() {
        this.memory.clear();
    }

}
