package com.tracer.report;

import com.tracer.analyzer.ClassGraphBuilder;
import com.tracer.model.ClassNode;

import java.util.*;

/**
 * Renders class flow diagram in Graphviz DOT format.
 */
public class GraphVizRenderer {
    private ClassGraphBuilder graphBuilder;

    public GraphVizRenderer(ClassGraphBuilder graphBuilder) {
        this.graphBuilder = graphBuilder;
    }

    public String render() {
        StringBuilder dot = new StringBuilder();
        
        dot.append("digraph ExecutionFlow {\\n");
        dot.append("rankdir=TB;\\n");
        dot.append("node [shape=box, style=rounded, fillcolor=\\\"#e7f3ff\\\", color=\\\"#0066cc\\\"];\\n");

        for (ClassNode node : graphBuilder.getNodes()) {
            String nodeId = sanitizeNodeId(node.getClassName());
            String label = node.getSimpleClassName() + " (" + node.getInvocationCount() + " calls)";
            dot.append(nodeId).append(" [label=\"").append(label).append("\"];\\n");
        }

        dot.append("\\n");

        Set<String> addedEdges = new HashSet<>();
        for (ClassGraphBuilder.ClassEdge edge : graphBuilder.getEdges()) {
            String fromId = sanitizeNodeId(edge.from.getClassName());
            String toId = sanitizeNodeId(edge.to.getClassName());
            String edgeKey = fromId + "->" + toId;

            if (!addedEdges.contains(edgeKey)) {
                dot.append(fromId).append(" -> ").append(toId).append(";\
");
                addedEdges.add(edgeKey);
            }
        }

        dot.append("}\\n");
        return dot.toString();
    }

    private String sanitizeNodeId(String className) {
        return className.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
