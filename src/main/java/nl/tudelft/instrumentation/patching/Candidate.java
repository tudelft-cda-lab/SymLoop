package nl.tudelft.instrumentation.patching;

import java.util.Optional;

public class Candidate {
    public final String[] operators;
    public Optional<double[]> suspiciousness = Optional.empty();
    public Optional<Double> score = Optional.empty();


    public Candidate(String[] operators) {
        this.operators = operators;
    }

    public void score(double score, double[] suspiciousness) {
        this.score = Optional.of(score);
        this.suspiciousness = Optional.of(suspiciousness);
    }

    public boolean hasScore() {
        return this.score.isPresent() && this.suspiciousness.isPresent();
    }

    public void hasBeenModified() {
        score = Optional.empty();
    }

    public Candidate deepCopy() {
        Candidate copy = new Candidate(operators.clone());
        this.suspiciousness.ifPresent(doubles -> copy.suspiciousness = Optional.ofNullable(doubles.clone()));
        this.score.ifPresent(s -> copy.score = Optional.of(s));
        return copy;
    }
}
