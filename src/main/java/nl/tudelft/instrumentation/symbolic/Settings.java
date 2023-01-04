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
    private static final int DEFAULT_MAX_RUNTIME_SINGLE_TRACE_S = 600;
    private static final int DEFAULT_MAX_TIME_S = -1;
    private static Settings singleton;

    public final boolean UNFOLD_AND;
    public final int LOOP_UNROLLING_AMOUNT;
    public final int MAX_LOOP_DETECTION_DEPTH;
    // Longest a single testcase is allowed to run
    public final int MAX_RUNTIME_SINGLE_TRACE_S;
    public final int MAX_TIME_S;
    public final boolean STOP_ON_FIRST_TIMEOUT;

    public final String[] INITIAL_TRACE;

    public final boolean CORRECT_INTEGER_MODEL;

    private static int parseTimeToS(String s) {
        if (s.equals("-1")) {
            return -1;
        }
        Pattern TIME_PATTERN = Pattern.compile("(?<amount>\\d+)(?<multiplier>m|s|h)?");
        Matcher m = TIME_PATTERN.matcher(s);
        if (m.matches()) {
            int amount = Integer.parseInt(m.group("amount"));
            String multiplier = m.group("multiplier");
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
            int maxLoopDetectionDepth, int maxRuntimeTraceS, int maxTimeS, boolean STOP_ON_FIRST_TIMEOUT, boolean CORRECT_INTEGER_MODEL) {
        this.UNFOLD_AND = unfoldAnd;
        this.INITIAL_TRACE = initial == null ? null : initial.split(",");
        this.LOOP_UNROLLING_AMOUNT = loopUnrollingAmount;
        this.MAX_LOOP_DETECTION_DEPTH = maxLoopDetectionDepth;
        this.MAX_RUNTIME_SINGLE_TRACE_S = maxRuntimeTraceS;
        this.MAX_TIME_S = maxTimeS;
        this.STOP_ON_FIRST_TIMEOUT = STOP_ON_FIRST_TIMEOUT;
        this.CORRECT_INTEGER_MODEL = CORRECT_INTEGER_MODEL;
    }

    public static Settings create(String[] args) {
        if (singleton == null) {
            CommandLine cl = parseArguments(args);
            boolean unfoldAnd = cl.hasOption("unfold-and");
            boolean STOP_ON_FIRST_TIMEOUT = !cl.hasOption("continue-on-timeout");
            boolean CORRECT_INTEGER_MODEL = !cl.hasOption("incorrect-integer-model");
            String initialTrace = cl.getOptionValue("initial-trace", null);
            int loopUnrollingAmount = Integer
                    .parseInt(cl.getOptionValue("unroll-loops",
                            String.valueOf(DEFAULT_LOOP_UNROLLING)));
            int loopDetectionDepth = Integer
                    .parseInt(cl.getOptionValue("loop-detection-depth",
                            String.valueOf(DEFAULT_LOOP_DETECTION_DEPTH)));
            int maxRuntimeTraceMs = Integer
                    .parseInt(cl.getOptionValue("max-runtime-single-trace",
                            String.valueOf(DEFAULT_MAX_RUNTIME_SINGLE_TRACE_S)));
            int maxTime = parseTimeToS(cl.getOptionValue("max-time", String.valueOf(DEFAULT_MAX_TIME_S)));
            Settings s = new Settings(unfoldAnd, initialTrace, loopUnrollingAmount,
                    loopDetectionDepth,
                    maxRuntimeTraceMs, maxTime, STOP_ON_FIRST_TIMEOUT, CORRECT_INTEGER_MODEL);
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
        options.addOption("u", "unfold-and", false,
                "Unfold 'AND' expressions to possibly make the loop constraints shorter.");
        options.addOption("cim", "correct-integer-model", false,
                "By default, the mod and division operators are not fully correct for negative values. Enabling this flag makes the model correct, but might lead to a degredation in performance.");
        options.addOption("c", "continue-on-timeout", false,
                "Continue execution whenever a single trace times out. (Note: this may lead to missing paths)");
        options.addOption("h", "help", false, "Show this help message");
        options.addOption("i", "initial-trace", true,
                "The initial trace to run the program on. Use commas to seperate input symbols. (Example: 'A,B,C')");
        options.addOption("l", "unroll-loops", true,
                String.format(
                        "Amount of times to unroll a loop before continuing with execution. This also is the amount of times a loop is verified to run. (Default: %d)",
                        DEFAULT_LOOP_UNROLLING));
        options.addOption("d", "loop-detection-depth", true,
                String.format("The number of steps to look back into the history to try to detect loops. (Default: %d)",
                        DEFAULT_LOOP_DETECTION_DEPTH));
        options.addOption("mrst", "max-runtime-single-trace", true,
                String.format("The number of seconds a single trace can run before its gets killed. (Default: %d)",
                        DEFAULT_MAX_RUNTIME_SINGLE_TRACE_S));
        options.addOption("m", "max-time", true,
                String.format(
                        "The number of seconds a single trace can run before its gets killed. Use -1 to run indefinetely (Default: %d)",
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
