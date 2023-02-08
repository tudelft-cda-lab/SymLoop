package nl.tudelft.instrumentation.symbolic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;

import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class MealyBFS<S, A extends MealyMachine<?, I, ?, O>, I, O> {

    // private MealyMachine<S, I, ?, O> m;
    // private Alphabet<I> alphabet;
    // private Map<S, Word<I>> access;
    private Queue<LoopDetectionHelper<I, S>> reached;

    private final MealyMachine<S, I, ?, O> m;
    private final Alphabet<I> alphabet;
    private final int depth;

    public MealyBFS(MealyMachine<S, I, ?, O> m, Alphabet<I> alphabet, int depth) {
        this.m = m;
        this.alphabet = alphabet;
        reached = new LinkedList<>();
        this.depth = depth;
    }

    public Collection<LoopInput<I>> getTransitions(S state) {
        reached.clear();
        LoopDetectionHelper<I, S> h = new LoopDetectionHelper<I, S>(null, state, null);
        reached.add(h);

        List<LoopInput<I>> paths = new ArrayList<LoopInput<I>>();

        while (!reached.isEmpty()) {
            h = reached.poll();
            // System.out.printf("Access: %s\n", h.getAccess());

            if (h.getAccess().length() >= depth) {
                // System.out.printf("BREAK Access: %s\n", h.getAccess());
                break;
            }
            for (I input : alphabet) {
                S nextState = m.getSuccessor(h.history, input);
                LoopDetectionHelper<I, S> next = h.add(input, nextState);
                Optional<LoopInput<I>> loop = next.isLoop();
                if (loop.isPresent()) {
                    if (h.history.equals(state)) {
                        // System.out.printf("Loop found for access: %s, loop: %s\n", loop.get().access, loop.get().loop);
                        paths.add(loop.get());
                    }
                } else {
                    reached.add(next);
                }
            }
        }
        paths.addAll(reached.stream().map(l -> new LoopInput<I>(l.getAccess(), Word.epsilon()))
                .collect(Collectors.toList()));
        return paths;
    }

}
