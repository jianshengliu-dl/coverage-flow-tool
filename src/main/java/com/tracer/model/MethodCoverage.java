package com.tracer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents coverage data for a single method
 */
public class MethodCoverage {

    private String methodName;
    private String descriptor;
    private boolean covered;
    private List<Integer> coveredLines = new ArrayList<>();
    private int startLine;
    private int endLine;

    public MethodCoverage(String methodName, String descriptor) {
        this.methodName = methodName;
        this.descriptor = descriptor;
    }

    public void addCoveredLine(int line) {
        if (!coveredLines.contains(line)) {
            coveredLines.add(line);
        }
    }

    // Getters & Setters
    public String getMethodName() { return methodName; }
    public String getDescriptor() { return descriptor; }
    public boolean isCovered() { return covered; }
    public void setCovered(boolean covered) { this.covered = covered; }
    public List<Integer> getCoveredLines() { return coveredLines; }
    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }
    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }
}
