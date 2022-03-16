package nl.tudelft.instrumentation.patching;

import java.util.*;

public class PatchingGA extends GeneticAlgorithm {


    public PatchingGA(int populationSize) {
        super(populationSize);
    }
    public ArrayList<String[]> getInitialPopulation(int populationSize) {
        ArrayList<String[]> population = new ArrayList<>();
        return population;
    }

    public void run(String[] candidate) {
    }

    public double[] getSuspiciousness(){
        return null;
    }

    public double getFitness() {
        return 0.0;
    }
}

