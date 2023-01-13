package nl.tudelft.instrumentation.symbolic;

import java.util.Comparator;
import java.util.List;

class NextTrace implements Comparable<NextTrace> {
    List<String> trace;
    private int linenr;
    private boolean value;
    final String from;

    static Comparator<NextTrace> comparator = Comparator.comparing(NextTrace::traceLength);
           

    public NextTrace(List<String> trace, int linenr, String from, boolean value) {
        this.trace = trace;
        this.linenr = linenr;
        this.from = from;
        this.value = value;
    }

    public int getLineNr() {
        return linenr;
    }

    public boolean getConditionValue() {
        return this.value;
    }

    @Override
    public int compareTo(NextTrace other) {
        return comparator.compare(this, other);
    }

    private int traceLength() {
        return trace.size();
    }

}
