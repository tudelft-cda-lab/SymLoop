package nl.tudelft.instrumentation.fuzzing;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

/**
 * You should write your own solution using this class.
 */
public class FuzzingLab {
    private enum FuzzMode {
        RANDOM,
        TOP_TRACE,
        EXPLORE_BRANCHES,
        LOWEST_DISTANCE,
        HILL_CLIMBER,
    }

    public enum VisitedEnum {
        NONE,
        TRUE,
        FALSE,
        BOTH;

        public static VisitedEnum from(boolean value) {
            if (value) {
                return TRUE;
            } else {
                return FALSE;
            }
        }

        VisitedEnum andVisit(boolean value) {
            if (this == NONE) {
                return VisitedEnum.from(value);
            } else if (this == TRUE && value) {
                return TRUE;
            } else if (this == TRUE && !value) {
                return BOTH;
            } else if (this == FALSE && !value) {
                return FALSE;
            } else if (this == FALSE && value) {
                return BOTH;
            } else if (this == BOTH) {
                return BOTH;
            }
            throw new AssertionError("unreachable");
        }

        boolean hasVisited(boolean value) {
            if (this == NONE) {
                return false;
            } else if (this == TRUE) {
                return value;
            } else if (this == FALSE) {
                return !value;
            } else if (this == BOTH) {
                return true;
            }
            throw new AssertionError("unreachable");
        }

        private VisitedEnum() {
        }

        public int amount() {
            switch (this) {
                case NONE:
                    return 0;
                case TRUE:
                    return 1;
                case FALSE:
                    return 1;
                case BOTH:
                    return 2;
                default:
                    throw new AssertionError("unreachable");
            }
        }
    }

    static Random r = new Random();
    static List<String> currentTrace;
    static final int MIN_TRACE_LENGTH = 0;
    static final int MAX_TRACE_LENGTH = 100;
    static boolean isFinished = false;

    static Map<Integer, VisitedEnum> branches = new HashMap<Integer, VisitedEnum>();
    static Map<Integer, Pair<Double, List<String>>> minimumBranchDistances = new HashMap<>();

    static Map<Integer, Double> minimumBranchTrue = new HashMap<>();
    static Map<Integer, Double> minimumBranchFalse = new HashMap<>();

    static List<String> bestTrace;
    static double bestTraceScore = 0;

    static List<Pair<Double, List<String>>> topTraces = new ArrayList<>(5);
    static final int NUM_TOP_TRACES = 5;
    static int iterations = 0;
    static final FuzzMode mode = FuzzMode.HILL_CLIMBER;
    // static final FuzzMode mode = FuzzMode.RANDOM;
    static long stableSince = System.currentTimeMillis();
    static final int STOP_WHEN_STABLE_FOR = 1000 * 30;

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

    static int stringDifference(String a, String b) {
        int index = 0;
        int difference = 0;
        while (index < a.length() && index < b.length()) {
            difference += Math.abs(a.charAt(index) - b.charAt(index));
            index++;
        }
        while (index < a.length()) {
            difference += a.charAt(index);
        }
        while (index < b.length()) {
            difference += b.charAt(index);
        }
        return difference;
    }

    static int getVarIntegerValue(MyVar x) {
        if (x.type == TypeEnum.INT) {
            return x.int_value;
        } else if (x.type == TypeEnum.UNARY && x.operator == "-") {
            return -getVarIntegerValue(x.left);
        } else if (x.type == TypeEnum.BINARY && x.operator == "+") {
            return getVarIntegerValue(x.left) + getVarIntegerValue(x.right);
        } else if (x.type == TypeEnum.BINARY && x.operator == "-") {
            return getVarIntegerValue(x.left) - getVarIntegerValue(x.right);
        }
        throw new AssertionError(String.format("Var not reducable to integer: %s", x.toString()));
    }

