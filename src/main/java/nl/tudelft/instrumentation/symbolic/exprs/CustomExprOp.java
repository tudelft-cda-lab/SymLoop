package nl.tudelft.instrumentation.symbolic.exprs;

import java.util.Arrays;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;

import nl.tudelft.instrumentation.symbolic.PathTracker;
import nl.tudelft.instrumentation.symbolic.Settings;

/**
 * 
 */

public class CustomExprOp extends CustomExpr {

    public enum Operation {
        ADD,
        SUB,
        EQ,
        LTE,
        GTE,
        LT,
        GT,
        AND,
        OR,
        NOT,
        MUL,
        MOD,
        DIV,
        UMIN,
        ITE,
    }

    public final Operation op;
    public final CustomExpr[] args;

    private Expr z3Cache = null;

    private CustomExprOp(ExprType type, Operation op, CustomExpr... args) {
        super(type);
        for (int i = 0; i < args.length; i++) {
            assert args[i] != null;
        }
        this.args = args;
        this.op = op;
    }

    public static CustomExprOp mkBinop(Operation op, CustomExpr left, CustomExpr right) {
        ExprType ret;
        switch (op) {
            case ADD:
            case DIV:
            case MOD:
            case MUL:
            case SUB:
                ret = ExprType.INT;
                break;
            case EQ:
            case GT:
            case GTE:
            case LT:
            case LTE:
                ret = ExprType.BOOL;
                break;
            default:
                ret = null;
                System.out.println("INVALID BINOP:" + op);
                assert false;

        }
        return new CustomExprOp(ret, op, left, right);
    }

    public static CustomExprOp mkEq(CustomExpr a, CustomExpr b) {
        if (a.type != b.type) {
            throw new IllegalArgumentException("TYPES NOT EQUAL");
        }
        return new CustomExprOp(ExprType.BOOL, Operation.EQ, a, b);
    }

    public static CustomExprOp mkAnd(CustomExpr... args) {
        for (CustomExpr e : args) {
            assertType(e, ExprType.BOOL);
        }
        return new CustomExprOp(ExprType.BOOL, Operation.AND, args);
    }

    public static CustomExpr mkUnaryMinus(CustomExpr var) {
        assertType(var, ExprType.INT);
        return new CustomExprOp(ExprType.INT, Operation.UMIN, var);
    }

    public static CustomExprOp mkOr(CustomExpr... args) {
        for (CustomExpr e : args) {
            assertType(e, ExprType.BOOL);
        }
        return new CustomExprOp(ExprType.BOOL, Operation.OR, args);
    }

