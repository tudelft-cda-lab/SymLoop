package nl.tudelft.instrumentation.patching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

public abstract class GeneticAlgorithm {

    public static class Candidate {
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

    private ArrayList<Candidate> population;
    private final int populationSize;
    private final Random random = new Random(4);

    public GeneticAlgorithm(int populationSize) {
        this.populationSize = populationSize;
        this.population = getInitialPopulation(populationSize);
        if (this.population.size() != populationSize) {
            throw new AssertionError("Length should match");
        }
    }

    public abstract ArrayList<Candidate> getInitialPopulation(int populationSize);

    public void generation() {

//        double[][] suspiciousness = new double[population.size()][];
        double[] scores = new double[population.size()];
        double totalScore = 0.0f;
        double minFit = Double.MAX_VALUE;
        int nonZero = 0;
        double maxFit = 0;
        for (int i = 0; i < population.size(); i++) {
            Candidate candidate = population.get(i);
            if (!candidate.hasScore()) {
                double fit = getFitness(candidate.operators);
                candidate.score(fit, getSuspiciousness());
            }
            double fit = candidate.score.orElseThrow(() -> new AssertionError("Not Possible"));
            minFit = Math.min(minFit, fit);
            maxFit = Math.max(maxFit, fit);
            scores[i] = fit;
            if (fit > 0.0) {
                nonZero++;
            }
            totalScore += fit;
        }
        double avgNonZero = (totalScore / nonZero) * 0.999;
        totalScore = 0;
        for (int i = 0; i < scores.length; i++) {
            scores[i] = Math.max(0, scores[i] - avgNonZero);
            totalScore += scores[i];
        }

        ArrayList<Candidate> newPopulation = new ArrayList<>();
        for (int i = 0; i < this.populationSize / 2; i++) {
            int indexA = randomCandidate(scores, totalScore);
            int indexB = randomCandidate(scores, totalScore);
            Candidate a = population.get(indexA).deepCopy();
            Candidate b = population.get(indexB).deepCopy();
            crossover(a, b);
            mutate(a);
            mutate(b);
            newPopulation.add(a);
            newPopulation.add(b);
             System.out.println(String.join(" ", a.operators));
             System.out.println(String.join(" ", b.operators));
        }
        this.population = newPopulation;
    }

    public void crossover(Candidate a, Candidate b) {
        if (a.operators.length != b.operators.length) {
            throw new IllegalArgumentException("Sizes should be equal");
        }
        boolean susp = a.suspiciousness.isPresent() && b.suspiciousness.isPresent();
        boolean differ = false;
        int cutOff = random.nextInt(a.operators.length);
        for (int i = cutOff; i < a.operators.length; i++) {
            differ = differ || !a.operators[i].equals(b.operators[i]);
            String tmp = a.operators[i];
            a.operators[i] = b.operators[i];
            b.operators[i] = tmp;
            if (susp) {
                double tempsus = a.suspiciousness.get()[i];
                a.suspiciousness.get()[i] = b.suspiciousness.get()[i];
                b.suspiciousness.get()[i] = tempsus;
            }
        }
        if (differ) {
            a.hasBeenModified();
            b.hasBeenModified();
        }
    }

    public int randomCandidate(double[] scores, double totalScore) {
        double rand = random.nextDouble() * totalScore;
        if (scores.length != population.size()) {
            throw new IllegalArgumentException("Sizes should be equal");
        }
        for (int i = 0; i < scores.length; i++) {
            rand -= scores[i];
            if (rand <= 0) {
                return i;
            }
        }
        return random.nextInt(scores.length);
//        throw new AssertionError("should not be possible");
    }


    public void mutate(Candidate c) {
        double[] suspicious = c.suspiciousness.orElseThrow(() -> new AssertionError("Mutate can only be done with suspiciousness values"));
        double sum = 0;
        if (c.operators.length != c.suspiciousness.get().length) {
            throw new IllegalArgumentException("Sizes should be equal");
        }
//        System.out.println(Arrays.toString(c.suspiciousness.get()));

        for (double v : suspicious) {
            sum += v;
        }
        double mutationRate = 0.5; //multiply the sum by number of operators on average that need to change.
        boolean mutated = false;
        for (int i = 0; i < suspicious.length; i++) {
            double v = suspicious[i];
            if (random.nextDouble() / mutationRate < v / sum) {
                c.operators[i] = randomOperator(i);
                System.out.printf("mutating at %d: susp is %f \n", i, v);
                mutated = true;
            }
        }
        if (mutated) {
            c.hasBeenModified();
        }
//        System.out.printf("Done %d mutations\n", mutations);
    }

    public String randomChoice(String[] a) {
        return a[random.nextInt(a.length)];
    }

    public String randomOperator(int operator_nr) {
        String[] booleanOperators = {"!=", "=="};
        String[] intOperators = {"!=", "==", "<", ">", "<=", ">="};
        if (PatchingLab.operatorIsInt[operator_nr]) {
            return randomChoice(intOperators);
        } else {
            return randomChoice(booleanOperators);
        }
    }

    public abstract double[] getSuspiciousness();

    public abstract double getFitness(String[] candidate);

}
