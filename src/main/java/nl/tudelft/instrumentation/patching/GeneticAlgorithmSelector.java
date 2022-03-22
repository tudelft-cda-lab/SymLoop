package nl.tudelft.instrumentation.patching;

import java.util.List;
import java.util.Random;

public abstract class GeneticAlgorithmSelector {
    protected Random random;

    protected abstract List<Candidate> select(List<Candidate> candidates, int populationSize);

    public void setRandom(Random random) {
        this.random = random;
    }
}