    static Double varAbs(MyVar a, MyVar b) {
        if (a.type == TypeEnum.STRING
                && b.type == TypeEnum.STRING) {
            return normalise(stringDifference(a.str_value, b.str_value));
        } else if (a.type == TypeEnum.BOOL
                && b.type == TypeEnum.BOOL) {
            return normalise(a.value == b.value ? 0 : 1);
        } else {
            int av = getVarIntegerValue(a);
            int bv = getVarIntegerValue(b);
            return normalise(Math.abs(av - bv));
        }
    }

    static Double notEqualDistance(MyVar a, MyVar b) {
        if (a.type == TypeEnum.STRING
                && b.type == TypeEnum.STRING) {
            return normalise(a.str_value.equals(b.str_value) ? 1 : 0);
        } else if (a.type == TypeEnum.BOOL
                && b.type == TypeEnum.BOOL) {
            return normalise(a.value == b.value ? 1 : 0);
        } else {
            int av = getVarIntegerValue(a);
            int bv = getVarIntegerValue(b);
            return normalise(av == bv ? 1 : 0);
        }
    }

    static Double binaryOperatorDistance(MyVar condition, boolean value) {
        int a;
        int b;
        switch (condition.operator) {
            case "==":
                if (value) {
                    return notEqualDistance(condition.left, condition.right);
                } else {
                    return varAbs(condition.left, condition.right);
                }
            case "!=":
                if (value) {
                    return varAbs(condition.left, condition.right);
                } else {
                    return notEqualDistance(condition.left, condition.right);
                }
            case "<":
                a = getVarIntegerValue(condition.left);
                b = getVarIntegerValue(condition.right);
                if (value) {
                    return normalise(a < b ? (b - a) : 0);
                } else {
                    return normalise(a < b ? 0 : (a - b + 1));
                }
            case "<=":
                a = getVarIntegerValue(condition.left);
                b = getVarIntegerValue(condition.right);
                if (value) {
                    return normalise(a <= b ? (b - a + 1) : 0);
                } else {
                    return normalise(a <= b ? 0 : (a - b));
                }
            case ">":
                a = getVarIntegerValue(condition.left);
                b = getVarIntegerValue(condition.right);
                if (value) {
                    return normalise(a > b ? (a - b) : 0);
                } else {
                    return normalise(a > b ? 0 : (b - a + 1));
                }
            case ">=":
                a = getVarIntegerValue(condition.left);
                b = getVarIntegerValue(condition.right);
                if (value) {
                    return normalise(a >= b ? (a - b + 1) : 0);
                } else {
                    return normalise(a >= b ? 0 : (b - a));
                }
            case "||":
                if (value) {
                    return normalise(branchDistance(condition.left, value)
                            + branchDistance(condition.right, value));
                } else {
                    return Math.min(branchDistance(condition.left, value),
                            branchDistance(condition.right, value));
                }
            case "&&":
                if (value) {
                    return Math.min(branchDistance(condition.left, value),
                            branchDistance(condition.right, value));
                } else {
                    return normalise(branchDistance(condition.left, value)
                            + branchDistance(condition.right, value));
                }
            case "^":
                if (value) {
                    return Math.min(normalise(branchDistance(condition.left, value)
                            + branchDistance(condition.right, !value)),
                            normalise(branchDistance(condition.left, !value)
                                    + branchDistance(condition.right, value)));
                } else {
                    return Math.min(normalise(branchDistance(condition.left, value)
                            + branchDistance(condition.right, value)),
                            normalise(branchDistance(condition.left, !value)
                                    + branchDistance(condition.right, !value)));
                }
        }
        throw new AssertionError("not implemented yet, binaryOperatorDistance: " + condition.operator);

    }

    static Double unaryOperatorDistance(MyVar condition, boolean value) {
        switch (condition.operator) {
            case "!":
                if (condition.left.type == TypeEnum.BOOL) {
                    if (value) {
                        return normalise(condition.left.value ? 1 : 0);
                    } else {
                        return normalise(condition.left.value ? 0 : 1);
                    }
                } else {
                    return 1 - branchDistance(condition.left, value);
                    // throw new AssertionError("not implemented yet, unaryOperatorDistance: +
                    // condition.operator);
                }
        }
        throw new AssertionError("not implemented yet, unaryOperatorDistance: " + condition.operator);
    }

