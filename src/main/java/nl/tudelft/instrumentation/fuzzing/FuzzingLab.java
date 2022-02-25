package nl.tudelft.instrumentation.fuzzing;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

/**
 * You should write your own solution using this class.
 */
public class FuzzingLab {
    private enum FuzzMode {
        RANDOM,
        COVERAGE_SET,
        HILL_CLIMBER,
    }

    static Random r = new Random();
    static List<String> currentTrace;
    static final int MIN_TRACE_LENGTH = 10;
    static final int MAX_TRACE_LENGTH = 50;
    static boolean isFinished = false;

    static BranchVisitedTracker branchTracker = new BranchVisitedTracker();
    static BranchVisitedTracker currentBranchTracker = new BranchVisitedTracker();
    // static Map<Integer, VisitedEnum> currentBranches = new HashMap<Integer,
    // VisitedEnum>();
    static Map<Integer, Pair<Double, List<String>>> minimumBranchDistances = new HashMap<>();

    static Map<Integer, Double> minimumBranchTrue = new HashMap<>();
    static Map<Integer, Double> minimumBranchFalse = new HashMap<>();

    static List<String> bestTrace;
    static double bestTraceScore = 0;

    static List<Pair<Double, List<String>>> topTraces = new ArrayList<>(5);
    static List<Pair<Double, List<String>>> topCombo = new ArrayList<>();
    static int NUM_TOP_TRACES = 1;
    static int iterations = 0;
    static final FuzzMode mode = FuzzMode.RANDOM;
    // static final FuzzMode mode = FuzzMode.RANDOM;
    // static final FuzzMode mode = FuzzMode.COVERAGE_SET;
    static long stableSince = System.currentTimeMillis();
    static final int STOP_WHEN_STABLE_FOR = 1000 * 10 * 10000;
    static final int STOP_AFTER = 1000 * 60 * 10;

    static Pair<Double, List<String>> latestTraceHC;

    static void initialize(String[] inputSymbols) {
        // Initialise a random trace from the input symbols of the problem.
        currentTrace = generateRandomTrace(inputSymbols);
    }

    static Set<Integer> outputErrors = new HashSet<>();
    static Pattern pattern = Pattern.compile("Invalid input: error_(\\d+)");
    private static int tracesPerIteration = 20;
    private static int lastVisited = 0;
    private static int traceLength = 10;

    private static int discoveredBranches = 0;
    private static boolean addToBranches = false;

    public static double branchDistance(MyVar condition, boolean value) {
        return BranchDistance.compute(condition, value);
    }

    /**
     * Write your solution that specifies what should happen when a new branch has
     * been found.
     */
    static void encounteredNewBranch(MyVar condition, boolean value, int line_nr) {
        Double bd = BranchDistance.compute(condition, value);
        if (!branchTracker.hasVisited(line_nr) && mode == FuzzMode.RANDOM) {
            incrementTop5();
        }
        if (!branchTracker.hasVisited(line_nr, value)) {
            if (!currentBranchTracker.hasVisited(line_nr, value)) {
                discoveredBranches += 1;
            }
        }
        if (addToBranches) {
            branchTracker.visit(line_nr, value);
        }
        currentBranchTracker.visit(line_nr, value);
        if (minimumBranchDistances.containsKey(line_nr)) {
            if (minimumBranchDistances.get(line_nr).getLeft() > bd) {
                minimumBranchDistances.put(line_nr, Pair.of(bd, currentTrace));
            }
        } else {
            minimumBranchDistances.put(line_nr, Pair.of(bd, currentTrace));
        }

        if (value) {
            double newMininum = Math.min(minimumBranchFalse.getOrDefault(line_nr, 1.0), bd);
            minimumBranchFalse.put(line_nr, newMininum);
            minimumBranchTrue.put(line_nr, 0.0);
        } else {
            double newMininum = Math.min(minimumBranchTrue.getOrDefault(line_nr, 1.0), bd);
            minimumBranchTrue.put(line_nr, newMininum);
            minimumBranchFalse.put(line_nr, 0.0);
        }
    }

