package nl.tudelft.instrumentation.patching;

import java.util.*;

public abstract class GeneticAlgorithm {

    private ArrayList<String[]> population;
    private int populationSize;
    private Random random = new Random(1);

    public GeneticAlgorithm(int populationSize) {
        this.populationSize = populationSize;
        this.population = getInitialPopulation(populationSize);
        if(this.population.size() != populationSize) {
            throw new AssertionError("Length should match");
        }
    }

    public abstract ArrayList<String[]> getInitialPopulation(int populationSize);

    public void generation() {
        ArrayList<Double> scores = new ArrayList<>();
        ArrayList<double[]> suspiciousness = new ArrayList<>();
        double totalScore = 0.0f;
        for (String[] candidate : population) {
            run(candidate);
            double fit = getFitness();
            scores.add(fit);
            suspiciousness.add(getSuspiciousness());
            totalScore += fit;
        }

        ArrayList<String[]> newPopulation = new ArrayList<>();
        for(int i = 0; i < this.populationSize/2; i++){
            int indexA = randomCandidate(scores, totalScore);
            int indexB = randomCandidate(scores, totalScore);
            String[] a = population.get(indexA).clone();
            String[] b = population.get(indexB).clone();
            double[] susA = suspiciousness.get(indexA).clone();
            double[] susB = suspiciousness.get(indexB).clone();
            crossover(a, b, susA, susB);
            mutate(a, susA);
            mutate(b, susB);
            newPopulation.add(a);
            newPopulation.add(b);
        }
        this.population = newPopulation;
    }

    public void crossover(String[] a, String[] b, double[] susA, double[] susB) {
        if(a.length != b.length) {
            throw new IllegalArgumentException("Sizes should be equal");
        }
        int cutOff = random.nextInt(a.length);
        for(int i = cutOff; i < a.length; i++){
            String tmp = a[i];
            a[i] = b[i];
            b[i] = tmp;
            double tempsus = susA[i];
            susA[i] = susB[i];
            susB[i] = tempsus;
        }
    }

    public int randomCandidate(ArrayList<Double> scores, double totalScore) {
        double rand = random.nextDouble() * totalScore;
        if(scores.size() != population.size()) {
            throw new IllegalArgumentException("Sizes should be equal");
        }
        for(int i = 0; i < scores.size(); i ++){
            rand -= scores.get(i);
            if(rand <= 0){
                return i;
            }
        }
        throw new AssertionError("should not be possible");
    }


    public void mutate(String[] candidate, double[] suspicious) {
        double sum = 0;
        if(candidate.length != suspicious.length) {
            throw new IllegalArgumentException("Sizes should be equal");
        }
        for(int i = 0; i < suspicious.length; i++) {
            sum += suspicious[i];
        }
        double mutationRate = sum * 1.0; //multiply the sum by number of operators on average that need to change.

        for(int i = 0; i < suspicious.length; i++) {
            if(random.nextDouble() * mutationRate < suspicious[i]) {
                candidate[i] = randomOperator(i);
            }
        }
    }

    public String randomChoice(String[] a){
        return a[random.nextInt(a.length)];
    }

    public String randomOperator(int operator_nr) {
        String[] booleanOperators = {"!=", "=="};
        String[] intOperators = {"!=", "==", "<", ">", "<=", ">="};
        if(PatchingLab.operatorIsBoolean[operator_nr]) {
            return randomChoice(booleanOperators);
        } else {
            return randomChoice(intOperators);
        }
    }
    
    public abstract void run(String[] candidate);

    public abstract double[] getSuspiciousness();

    public abstract double getFitness();

}
