package nl.tudelft.instrumentation.fuzzing;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
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
    static int traceLength = 10;
    static boolean isFinished = false;

    static Map<Integer, VisitedEnum> branches = new HashMap<Integer, VisitedEnum>();
    static Map<Integer, Pair<Double, List<String>>> minimumBranchDistances = new HashMap<>();
    static List<String> bestTrace;
    static int bestTraceScore = 0;

    static int sum = 0;
    static List<Pair<Integer, List<String>>> topTraces = new ArrayList<>(5);
    static final int NUM_TOP_TRACES = 5;
    static int iterations = 0;
    static final FuzzMode mode = FuzzMode.EXPLORE_BRANCHES;

    static void initialize(String[] inputSymbols) {
        // Initialise a random trace from the input symbols of the problem.
        currentTrace = generateRandomTrace(inputSymbols);
    }

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

    static Double varAbs(MyVar a, MyVar b) {
        if (a.type == TypeEnum.INT
                && b.type == TypeEnum.INT) {
            return normalise(Math.abs(a.int_value - b.int_value));
        } else if (a.type == TypeEnum.STRING
                && b.type == TypeEnum.STRING) {
            return normalise(stringDifference(a.str_value, b.str_value));
        } else if (a.type == TypeEnum.BOOL
                && b.type == TypeEnum.BOOL) {
            return normalise(a.value == b.value ? 0 : 1);
        }
        throw new AssertionError("Both types should be equal");
    }

    static Double notEqualDistance(MyVar a, MyVar b) {
        if (a.type == TypeEnum.INT
                && a.type == TypeEnum.INT) {
            return normalise(a.int_value == b.int_value ? 1 : 0);
        } else if (a.type == TypeEnum.STRING
                && b.type == TypeEnum.STRING) {
            return normalise(a.str_value.equals(b.str_value) ? 1 : 0);
        } else if (a.type == TypeEnum.BOOL
                && b.type == TypeEnum.BOOL) {
            return normalise(a.value == b.value ? 1 : 0);
        }
        throw new AssertionError("Both types should be equal");
    }

    static Double binaryOperatorDistance(MyVar condition, boolean value) {
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
                if (condition.left.type == TypeEnum.INT
                        && condition.right.type == TypeEnum.INT) {
                    int a = condition.left.int_value;
                    int b = condition.right.int_value;
                    if (value) {
                        return normalise(a < b ? (b - a) : 0);
                    } else {
                        return normalise(a < b ? 0 : (a - b + 1));
                    }
                }
            case "<=":
                if (condition.left.type == TypeEnum.INT
                        && condition.right.type == TypeEnum.INT) {
                    int a = condition.left.int_value;
                    int b = condition.right.int_value;
                    if (value) {
                        return normalise(a <= b ? (b - a + 1) : 0);
                    } else {
                        return normalise(a <= b ? 0 : (a - b));
                    }
                }
            case ">":
                if (condition.left.type == TypeEnum.INT
                        && condition.right.type == TypeEnum.INT) {
                    int a = condition.left.int_value;
                    int b = condition.right.int_value;
                    if (value) {
                        return normalise(a > b ? (a - b) : 0);
                    } else {
                        return normalise(a > b ? 0 : (b - a + 1));
                    }
                }
            case ">=":
                if (condition.left.type == TypeEnum.INT
                        && condition.right.type == TypeEnum.INT) {
                    int a = condition.left.int_value;
                    int b = condition.right.int_value;
                    if (value) {
                        return normalise(a >= b ? (a - b + 1) : 0);
                    } else {
                        return normalise(a >= b ? 0 : (b - a));
                    }
                }
            case "||":
                if (value) {
                    return branchDistance(condition.left, value)
                            + branchDistance(condition.right, value);
                } else {
                    return Math.min(branchDistance(condition.left, value),
                            branchDistance(condition.right, value));
                }
            case "&&":
                if (value) {
                    return Math.min(branchDistance(condition.left, value),
                            branchDistance(condition.right, value));
                } else {
                    return branchDistance(condition.left, value)
                            + branchDistance(condition.right, value);
                }
            case "^":
                if (value) {
                    return Math.min(branchDistance(condition.left, value)
                            + branchDistance(condition.right, !value),
                            branchDistance(condition.left, !value)
                                    + branchDistance(condition.right, value));
                } else {
                    return Math.min(branchDistance(condition.left, value)
                            + branchDistance(condition.right, value),
                            branchDistance(condition.left, !value)
                                    + branchDistance(condition.right, !value));
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

        if (mode == FuzzMode.RANDOM || topTraces.size() == 0) {
            return generateRandomTrace(inputSymbols);
        } else if (mode == FuzzMode.TOP_TRACE) {
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
                Pair<Double, List<String>> minimum = minimumBranchDistances.get(first.get().getKey());
                System.out.printf("Trying to get to line: %d, current distance: %f\n", first.get().getKey(),
                        minimum.getKey());
                return mutate(minimum.getValue(), inputSymbols);
            } else {
                throw new AssertionError("No more branches to visit");
            }
        } else {
            throw new Error("Unimplemented mode: " + mode);
        }
    }

    static List<String> mutate(List<String> base, String[] symbols) {
        int amount = r.nextInt(base.size());
        base = new ArrayList<>(base);
        for (int i = 0; i < amount; i++) {
            base.set(r.nextInt(base.size()), symbols[r.nextInt(symbols.length)]);
        }
        return base;
    }

    /**
     * Generate a random trace from an array of symbols.
     * 
     * @param symbols the symbols from which a trace should be generated from.
     * @return a random trace that is generated from the given symbols.
     */
    static List<String> generateRandomTrace(String[] symbols) {
        ArrayList<String> trace = new ArrayList<>();
        for (int i = 0; i < traceLength; i++) {
            trace.add(symbols[r.nextInt(symbols.length)]);
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

    static void run() {
        initialize(DistanceTracker.inputSymbols);
        DistanceTracker.runNextFuzzedSequence(currentTrace.toArray(new String[0]));

        System.out.println(branches.toString());
        // Place here your code to guide your fuzzer with its search.
        while (!isFinished) {
            iterations++;
            // Do things!
            try {
                // branches.clear();
                sum = 0;
                currentTrace = fuzz(DistanceTracker.inputSymbols);
                DistanceTracker.runNextFuzzedSequence(currentTrace.toArray(new String[0]));
                int visited = numVisited();
                int total = totalBranches();
                int score = visited;
                System.out.printf("Iteration %d: Visited %d out of %d: %d%%\n", iterations, visited, total,
                        visited * 100 / total);

                if (total == visited) {
                    isFinished = true;
                }
                if (score > bestTraceScore) {
                    bestTraceScore = score;
                    bestTrace = new ArrayList<>(currentTrace);
                    System.out.printf("New best: %d, with trace: %s\n", score,
                            currentTrace.toString());
                }

                Pair<Integer, List<String>> current = Pair.of(sum, currentTrace);
                topTraces.add(current);
                // While the current item is lower then the item at index i
                for (int i = topTraces.size() - 2; i >= 0 && topTraces.get(i).getLeft() > current.getLeft(); i--) {
                    topTraces.set(i + 1, topTraces.get(i));
                    topTraces.set(i, current);
                }
                // Keep the list at a maximum size
                if (topTraces.size() > NUM_TOP_TRACES) {
                    topTraces.remove(NUM_TOP_TRACES);
                }

                // System.out.println("Current top 5:");
                // for (int i = 0; i < topTraces.size(); i++) {
                // Pair<Integer, List<String>> pair = topTraces.get(i);
                // System.out.printf("Number %d: %s, with score %d\n", i + 1,
                // pair.getRight().toString(),
                // pair.getLeft());
                // }

                Thread.sleep(0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Method that is used for catching the output from standard out.
     * You should write your own logic here.
     * 
     * @param out the string that has been outputted in the standard out.
     */
    public static void output(String out) {
        System.out.println(out);
    }
}
