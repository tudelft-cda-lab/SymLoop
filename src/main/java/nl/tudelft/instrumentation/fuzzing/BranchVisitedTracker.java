package nl.tudelft.instrumentation.fuzzing;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BranchVisitedTracker {
    private Map<Integer, VisitedEnum> visited;

    public BranchVisitedTracker() {
        visited = new HashMap<Integer, VisitedEnum>();
    }

    public boolean hasVisited(int line) {
        return visited.containsKey(line);
    }

    public boolean hasVisited(int line, boolean value) {
        return visited.getOrDefault(line, VisitedEnum.NONE).hasVisited(value);
    }

    public boolean hasVisitedBoth(int line) {
        return visited.getOrDefault(line, VisitedEnum.NONE).equals(VisitedEnum.BOTH);
    }

    public void visit(int line, boolean value) {
        visited.put(line, visited.getOrDefault(line, VisitedEnum.NONE).andVisit(value));
    }

    public int totalBranches() {
        return visited.size() * 2;
    }

    public int numVisited() {
        int n = 0;
        for (Integer line : visited.keySet()) {
            n += visited.get(line).amount();
        }
        return n;
    }

    public Set<Integer> lines() {
        return visited.keySet();
    }

    public void clear() {
        visited.clear();
    }
}
