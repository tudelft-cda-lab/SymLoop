package nl.tudelft.instrumentation.patching;

import java.util.*;

public class PatchingLab {

    static Random r = new Random();
    static boolean isFinished = false;

    static void initialize() {
        // initialize the population based on OperatorTracker.operators
    }

    // encounteredOperator gets called for each operator encountered while running tests
    static boolean encounteredOperator(String operator, int left, int right, int operator_nr) {
        // Do something useful

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

        String replacement = OperatorTracker.operators[operator_nr];
        if (replacement.equals("!=")) return left != right;
        if (replacement.equals("==")) return left == right;
        return false;
    }

    static void basicFitness(List<Boolean> results) {
        System.out.println(results);

        int counter = 0;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == false) {
                counter++;
            }
        }

        System.out.println(counter);
        System.out.println(results.size());
        float fitness = (float) counter / (float) results.size();
        System.out.println("Fitness for this problem is: " + fitness);
    }

    static void run() {
        initialize();

        // Place the code here you want to run once:
        // You want to change this of course, this is just an example
        // Tests are loaded from resources/tests.txt, make sure you put in the right tests for the right problem!
        List<Boolean> testresults = OperatorTracker.runAllTests();
        System.out.println("Entered run");

        basicFitness(testresults);

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