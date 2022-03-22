package nl.tudelft.instrumentation.patching;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public abstract class GeneticAlgorithm {

    private List<Candidate> population;
    private final int populationSize;
    protected final Random random = new Random(4);
    private final GeneticAlgorithmSelector selector;

    public GeneticAlgorithm(int populationSize, GeneticAlgorithmSelector selector) {
        this.populationSize = populationSize;
        this.population = getInitialPopulation(populationSize);
        this.selector = selector;
        this.selector.setRandom(random);
        if (this.population.size() != populationSize) {
            throw new AssertionError("Length should match");
        }
    }

    public abstract List<Candidate> getInitialPopulation(int populationSize);

    public void generation() {
        population.stream().filter((x) -> !x.hasScore()).forEach(candidate -> {
            double fit = getFitness(candidate.operators);
            candidate.score(fit, getSuspiciousness());
        });
        List<Candidate> newPopulation = this.selector.select(this.population);
        for (int i = 0; i < this.populationSize; i += 2) {
            Candidate a = population.get(i);
            Candidate b = population.get(i + 1);
            crossover(a, b);
            mutate(a);
            mutate(b);
            newPopulation.add(a);
            newPopulation.add(b);
//             System.out.println(String.join(" ", a.operators));
//             System.out.println(String.join(" ", b.operators));
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


    public void mutate(Candidate c) {
        double[] suspicious = c.suspiciousness.orElseThrow(() -> new AssertionError("Mutate can only be done with suspiciousness values"));
        double sum = 0;
        if (c.operators.length != c.suspiciousness.get().length) {
            throw new IllegalArgumentException("Sizes should be equal");
        }

        for (double v : suspicious) {
            sum += v;
        }
        double mutationRate = 1.0; //multiply the sum by number of operators on average that need to change.
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
