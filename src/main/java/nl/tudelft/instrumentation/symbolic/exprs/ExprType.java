package nl.tudelft.instrumentation.symbolic.exprs;

import com.microsoft.z3.Context;
import com.microsoft.z3.Sort;

import nl.tudelft.instrumentation.symbolic.PathTracker;

/**
 * 
 */

public enum ExprType {
    STRING,
    INT,
    BOOL;

    public Sort toSort() {
        Context ctx = PathTracker.ctx;
        switch (this) {
            case BOOL:
                return ctx.getBoolSort();
            case INT:
                return ctx.getIntSort();
            case STRING:
                return ctx.getStringSort();
            default:
                assert false;
                return null;
        }
    }

    public static ExprType fromSort(Sort s) {
        Context ctx = PathTracker.ctx;
        if (s.equals(ctx.getIntSort())) {
            return INT;
        } else if (s.equals(ctx.getStringSort())) {
            return STRING;
        } else if (s.equals(ctx.getBoolSort())) {
            return BOOL;
        }
        assert false;
        return INT;
    }
}
