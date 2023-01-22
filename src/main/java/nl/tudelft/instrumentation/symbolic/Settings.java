package nl.tudelft.instrumentation.symbolic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Settings {
    private static final int DEFAULT_LOOP_UNROLLING = 10;
    private static final int DEFAULT_LOOP_DETECTION_DEPTH = 1;
    private static final int DEFAULT_MAX_TIME_S = -1;
    private static final int DEFAULT_SOLVER_TIMEOUT_S = -1;
    private static Settings singleton;

    public final boolean DO_HALF_LOOPS = true;
    public final boolean UNFOLD_AND;
    public final boolean MINIMIZE;
    public final int LOOP_UNROLLING_AMOUNT;
    public final int MAX_LOOP_DETECTION_DEPTH;
    // Longest a single testcase is allowed to run
    public final int MAX_TIME_S;
    public final int SOLVER_TIMEOUT_S;

    public final String[] INITIAL_TRACE;
    public final String[] LOOP_TRACE;
    public final boolean VERIFY_LOOP;

    public final boolean CORRECT_INTEGER_MODEL;

    public String parameters() {
            return String.format("Settings: d=%d,l=%d,m=%d,st=%d,u=%b", MAX_LOOP_DETECTION_DEPTH, LOOP_UNROLLING_AMOUNT, MAX_TIME_S, SOLVER_TIMEOUT_S, UNFOLD_AND);
    }

    private static int parseTimeToS(String s) {
        if (s.equals("-1")) {
            return -1;
        }
        Pattern TIME_PATTERN = Pattern.compile("(?<amount>\\d+)(?<multiplier>m|s|h)?");
        Matcher m = TIME_PATTERN.matcher(s);
        if (m.matches()) {
            int amount = Integer.parseInt(m.group("amount"));
            String multiplier = m.group("multiplier");
            if (multiplier == null) {
                multiplier = "s";
            }
            int seconds_multiplier = 1;
            switch (multiplier) {
                case "s":
                    seconds_multiplier = 1;
                    break;

                case "m":
                    seconds_multiplier = 60;
                    break;

                case "h":
                    seconds_multiplier = 60 * 60;
                    break;
            }
            return amount * seconds_multiplier;
        }
        System.err.printf("UNABLE TO PARSE TIME STRING: %s\n", s);
        System.exit(1);
        return -1;
    }

    private Settings(boolean unfoldAnd, String initial,
            int loopUnrollingAmount,
            int maxLoopDetectionDepth, int maxTimeS, boolean CORRECT_INTEGER_MODEL, boolean MINIMIZE, int SOLVER_TIMEOUT_S, String verifyLoop) {
        this.UNFOLD_AND = unfoldAnd;
        this.INITIAL_TRACE = initial == null ? null : initial.split(",");
        this.VERIFY_LOOP = verifyLoop != null;
        this.LOOP_TRACE = verifyLoop == null ? null : verifyLoop.split(",");
        this.LOOP_UNROLLING_AMOUNT = loopUnrollingAmount;
        this.MAX_LOOP_DETECTION_DEPTH = maxLoopDetectionDepth;
        this.MAX_TIME_S = maxTimeS;
        this.CORRECT_INTEGER_MODEL = CORRECT_INTEGER_MODEL;
        this.MINIMIZE = MINIMIZE;
        this.SOLVER_TIMEOUT_S = SOLVER_TIMEOUT_S;
    }

    public static Settings create(String[] args) {
        if (singleton == null) {
            CommandLine cl = parseArguments(args);
            boolean unfoldAnd = cl.hasOption("unfold-and");
            boolean CORRECT_INTEGER_MODEL = !cl.hasOption("incorrect-integer-model");
            boolean MINIMIZE = !cl.hasOption("no-minimize");
            String initialTrace = cl.getOptionValue("initial-trace", null);
            String VERIFY_LOOP = cl.getOptionValue("verify-loop", null);
            int loopUnrollingAmount = Integer
                    .parseInt(cl.getOptionValue("unroll-loops",
                            String.valueOf(DEFAULT_LOOP_UNROLLING)));
            int loopDetectionDepth = Integer
                    .parseInt(cl.getOptionValue("loop-detection-depth",
                            String.valueOf(DEFAULT_LOOP_DETECTION_DEPTH)));
            int maxTime = parseTimeToS(cl.getOptionValue("max-time", String.valueOf(DEFAULT_MAX_TIME_S)));
            int SOLVER_TIMEOUT_S = parseTimeToS(cl.getOptionValue("solver-timeout", String.valueOf(DEFAULT_SOLVER_TIMEOUT_S)));
            Settings s = new Settings(unfoldAnd, initialTrace, loopUnrollingAmount,
                    loopDetectionDepth, maxTime, CORRECT_INTEGER_MODEL, MINIMIZE, SOLVER_TIMEOUT_S, VERIFY_LOOP);
            singleton = s;
            return s;
        } else {
            return singleton;
        }
    }

    public static Settings getInstance() {
        return singleton;
    }

    private static Options getOptions() {
        Options options = new Options();
        options.addOption("n", "no-minimize", false,
                "Disable minimization strategy to the solver for loop constraints. Minimization makes the generated inputs shorter.");
        options.addOption("u", "unfold-and", false,
                "Unfold 'AND' expressions to possibly make the loop constraints shorter.");
        options.addOption("icim", "incorrect-integer-model", false,
                "By default, the mod and division operators are not fully correct for negative values. Enabling this flag makes the model correct, but might lead to a degredation in performance.");
        options.addOption("h", "help", false, "Show this help message");
        options.addOption("i", "initial-trace", true,
                "The initial trace to run the program on. Use commas to seperate input symbols. (Example: 'A,B,C')");
        options.addOption("v", "verify-loop", true,
                "The looping part of a trace to verify is infinitely repeating. Use commas to seperate input symbols. (Example: 'A,B,C')");
        options.addOption("l", "unroll-loops", true,
                String.format(
                        "Amount of times to unroll a loop before continuing with execution. This also is the amount of times a loop is verified to run. (Default: %d)",
                        DEFAULT_LOOP_UNROLLING));
        options.addOption("d", "loop-detection-depth", true,
                String.format("The number of steps to look back into the history to try to detect loops. (Default: %d)",
                        DEFAULT_LOOP_DETECTION_DEPTH));
        options.addOption("st", "solver-timeout", true,
                String.format("The number of seconds a single call to the solver can take before its gets killed. Use -1 to have no limit. (Default: %d)",
                        DEFAULT_SOLVER_TIMEOUT_S));
        options.addOption("m", "max-time", true,
                String.format(
                        "The amount of time to keep running. Use -1 to run indefinetely (Default: %d)",
                        DEFAULT_MAX_TIME_S));
        return options;
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("nl.tudelft.instrumentation.Main", options, true);
    }

    private static CommandLine parseArguments(String[] args) {
        Options options = getOptions();
        DefaultParser parser = new DefaultParser();
        CommandLine line = null;
        try {
            line = parser.parse(options, args);
            if (line.hasOption("help")) {
                printHelp(options);
                System.exit(0);
            }
        } catch (ParseException ex) {
            System.err.println("Failed to parse command line arguments");
            System.err.println(ex.toString());
            printHelp(options);
            System.exit(1);
        }
        return line;
    }
}
