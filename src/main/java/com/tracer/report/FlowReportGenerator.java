package com.tracer.report;

import com.tracer.model.ExecutionTrace;
import com.tracer.model.ClassNode;
import com.tracer.analyzer.CallChainAnalyzer;
import com.tracer.analyzer.ClassGraphBuilder;
import com.tracer.analyzer.ExecutionFlowAnalyzer;

import java.io.*;
import java.util.*;

/**
 * Generates a comprehensive flow report from execution trace.
 * Creates HTML files visualizing the business flow.
 */
public class FlowReportGenerator {
    private ExecutionTrace trace;
    private CallChainAnalyzer chainAnalyzer;
    private ClassGraphBuilder graphBuilder;
    private ExecutionFlowAnalyzer flowAnalyzer;
    private File outputDir;

    public FlowReportGenerator() {}

    /**
     * Generate report from execution trace
     */
    public File generate(ExecutionTrace trace, File outputDir) throws IOException {
        this.trace = trace;
        this.outputDir = outputDir;
        outputDir.mkdirs();

        this.chainAnalyzer = new CallChainAnalyzer(trace);
        Map<String, ClassNode> classNodes = chainAnalyzer.analyze();

        this.graphBuilder = new ClassGraphBuilder(trace);
        graphBuilder.build();

        this.flowAnalyzer = new ExecutionFlowAnalyzer(trace);

        generateIndexReport();
        generateFlowDiagramReport();
        generateDetailedReport();
        generateStatisticsReport();
        copyResources();

        return new File(outputDir, "index.html");
    }

    private void generateIndexReport() throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\\n");
        html.append("<html>\\n<head>\\n");
        html.append("<meta charset='UTF-8'>\\n");
        html.append("<title>Coverage Flow Report</title>\\n");
        html.append("<link rel='stylesheet' href='style.css'>\\n");
        html.append("</head>\\n<body>\\n");
        html.append("<div class='container'>\\n");
        html.append("<h1>Coverage Flow Report</h1>\\n");
        html.append("<div class='nav'><a href='flow-diagram.html'>Flow Diagram</a>");
        html.append("<a href='details.html'>Details</a>");
        html.append("<a href='statistics.html'>Statistics</a></div>\\n");
        html.append("<div class='summary'><h2>Execution Summary</h2>\\n<ul>\\n");
        html.append("<li>Entry: <strong>").append(trace.getEntryClass()).append("</strong></li>\\n");
        html.append("<li>Methods: <strong>").append(trace.getTotalMethodCalls()).append("</strong></li>\\n");
        html.append("<li>Classes: <strong>").append(trace.getAllClassesInvolved().size()).append("</strong></li>\\n");
        html.append("<li>Duration: <strong>").append(trace.getDuration()).append("ms</strong></li>\\n");
        html.append("</ul></div></div>\\n</body>\\n</html>\\n");
        writeFile("index.html", html.toString());
    }

    private void generateFlowDiagramReport() throws IOException {
        HtmlFlowRenderer renderer = new HtmlFlowRenderer(graphBuilder);
        String diagramHtml = renderer.render();
        writeFile("flow-diagram.html", diagramHtml);

        GraphVizRenderer graphViz = new GraphVizRenderer(graphBuilder);
        String dotContent = graphViz.render();
        writeFile("flow.dot", dotContent);
    }

    private void generateDetailedReport() throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\\n<html>\\n<head>\\n");
        html.append("<meta charset='UTF-8'>\\n");
        html.append("<title>Execution Details</title>\\n");
        html.append("<link rel='stylesheet' href='style.css'>\\n");
        html.append("</head>\\n<body>\\n<div class='container'>\\n");
        html.append("<h1>Execution Details</h1>\\n");
        html.append("<table class='methods-table'><thead><tr>");
        html.append("<th>#</th><th>Class</th><th>Method</th><th>Duration(ms)</th><th>Depth</th>");
        html.append("</tr></thead><tbody>\\n");

        for (var call : trace.getMethodCalls()) {
            html.append("<tr><td>").append(call.getCallOrder()).append("</td>");
            html.append("<td>").append(call.getClassName()).append("</td>");
            html.append("<td>").append(call.getMethodName()).append("</td>");
            html.append("<td>").append(call.getDuration()).append("</td>");
            html.append("<td>").append(call.getDepth()).append("</td></tr>\\n");
        }

        html.append("</tbody></table></div></body></html>\\n");
        writeFile("details.html", html.toString());
    }

    private void generateStatisticsReport() throws IOException {
        ExecutionFlowAnalyzer.FlowSummary summary = flowAnalyzer.analyze();
        Map<String, Integer> callsByClass = flowAnalyzer.getCallCountByClass();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\\n<html>\\n<head>\\n");
        html.append("<meta charset='UTF-8'>\\n<title>Statistics</title>\\n");
        html.append("<link rel='stylesheet' href='style.css'>\\n</head>\\n<body>\\n");
        html.append("<div class='container'><h1>Statistics</h1>\\n");
        html.append("<p>Total Time: ").append(summary.totalExecutionTime).append("ms</p>\\n");
        html.append("<p>Calls: ").append(summary.totalMethodCalls).append("</p>\\n");
        html.append("<p>Classes: ").append(summary.uniqueClasses).append("</p>\\n");
        html.append("</div></body></html>\\n");
        writeFile("statistics.html", html.toString());
    }

    private void copyResources() throws IOException {
        String css = "body { font-family: Arial; margin: 0; padding: 20px; background: #f5f5f5; }\\n" +
                ".container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; }\\n" +
                "h1 { color: #333; border-bottom: 3px solid #007bff; }\\n" +
                "table { width: 100%; border-collapse: collapse; margin: 20px 0; }\\n" +
                "th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }\\n" +
                "th { background-color: #f9f9f9; font-weight: bold; }\\n";
        writeFile("style.css", css);
    }

    private void writeFile(String filename, String content) throws IOException {
        File file = new File(outputDir, filename);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
