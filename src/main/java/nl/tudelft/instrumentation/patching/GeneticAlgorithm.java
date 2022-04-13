package nl.tudelft.instrumentation.patching;


import java.util.Comparator;
import java.util.List;
import java.util.Random;

public abstract class GeneticAlgorithm {

    private List<Candidate> population;
    private final int populationSize;
    protected final Random random = new Random();
    private final GeneticAlgorithmSelector selector;
    private int generation = 0;
    private final long starttime;

    public GeneticAlgorithm(int populationSize, GeneticAlgorithmSelector selector) {
        this.populationSize = populationSize;
        this.population = getInitialPopulation(populationSize);
        this.selector = selector;
        this.selector.setRandom(random);
        starttime = System.currentTimeMillis();
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
        population.sort(Comparator.comparingDouble(Candidate::getScore).reversed());
        System.out.println(population);
        List<Candidate> newPopulation = this.selector.select(this.population, this.populationSize);
        for (int i = 0; i < this.populationSize; i += 2) {
            Candidate a = newPopulation.get(i);
            Candidate b = newPopulation.get(i + 1);
            if (random.nextDouble() < 0.3) {
                onePointCrossover(a, b);
                //twoPointCrossover(a,b);
            }
            mutate(a);
            mutate(b);
        }
        newPopulation.add(new Candidate(randomOperators()));
        newPopulation.add(population.get(0).deepCopy());
        Candidate c = population.get(0).deepCopy();
        mutatePercantage(0.05, c);
        newPopulation.add(c);

        generation += 1;
        int score = (int) Math.floor(population.get(0).getScore());
        System.out.printf("Gen, %d, maxscore, %d, time, %d, percentage, %.3f\n", generation, score, System.currentTimeMillis() - starttime, (double) score / (double) OperatorTracker.tests.size());
        this.population = newPopulation;


    }

    public void onePointCrossover(Candidate a, Candidate b) {
        if (a.operators.length != b.operators.length) {
            throw new IllegalArgumentException("Sizes should be equal");
        }
        boolean susp = a.suspiciousness.isPresent() && b.suspiciousness.isPresent();
        int cutOff = random.nextInt(a.operators.length);
        for (int i = cutOff; i < a.operators.length; i++) {
            String tmp = a.operators[i];
            a.operators[i] = b.operators[i];
            b.operators[i] = tmp;
            if (susp) {
                double tempsus = a.suspiciousness.get()[i];
                a.suspiciousness.get()[i] = b.suspiciousness.get()[i];
                b.suspiciousness.get()[i] = tempsus;
            }
        }
        a.hasBeenModified();
        b.hasBeenModified();
    }

    public void twoPointCrossover(Candidate a, Candidate b) {
        if (a.operators.length != b.operators.length) {
            throw new IllegalArgumentException("Sizes should be equal");
        }
        boolean susp = a.suspiciousness.isPresent() && b.suspiciousness.isPresent();
        int cutOff1 = random.nextInt(a.operators.length);
        int cutOff2 = random.nextInt(a.operators.length - cutOff1);
        for (int i = cutOff1; i < cutOff1+cutOff2; i++) {
            String tmp = a.operators[i];
            a.operators[i] = b.operators[i];
            b.operators[i] = tmp;
            if (susp) {
                double tempsus = a.suspiciousness.get()[i];
                a.suspiciousness.get()[i] = b.suspiciousness.get()[i];
                b.suspiciousness.get()[i] = tempsus;
            }
        }
        a.hasBeenModified();
        b.hasBeenModified();
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
        double mutationRate = 4.0 / suspicious.length; //multiply the sum by number of operators on average that need to change.
        int mutated = 0;
        for (int i = 0; i < suspicious.length; i++) {
            double v = suspicious[i];
            if (v >= 0.5 && random.nextDouble() < mutationRate * v) {
                c.operators[i] = randomOperatorNotSame(i, c.operators[i]);
//                System.out.printf("mutating at %d: susp is %f \n", i, v);
                mutated += 1;
            }
        }
//        System.out.printf("mutated: %d\n", mutated);
//        if (mutated) {
        c.hasBeenModified();
//        }
    }

    public void mutatePercantage(double percentage, Candidate c) {
        for (int i = 0; i < c.operators.length * percentage; i++) {
            int r = random.nextInt(c.operators.length);
            c.operators[r] = randomOperator(r);
        }
        c.hasBeenModified();
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

    public String randomOperatorNotSame(int operator_nr, String old) {
        String[] booleanOperators = {"!=", "=="};
        String[] intOperators = {"!=", "==", "<", ">", "<=", ">="};
        if (PatchingLab.operatorIsInt[operator_nr]) {
            int i = random.nextInt(intOperators.length - 1);
            return intOperators[i+1];
        } else {
            if (old.equals("!=")) {
                return "==";
            } else if (old.equals("==")) {
                return "!=";
            } else {
                return randomChoice(booleanOperators);
            }
        }
    }


    public abstract double[] getSuspiciousness();

    public abstract double getFitness(String[] candidate);

    public String[] randomOperators() {
        String[] operators = new String[OperatorTracker.operators.length];
        for (int j = 0; j < operators.length; j++) {
            operators[j] = randomOperator(j);
        }
        return operators;
    }

}
