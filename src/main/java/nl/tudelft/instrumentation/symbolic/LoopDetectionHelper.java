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

public class LoopDetectionHelper<I, S> {
    public final I access;
    public final S history;

    public final LoopDetectionHelper<I, S> previous;

    public LoopDetectionHelper(I access, S history, LoopDetectionHelper<I, S> previous) {
        this.access = access;
        this.history = history;
        this.previous = previous;
    }

    public S last() {
        return history;
    }

    public LoopDetectionHelper<I, S> add(I sym, S s) {
        return new LoopDetectionHelper<>(sym, s, this);
    }

    public Word<I> getAccess() {
        Word<I> a = Word.epsilon();
        LoopDetectionHelper<I, S> current = this;
        while (current.previous != null) {
            a = a.prepend(current.access);
            current = current.previous;
        }
        return a;
    }

    public Optional<LoopInput<I>> isLoop() {
        S last = last();
        LoopDetectionHelper<I, S> current = this.previous;
        Word<I> loop = Word.fromSymbols(access);
        while (current != null) {
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
