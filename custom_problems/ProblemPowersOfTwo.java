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
            i++;
            cf = false;
        }
        if (cf && input.equals("p")) {
            cf = false;
            if (i == 1073741824) { Errors.__VERIFIER_error(30); }
            if (i == 536870912) { Errors.__VERIFIER_error(29); }
            if (i == 268435456) { Errors.__VERIFIER_error(28); }
            if (i == 134217728) { Errors.__VERIFIER_error(27); }
            if (i == 67108864) { Errors.__VERIFIER_error(26); }
            if (i == 33554432) { Errors.__VERIFIER_error(25); }
            if (i == 16777216) { Errors.__VERIFIER_error(24); }
            if (i == 8388608) { Errors.__VERIFIER_error(23); }
            if (i == 4194304) { Errors.__VERIFIER_error(22); }
            if (i == 2097152) { Errors.__VERIFIER_error(21); }
            if (i == 1048576) { Errors.__VERIFIER_error(20); }
            if (i == 524288) { Errors.__VERIFIER_error(19); }
            if (i == 262144) { Errors.__VERIFIER_error(18); }
            if (i == 131072) { Errors.__VERIFIER_error(17); }
            if (i == 65536) { Errors.__VERIFIER_error(16); }
            if (i == 32768) { Errors.__VERIFIER_error(15); }
            if (i == 16384) { Errors.__VERIFIER_error(14); }
            if (i == 8192) { Errors.__VERIFIER_error(13); }
            if (i == 4096) { Errors.__VERIFIER_error(12); }
            if (i == 2048) { Errors.__VERIFIER_error(11); }
            if (i == 1024) { Errors.__VERIFIER_error(10); }
            if (i == 512) { Errors.__VERIFIER_error(9); }
            if (i == 256) { Errors.__VERIFIER_error(8); }
            if (i == 128) { Errors.__VERIFIER_error(7); }
            if (i == 64) { Errors.__VERIFIER_error(6); }
            if (i == 32) { Errors.__VERIFIER_error(5); }
            if (i == 16) { Errors.__VERIFIER_error(4); }
            if (i == 8) { Errors.__VERIFIER_error(3); }
            if (i == 4) { Errors.__VERIFIER_error(2); }
            if (i == 2) { Errors.__VERIFIER_error(1); }
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
