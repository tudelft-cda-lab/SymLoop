package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ErrorTracker {

    private SortedMap<Integer, Long> errors = new TreeMap<>();
    static Pattern pattern = Pattern.compile("Invalid input: error_(\\d+)");

    private List<String> errorTraces = new ArrayList<>();

    public ErrorTracker() {
    }

    public long getTime() {
        long now = System.currentTimeMillis();
        return now - SymbolicExecutionLab.START_TIME;
    }

    public boolean add(String line) {
        Integer error = getError(line);
        if (error == null) {
            return false;
        } else {
            return this.add(error);
        }
    }

    public boolean add(int error) {
        if (errors.containsKey(error)) {
            return false;
        }
        errorTraces.add(SymbolicExecutionLab.processedInput);
        this.errors.put(error, getTime());
        return true;
    }

    public int amount() {
        return this.errors.size();
    }

    public Set<Integer> getSet() {
        return this.errors.keySet();
    }

    public Integer getError(String line) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            int error = Integer.parseInt(matcher.group(1));
            return error;
        }
        return null;
    }

    public boolean isError(String line) {
        Matcher matcher = pattern.matcher(line);
        return matcher.find();
    }

    public String toString() {
        return String.format("ErrorTraces:\n\t%s\nErrorcodes: %s",
                String.join("\n\t", errorTraces),
                String.join(", ", errors.keySet().stream().map(e -> e.toString()).collect(Collectors.toList())));
    }


    public String summary() {
        String output = "";
        for(Entry<Integer, Long> e : this.errors.entrySet()) {
            double seconds = e.getValue().doubleValue() / 1000.0;
            output += String.format("Error %3d: %2.3f\n", e.getKey(), seconds);
        }
        output += String.format("total errors: %d", errors.size());
        return output;
    }
}
