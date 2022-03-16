package nl.tudelft.instrumentation.patching;

import java.util.*;

public class PatchingGA extends GeneticAlgorithm {


    public PatchingGA(int populationSize) {
        super(populationSize);
    }
    public ArrayList<String[]> getInitialPopulation(int populationSize) {
        ArrayList<String[]> population = new ArrayList<>();
        for(int i = 0; i < populationSize; i++) {
            population.add(OperatorTracker.operators.clone());
        }
        return population;
    }

    public void run(String[] candidate) {
        PatchingLab.runCandidate(candidate);
    }

    public double[] getSuspiciousness(){
        return PatchingLab.currentSuspicious;
    }

    public double getFitness() {
        return PatchingLab.currentFitness;
    }
}

