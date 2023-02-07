package nl.tudelft.instrumentation.symbolic;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class MealyLoopDetector<S, A extends MealyMachine<?, I, ?, O>, I, O> implements Iterator<LoopInput<I>> {

    protected class LoopDetectionHelper {

        public final I access;
        public final S history;

        public final LoopDetectionHelper previous;

        public LoopDetectionHelper(I access, S history, LoopDetectionHelper previous) {
            this.access = access;
            this.history = history;
            this.previous = previous;
        }

        public S last() {
            return history;
        }

        public LoopDetectionHelper add(I sym, S s) {
            return new LoopDetectionHelper(sym, s, this);
        }

        public Word<I> getAccess() {
            Word<I> a = Word.epsilon();
            LoopDetectionHelper current = this;
            while (current.previous != null) {
                a = a.prepend(current.access);
                current = current.previous;
            }
            return a;
        }

        public Optional<LoopInput<I>> isLoop() {
            S last = last();
            LoopDetectionHelper current = this.previous;
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

    private MealyMachine<S, I, ?, O> m;
    private Alphabet<I> alphabet;
    private Map<S, Word<I>> access;
    private List<LoopDetectionHelper> reached;

    public MealyLoopDetector(MealyMachine<S, I, ?, O> m, Alphabet<I> alphabet) {
        this.m = m;
        this.alphabet = alphabet;
        access = getAccessSequences(m);
        init();
    }

    protected Map<S, Word<I>> getAccessSequences(MealyMachine<S, I, ?, O> hypothesis) {
        Map<S, Word<I>> access = new HashMap<>();
        Queue<S> q = new LinkedList<>();
        Queue<Word<I>> wq = new LinkedList<>();
        q.add(hypothesis.getInitialState());
        wq.add(Word.epsilon());
        while (!q.isEmpty()) {
            S state = q.poll();
            Word<I> w = wq.poll();
            for (I input : this.alphabet) {
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

    private void init() {
        reached = new LinkedList<>();
        reached.add(new LoopDetectionHelper(null, m.getInitialState(), null));
    }

    private Iterator<I> al;
    LoopDetectionHelper h = null;

    private LoopInput<I> next = null;

    private LoopInput<I> getNext() {
        while (true) {
            if (h == null || !al.hasNext()) {
                if (reached.size() == 0) {
                    return null;
                }
                h = reached.remove(0);
                al = this.alphabet.iterator();
            }
            S last = h.last();
            while (al.hasNext()) {
                I input = this.al.next();
                LoopDetectionHelper next = h.add(input, m.getSuccessor(last, input));
                Optional<LoopInput<I>> loop = next.isLoop();
                if (loop.isPresent()) {
                    LoopInput<I> li = loop.get();
                    if (access.get(next.history).equals(li.access)) {
                        return li;
                    }
                } else {
                    reached.add(next);
                }
            }
        }
    }

    @Override
    public boolean hasNext() {
        if (next != null) {
            return true;
        } else {
            next = getNext();
        }
        return next != null;
    }

    @Override
    public LoopInput<I> next() {
        if (next != null) {
            LoopInput<I> temp = next;
            next = null;
            return temp;
        }
        return getNext();
    }

}
