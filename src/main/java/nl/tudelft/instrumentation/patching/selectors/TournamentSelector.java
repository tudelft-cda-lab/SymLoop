package nl.tudelft.instrumentation.patching.selectors;

import nl.tudelft.instrumentation.patching.Candidate;
import nl.tudelft.instrumentation.patching.GeneticAlgorithmSelector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TournamentSelector extends GeneticAlgorithmSelector {

    private final int K;
    private final double p;

    public TournamentSelector(int k, double p) {
        this.K = k;
        this.p = p;
    }

    @Override
    protected List<Candidate> select(List<Candidate> candidates, int populationSize) {
        List<Candidate> pool = new ArrayList<>(K);
        for (int i = 0; i < K; i++) {
            pool.add(candidates.get(random.nextInt(candidates.size())));
        }
        pool.sort(Comparator.comparingDouble(Candidate::getScore).reversed());
        List<Candidate> selected = new ArrayList<>(candidates.size());
        for(int i = 0; i < populationSize; i++){
            selected.add(selectIndividual(pool).deepCopy());
        }
        return selected;
    }

    protected Candidate selectIndividual(List<Candidate> sorted) {
        int i = 0;
        while(p < random.nextDouble() && i+1 < sorted.size()) {
            i += 1;
        }
        return sorted.get(i);
    }
}