    static double normalise(double d) {
        return d / (d + 1);
    }

    static Double normalise(int dist) {
        Double d = Double.valueOf(dist);
        return d / (d + 1);
    }

    /**
     * Making a if-statement true: (value = false) (target = true)
     * a : d = {0 if a is true, 1 otherwise}
     * !a : d = {1 if a is true, 0 otherwise} CHECK
     * a == b : d = abs(a-b) CHECK
     * a != b : d = {0 if a !=b, 1 otherwise} CHECK
     * a < b : d = {0 if a < b; a-b + K otherwise} CHECK
     * a <= b : d = {0 if a <= b; a-b otherwise} CHECK
     * a > b : d = {0 if a > b; b-a + K otherwise} CHECK
     * a >= b : d = {0 if a >= b; b - a otherwise} CHECK
     * and for combinations of predicates:
     * 
     * p1 && p2 : d = d(p1) + d(p2) CHECK
     * p1 | p2 : d = min(d(p1), d(p2)) CHECK
     * p1 XOR p2 : d = min(d(p1) + d(!p2), d(!p1) + d(p2)) CHECK
     * !p1 : d = 1 - d(p1) CHECK
     * 
     * Making a if-statement false: (value = true) (target = false)
     * a : d = {1 if a is true, 0 otherwise}
     * !a : d = {0 if a is true, 1 otherwise} CHECK
     * a == b : d = {0 if a != b, 1 otherwise} CHECK
     * a != b : d = abs(a-b) CHECK
     * a < b : d = {b-a if a < b; 0 otherwise} CHECK
     * a <= b : d = {b-a+1 if a <= b; 0 otherwise} CHECK
     * a > b : d = {a-b if a > b; 0 otherwise} CHECK
     * a >= b : b = {a-+1 if a >= b; 0 otherwise} CHECK
     * 
     * and for combinations of predicates:
     * p1 & p2 : d = min(d(p1), d(p2)) CHECK
     * p1 | p2 : d = d(p1) + d(p2) CHECK
     * p1 XOR p2 : d = min(d(p1) + d(p2), d(!p1) + d(!p2)) CHECK
     * !p1 : d = 1 - d(p1) CHECK
     */
    static Double branchDistance(MyVar condition, boolean value) {
        // System.out.println(condition);
        switch (condition.type) {
            case BINARY:
                return binaryOperatorDistance(condition, value);
            case UNARY:
                return unaryOperatorDistance(condition, value);
            case BOOL:
                if (value) {
                    return normalise(condition.value ? 1 : 0);
                } else {
                    return normalise(condition.value ? 0 : 1);
                }
            default:
                break;
        }
        throw new AssertionError("not implemented yet, branchDistance: " + condition.toString());
    }

