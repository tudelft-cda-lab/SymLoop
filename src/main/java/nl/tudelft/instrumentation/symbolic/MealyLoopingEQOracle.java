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
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

    private HashSet<String> checked = new HashSet<>();

    private int lookahead;

    private int maxLoopDepth;

    public MealyLoopingEQOracle(MealyMembershipOracle<I, O> sulOracle, int lookahead, Alphabet<I> a, int maxLoopDepth) {
        super(sulOracle, lookahead);
        this.a = a;
        this.lookahead = lookahead;
        this.maxLoopDepth = maxLoopDepth;
    }

    protected <S> Stream<LoopInput<I>> getLoops(MealyMachine<S, I, ?, O> m) {
        MealyLoopDetector<S, MealyMachine<S, I, ?, O>, I, O> detector = new MealyLoopDetector<S, MealyMachine<S, I, ?, O>, I, O>(
                m, a, maxLoopDepth);
        Iterator<LoopInput<I>> i = detector;
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(i, 0), false);
    }

    protected <S> Stream<LoopInput<I>> getTransitions(Word<I> access, MealyMachine<S, I, ?, O> m) {
        MealyBFS<S, MealyMachine<?, I, ?, O>, I, O> bfs = new MealyBFS<>(m, a, lookahead);
        Collection<LoopInput<I>> ls = bfs.getTransitions(m.getState(access));
        System.out.printf("FOR ACCESS: %s, DS: %s\n", access, ls.size());
        return ls.stream();
    }

    @Override
    protected Stream<Word<I>> generateTestWords(MealyMachine<?, I, ?, O> hypothesis, Collection<? extends I> arg1) {

        List<Word<I>> characterizingSet = new ArrayList<>();
        // CharacterizingSets.findCharacterizingSet(hypothesis, this.a,
        // characterizingSet);
        for (I input : a) {
            Word<I> w = Word.fromLetter(input);
            if (!characterizingSet.contains(w)) {
                characterizingSet.add(w);
            }
        }

        // String[][] ds = new String[characterizingSet.size()][];
        // System.out.printf("characterizingSet size: %d\n", ds.length);
        // // characterizingSet.stream().map(x -> x.as
        // for (int i = 0; i < characterizingSet.size(); i++) {
        // ds[i] = characterizingSet.get(i).asList().toArray(String[]::new);
        // System.out.printf("ds[%d]: %s\n", i, characterizingSet.get(i));
        // }

        try {
            GraphDOT.write(hypothesis, this.a, new BufferedWriter(new FileWriter("hyp.dot")));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Stream<Word<I>> stream = getLoops(hypothesis)
                .filter(loop -> checked.add(String.format("%s - %s", loop.access, loop.loop)))
                .filter(loop -> {
                    Word<O> out = hypothesis.computeOutput(Word.fromWords(loop.access, loop.loop));
                    return !out.lastSymbol().equals("invalid") && !out.lastSymbol().toString().startsWith("error");
                })
                .map(loop -> {
                    String[] access = loop.access.asList().toArray(String[]::new);
                    String[] l = loop.loop.asList().toArray(String[]::new);

                    Stream<List<String>> ds = getTransitions(loop.access, hypothesis)
                            .map(dt -> (List<String>) Word.fromWords(dt.access, dt.loop).asList());
                    LoopVerifyResult r = SymbolicExecutionLab.verifyLoop(access, l, ds);
                    if (r.getS() == LoopVerifyResult.State.NO_LOOP_FOUND) {
                        // System.out.printf("access: %s, loop: %s\n", loop.access, loop.loop);
                        return Optional.of(Word.fromWords(loop.access, loop.loop, loop.loop));
                    }
                    System.out.println(r.getS());
                    if (r.hasCounter()) {
                        ArrayList<Word<I>> counter = new ArrayList<>();
                        Word<I> c = (Word<I>) Word.fromSymbols(r.getCounter());
                        counter.add(c);
                        SymbolicExecutionLab.printfBlue("COUNTER: %s\n", c);
                        Optional<Word<I>> m = Optional.of(c);
                        return m;
                    }
                    Optional<Word<I>> m = Optional.empty();
                    return m;
                })
                .filter(Optional::isPresent).map(Optional::get);
        return stream;
    }

}
