import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProblemDivision {
    static BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    private String[] inputs = { "A" };

    public void calculateOutput(String input) {
        if (-1 / 5 == -1) {
            Errors.__VERIFIER_error(0);
        }
    }

    public static void main(String[] args) throws Exception {
        // init system and input reader
        ProblemDivision eca = new ProblemDivision();

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
