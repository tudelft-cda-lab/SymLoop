package nl.tudelft.instrumentation.patching.selectors;

import nl.tudelft.instrumentation.patching.Candidate;
import nl.tudelft.instrumentation.patching.GeneticAlgorithmSelector;

import java.util.ArrayList;
import java.util.List;

public class RouletteSelector extends GeneticAlgorithmSelector {

    @Override
    public List<Candidate> select(List<Candidate> population, int populationSize) {
        double[] scores = new double[population.size()];
        double totalScore = 0.0f;
        double minFit = Double.MAX_VALUE;
        int nonZero = 0;
        double maxFit = 0;
        for (int i = 0; i < population.size(); i++) {
            Candidate candidate = population.get(i);
            double fit = candidate.score.orElseThrow(() -> new AssertionError("Not Possible"));
            minFit = Math.min(minFit, fit);
            maxFit = Math.max(maxFit, fit);
            scores[i] = fit;
            if (fit > 0.0) {
                nonZero++;
            }
            totalScore += fit;
        }
        double avgNonZero = (totalScore / nonZero) * 0.999;
        totalScore = 0;
        for (int i = 0; i < scores.length; i++) {
            scores[i] = Math.max(0, scores[i] - avgNonZero);
            totalScore += scores[i];
        }
        List<Candidate> selected = new ArrayList<>(population.size());
        for (int i = 0; i < populationSize; i++) {
            int index = randomCandidateIndex(scores, totalScore);
            selected.add(population.get(index).deepCopy());
        }
        return selected;
    }

    public int randomCandidateIndex(double[] scores, double totalScore) {
        double rand = random.nextDouble() * totalScore;
        for (int i = 0; i < scores.length; i++) {
            rand -= scores[i];
            if (rand <= 0) {
                return i;
            }
        }
        return random.nextInt(scores.length);
    }
}
