package com.tracer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in the business flow graph
 */
public class FlowNode {

    private String id;
    private String label;
    private String className;
    private String methodName;
    private List<Integer> executedLines = new ArrayList<>();
    private List<String> nextNodeIds = new ArrayList<>();
    private NodeType type;

    public enum NodeType {
        START, CLASS, METHOD, END
    }

    public FlowNode(String id, String label, NodeType type) {
        this.id = id;
        this.label = label;
        this.type = type;
    }

    public void addNextNode(String nodeId) {
        if (!nextNodeIds.contains(nodeId)) {
            nextNodeIds.add(nodeId);
        }
    }

    public void addExecutedLine(int line) {
        if (!executedLines.contains(line)) {
            executedLines.add(line);
        }
    }

    // Getters & Setters
    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public List<Integer> getExecutedLines() { return executedLines; }
    public List<String> getNextNodeIds() { return nextNodeIds; }
    public NodeType getType() { return type; }
}
