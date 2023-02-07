package nl.tudelft.instrumentation.symbolic;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
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
import net.automatalib.serialization.dot.GraphDOT;
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

        public LoopInput(Word<I> access, Word<I> loop) {
            this.access = access;
            this.loop = loop;
        }
    }

    private HashSet<String> checked = new HashSet<>();

    private class LoopDetectionHelper<S> {

        public final I access;
        public final S history;

        public final LoopDetectionHelper<S> previous;

        public LoopDetectionHelper(I access, S history, LoopDetectionHelper<S> previous) {
            this.access = access;
            this.history = history;
            this.previous = previous;
            // assert this.history.size() > 0;
        }

        public S last() {
            return history;
        }

        public LoopDetectionHelper<S> add(I sym, S s) {
            return new LoopDetectionHelper<S>(sym, s, this);
        }

        public Word<I> getAccess() {
            Word<I> a = Word.epsilon();
            LoopDetectionHelper<S> current = this;
            while (current.previous != null) {
                a = a.prepend(current.access);
                current = current.previous;
            }
            return a;
        }

        public Optional<LoopInput<S>> isLoop() {
            S last = last();
            LoopDetectionHelper<S> current = this.previous;
            Word<I> loop = Word.fromSymbols(access);
            while (current.previous != null) {
                if (current.history.equals(last)) {
                    Word<I> a = current.getAccess();
                    // System.out.printf("IS A LOOP: %s, %s", a, loop);
                    return Optional.of(new LoopInput<>(a, loop));
                }
                loop = loop.prepend(current.access);
                current = current.previous;
            }
            return Optional.empty();
        }
    }

    public MealyLoopingEQOracle(MealyMembershipOracle<I, O> sulOracle, int lookahead, Alphabet<I> a) {
        super(sulOracle, lookahead);
        this.a = a;
    }

    protected <S> Collection<LoopInput<S>> getLoops(MealyMachine<S, I, ?, O> m) {
        Map<S, Word<I>> access = getAccessSequences(m);
        List<LoopDetectionHelper<S>> reached = new LinkedList<>();
        reached.add(new LoopDetectionHelper<S>(null, m.getInitialState(), null));
        List<LoopInput<S>> loops = new ArrayList<>();
        while (!reached.isEmpty()) {
            LoopDetectionHelper<S> h = reached.remove(0);
            S last = h.last();
            for (I input : this.a) {
                LoopDetectionHelper<S> next = h.add(input, m.getSuccessor(last, input));
                Optional<LoopInput<S>> loop = next.isLoop();
                if (loop.isPresent()) {
                    LoopInput<S> li = loop.get();
                    if(access.get(next.history).equals(li.access)) {
                        loops.add(li);
                    }
                } else {
                    reached.add(next);
                }
            }
        }
        return loops;
    }

    protected <S> Map<S, Word<I>> getAccessSequences(MealyMachine<S, I, ?, O> hypothesis) {
        Map<S, Word<I>> access = new HashMap<>();
        Queue<S> q = new LinkedList<>();
        Queue<Word<I>> wq = new LinkedList<>();
        q.add(hypothesis.getInitialState());
        wq.add(Word.epsilon());
        while (!q.isEmpty()) {
            S state = q.poll();
            Word<I> w = wq.poll();
            for(I input : a) {
                S next = hypothesis.getSuccessor(state, input);
                if (!access.containsKey(next)) {
                    Word<I> nw = w.append(input);
                    access.put(next, nw);
                    q.add(next);
                    wq.add(nw);
                }
            }
        }
        assert hypothesis.getStates().size() == access.size();
        return access;
    }

    @Override
    protected Stream<Word<I>> generateTestWords(MealyMachine<?, I, ?, O> hypothesis, Collection<? extends I> arg1) {

        List<Word<I>> characterizingSet = new ArrayList<>();
        CharacterizingSets.findCharacterizingSet(hypothesis, this.a, characterizingSet);
        for (I input : a) {
            Word<I> w = Word.fromLetter(input);
            if (!characterizingSet.contains(w)) {
                characterizingSet.add(w);
            }
        }

        String[][] ds = new String[characterizingSet.size()][];
        System.out.printf("characterizingSet size: %d\n", ds.length);
        for (int i = 0; i < characterizingSet.size(); i++) {
            ds[i] = characterizingSet.get(i).asList().toArray(String[]::new);
            System.out.printf("ds[%d]: %s\n", i, characterizingSet.get(i));
        }

        try {
            GraphDOT.write(hypothesis, this.a, new BufferedWriter(new FileWriter("hyp.dot")));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } // may throw IOException!
        Collection<?> loops = getLoops(hypothesis);
        System.out.printf("Found %d loops\n", loops.size());
        for (MealyLoopingEQOracle<A, I, O>.LoopInput<?> loop : getLoops(hypothesis)) {
            String key = String.format("%s - %s", loop.access, loop.loop);
            if (!checked.add(key)) {
                continue;
            }
            String[] access = loop.access.asList().toArray(String[]::new);

            Object startingState = hypothesis.getState(loop.access);
            // CharacterizingSets.

            String[] l = loop.loop.asList().toArray(String[]::new);
            Word<O> out = hypothesis.computeOutput(Word.fromWords(loop.access, loop.loop));
            if (out.lastSymbol().equals("invalid") || out.lastSymbol().toString().startsWith("error")) {
                continue;
            } else {
                System.out.println(out);
            }
            LoopVerifyResult r = SymbolicExecutionLab.verifyLoop(access, l, ds);
            System.out.println(r.getS());
            if (r.hasCounter()) {
                // Stream.concat(
                ArrayList<Word<I>> counter = new ArrayList<>();
                Word<I> c = (Word<I>) Word.fromSymbols(r.getCounter());
                counter.add(c);
                SymbolicExecutionLab.printfBlue("COUNTER: %s", c);
                return Stream.concat(counter.stream(), super.generateTestWords(hypothesis, arg1));
            }
            // if(r.getS()== LoopVerifyResult.State.NO_LOOP_FOUND) {
            // // assert false: "No loop found";
            // }
        }
        System.out.println("NORMAL W METHOD");
        // System.exit(0);
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
