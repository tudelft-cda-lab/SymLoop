package nl.tudelft.instrumentation.symbolic;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import nl.tudelft.instrumentation.symbolic.SolverInterface.SolvingForType;
import nl.tudelft.instrumentation.symbolic.exprs.ConstantCustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExpr;
import nl.tudelft.instrumentation.symbolic.exprs.CustomExprOp;
import nl.tudelft.instrumentation.symbolic.exprs.ExprType;
import nl.tudelft.instrumentation.symbolic.exprs.NamedCustomExpr;
import de.learnlib.algorithms.lstar.mealy.ExtensibleLStarMealy;
import de.learnlib.algorithms.lstar.mealy.ExtensibleLStarMealyBuilder;
import de.learnlib.algorithms.ttt.mealy.TTTLearnerMealy;
import de.learnlib.algorithms.ttt.mealy.TTTLearnerMealyBuilder;
import de.learnlib.api.algorithm.LearningAlgorithm.MealyLearner;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.datastructure.observationtable.writer.ObservationTableASCIIWriter;
import de.learnlib.filter.cache.mealy.MealyCacheOracle;
import de.learnlib.filter.statistic.oracle.MealyCounterOracle;
import de.learnlib.oracle.equivalence.EQOracleChain;
import de.learnlib.oracle.equivalence.MealyWMethodEQOracle;
import de.learnlib.util.Experiment.MealyExperiment;
import de.learnlib.util.statistics.SimpleProfiler;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * Class for symbolic learning of a problem
 */
public class SymbolicLearner {

    static boolean VERIFY_LOOP = false;

    static boolean isNotVerifyingOrCanSkip() {
        Settings s = Settings.getInstance();
        if (!VERIFY_LOOP) {
            return true;
        }
        SymbolicExecutionLab.shouldSolve = false;
        if (s.COLLECT_PATHS) {
            return true;
        }
        int basePart = ACCESS.length;
        int loopPart = LOOP_TRACE.length;
        if (SymbolicExecutionLab.inputInIndex >= basePart + loopPart) {
            SymbolicExecutionLab.shouldSolve = true;
            if (SymbolicExecutionLab.inputInIndex == basePart + loopPart) {
                return true;
            }
            return false;
        }
        SymbolicExecutionLab.shouldSolve = false;
        return false;
    }

    static Optional<String[]> counterExample = Optional.empty();

    static int indexBefore = -1;
    static Map<String, Integer> from;
    static int saveAtIndex = -1;
    static boolean loopVerification;
    static int loopSize;
    private static String[] ACCESS;
    private static String[] LOOP_TRACE;

    static LoopVerifyResult collectPaths(String[] LOOP_TRACE, List<String> base, Stream<List<String>> distinguishers) {
        NextTrace intialTrace = new NextTrace(base, 0, "<collect solver>", false);
        SymbolicExecutionLab.runNext(intialTrace);
        OptimizingSolver solver = PathTracker.solver;
        PathTracker.solver = new InferringSolver();
        LinkedList<MyVar> trackerInputs = new LinkedList<>(PathTracker.inputs);
        Map<String, Integer> toCounts = SymbolicExecutionLab.loopDetector.history.getVariables();
        HashMap<MyVar, Replacement> loopIterations = PathTracker.loopIterations;
        PathTracker.loopIterations = new HashMap<>();

        String full = String.join("", base);
        full += String.join("", LOOP_TRACE);
        if (SymbolicExecutionLab.loopDetector.isSelfLooping(full)) {
            return LoopVerifyResult.self();
        } else if (!SymbolicExecutionLab.loopDetector.containsLoop(full)) {
            return LoopVerifyResult.notFound();
        } else if (counterExample.isPresent()) {
            return LoopVerifyResult.counter(counterExample.get());
        }

        SymbolicExecutionLab.isCreatingPaths = true;
        Optional<LoopVerifyResult> res = distinguishers.map(d -> {
            List<String> inputs = new ArrayList<>();
            inputs.addAll(base);
            inputs.addAll(d);
            NextTrace trace = new NextTrace(inputs, 0, "<collect paths>", false);
            saveAtIndex = inputs.size() - d.size();
            SymbolicExecutionLab.runNext(trace);
            assert indexBefore != -1;

            int currentIndex = SymbolicExecutionLab.loopDetector.history.getNumberOfSaves();
            Pair<CustomExpr, CustomExpr> c = SymbolicExecutionLab.loopDetector.history
                    .getSeperateAssignAndBranches(currentIndex - indexBefore + 1);

            PathTracker.loopIterations = loopIterations;
            checkAfterLoop(solver, d, c.getKey(), c.getValue(), toCounts, trackerInputs,
                    SymbolicExecutionLab.loopDetector.history.getVariables());
            PathTracker.loopIterations = new HashMap<>();
            if (counterExample.isPresent()) {
                return Optional.of(LoopVerifyResult.counter(counterExample.get()));
            }
            Optional<LoopVerifyResult> result = Optional.empty();
            return result;
        }).filter(Optional::isPresent).map(Optional::get).findFirst();
        SymbolicExecutionLab.isCreatingPaths = false;
        return res.orElse(LoopVerifyResult.probably());
    }

