package nl.tudelft.instrumentation.patching;

import java.util.*;

public class PatchingLab {

    static Random r = new Random();
    static boolean isFinished = false;
    static boolean[] operatorIsBoolean;
    static int[] visited;

    static GeneticAlgorithm ga;

    static void initialize() {
        // initialize the population based on OperatorTracker.operators
        operatorIsBoolean = new boolean[OperatorTracker.operators.length];
        visited = new int[OperatorTracker.operators.length];
         ga = new PatchingGA(10);
    }

    // encounteredOperator gets called for each operator encountered while running tests
    static boolean encounteredOperator(String operator, int left, int right, int operator_nr) {
        // Do something useful
        visited[operator_nr] = 1;

        String replacement = OperatorTracker.operators[operator_nr];
        if (replacement.equals("!=")) return left != right;
        if (replacement.equals("==")) return left == right;
        if (replacement.equals("<")) return left < right;
        if (replacement.equals(">")) return left > right;
        if (replacement.equals("<=")) return left <= right;
        if (replacement.equals(">=")) return left >= right;
        return false;
    }

    static boolean encounteredOperator(String operator, boolean left, boolean right, int operator_nr) {
        // Do something useful
        operatorIsBoolean[operator_nr] = true;
        visited[operator_nr] = 1;

        String replacement = OperatorTracker.operators[operator_nr];
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

        for (int i = 0; i < amountOperators; i++) {
            double score;

            if (fail[i] == 0 && pass[i] == 0) {
                score = 1;
            } else {
                score = ((double) fail[i] / (double) totalfail) /
                        (((double) fail[i] / (double) totalfail) + ((double) pass[i] / (double) totalpass));
            }
            scores[i] = score;
        }

        return scores;
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

        System.out.println(Arrays.toString(hues));
        return hues;
    }

    static void run() {
        initialize();

        // Place the code here you want to run once:
        // You want to change this of course, this is just an example
        // Tests are loaded from resources/tests.txt, make sure you put in the right tests for the right problem!
        List<Boolean> testresults = OperatorTracker.runAllTests();
        System.out.println("Entered run");
        System.out.println(OperatorTracker.tests.size());


        // basicFitness(testresults);

        double[] sussie = calcSuspicious();

        // Loop here, running your genetic algorithm until you think it is done
        while (!isFinished) {
            // Do things!
            try {
                System.out.println("Woohoo, looping!");
                Thread.sleep(1000);
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
