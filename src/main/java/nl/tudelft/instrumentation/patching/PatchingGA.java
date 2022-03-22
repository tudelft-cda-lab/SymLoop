package nl.tudelft.instrumentation.patching;

import nl.tudelft.instrumentation.patching.selectors.RouletteSelector;

import java.util.*;

public class PatchingGA extends GeneticAlgorithm {


    public PatchingGA(int populationSize) {
        super(populationSize, new RouletteSelector());
    }
    public List<Candidate> getInitialPopulation(int populationSize) {
        ArrayList<Candidate> population = new ArrayList<>();
        for(int i = 0; i < populationSize; i++) {
            // String[] operators = new String[OperatorTracker.operators.length];
            // for(int j = 0; j < operators.length; j++) {
            //     operators[j] = randomOperator(j);
            // }
            String[] operators = OperatorTracker.operators.clone();
            population.add(new Candidate(operators));
        }
        return population;
    }

    public double[] getSuspiciousness(){
        return PatchingLab.currentSuspicious;
    }

    public double getFitness(String[] candidate) {
        PatchingLab.runCandidate(candidate);
        return PatchingLab.currentFitness;
    }
}

