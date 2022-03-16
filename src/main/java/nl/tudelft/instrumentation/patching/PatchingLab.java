package nl.tudelft.instrumentation.patching;

import java.util.*;

public class PatchingLab {

    static Random r = new Random();
    static boolean isFinished = false;
    static boolean[] operatorIsBoolean;
    static int[] visited;

    static String[] currentOperators = null;
    static double[] currentSuspicious = null;
    static double currentFitness = 0;

    static GeneticAlgorithm ga;

    static void initialize() {
        // initialize the population based on OperatorTracker.operators
        operatorIsBoolean = new boolean[OperatorTracker.operators.length];
        visited = new int[OperatorTracker.operators.length];
        ga = new PatchingGA(100);
    }

    // encounteredOperator gets called for each operator encountered while running tests
    static boolean encounteredOperator(String operator, int left, int right, int operator_nr) {
        // Do something useful
        visited[operator_nr] = 1;

        String replacement = getOperator(operator_nr);
        if (replacement.equals("!=")) return left != right;
        if (replacement.equals("==")) return left == right;
        if (replacement.equals("<")) return left < right;
        if (replacement.equals(">")) return left > right;
        if (replacement.equals("<=")) return left <= right;
        if (replacement.equals(">=")) return left >= right;
        return false;
    }


    static String getOperator(int operator_nr) {
        if(currentOperators != null) {
            return currentOperators[operator_nr];
        }
        return OperatorTracker.operators[operator_nr];
    }

    static boolean encounteredOperator(String operator, boolean left, boolean right, int operator_nr) {
        // Do something useful
        operatorIsBoolean[operator_nr] = true;
        visited[operator_nr] = 1;

        String replacement = getOperator(operator_nr);
        if (replacement.equals("!=")) return left != right;
        if (replacement.equals("==")) return left == right;
        return false;
    }

    static double basicFitness(List<Boolean> results) {
        double counter = 0;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == false) {
                counter += 1;
            }
        }

        double fitness = counter / (double) results.size();
        return fitness;
    }

    static double[] tarantulations() {
        int amountOperators = OperatorTracker.operators.length;
        int[] pass = new int[amountOperators];
        int[] fail = new int[amountOperators];
        double[] scores = new double[amountOperators];
        int totalpass = 0;
        int totalfail = 0;

        int amountTests = OperatorTracker.tests.size();
        for (int i = 0; i < amountTests; i++) {
            boolean res = OperatorTracker.runTest(i);
            if (res) {
                totalpass++;
                addArrays(pass, visited);
            } else {
                totalfail++;
                addArrays(fail, visited);
            }
        }
        if(totalfail == 0){
            System.out.println("FOUND PATCH");
            System.out.println(Arrays.asList(currentOperators));
            System.exit(0);
        }

        double totalfailed = (double) totalfail;
        double totalpassed = (double) totalpass;
        currentFitness = calculateFitness(totalpass, totalfail);
        System.out.printf("fitness: %f, totalpass: %d, totalfail: %d\n", currentFitness, totalpass, totalfail);

        for (int i = 0; i < amountOperators; i++) {
            double score = 1.0;

            if (fail[i] != 0 || pass[i] != 0) {
                double failed = fail[i];
                double passed = pass[i];
                score = (failed / totalfailed) / ((failed / totalfailed) + (passed / totalpassed));


            }
            scores[i] = score;
        }

        return scores;
    }

    static double calculateFitness(int totalpass, int totalfailed) {
        // return 1.0 / (0.1 * totalpass + 10.0 * totalfailed);
        return 1.0 / (totalfailed * totalfailed);
    }

    static void addArrays(int[] a1, int[] a2) {
        if (a1.length != a2.length) {
            throw new IllegalArgumentException("Array length not equal!");
        }

        for (int i = 0; i < a1.length; i++) {
            a1[i] += a2[i];

            // reset visited while you're already looping through it
            a2[i] = 0;
        }
    }

    static double[] calcSuspicious() {
        double[] hues = tarantulations();
        for (int i = 0; i < hues.length; i++) {
            hues[i] = 1 - hues[i];
        }
        return hues;

    }

    static void runCandidate(String[] candidate) {
        currentOperators = candidate;
        currentSuspicious = calcSuspicious();
    }

    static void run() {
        initialize();


        // Place the code here you want to run once:
        // You want to change this of course, this is just an example
        // Tests are loaded from resources/tests.txt, make sure you put in the right tests for the right problem!
        List<Boolean> testresults = OperatorTracker.runAllTests();
        System.out.println("Entered run");
        // System.out.println(OperatorTracker.tests.size());


        // basicFitness(testresults);
        // Loop here, running your genetic algorithm until you think it is done
        while (!isFinished) {
            // Do things!
            ga.generation();

            try {
                System.out.println("Woohoo, looping!");
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void output(String out) {
        // This will get called when the problem code tries to print things,
        // the prints in the original code have been removed for your convenience

        // System.out.println(out);
    }
}
