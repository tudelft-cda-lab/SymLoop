import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProblemPowersOfTwo {
    static BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    private String[] inputs = { "i", "p"};

    public int i = 0;
    public boolean cf = true;
    public boolean done = false;

    public void calculateOutput(String input) {
        cf = true;
        if (cf && input.equals("i")) {
            i += 1;
            cf = false;
        }
        if (cf && input.equals("p")) {
            cf = false;
            if (i > 128) { Errors.__VERIFIER_error(7); }
            if (i > 64) { Errors.__VERIFIER_error(6); }
            if (i > 32) { Errors.__VERIFIER_error(5); }
            if (i > 16) { Errors.__VERIFIER_error(4); }
            if (i > 8) { Errors.__VERIFIER_error(3); }
            if (i > 4) { Errors.__VERIFIER_error(2); }
            if (i > 2) { Errors.__VERIFIER_error(1); }
        }
    }

    public static void main(String[] args) throws Exception {
        // init system and input reader
        ProblemPowersOfTwo eca = new ProblemPowersOfTwo();

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