    /**
     * Method for fuzzing new inputs for a program.
     * 
     * @param inputSymbols the inputSymbols to fuzz from.
     * @return a fuzzed sequence
     */
    static List<String> fuzz(String[] inputSymbols) {
        /*
         * Add here your code for fuzzing a new sequence for the RERS problem.
         * You can guide your fuzzer to fuzz "smart" input sequences to cover
         * more branches. Right now we just generate a complete random sequence
         * using the given input symbols. Please change it to your own code.
         */

        if (mode == FuzzMode.RANDOM) {
            return generateRandomTrace(inputSymbols);
        } else if (mode == FuzzMode.HILL_CLIMBER) {
            // If it is the start of the Hill Climber process
            if (latestTraceHC == null) {
                List<String> randomTrace = generateRandomTrace(inputSymbols);
                double score = computeScore(randomTrace);
                latestTraceHC = Pair.of(score, randomTrace);
            } else {
                // Recompute the score
                latestTraceHC = Pair.of(computeScore(latestTraceHC.getRight()), latestTraceHC.getRight());
            }

            List<Pair<Double, List<String>>> mutations = new ArrayList<>();

            // Generate mutated traces
            for (int i = 0; i < tracesPerIteration; i++) {
                List<String> newMutation = hillClimberMutate(latestTraceHC.getRight(), inputSymbols);
                double score = computeScore(newMutation);
                mutations.add(Pair.of(score, newMutation));
            }

            // Pick best improving trace
            int lowestIndex = 0;
            double lowestScore = mutations.get(0).getLeft();
            for (int i = 1; i < mutations.size(); i++) {
                double currentScore = mutations.get(i).getLeft();
                if (currentScore < lowestScore) {
                    lowestScore = currentScore;
                    lowestIndex = i;
                }
            }
            // If the best of the traces is not better than the last one
            // And with a 10% chance
            if (lowestScore > latestTraceHC.getLeft() && r.nextDouble() < 0.1) {
                // Generate a random trace
                List<String> trace = generateRandomTrace(inputSymbols);
                latestTraceHC = Pair.of(computeScore(trace), trace);
            } else {
                latestTraceHC = mutations.get(lowestIndex);
            }
            return latestTraceHC.getRight();
        } else if (mode == FuzzMode.COVERAGE_SET) {
            return generateRandomTrace(inputSymbols);
        } else {
            throw new Error("Unimplemented mode: " + mode);
        }
    }

    static List<String> mutate(List<String> base, String[] symbols) {
        int amount = r.nextInt(base.size());
        base = new ArrayList<>(base);
        for (int i = 0; i < amount; i++) {
            base.set(r.nextInt(base.size()), randomSymbolFrom(symbols));
        }
        return base;
    }

    static String randomSymbolFrom(String[] symbols) {
        return symbols[r.nextInt(symbols.length)];
    }

    static List<String> hillClimberMutate(List<String> current, String[] symbols) {
        // Configure these variables to your liking
        double mutateChance = 0.1;
        double removeChance = 0.01;
        double addChance = 0.005;

        List<String> result = new ArrayList<>();

        for (int i = 0; i < current.size(); i++) {
            Double random = r.nextDouble();
            random -= mutateChance;
            if (random <= 0) {
                // add mutation
                result.add(randomSymbolFrom(symbols));
                continue;
            }
            random -= addChance;
            if (random <= 0) {
                // add random one and current one
                result.add(current.get(i));
                result.add(randomSymbolFrom(symbols));
                continue;
            }
            random -= removeChance;
            if (random <= 0) {
                // else remove aka do nothing with current
            } else {
                // keep current input as it is, so add it
                result.add(current.get(i));
            }
        }

        // add new symbol at the end.
        if (r.nextDouble() < addChance) {
            result.add(randomSymbolFrom(symbols));
        }

        return result;
    }

