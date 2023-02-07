package nl.tudelft.instrumentation.symbolic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Stream;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.oracle.equivalence.AbstractTestWordEQOracle;
import de.learnlib.oracle.equivalence.MealyWMethodEQOracle;
import net.automatalib.automata.concepts.Output;
import net.automatalib.automata.concepts.SuffixOutput;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.util.automata.equivalence.CharacterizingSets;
import net.automatalib.util.tries.SuffixTrieNode;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

// public class MealyLoopingEQOracle<A extends Output<I, D>, I, D> implements EquivalenceOracle<A, I, D> {

// AbstractTestWordEQOracle<A extends Output<I, D>, I, D>

public class MealyLoopingEQOracle<A extends MealyMachine<?, I, ?, O>, I, O> extends MealyWMethodEQOracle<I, O> {

    private Alphabet<I> a;

    private class LoopInput<S> {
        public final Word<I> access;
        public final Word<I> loop;
        public final List<S> states;

        public LoopInput(Word<I> access, Word<I> loop, List<S> states) {
            this.access = access;
            this.loop = loop;
            this.states = states;
        }
    }

    private class LoopDetectionHelper<S> {

        public final Word<I> access;
        public final List<S> history;

        public LoopDetectionHelper(Word<I> access, List<S> history) {
            this.access = access;
            this.history = history;
            assert this.history.size() > 0;
        }

        public S last() {
            return history.get(history.size() - 1);
        }

        public LoopDetectionHelper<S> add(I sym, S s) {
            List<S> newHistory = new ArrayList<>(history);
            newHistory.add(s);
            return new LoopDetectionHelper<S>(access.append(sym), newHistory);
        }

        public Optional<LoopInput<S>> isLoop() {
            Object last = last();
            for (int i = history.size() - 2; i >= 0; i--) {
                if (history.get(i).equals(last)) {
                    Word<I> access = this.access.prefix(i);
                    Word<I> loop = this.access.suffix(this.access.size() - access.size());
                    System.out.printf("access: %s\nloop: %s\n", access, loop);
                    List<S> states = history.subList(i, history.size() - 1);
                    return Optional.of(new LoopInput<>(access, loop, states));
                }
            }
            return Optional.empty();
        }
    }

    public MealyLoopingEQOracle(MealyMembershipOracle<I, O> sulOracle, int lookahead, Alphabet<I> a) {
        super(sulOracle, lookahead);
        this.a = a;
    }

    protected <S> Collection<LoopInput<S>> getLoops(MealyMachine<S, I, ?, O> m) {
        List<LoopDetectionHelper<S>> reached = new ArrayList<>();
        List<S> initial = new ArrayList<>();
        initial.add(m.getInitialState());
        reached.add(new LoopDetectionHelper<>(Word.epsilon(), initial));
        List<LoopInput<S>> loops = new ArrayList<>();
        while (!reached.isEmpty()) {
            LoopDetectionHelper<S> h = reached.remove(0);
            S last = h.last();
            // m.getSuccessor
            for (I input : this.a) {
                LoopDetectionHelper<S> next = h.add(input, m.getSuccessor(last, input));
                Optional<LoopInput<S>> loop = next.isLoop();
                if (loop.isPresent()) {
                    loops.add(loop.get());
                } else {
                    reached.add(next);
                }
            }
        }
        return loops;
    }

    @Override
    protected Stream<Word<I>> generateTestWords(MealyMachine<?, I, ?, O> hypothesis, Collection<? extends I> arg1) {

        final Iterator<Word<I>> characterizingSet = CharacterizingSets.characterizingSetIterator(hypothesis, this.a);
        // TODO Auto-generated method stub
        // arg1.
        // hypothesis.g
        List<Entry<Object, Word<I>>> states = new ArrayList<>();
        getLoops(hypothesis);
        // hypothesis.getTransitios

        // for (C s : hypothesis.getStates()) {
        // // s
        // }
        return super.generateTestWords(hypothesis, arg1);
    }

    // @Override
    // protected Stream<Word<I>> generateTestWords(A hypothesis, Collection<?
    // extends I> arg1) {
    // // TODO Auto-generated method stub
    // return null;
    // }

}

// de.learnlib.util.Experiment.MealyExperiment.MealyExperiment<String,String>(
// LearningAlgorithm<? extends MealyMachine<?, String, ?, String>, String,
// Word<String>> learningAlgorithm,
// EquivalenceOracle<?super
// MealyMachine<?,String,?,String>,String,Word<String>>equivalenceAlgorithm,
// Alphabet<String> inputs
// )
