package com.tracer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;

/**
 * Represents coverage data for a single Java class.
 * Also carries the architectural layer it belongs to.
 */
public class ClassCoverage {

    private String    className;        // e.g. com.example.service.UserService
    private String    simpleClassName;  // e.g. UserService
    private String    sourceFile;       // absolute path to .java source file
    private LayerType layerType = LayerType.UNKNOWN;

    private List<MethodCoverage>    methods        = new ArrayList<>();
    private Map<Integer, Boolean>   lineCoverageMap = new TreeMap<>();
    private List<String>            sourceLines    = new ArrayList<>();

    public ClassCoverage(String className) {
        this.className = className;
        int lastDot = className.lastIndexOf('.');
        this.simpleClassName = lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    public void addLineCoverage(int lineNumber, boolean covered) {
        lineCoverageMap.put(lineNumber, covered);
    }

    public void addMethod(MethodCoverage method) {
        methods.add(method);
    }

    public int getCoveredLineCount() {
        return (int) lineCoverageMap.values().stream().filter(v -> v).count();
    }

    public int getTotalLineCount() {
        return lineCoverageMap.size();
    }

    public double getCoveragePercent() {
        if (getTotalLineCount() == 0) return 0;
        return (double) getCoveredLineCount() / getTotalLineCount() * 100;
    }

    // ── Getters & Setters ────────────────────────────────────────────
    public String getClassName()                          { return className; }
    public String getSimpleClassName()                    { return simpleClassName; }
    public String getSourceFile()                         { return sourceFile; }
    public void   setSourceFile(String sourceFile)        { this.sourceFile = sourceFile; }
    public LayerType getLayerType()                       { return layerType; }
    public void   setLayerType(LayerType layerType)       { this.layerType = layerType; }
    public List<MethodCoverage>  getMethods()             { return methods; }
    public Map<Integer, Boolean> getLineCoverageMap()     { return lineCoverageMap; }
    public List<String>          getSourceLines()         { return sourceLines; }
    public void setSourceLines(List<String> sourceLines)  { this.sourceLines = sourceLines; }
}