    /**
     * Write your solution that specifies what should happen when a new branch has
     * been found.
     */
    static void encounteredNewBranch(MyVar condition, boolean value, int line_nr) {
        Double bd = branchDistance(condition, value);
        // System.out.printf("line %5d, now: %b,\tdist: %2d, %s\n", line_nr, value, bd,
        // condition.toString());
        branches.put(line_nr, branches.getOrDefault(line_nr, VisitedEnum.NONE).andVisit(value));
        if (minimumBranchDistances.containsKey(line_nr)) {
            if (minimumBranchDistances.get(line_nr).getLeft() > bd) {
                minimumBranchDistances.put(line_nr, Pair.of(bd, currentTrace));
            }
        } else {
            // System.out.printf("new branch on line: %d\n", line_nr);
            minimumBranchDistances.put(line_nr, Pair.of(bd, currentTrace));
        }

        if (value) {
            double newMininum = Math.min(minimumBranchTrue.getOrDefault(line_nr, 1.0), bd);
            minimumBranchTrue.put(line_nr, newMininum);
            minimumBranchFalse.put(line_nr, 0.0);
        } else {
            double newMininum = Math.min(minimumBranchFalse.getOrDefault(line_nr, 1.0), bd);
            minimumBranchFalse.put(line_nr, newMininum);
            minimumBranchTrue.put(line_nr, 0.0);
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
        } else if (mode == FuzzMode.TOP_TRACE) {
            if (topTraces.size() == 0) {
                return generateRandomTrace(inputSymbols);
            }
            System.out.printf("total branch distance %d\n", topTraces.get(0).getKey());
            return mutate(topTraces.get(0).getRight(), inputSymbols);
        } else if (mode == FuzzMode.EXPLORE_BRANCHES || mode == FuzzMode.LOWEST_DISTANCE) {
            // Only visit branches that have not been visited both ways.
            Stream<Entry<Integer, VisitedEnum>> toVisit = branches.entrySet().stream().filter(x -> {
                return x.getValue() != VisitedEnum.BOTH;
            });
            if (mode == FuzzMode.EXPLORE_BRANCHES) {
                // Visit in order of line numbers
                toVisit = toVisit.sorted((a, b) -> {
                    return a.getKey().compareTo(b.getKey());
                });
            } else if (mode == FuzzMode.LOWEST_DISTANCE) {
                // Visit in order of branch with smallest branch distance
                toVisit = toVisit.sorted((a, b) -> {
                    return minimumBranchDistances.get(a.getKey()).getKey()
                            .compareTo(minimumBranchDistances.get(b.getKey()).getKey());
                });
            }
            // Sometimes skip a branch
            // if (numVisited() < totalBranches() - 5) {
            // toVisit = toVisit.skip(r.nextInt(5));
            // }
            Optional<Entry<Integer, VisitedEnum>> first = toVisit.findAny();
            if (first.isPresent()) {
                int ln = first.get().getKey();
                Pair<Double, List<String>> minimum = minimumBranchDistances.get(ln);
                // if (ln != lastFuzzLine) {
                // System.out.printf("Trying to get to line: %d, current distance: %f\n", ln,
                // minimum.getKey());
                // lastFuzzLine = ln;
                // }
                return mutate(minimum.getValue(), inputSymbols);
            } else {
                throw new AssertionError("No more branches to visit");
            }
        } else if (mode == FuzzMode.HILL_CLIMBER) {
            // System.out.println("USING THE HILL CLIMBER!");

            // If it is the start of the Hill Climber process
            if (latestTraceHC == null) {
                System.out.println("First Hill Climber Value!");
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
            // if the best of the traces is not better than the last one
            if (lowestScore > latestTraceHC.getLeft() && r.nextDouble() < 0.1) {
                // Generate a random trace
                List<String> trace = generateRandomTrace(inputSymbols);
                latestTraceHC = Pair.of(computeScore(trace), trace);
                // Increase the number of traces to generate for the next rounds
                // amountToGenerate++;
            } else {
                latestTraceHC = mutations.get(lowestIndex);
            }
            return latestTraceHC.getRight();
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

        // System.out.println("MUTATING START!");
        // System.out.println("Mutating: " + current.getRight());

        // Configure these variables to your liking
        double mutateChance = 0.1;
        double removeChance = 0.01;
        double addChance = 0.005;

        List<String> result = new ArrayList<>();

        for (int i = 0; i < current.size(); i++) {
            Double random = r.nextDouble();
            // System.out.println("Random: " + random);
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
                // System.out.println("Removal!");
            } else {
                // keep current input as it is, so add it
                // System.out.println("Do nothing!");
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
        if (mode == FuzzMode.RANDOM) {
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

    static int totalBranches() {
        return branches.size() * 2;
    }

    static int numVisited() {
        int visited = 0;
        for (Integer lineNumber : branches.keySet()) {
            visited += branches.get(lineNumber).amount();
        }
        return visited;
    }

    static void printTop5(List<Pair<Double, List<String>>> traces) {
        System.out.println("Current top 5:");
        for (int i = 0; i < traces.size(); i++) {
            Pair<Double, List<String>> pair = traces.get(i);
            System.out.printf("Number %d: %s, with score %.2f\n", i + 1,
                    pair.getRight().toString(),
                    pair.getLeft());
        }
    }

    static void updateTop5(List<Pair<Double, List<String>>> oldTop5,
            Pair<Double, List<String>> current) {

        oldTop5.add(current);
        // System.out.println("Current: " + current);

        // While the current item is lower then the item at index i
        for (int i = oldTop5.size() - 2; i >= 0 && oldTop5.get(i).getLeft() > current.getLeft(); i--) {
            oldTop5.set(i + 1, oldTop5.get(i));
            oldTop5.set(i, current);
        }
        // Keep the list at a maximum size
        if (oldTop5.size() > NUM_TOP_TRACES) {
            oldTop5.remove(NUM_TOP_TRACES);
        }
    }

    static void printAnswersQuestion1() {
        // Question A
        System.out.printf("Unique branches: Visited %d out of %d. Errors found: %d\n",
                numVisited(), totalBranches(), outputErrors.size());

        // Question B
        System.out.printf("Best: %.2f, with trace: %s\n", bestTraceScore,
                bestTrace.toString());

        // Question C
        // printTop5(topTraces);
    }

    static void reset() {
        minimumBranchTrue.clear();
        minimumBranchFalse.clear();
    }

    static void run() {
        initialize(DistanceTracker.inputSymbols);
        DistanceTracker.runNextFuzzedSequence(currentTrace.toArray(new String[0]));

        long start = System.currentTimeMillis();

        System.out.println(branches.toString());
        // Place here your code to guide your fuzzer with its search.
        while (!isFinished && stableSince + STOP_WHEN_STABLE_FOR > System.currentTimeMillis()) {
            iterations++;
            // Do things!
            try {
                currentTrace = fuzz(DistanceTracker.inputSymbols);
                double score = computeScore(currentTrace);

                int visited = numVisited();
                int total = totalBranches();

                long now = System.currentTimeMillis();
                if (visited > lastVisited) {
                    stableSince = now;
                    lastVisited = visited;
                }

                System.out.printf(
                        "Iter %d: Visited %d / %d. Errors: %d. Score: %.2f, Traces per iter: %d. tracelength: %d. Running for %d.\n",
                        iterations, visited, total,
                        outputErrors.size(), score, tracesPerIteration,
                        currentTrace.size(), (now - start) / 1000);

                if (score > bestTraceScore) {
                    bestTraceScore = score;
                    bestTrace = new ArrayList<>(currentTrace);
                    System.out.printf("New best: %f, with trace: %s\n", score,
                            currentTrace.toString());
                }

                // updateTop5(topTraces, Pair.of(score, currentTrace));

                // printAnswersQuestion1();

                if (iterations % 1000 == 0) {
                    Thread.sleep(0);
                }
                isFinished = total == visited && total > 0;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.printf("stable after %d seconds (over a period of %ds)\n", (stableSince - start) / 1000,
                (System.currentTimeMillis() - stableSince)  / 1000);
        System.exit(0);
    }

    static double branchSumUnvisited() {
        double sum = 0;
        for (int line_nr : branches.keySet()) {
            VisitedEnum visited = branches.get(line_nr);
            if (!visited.hasVisited(true)) {
                sum += minimumBranchFalse.getOrDefault(line_nr, 1.0);
            }
            if (!visited.hasVisited(false)) {
                sum += minimumBranchTrue.getOrDefault(line_nr, 1.0);
            }
        }
        return sum;
    }

    static double branchesSumAll() {
        double sum = 0;
        for (int line_nr : branches.keySet()) {
            sum += minimumBranchFalse.getOrDefault(line_nr, 1.0);
            sum += minimumBranchTrue.getOrDefault(line_nr, 1.0);
        }
        return sum;
    }

    static double computeScore(List<String> trace) {
        reset();
        DistanceTracker.runNextFuzzedSequence(trace.toArray(new String[0]));
        return branchSumUnvisited();
        // return branchesSumAll();
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
            // System.out.printf("MATCH: %s %s\n", out, matcher.group(1));
            outputErrors.add(Integer.parseInt(matcher.group(1)));
        } else {
            // System.out.println(out);
        }
    }
}
