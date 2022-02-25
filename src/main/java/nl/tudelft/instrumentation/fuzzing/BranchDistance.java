package nl.tudelft.instrumentation.fuzzing;

/**
 * Making a if-statement true: (value = false) (target = true)
 * a : d = {0 if a is true, 1 otherwise}
 * !a : d = {1 if a is true, 0 otherwise}
 * a == b : d = abs(a-b)
 * a != b : d = {0 if a !=b, 1 otherwise}
 * a < b : d = {0 if a < b; a-b + K otherwise}
 * a <= b : d = {0 if a <= b; a-b otherwise}
 * a > b : d = {0 if a > b; b-a + K otherwise}
 * a >= b : d = {0 if a >= b; b - a otherwise}
 * and for combinations of predicates:
 * 
 * p1 && p2 : d = d(p1) + d(p2)
 * p1 | p2 : d = min(d(p1), d(p2))
 * p1 XOR p2 : d = min(d(p1) + d(!p2), d(!p1) + d(p2))
 * !p1 : d = 1 - d(p1)
 * 
 * Making a if-statement false: (value = true) (target = false)
 * a : d = {1 if a is true, 0 otherwise}
 * !a : d = {0 if a is true, 1 otherwise}
 * a == b : d = {0 if a != b, 1 otherwise}
 * a != b : d = abs(a-b)
 * a < b : d = {b-a if a < b; 0 otherwise}
 * a <= b : d = {b-a+1 if a <= b; 0 otherwise}
 * a > b : d = {a-b if a > b; 0 otherwise}
 * a >= b : b = {a-+1 if a >= b; 0 otherwise}
 * 
 * and for combinations of predicates:
 * p1 & p2 : d = min(d(p1), d(p2))
 * p1 | p2 : d = d(p1) + d(p2)
 * p1 XOR p2 : d = min(d(p1) + d(p2), d(!p1) + d(!p2))
 * !p1 : d = 1 - d(p1)
 */
public class BranchDistance {

    private static int stringDifference(String a, String b) {
        int index = 0;
        int difference = 0;
        while (index < a.length() && index < b.length()) {
            difference += Math.abs(a.charAt(index) - b.charAt(index));
            index++;
        }
        while (index < a.length()) {
            difference += a.charAt(index);
        }
        while (index < b.length()) {
            difference += b.charAt(index);
        }
        return difference;
    }

    private static int getVarIntegerValue(MyVar x) {
        if (x.type == TypeEnum.INT) {
            return x.int_value;
        } else if (x.type == TypeEnum.UNARY && x.operator == "-") {
            return -getVarIntegerValue(x.left);
        } else if (x.type == TypeEnum.BINARY && x.operator == "+") {
            return getVarIntegerValue(x.left) + getVarIntegerValue(x.right);
        } else if (x.type == TypeEnum.BINARY && x.operator == "-") {
            return getVarIntegerValue(x.left) - getVarIntegerValue(x.right);
        }
        throw new AssertionError(String.format("Var not reducable to integer: %s", x.toString()));
    }

    private static Double varAbs(MyVar a, MyVar b) {
        if (a.type == TypeEnum.STRING
                && b.type == TypeEnum.STRING) {
            return normalise(stringDifference(a.str_value, b.str_value));
        } else if (a.type == TypeEnum.BOOL
                && b.type == TypeEnum.BOOL) {
            return normalise(a.value == b.value ? 0 : 1);
        } else {
            int av = getVarIntegerValue(a);
            int bv = getVarIntegerValue(b);
            return normalise(Math.abs(av - bv));
        }
    }

    private static Double notEqualDistance(MyVar a, MyVar b) {
        if (a.type == TypeEnum.STRING
                && b.type == TypeEnum.STRING) {
            return normalise(a.str_value.equals(b.str_value) ? 1 : 0);
        } else if (a.type == TypeEnum.BOOL
                && b.type == TypeEnum.BOOL) {
            return normalise(a.value == b.value ? 1 : 0);
        } else {
            int av = getVarIntegerValue(a);
            int bv = getVarIntegerValue(b);
            return normalise(av == bv ? 1 : 0);
        }
    }

