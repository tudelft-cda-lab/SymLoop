package nl.tudelft.instrumentation.patching;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Arrays;

public abstract class GeneticAlgorithm {

    private ArrayList<String[]> population;
    private final int populationSize;
    private final Random random = new Random(4);

    public GeneticAlgorithm(int populationSize) {
        this.populationSize = populationSize;
        this.population = getInitialPopulation(populationSize);
        if(this.population.size() != populationSize) {
            throw new AssertionError("Length should match");
        }
    }

    public abstract ArrayList<String[]> getInitialPopulation(int populationSize);

    public void generation() {
        double[] scores = new double[population.size()];
        double[][] suspiciousness = new double[population.size()][];
        double totalScore = 0.0f;
        double minFit = Double.MAX_VALUE;
        int i = 0;
        int nonZero = 0;
        double maxFit = 0;
        for (i = 0; i < population.size(); i++) {
            String[] candidate = population.get(i);
            double fit = getFitness(candidate);
            minFit = Math.min(minFit, fit);
            maxFit = Math.max(maxFit, fit);
            scores[i] = fit;
            if (fit > 0.0) {
                nonZero++;
            }
            suspiciousness[i] = getSuspiciousness();
            totalScore += fit;
        }
        double avgNonZero = (totalScore / nonZero)*0.999;
        totalScore=0;
        for (i = 0; i < scores.length; i++) {
            scores[i] = Math.max(0, scores[i] - avgNonZero);
            totalScore += scores[i];
        }
        // System.out.printf("Averga %f, Max: %f\n", avgNonZero, maxFit);

        ArrayList<String[]> newPopulation = new ArrayList<>();
        for(i = 0; i < this.populationSize/2; i++){
            int indexA = randomCandidate(scores, totalScore);
            int indexB = randomCandidate(scores, totalScore);
            String[] a = population.get(indexA).clone();
            String[] b = population.get(indexB).clone();
            double[] susA = suspiciousness[indexA].clone();
            double[] susB = suspiciousness[indexB].clone();
            // crossover(a, b, susA, susB);
            mutate(a, susA);
            mutate(b, susB);
            newPopulation.add(a);
            newPopulation.add(b);
            // System.out.println(String.join(" ", a));
            // System.out.println(String.join(" ", b));
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

    public int randomCandidate(double[] scores, double totalScore) {
        double rand = random.nextDouble() * totalScore;
        if(scores.length != population.size()) {
            throw new IllegalArgumentException("Sizes should be equal");
        }
        for(int i = 0; i < scores.length; i ++){
            rand -= scores[i];
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
        System.out.println(Arrays.toString(suspicious));
        for (double v : suspicious) {
            if (v <= 0.5)  {
                continue;
            }
            sum += v;
        }
        double mutationRate = 2.0; //multiply the sum by number of operators on average that need to change.
        for(int i = 0; i < suspicious.length; i++) {
            double v = suspicious[i];
            if (v <= 0.5)  {
                continue;
            }
            double value = v;
            if(random.nextDouble() / mutationRate < value / sum) {
                candidate[i] = randomOperator(i);
                System.out.printf("mutating at %d: susp is %f \n", i, v);
            }
        }
//        System.out.printf("Done %d mutations\n", mutations);
    }

    public String randomChoice(String[] a){
        return a[random.nextInt(a.length)];
    }

    public String randomOperator(int operator_nr) {
        String[] booleanOperators = {"!=", "=="};
        String[] intOperators = {"!=", "==", "<", ">", "<=", ">="};
        if(PatchingLab.operatorIsInt[operator_nr]) {
            return randomChoice(intOperators);
        } else {
            return randomChoice(booleanOperators);
        }
    }

    public abstract double[] getSuspiciousness();

    public abstract double getFitness(String [] candidate);

}
