package nl.tudelft.instrumentation.symbolic;

public class LoopVerifyResult {

    enum State {
        NO_LOOP_FOUND,
        SELF,
        PROBABLY,
        COUNTER,
    }

    private final State s;
    private final String[] counter;

    public State getS() {
        return s;
    }

    public String[] getCounter() {
        return counter;
    }

    LoopVerifyResult(State s, String[] counter) {
        this.s = s;
        this.counter = counter;
        if (s == State.COUNTER) {
            assert counter != null;
        }
    }

    static LoopVerifyResult self() {
        return new LoopVerifyResult(State.SELF, null);
    }

    static LoopVerifyResult notFound() {
        return new LoopVerifyResult(State.NO_LOOP_FOUND, null);
    }

    static LoopVerifyResult probably() {
        return new LoopVerifyResult(State.PROBABLY, null);
    }

    static LoopVerifyResult counter(String[] counter) {
        return new LoopVerifyResult(State.PROBABLY, counter);
    }

    boolean hasCounter() {
        return s == State.COUNTER;
    }

}
