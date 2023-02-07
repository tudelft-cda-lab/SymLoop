package nl.tudelft.instrumentation.symbolic;

import net.automatalib.words.Word;

public class LoopInput<I> {
    public final Word<I> access;
    public final Word<I> loop;

    public LoopInput(Word<I> access, Word<I> loop) {
        this.access = access;
        this.loop = loop;
    }
}



