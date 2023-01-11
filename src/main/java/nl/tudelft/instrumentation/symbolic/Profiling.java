package nl.tudelft.instrumentation.symbolic;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import nl.tudelft.instrumentation.symbolic.OptimizingSolver.DataPoint;

public class Profiling {
    private BufferedWriter writer;

    public Profiling() {
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("solvertimes.csv")));
            writer.write("TYPE\tNS\tTRACE_LEN\tLOOPS\n");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void saveSolverTimes() {
        if (writer != null) {
            try {
                for (DataPoint d : OptimizingSolver.solverTimes) {
                    writer.write(
                            String.format("%c\t%d\t%d\t%d\n", d.type.c, d.timeInNs, d.traceLength, d.numberOfLoops));
                }
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            OptimizingSolver.solverTimes.clear();
        }
    }
}
