package com.tracer.report;

import com.tracer.analyzer.ClassGraphBuilder;
import com.tracer.model.ClassNode;

import java.util.*;

/**
 * Renders class flow diagram as interactive HTML.
 */
public class HtmlFlowRenderer {
    private ClassGraphBuilder graphBuilder;

    public HtmlFlowRenderer(ClassGraphBuilder graphBuilder) {
        this.graphBuilder = graphBuilder;
    }

    public String render() {
        Collection<ClassNode> nodes = graphBuilder.getNodes();
        List<ClassGraphBuilder.ClassEdge> edges = graphBuilder.getEdges();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\\n<html>\\n<head>\\n");
        html.append("<meta charset='UTF-8'>\\n");
        html.append("<title>Flow Diagram</title>\\n");
        html.append("<link rel='stylesheet' href='style.css'>\\n");
        html.append("<style>\\n");
        html.append(".flow-diagram { border: 1px solid #ccc; margin: 20px 0; }\\n");
        html.append(".node { cursor: pointer; }\\n");
        html.append("</style>\\n</head>\\n<body>\\n<div class='container'>\\n");
        html.append("<h1>Execution Flow Diagram</h1>\\n");
        html.append("<svg width='1200' height='800' class='flow-diagram'>\\n");

        int nodeIndex = 0;
        int cols = (int) Math.ceil(Math.sqrt(nodes.size()));
        for (ClassNode node : nodes) {
            int row = nodeIndex / cols;
            int col = nodeIndex % cols;
            int x = col * 250 + 50;
            int y = row * 150 + 50;

            html.append("<rect x='").append(x).append("' y='").append(y);
            html.append("' width='200' height='80' fill='#e7f3ff' stroke='#0066cc' stroke-width='2'/>\\n");
            html.append("<text x='").append(x + 100).append("' y='").append(y + 30);
            html.append("' text-anchor='middle' font-weight='bold'>").append(node.getSimpleClassName());
            html.append("</text>\\n");
            nodeIndex++;
        }

        html.append("</svg>\\n");
        html.append("<div class='legend'><h3>Legend</h3>");
        html.append("<p>Blue boxes represent classes in the execution flow</p></div>\\n");
        html.append("</div></body></html>\\n");
        
        return html.toString();
    }
}
