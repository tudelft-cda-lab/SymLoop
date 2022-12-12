package nl.tudelft.instrumentation.symbolic;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ErrorTracker {

    private Set<Integer> errors = new HashSet<Integer>();;
    static Pattern pattern = Pattern.compile("Invalid input: error_(\\d+)");

    public ErrorTracker() {
    }

    public boolean add(String line) {
        Integer error = getError(line);
        if (error == null) {
            return false;
        } else {
            return this.errors.add(error);
        }
    }

    public boolean add(int error) {
        if (this.errors.contains(error)) {
            return false;
        } else {
            this.errors.add(error);
            return true;
        }
    }

    public int amount() {
        return this.errors.size();
    }

    public Set<Integer> getSet() {
        return this.errors;
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
}
