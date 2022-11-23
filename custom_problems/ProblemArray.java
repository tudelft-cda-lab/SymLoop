import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProblemArray {
    static BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    private String[] inputs = { "A", "B", "C", "D" };

    public int[] a = { 0, 1, 2, 3, 4 };
    public int[] b = { 5, 6, 7 };
    public boolean cf = true;
    public boolean equal = false;

    private void errorCheck() {
        if (cf && a[0] == 1) {
            cf = false;
            Errors.__VERIFIER_error(0);
        }
    }

    private void calculateOutputm50(String input) {
        if (cf && a[0] == b[0]) {
            cf = false;
            a[0] = a[1];
            System.out.println("1.1");
        }
    }

    public void calculateOutput(String input) {
        cf = true;
        if (cf && input.equals("A")) {
            calculateOutputm50(input);
        }
        if (cf && input.equals("B")) {
            cf = false;
            equal = true;
            b = a;
        }
        if (cf && input.equals("C") && equal && b[3] == 3) {
            // System.exit(1);
            cf = false;
        }
        errorCheck();
        if (cf)
            throw new IllegalArgumentException("Current state has no transition for this input!");
    }

    public static void main(String[] args) throws Exception {
        // init system and input reader
        ProblemArray eca = new ProblemArray();

        // main i/o-loop
        while (true) {
            // read input
            String input = stdin.readLine();

            try {
                // operate eca engine output =
                eca.calculateOutput(input);
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid input: " + e.getMessage());
            }
        }
    }
}
