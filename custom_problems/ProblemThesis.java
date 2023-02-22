import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProblemThesis {
    static BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    private String[] inputs = { "i", "p" };
    private final int LIMIT = Integer.parseInt(System.getenv("LIMIT"));

    public int i = 0;
    public boolean cf = true;
    public boolean done = false;

    public void calculateOutput(String input) {
        cf = true;
        if (cf && input.equals("i")) {
            System.out.println("I");
            i+=1;
            cf = false;
        }
        if (cf && input.equals("p")) {
            cf = false;
            System.out.println("P");
            if (i == LIMIT) {
                Errors.__VERIFIER_error(LIMIT);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // init system and input reader
        ProblemThesis eca = new ProblemThesis();

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