    static void checkAfterLoop(OptimizingSolver s, List<String> symbols, CustomExpr assign, CustomExpr path,
            Map<String, Integer> toCounts, LinkedList<MyVar> inputs, Map<String, Integer> upto) {
        List<Replacement> rs = new ArrayList<>();

        for (Entry<String, Integer> e : from.entrySet()) {
            String name = e.getKey();
            int f = e.getValue();
            int up = upto.get(name);
            int added = toCounts.get(name) - f;
            rs.add(new Replacement(
                    name,
                    null,
                    up,
                    added,
                    f - 1));
        }

        CustomExpr newPath = Replacement.applyAllTo(rs, path);
        CustomExpr newAssign = Replacement.applyAllTo(rs, assign);

        PathTracker.inputs = new LinkedList<>(inputs);
        List<CustomExpr> inputConstraints = new ArrayList<>();

        for (int i = 0; i < symbols.size(); i++) {
            String sym = symbols.get(i);
            String inputName = SymbolicExecutionLab.getVarName(SymbolicExecutionLab.loopDetector.inputName,
                    toCounts.get(SymbolicExecutionLab.loopDetector.inputName) + i);
            // System.out.printf("sym: %s, inputName: %s\n", sym, inputName);
            NamedCustomExpr input = new NamedCustomExpr(inputName, ExprType.STRING);
            PathTracker.inputs.add(new MyVar(input));
            inputConstraints.add(CustomExprOp.mkEq(ConstantCustomExpr.fromString(sym), input));
        }

        CustomExpr single = CustomExprOp.mkAnd(
                CustomExprOp.mkAnd(inputConstraints),
                CustomExprOp.mkNot(newPath), newAssign);

        // System.out.println(single);

        OptimizingSolver old = PathTracker.solver;
        PathTracker.solver = s;
        // System.out.printf("Now doing %s\n", String.join(",", symbols));
        boolean possible = PathTracker.solve(single, SolvingForType.EQUIVALENCE, false, true);
        // System.out.printf("possible: %b\n", possible);
        PathTracker.solver = old;
        PathTracker.inputs = new LinkedList<>();
    }

    static LoopVerifyResult verifyLoop(String[] ACCESS, String[] LOOP_TRACE,
            Stream<List<String>> DISTINGUISHING_TRACES) {
        SymbolicExecutionLab.VERIFY_LOOP = true;
        SymbolicLearner.ACCESS = ACCESS;
        SymbolicLearner.LOOP_TRACE = LOOP_TRACE;
        SymbolicExecutionLab.printThisRun = false;
        Settings s = Settings.getInstance();
        loopVerification = true;
        loopSize = LOOP_TRACE.length;
        // printfGreen("Verifying loop: %s\n", String.join("", ACCESS));
        assert ACCESS != null;
        List<String> input = new ArrayList<>();
        input.addAll(Arrays.asList(ACCESS));
        input.addAll(Arrays.asList(LOOP_TRACE));

        String full = String.join("", input);

        if (SymbolicExecutionLab.loopDetector.isSelfLooping(full)) {
            return LoopVerifyResult.self();
        }

        // printfBlue("Full: %s\n", full);
        if (s.SUFFIX != null && s.SUFFIX.length > 0) {
            input.addAll(Arrays.asList(s.SUFFIX));
        }

        if (DISTINGUISHING_TRACES != null) {
            return collectPaths(LOOP_TRACE, input, DISTINGUISHING_TRACES);
        } else {
            input.addAll(Arrays.asList(LOOP_TRACE));
            NextTrace trace = new NextTrace(input, 0, "<initial>", false);
            SymbolicExecutionLab.runNext(trace);
        }
        if (counterExample.isPresent()) {
            return LoopVerifyResult.counter(counterExample.get());
        }

        // printQs();
        if (SymbolicExecutionLab.loopDetector.isSelfLooping(full)) {
            return LoopVerifyResult.self();
        } else if (SymbolicExecutionLab.loopDetector.containsLoop(full)) {
            return LoopVerifyResult.probably();
        } else {
            return LoopVerifyResult.notFound();
        }
    }

