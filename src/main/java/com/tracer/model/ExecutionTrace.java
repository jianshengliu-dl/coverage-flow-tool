package com.tracer.model;

import java.util.*;

/**
 * Represents a complete execution trace from start to end.
 * Contains all method calls in execution order.
 */
public class ExecutionTrace {
    private List<MethodCall> methodCalls;  // All method calls in order
    private long startTime;
    private long endTime;
    private String entryClass;             // First class that was called
    private String entryMethod;            // First method that was called
    private Set<String> allClassesInvolved; // Unique classes in this trace

    public ExecutionTrace() {
        this.methodCalls = new ArrayList<>();
        this.allClassesInvolved = new LinkedHashSet<>();
    }

    public void addMethodCall(MethodCall call) {
        methodCalls.add(call);
        allClassesInvolved.add(call.getClassName());
    }

    public List<MethodCall> getMethodCalls() {
        return methodCalls;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getEntryClass() {
        return entryClass;
    }

    public void setEntryClass(String entryClass) {
        this.entryClass = entryClass;
    }

    public String getEntryMethod() {
        return entryMethod;
    }

    public void setEntryMethod(String entryMethod) {
        this.entryMethod = entryMethod;
    }

    public Set<String> getAllClassesInvolved() {
        return allClassesInvolved;
    }

    public long getDuration() {
        return endTime - startTime;
    }

    public int getTotalMethodCalls() {
        return methodCalls.size();
    }

    @Override
    public String toString() {
        return "ExecutionTrace{" +
                "totalCalls=" + methodCalls.size() +
                ", duration=" + getDuration() + "ms" +
                ", uniqueClasses=" + allClassesInvolved.size() +
                '}';
    }
}
