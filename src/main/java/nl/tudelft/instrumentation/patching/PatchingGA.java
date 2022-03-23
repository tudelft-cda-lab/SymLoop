package nl.tudelft.instrumentation.patching;

import nl.tudelft.instrumentation.patching.selectors.RouletteSelector;
import nl.tudelft.instrumentation.patching.selectors.TournamentSelector;

import java.util.*;

public class PatchingGA extends GeneticAlgorithm {


    public PatchingGA(int populationSize) {
//        super(populationSize, new RouletteSelector());
        super(populationSize, new TournamentSelector(10, 0.7));
    }

    public List<Candidate> getInitialPopulation(int populationSize) {
        ArrayList<Candidate> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            String[] operators = OperatorTracker.operators.clone();
//            String[] operators = randomOperators();
            population.add(new Candidate(operators));
        }
        return population;
    }

    public double[] getSuspiciousness() {
        return PatchingLab.currentSuspicious;
    }

    public double getFitness(String[] candidate) {
        PatchingLab.runCandidate(candidate);
        return PatchingLab.currentFitness;
    }
}