    /**
     * Generate a random trace from an array of symbols.
     * 
     * @param symbols the symbols from which a trace should be generated from.
     * @return a random trace that is generated from the given symbols.
     */
    static List<String> generateRandomTrace(String[] symbols) {
        ArrayList<String> trace = new ArrayList<>();
        int length = traceLength;
        if (mode == FuzzMode.RANDOM || mode == FuzzMode.COVERAGE_SET) {
            length = r.nextInt(MAX_TRACE_LENGTH - MIN_TRACE_LENGTH) + MIN_TRACE_LENGTH;
        } else if (currentTrace != null) {
            length = Math.max(currentTrace.size(), traceLength);
            length = r.nextInt(length) + length / 2;
        }
        for (int i = 0; i < length; i++) {
            trace.add(randomSymbolFrom(symbols));
        }
        return trace;
    }

    static void printTop5(List<Pair<Double, List<String>>> traces) {
        System.out.println("Current top 5:");
        for (int i = 0; i < traces.size(); i++) {
            Pair<Double, List<String>> pair = traces.get(i);
            System.out.printf("#%02d with score %6.2f %s\n", i + 1,
                    pair.getLeft(),
                    String.join("", pair.getRight()));
        }
    }

    static void updateTop5(List<Pair<Double, List<String>>> oldTop5,
            Pair<Double, List<String>> current) {

        if (current.getKey() <= 0) {
            return;
        }
        oldTop5.add(current);
        // System.out.println("Current: " + current);

        // While the current item is lower then the item at index i
        for (int i = oldTop5.size() - 2; i >= 0 && oldTop5.get(i).getLeft() < current.getLeft(); i--) {
            oldTop5.set(i + 1, oldTop5.get(i));
            oldTop5.set(i, current);
        }
        // Keep the list at a maximum size
        if (oldTop5.size() > NUM_TOP_TRACES) {
            oldTop5.remove(NUM_TOP_TRACES);
        }
    }

    static void incrementTop5() {
        for (int i = 0; i < topTraces.size(); i++) {
            Pair<Double, List<String>> pair = topTraces.get(i);
            topTraces.set(i, Pair.of(pair.getLeft() + 2, pair.getRight()));
        }
    }

    static void printAnswersQuestion1() {
        // Question A
        // System.out.printf("Unique branches: Visited %d out of %d. Errors found: d\n",
        //         branchTracker.numVisited(), branchTracker.totalBranches(), outputErrors.size());

        // Question B
        // System.out.printf("Best: %.2f, with trace: %s\n", bestTraceScore,
        // bestTrace.toString());

        // Question C
        printTop5(topTraces);
    }

    static void reset() {
        minimumBranchTrue.clear();
        minimumBranchFalse.clear();
        currentBranchTracker.clear();
        discoveredBranches = 0;
    }

    static boolean hasElapsed(long since, long amount) {
        return since + amount <= System.currentTimeMillis();
    }

