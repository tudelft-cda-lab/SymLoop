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

    private MealyMachine<S, I, ?, O> m;
    private Alphabet<I> alphabet;
    private Map<S, Word<I>> access;
    private List<LoopDetectionHelper<I, S>> reached;
    private int maxLoopDepth;

    public MealyLoopDetector(MealyMachine<S, I, ?, O> m, Alphabet<I> alphabet, int maxLoopDepth) {
        this.m = m;
        this.alphabet = alphabet;
        access = getAccessSequences(m);
        this.maxLoopDepth = maxLoopDepth;
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
        reached.add(new LoopDetectionHelper<I, S>(null, m.getInitialState(), null));
    }

    private Iterator<I> al;
    LoopDetectionHelper<I, S> h = null;

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
                LoopDetectionHelper<I, S> next = h.add(input, m.getSuccessor(last, input));
                Optional<LoopInput<I>> loop = next.isLoop();
                if (loop.isPresent()) {
                    LoopInput<I> li = loop.get();
                    if(li.loop.size() > maxLoopDepth) {
                        continue;
                    }
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
