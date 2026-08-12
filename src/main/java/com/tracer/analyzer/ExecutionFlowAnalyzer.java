package com.tracer.analyzer;

import com.tracer.model.ExecutionTrace;
import com.tracer.model.MethodCall;

import java.util.*;

/**
 * Analyzes the execution flow to extract key business flow information.
 * Identifies critical paths, bottlenecks, and flow patterns.
 */
public class ExecutionFlowAnalyzer {
    private ExecutionTrace trace;
    private List<MethodCall> calls;

    public ExecutionFlowAnalyzer(ExecutionTrace trace) {
        this.trace = trace;
        this.calls = trace.getMethodCalls();
    }

    /**
     * Analyze execution flow and return summary
     */
    public FlowSummary analyze() {
        FlowSummary summary = new FlowSummary();
        summary.totalExecutionTime = trace.getDuration();
        summary.totalMethodCalls = calls.size();
        summary.uniqueClasses = trace.getAllClassesInvolved().size();
        summary.maxDepth = calculateMaxDepth();
        summary.averageDepth = calculateAverageDepth();
        summary.criticalPath = findCriticalPath();
        summary.slowestMethod = findSlowestMethod();
        summary.mostCalledClass = findMostCalledClass();
        return summary;
    }

    /**
     * Find the critical path (longest execution path through the call tree)
     */
    public List<MethodCall> findCriticalPath() {
        List<MethodCall> criticalPath = new ArrayList<>();
        long maxDuration = 0;

        int i = 0;
        while (i < calls.size()) {
            List<MethodCall> path = new ArrayList<>();
            long pathDuration = 0;
            int startDepth = calls.get(i).getDepth();

            while (i < calls.size()) {
                MethodCall call = calls.get(i);
                path.add(call);
                pathDuration += call.getDuration();

                if (i + 1 < calls.size() && calls.get(i + 1).getDepth() <= startDepth) {
                    break;
                }
                i++;
            }
            i++;

            if (pathDuration > maxDuration) {
                maxDuration = pathDuration;
                criticalPath = new ArrayList<>(path);
            }
        }

        return criticalPath;
    }

    /**
     * Find the method with the longest execution time
     */
    public MethodCall findSlowestMethod() {
        MethodCall slowest = null;
        long maxDuration = 0;

        for (MethodCall call : calls) {
            if (call.getDuration() > maxDuration) {
                maxDuration = call.getDuration();
                slowest = call;
            }
        }

        return slowest;
    }

    /**
     * Find the most frequently called class
     */
    public String findMostCalledClass() {
        Map<String, Integer> classCallCount = new HashMap<>();

        for (MethodCall call : calls) {
            String className = call.getClassName();
            classCallCount.put(className, classCallCount.getOrDefault(className, 0) + 1);
        }

        return classCallCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Calculate maximum call stack depth
     */
    private int calculateMaxDepth() {
        return calls.stream()
                .mapToInt(MethodCall::getDepth)
                .max()
                .orElse(0);
    }

    /**
     * Calculate average call stack depth
     */
    private double calculateAverageDepth() {
        return calls.stream()
                .mapToInt(MethodCall::getDepth)
                .average()
                .orElse(0);
    }

    /**
     * Get call count by class
     */
    public Map<String, Integer> getCallCountByClass() {
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Long> counts = new HashMap<>();
        
        for (MethodCall call : calls) {
            String className = call.getClassName();
            counts.put(className, counts.getOrDefault(className, 0L) + 1);
        }
        
        counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> result.put(e.getKey(), e.getValue().intValue()));
        
        return result;
    }

    /**
     * Get execution time breakdown by class
     */
    public Map<String, Long> getExecutionTimeByClass() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (MethodCall call : calls) {
            String className = call.getClassName();
            result.put(className, result.getOrDefault(className, 0L) + call.getDuration());
        }
        return result;
    }

    /**
     * Check if a class is in the critical path
     */
    public boolean isInCriticalPath(String className) {
        List<MethodCall> path = findCriticalPath();
        return path.stream().anyMatch(call -> call.getClassName().equals(className));
    }

    /**
     * Get bottleneck classes (high execution time and frequent calls)
     */
    public List<String> getBottlenecks(double percentile) {
        Map<String, Long> execTime = getExecutionTimeByClass();
        long threshold = (long) (execTime.values().stream().mapToLong(l -> l).average().orElse(0) * percentile);

        return execTime.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .sorted((a, b) -> Long.compare(execTime.get(b), execTime.get(a)))
                .toList();
    }

    public static class FlowSummary {
        public long totalExecutionTime;
        public int totalMethodCalls;
        public int uniqueClasses;
        public int maxDepth;
        public double averageDepth;
        public List<MethodCall> criticalPath;
        public MethodCall slowestMethod;
        public String mostCalledClass;

        @Override
        public String toString() {
            return "FlowSummary{" +
                    "totalTime=" + totalExecutionTime + "ms" +
                    ", calls=" + totalMethodCalls +
                    ", classes=" + uniqueClasses +
                    ", maxDepth=" + maxDepth +
                    ", avgDepth=" + String.format("%.2f", averageDepth) +
                    '}';
        }
    }
}