    static void run() {
        initialize(DistanceTracker.inputSymbols);
        DistanceTracker.runNextFuzzedSequence(currentTrace.toArray(new String[0]));

        long start = System.currentTimeMillis();

        // Place here your code to guide your fuzzer with its search.
        while (!isFinished && !hasElapsed(stableSince, STOP_WHEN_STABLE_FOR) && !hasElapsed(start, STOP_AFTER)) {
            iterations++;
            // Do things!
            try {
                currentTrace = fuzz(DistanceTracker.inputSymbols);
                addToBranches = mode != FuzzMode.COVERAGE_SET;
                double score = computeScore(currentTrace);
                addToBranches = true;

                int visited = branchTracker.numVisited();
                int total = branchTracker.totalBranches();

                long now = System.currentTimeMillis();
                if (visited > lastVisited) {
                    stableSince = now;
                }
                lastVisited = visited;
                System.out.printf(
                        "Iter %d: Visited %d / %d. Errors: %d. Score: %.2f, Traces per iter: %d. tracelength: %d. Running for %.2f\n",
                        iterations, visited, total,
                        outputErrors.size(), score, tracesPerIteration,
                        currentTrace.size(), (now - start) / 1000.0);

                if (score > bestTraceScore) {
                    bestTraceScore = score;
                    bestTrace = new ArrayList<>(currentTrace);
                    System.out.printf("New best: %f, with trace: %s\n", score,
                            currentTrace.toString());
                    updateTop5(topTraces, Pair.of(score, currentTrace));
                }

                if (iterations % 20000 == 0 && mode == FuzzMode.COVERAGE_SET) {
                    if (topTraces.size() == 0) {
                        System.out.println("=========================");
                        printTop5(topCombo);
                        System.out.println("=========================");
                        System.exit(-1);
                    }
                    NUM_TOP_TRACES = 100;
                    score = computeScore(topTraces.get(0).getRight());
                    updateTop5(topCombo, Pair.of(score, topTraces.get(0).getRight()));
                    System.out.println("=========================");
                    printTop5(topCombo);
                    System.out.println("=========================");
                    topTraces.clear();
                    NUM_TOP_TRACES = 1;
                    Thread.sleep(1000);
                    // compress();
                }
                stableSince = now;

                if (iterations % 1000 == 0) {
                    Thread.sleep(0);
                }
                isFinished = total == visited && total > 0;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        ArrayList<Integer> out = new ArrayList<>(outputErrors);
        out.sort((a, b) -> a.compareTo(b));
        System.out.printf("stable after %d seconds (over a period of %ds): Erros: %s\n", (stableSince - start) / 1000,
                (System.currentTimeMillis() - stableSince) / 1000, out.toString());
        System.exit(0);
    }

    static double branchSumUnvisited() {
        double sum = 0;
        for (int line_nr : branchTracker.lines()) {
            if (!branchTracker.hasVisited(line_nr, true)) {
                sum += minimumBranchTrue.getOrDefault(line_nr, 1.0);
            }
            if (!branchTracker.hasVisited(line_nr, false)) {
                sum += minimumBranchFalse.getOrDefault(line_nr, 1.0);
            }
        }
        return sum;
    }

    static double branchesSumAll() {
        double sum = 0;
        for (int line_nr : branchTracker.lines()) {
            sum += minimumBranchFalse.getOrDefault(line_nr, 1.0);
            sum += minimumBranchTrue.getOrDefault(line_nr, 1.0);
        }
        return sum;
    }

    static double computeScore(List<String> trace) {
        reset();
        DistanceTracker.runNextFuzzedSequence(trace.toArray(new String[0]));
        if (mode == FuzzMode.RANDOM) {
            return branchesSumAll();
        } else if (mode == FuzzMode.HILL_CLIMBER) {
            return branchSumUnvisited();
        } else if (mode == FuzzMode.COVERAGE_SET) {
            return discoveredBranches;
        }
        throw new IllegalArgumentException("No scoring for current mode");
    }

    static void compress() {
        List<Pair<Integer, List<String>>> sorted = topTraces.stream().map((a) -> {
            reset();
            DistanceTracker.runNextFuzzedSequence(a.getRight().toArray(new String[0]));
            return Pair.of(currentBranchTracker.numVisited(), a.getRight());
        }).sorted((a, b) -> {
            return b.getKey().compareTo(a.getKey());
        }).collect(Collectors.toList());
        branchTracker.clear();
        topTraces.clear();
        for (Pair<Integer, List<String>> trace : sorted) {
            double score = computeScore(trace.getRight());
            if (score > 2) {
                updateTop5(topTraces, Pair.of(score, trace.getRight()));
            }
        }
        NUM_TOP_TRACES++;
    }

    /**
     * Method that is used for catching the output from standard out.
     * You should write your own logic here.
     * 
     * @param out the string that has been outputted in the standard out.
     */
    public static void output(String out) {
        Matcher matcher = pattern.matcher(out);
        if (matcher.find()) {
            outputErrors.add(Integer.parseInt(matcher.group(1)));
        } else {
            // System.out.println(out);
        }
    }
}
