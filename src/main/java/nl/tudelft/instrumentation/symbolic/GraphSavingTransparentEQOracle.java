package nl.tudelft.instrumentation.symbolic;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.filter.statistic.oracle.MealyCounterOracle;
import de.learnlib.oracle.equivalence.MealyWMethodEQOracle;
import de.learnlib.util.statistics.SimpleProfiler;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.words.Word;

public class GraphSavingTransparentEQOracle<A extends MealyMachine<?, I, ?, O>, I, O>
        extends MealyWMethodEQOracle<I, O> {

    private final String problem;
    private final String base;
    private int iteration = 0;
    private MealyCounterOracle<I, O> sul;

    private List<String> csvData = new ArrayList<>();
    long startTime;

    public GraphSavingTransparentEQOracle(MealyCounterOracle<I, O> sulOracle) {
        super(sulOracle, 0);
        this.sul = sulOracle;
        problem = PathTracker.problem.getClass().getName();
        iteration += 1;
        base = String.format("./dots/%s", problem);
        File b = new File(base);
        if (!b.exists()) {
            assert b.mkdirs();
        }
        csvData.add("iteration,time,states,errors,total_queries,mem_queries,sym_queries");
        startTime = System.currentTimeMillis();
    }

    public String fileName(int nStates) {
        return String.format("%s/hyp-%03d-%d.dot", base, iteration++, nStates);
    }

    public void writeCSV() {
        File csvOutputFile = new File(String.format("%s/data-w%d-d%d.csv", base, Settings.getInstance().W, Settings.getInstance().MAX_LOOP_DETECTION_DEPTH));
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            csvData.stream().forEach(pw::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert csvOutputFile.exists();
    }

    @Override
    protected Stream<Word<I>> generateTestWords(MealyMachine<?, I, ?, O> hypothesis, Collection<? extends I> alphabet) {

        long elapsed = System.currentTimeMillis() - startTime;
        int states = hypothesis.size();
        long errors = SymbolicExecutionLab.errorTracker.amount();
        long mem_queries = sul.getStatisticalData().getCount();
        long sym_queries = PathTracker.symbolicQueries.getCount();
        long total_queries = mem_queries + sym_queries;
        System.out.println("States: " + states);
        SimpleProfiler.logResults();
        System.out.println(SimpleProfiler.getResults());
        long[] line = { iteration, elapsed, states, errors, total_queries, mem_queries, sym_queries };
        csvData.add(String.join(",", Arrays.stream(line).mapToObj(Long::toString).toArray(String[]::new)));
        writeCSV();

        String filename = fileName(states);
        try {
            GraphDOT.write(hypothesis, alphabet, new BufferedWriter(new FileWriter(filename)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Stream.empty();
    }

}
