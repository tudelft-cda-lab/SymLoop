package nl.tudelft.instrumentation.symbolic;

import java.util.Collection;
import java.util.List;

import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.query.Query;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;

public class RersOracle implements MealyMembershipOracle<String, String> {

    @Override
    public void processQueries(Collection<? extends Query<String, Word<String>>> arg0) {
        for (Query<String, Word<String>> q : arg0) {
            String[] query = q.getInput().asList().toArray(String[]::new);
            WordBuilder<String> w = new WordBuilder<>();
            List<String> out = PathTracker.getMembershipOutput(query);
            // Only add output for the suffix
            w.addAll(out.subList(q.getPrefix().size(), out.size()));
            q.answer(w.toWord());
        }

    }
}
