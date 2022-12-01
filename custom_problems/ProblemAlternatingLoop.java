import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProblemAlternatingLoop {
    static BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    private String[] inputs = { "<", ">", "p" };

    public int i = 0;
    public boolean cf = true;
    public boolean open = false;

    public void calculateOutput(String input) {
        cf = true;
        if (cf && input.equals("<") && !open) {
            open = true;
            cf = false;
        }
        if (cf && input.equals(">") && open) {
            open = false;
            i += 1;
            cf = false;
        }
        if (cf && input.equals("p")) {
            cf = false;
            if (i > 4) {
                Errors.__VERIFIER_error(0);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // init system and input reader
        ProblemAlternatingLoop eca = new ProblemAlternatingLoop();

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
