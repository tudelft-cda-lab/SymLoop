package nl.tudelft.instrumentation.symbolic;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Stream;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.oracle.equivalence.MealyWMethodEQOracle;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.words.Word;

public class GraphSavingTransparentEQOracle<A extends MealyMachine<?, I, ?, O>, I, O>
        extends MealyWMethodEQOracle<I, O> {

    private final String problem;
    private final String base;
    private int iteration = 0;

    public GraphSavingTransparentEQOracle(MealyMembershipOracle<I, O> sulOracle) {
        super(sulOracle, 0);
        problem = PathTracker.problem.getClass().getName();
        iteration += 1;
        base = String.format("./dots/%s", problem);
        File b = new File(base);
        if (!b.exists()) {
            assert b.mkdirs();
        }
    }

    public String fileName(int nStates) {
        return String.format("%s/hyp-%03d-%d.dot", base, iteration++, nStates);
    }

    @Override
    protected Stream<Word<I>> generateTestWords(MealyMachine<?, I, ?, O> hypothesis, Collection<? extends I> alphabet) {
        String filename = fileName(hypothesis.size());
        try {
            GraphDOT.write(hypothesis, alphabet, new BufferedWriter(new FileWriter(filename)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Stream.empty();
    }

}
