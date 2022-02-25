package nl.tudelft.instrumentation.fuzzing;

public enum VisitedEnum {
    NONE,
    TRUE,
    FALSE,
    BOTH;

    public static VisitedEnum from(boolean value) {
        if (value) {
            return TRUE;
        } else {
            return FALSE;
        }
    }

    VisitedEnum andVisit(boolean value) {
        if (this == NONE) {
            return VisitedEnum.from(value);
        } else if (this == TRUE && value) {
            return TRUE;
        } else if (this == TRUE && !value) {
            return BOTH;
        } else if (this == FALSE && !value) {
            return FALSE;
        } else if (this == FALSE && value) {
            return BOTH;
        } else if (this == BOTH) {
            return BOTH;
        }
        throw new AssertionError("unreachable");
    }

    boolean hasVisited(boolean value) {
        if (this == NONE) {
            return false;
        } else if (this == TRUE) {
            return value;
        } else if (this == FALSE) {
            return !value;
        } else if (this == BOTH) {
            return true;
        }
        throw new AssertionError("unreachable");
    }

    private VisitedEnum() {
    }

    public int amount() {
        switch (this) {
            case NONE:
                return 0;
            case TRUE:
                return 1;
            case FALSE:
                return 1;
            case BOTH:
                return 2;
            default:
                throw new AssertionError("unreachable");
        }
    }
}