    private static Double binaryOperatorDistance(MyVar condition, boolean value) {
        int a;
        int b;
        switch (condition.operator) {
            case "==":
                if (value) {
                    return notEqualDistance(condition.left, condition.right);
                } else {
                    return varAbs(condition.left, condition.right);
                }
            case "!=":
                if (value) {
                    return varAbs(condition.left, condition.right);
                } else {
                    return notEqualDistance(condition.left, condition.right);
                }
            case "<":
                a = getVarIntegerValue(condition.left);
                b = getVarIntegerValue(condition.right);
                if (value) {
                    return normalise(a < b ? (b - a) : 0);
                } else {
                    return normalise(a < b ? 0 : (a - b + 1));
                }
            case "<=":
                a = getVarIntegerValue(condition.left);
                b = getVarIntegerValue(condition.right);
                if (value) {
                    return normalise(a <= b ? (b - a + 1) : 0);
                } else {
                    return normalise(a <= b ? 0 : (a - b));
                }
            case ">":
                a = getVarIntegerValue(condition.left);
                b = getVarIntegerValue(condition.right);
                if (value) {
                    return normalise(a > b ? (a - b) : 0);
                } else {
                    return normalise(a > b ? 0 : (b - a + 1));
                }
            case ">=":
                a = getVarIntegerValue(condition.left);
                b = getVarIntegerValue(condition.right);
                if (value) {
                    return normalise(a >= b ? (a - b + 1) : 0);
                } else {
                    return normalise(a >= b ? 0 : (b - a));
                }
            case "||":
                if (value) {
                    return normalise(compute(condition.left, value)
                            + compute(condition.right, value));
                } else {
                    return Math.min(compute(condition.left, value),
                            compute(condition.right, value));
                }
            case "&&":
                if (value) {
                    return Math.min(compute(condition.left, value),
                            compute(condition.right, value));
                } else {
                    return normalise(compute(condition.left, value)
                            + compute(condition.right, value));
                }
            case "^":
                if (value) {
                    return Math.min(normalise(compute(condition.left, value)
                            + compute(condition.right, !value)),
                            normalise(compute(condition.left, !value)
                                    + compute(condition.right, value)));
                } else {
                    return Math.min(normalise(compute(condition.left, value)
                            + compute(condition.right, value)),
                            normalise(compute(condition.left, !value)
                                    + compute(condition.right, !value)));
                }
        }
        throw new AssertionError("not implemented yet, binaryOperatorDistance: " + condition.operator);

    }

    private static Double unaryOperatorDistance(MyVar condition, boolean value) {
        switch (condition.operator) {
            case "!":
                if (condition.left.type == TypeEnum.BOOL) {
                    if (value) {
                        return normalise(condition.left.value ? 1 : 0);
                    } else {
                        return normalise(condition.left.value ? 0 : 1);
                    }
                } else {
                    return 1 - compute(condition.left, value);
                    // throw new AssertionError("not implemented yet, unaryOperatorDistance: +
                    // condition.operator);
                }
        }
        throw new AssertionError("not implemented yet, unaryOperatorDistance: " + condition.operator);
    }

    private static double normalise(double d) {
        return d / (d + 1);
    }

    private static Double normalise(int dist) {
        Double d = Double.valueOf(dist);
        return d / (d + 1);
    }

    public static Double compute(MyVar condition, boolean value) {
        switch (condition.type) {
            case BINARY:
                return binaryOperatorDistance(condition, value);
            case UNARY:
                return unaryOperatorDistance(condition, value);
            case BOOL:
                if (value) {
                    return normalise(condition.value ? 1 : 0);
                } else {
                    return normalise(condition.value ? 0 : 1);
                }
            default:
                break;
        }
        throw new AssertionError("not implemented yet, branchDistance: " + condition.toString());
    }

}