    public static void learn() throws IOException {

        int EXPLORATION_DEPTH = 4;
        Alphabet<String> inputs = Alphabets.fromArray(PathTracker.inputSymbols);

        MealyMembershipOracle<String, String> sul = new RersOracle();
        MealyCounterOracle<String, String> mCounter = new MealyCounterOracle<String, String>(sul, "membership queries");
        MealyCacheOracle<String, String> m = MealyCacheOracle.createDAGCacheOracle(inputs, mCounter.asOracle());

        // oracle for counting queries wraps SUL
        // MealyCounterOracle<String, String> mqOracle = new MealyCounterOracle<>(sul,
        // "membership queries");

        // construct L* instance
        // ClassicLStarMealy
        // ClassicLStarMealy<String, String> lstar = new
        // ClassicLStarMealyBuilder<String, String>()
        ExtensibleLStarMealy<String, String> lstar = new ExtensibleLStarMealyBuilder<String, String>()
                .withAlphabet(inputs) // input
                .withOracle(m)
                .create();
        // TTTLearnerMealyBuilder<String, String>
        TTTLearnerMealy<String, String> ttt = new TTTLearnerMealyBuilder<String, String>()
                .withAlphabet(inputs)
                .withOracle(m)
                .create();

        MealyLearner<String, String> learner = ttt;
        // // alphabet
        // .withOracle(sul) // membership oracle
        // .create();

        // construct a W-method conformance test exploring the system up to depth
        // EXPLORATION_DEPTH from every state of a hypothesis
        MealyWMethodEQOracle<String, String> wMethod = new MealyWMethodEQOracle<String, String>(m, EXPLORATION_DEPTH);

        int MAX_LOOP_DEPTH = Settings.getInstance().MAX_LOOP_DETECTION_DEPTH;
        MealyLoopingEQOracle<MealyMachine<?, String, ?, String>, String, String> loopMethod = new MealyLoopingEQOracle<>(
                m, EXPLORATION_DEPTH, inputs, MAX_LOOP_DEPTH);

        GraphSavingTransparentEQOracle<MealyMachine<?, String, ?, String>, String, String> stats = new GraphSavingTransparentEQOracle<MealyMachine<?, String, ?, String>, String, String>(
                mCounter);

        // Combine the loopMethod with the wMethod
        EQOracleChain<MealyMachine<?, String, ?, String>, String, Word<String>> chain = new EQOracleChain<>(
                stats,
                wMethod,
                loopMethod,
                stats);

        // construct a learning experiment from
        // the learning algorithm and the conformance test.
        // The experiment will execute the main loop of
        // active learning
        // DFAExperiment<Character> experiment = new DFAExperiment<>(lstar, wMethod,
        // inputs);
        // de.learnlib.util.Experiment.MealyExperiment.MealyExperiment<String,
        // String>(LearningAlgorithm<? extends MealyMachine<?, String, ?, String>,
        // String, Word<String>> learningAlgorithm, EquivalenceOracle<? super
        // MealyMachine<?, String, ?, String>, String, Word<String>>
        // equivalenceAlgorithm, Alphabet<String> inputs)

        // MealyExperiment<String, String> experiment = new MealyExperiment<String,
        // String>(lstar, wMethod, inputs);
        MealyExperiment<String, String> experiment = new MealyExperiment<String, String>(learner, chain, inputs);

        // turn on time profiling
        experiment.setProfile(true);

        // enable logging of models
        experiment.setLogModels(true);

        // run experiment
        experiment.run();
        System.out.println("Done running");

        // get learned model
        MealyMachine<?, String, ?, String> result = experiment.getFinalHypothesis();

        // report results
        System.out.println("-------------------------------------------------------");

        // profiling
        System.out.println(SimpleProfiler.getResults());

        // learning statistics
        System.out.println(experiment.getRounds().getSummary());
        System.out.println(mCounter.getStatisticalData().getSummary());

        // model statistics
        System.out.println("States: " + result.size());
        System.out.println("Sigma: " + inputs.size());

        // show model
        System.out.println();
        System.out.println("Model: ");
        GraphDOT.write(result, inputs, new BufferedWriter(new FileWriter("final.dot"))); // may throw IOException!

        // Visualization.visualize(result, inputs, new MealyVisualizationHelper<String,
        // String>());
        // Visualization.vis

        System.out.println("-------------------------------------------------------");

        System.out.println("Final observation table:");
        new ObservationTableASCIIWriter<>().write(lstar.getObservationTable(), System.out);

        // OTUtils.displayHTMLInBrowser(lstar.getObservationTable());
        // OTUtils.displayHTMLInBrowser(lstar.getHypothesisModel());
        System.exit(0);
    }

}
