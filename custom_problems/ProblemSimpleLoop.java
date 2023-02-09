import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProblemSimpleLoop {
    static BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    private String[] inputs = { "i", "p"};

    public int i = 0;
    public boolean cf = true;
    public boolean done = false;

    public void calculateOutput(String input) {
        cf = true;
        if (cf && input.equals("i") && i < 6) {
            i += 1;
            cf = false;
            System.out.println("i");
        }
        if (cf && input.equals("p")) {
            cf = false;
            if (i > 4) {
                Errors.__VERIFIER_error(0);
            } else {
                System.out.println("n");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // init system and input reader
        ProblemSimpleLoop eca = new ProblemSimpleLoop();

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
