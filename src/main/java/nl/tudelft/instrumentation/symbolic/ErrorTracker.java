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
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return this.errors.add(Integer.parseInt(matcher.group(1)));
        } else {
            return false;
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
}
