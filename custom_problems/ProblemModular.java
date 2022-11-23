import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProblemModular {
    static BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    private String[] inputs = { "A", "B", "C", "D" };

    public int a = -3;
    public boolean cf = true;
    public boolean equal = false;

    private void errorCheck() {
        if (cf && a % 3 == -3) {
            cf = false;
            Errors.__VERIFIER_error(0);
        }
    }

    public void calculateOutput(String input) {
        cf = true;
        if (cf && input.equals("A")) {

        }
        errorCheck();
        if (cf)
            throw new IllegalArgumentException("Current state has no transition for this input!");
    }

    public static void main(String[] args) throws Exception {
        // init system and input reader
        ProblemModular eca = new ProblemModular();

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