    public static CustomExprOp mkSub(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.INT, Operation.SUB, a, b);
    }

    public static CustomExprOp mkITE(CustomExpr condition, CustomExpr a, CustomExpr b) {
        assertType(condition, ExprType.BOOL);
        assert a.type.equals(b.type);
        return new CustomExprOp(a.type, Operation.ITE, condition, a, b);
    }

    public static CustomExprOp mkDiv(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.INT, Operation.DIV, a, b);
    }

    public static CustomExprOp mkMod(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.INT, Operation.MOD, a, b);
    }

    public static CustomExprOp mkAdd(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.INT, Operation.ADD, a, b);
    }

    public static CustomExprOp mkMul(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.INT, Operation.MUL, a, b);
    }

    public static CustomExprOp mkLt(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.BOOL, Operation.LT, a, b);
    }

    public static CustomExprOp mkLe(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.BOOL, Operation.LTE, a, b);
    }

    public static CustomExprOp mkGt(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.BOOL, Operation.GT, a, b);
    }

    public static CustomExprOp mkGe(CustomExpr a, CustomExpr b) {
        assertType(a, ExprType.INT);
        assertType(b, ExprType.INT);
        return new CustomExprOp(ExprType.BOOL, Operation.GTE, a, b);
    }

    private static void assertType(CustomExpr e, ExprType t) {
        assert e.type.equals(t);
    }

    public static CustomExprOp mkNot(CustomExpr a) {
        assertType(a, ExprType.BOOL);
        return new CustomExprOp(ExprType.BOOL, Operation.NOT, a);
    }

    public ArithExpr[] argsToArith() {
        ArithExpr[] newArgs = new ArithExpr[this.args.length];
        for (int i = 0; i < newArgs.length; i++) {
            newArgs[i] = this.args[i].toArithExpr();
        }
        return newArgs;
    }

    public BoolExpr[] argsToBool() {
        BoolExpr[] newArgs = new BoolExpr[this.args.length];
        for (int i = 0; i < newArgs.length; i++) {
            newArgs[i] = this.args[i].toBoolExpr();
        }
        return newArgs;
    }

    static ArithExpr mkAbs(Context ctx, ArithExpr a) {
        return (ArithExpr) ctx.mkITE(ctx.mkGe(a, ctx.mkInt(0)), a, ctx.mkUnaryMinus(a));
    }

    static ArithExpr mkSign(Context ctx, ArithExpr a) {
        return (ArithExpr) ctx.mkITE(
                ctx.mkEq(a, ctx.mkInt(0)),
                ctx.mkInt(0),
                ctx.mkITE(
                        ctx.mkGt(a, ctx.mkInt(0)),
                        ctx.mkInt(1),
                        ctx.mkInt(-1)));
    }


    @Override
    public Expr toZ3() {
        if(this.z3Cache == null) {
            this.z3Cache = toZ3non_cached();
        }
        return this.z3Cache;
    }

    public Expr toZ3non_cached() {
        Context ctx = PathTracker.ctx;
        switch (this.op) {
            case ADD:
                return ctx.mkAdd(this.argsToArith());
            case AND:
                return ctx.mkAnd(this.argsToBool());
            case EQ:
                return ctx.mkEq(this.args[0].toZ3(), this.args[1].toZ3());
            case GT:
                return ctx.mkGt(this.args[0].toArithExpr(), this.args[1].toArithExpr());
            case GTE:
                return ctx.mkGe(this.args[0].toArithExpr(), this.args[1].toArithExpr());
            case LT:
                return ctx.mkLt(this.args[0].toArithExpr(), this.args[1].toArithExpr());
            case LTE:
                return ctx.mkLe(this.args[0].toArithExpr(), this.args[1].toArithExpr());
            case MUL:
                return ctx.mkMul(this.argsToArith());
            case NOT:
                return ctx.mkNot(this.args[0].toBoolExpr());
            case OR:
                return ctx.mkOr(this.argsToBool());
            case SUB:
                return ctx.mkSub(this.argsToArith());
            case DIV:
                IntExpr left = this.args[0].toArithExpr();
                IntExpr right = this.args[1].toArithExpr();
                if (Settings.getInstance().CORRECT_INTEGER_MODEL) {
                    return ctx.mkMul(
                            ctx.mkDiv(
                                    mkAbs(ctx, left),
                                    mkAbs(ctx, right)),
                            mkSign(ctx, left),
                            mkSign(ctx, right));
                } else {
                    return ctx.mkDiv(left, right);
                }
            case MOD:
                left = this.args[0].toArithExpr();
                right = this.args[1].toArithExpr();
                ArithExpr mod = ctx.mkMod(left, right);
                if (Settings.getInstance().CORRECT_INTEGER_MODEL) {
                    return ctx.mkITE(
                            ctx.mkOr(
                                    ctx.mkGe(left,
                                            (ArithExpr) ctx.mkInt(0)),
                                    ctx.mkEq(mod,
                                            (ArithExpr) ctx.mkInt(0))),

                            mod,
                            ctx.mkSub(mod, right));
                } else {
                    return mod;
                }
            case UMIN:
                return ctx.mkUnaryMinus(this.args[0].toArithExpr());
            case ITE:
                return ctx.mkITE(this.args[0].toBoolExpr(), this.args[1].toZ3(), this.args[2].toZ3());
            default:
                break;
        }
        assert false;
        return null;
    }

    @Override
    public CustomExpr substitute(String[] from, String[] to) {
        CustomExpr[] newArgs = new CustomExpr[this.args.length];
        CustomExpr old;
        boolean self = true;
        for(int i = 0; i < newArgs.length; i++) {
            old = newArgs[i];
            newArgs[i] = this.args[i].substitute(from, to);
            if (old != newArgs[i]) {
                self = false;
            }
        }
        if (self) {
            return this;
        } else {
            return new CustomExprOp(type, this.op, newArgs);
        }
    }

    public boolean equals(Object other) {
        if (super.equals(other) && other instanceof CustomExprOp) {
            CustomExprOp o = (CustomExprOp) other;
            return o.op == op && Arrays.equals(o.args, args);
        }
        return false;
    }

}
