package com.tracer;

import com.tracer.model.ExecutionTrace;
import com.tracer.model.MethodCall;
import com.tracer.parser.TraceDataParser;
import com.tracer.analyzer.CallChainAnalyzer;
import com.tracer.analyzer.ClassGraphBuilder;
import com.tracer.report.FlowReportGenerator;

import java.io.File;
import java.util.List;

/**
 * Main entry point for Coverage Flow Tool v1.2.0
 *
 * Interactive mode (no args):
 *   Guides user step-by-step, supports trace data input.
 *
 * Direct mode:
 *   java -jar coverage-flow-tool.jar <trace-file> <source-root> <out-dir> [pkg]
 */
public class Main {

    public static void main(String[] args) throws Exception {
        printBanner();

        if (args.length == 0) {
            new InteractiveMode().run();
            return;
        }

        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String inputPath     = args[0];
        String sourceRoot    = args.length > 1 ? args[1] : "";
        String outputDir     = args.length > 2 ? args[2] : "flow-report";

        System.out.println("Input       : " + inputPath);
        System.out.println("Source Root : " + sourceRoot);
        System.out.println("Output Dir  : " + outputDir);
        System.out.println("----------------------------------------");

        System.out.println("[1/3] Parsing trace data...");
        TraceDataParser parser = new TraceDataParser();
        ExecutionTrace trace = parser.parseFromFile(new File(inputPath));
        System.out.println("      Found " + trace.getMethodCalls().size() + " method calls.");

        System.out.println("[2/3] Generating flow report...");
        FlowReportGenerator generator = new FlowReportGenerator();
        File report = generator.generate(trace, new File(outputDir));

        System.out.println("[3/3] Done!");
        System.out.println("========================================");
        System.out.println("\u2705 Report: " + report.getAbsolutePath());
        System.out.println("   Open  : file://" + report.getAbsolutePath());
        System.out.println("========================================");
    }

    private static void printBanner() {
        System.out.println("\u2554" + "\u2550".repeat(42) + "\u2557");
        System.out.println("\u2551   Coverage Flow Tool  v1.2.0             \u2551");
        System.out.println("\u2551   Execution Path Tracer                   \u2551");
        System.out.println("\u2551   Generate Business Flow Diagrams        \u2551");
        System.out.println("\u255a" + "\u2550".repeat(42) + "\u255d");
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  Interactive : java -jar coverage-flow-tool.jar");
        System.out.println("  Direct mode : java -jar coverage-flow-tool.jar <trace-file> <source-root> <out-dir>");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar coverage-flow-tool.jar");
        System.out.println("  java -jar coverage-flow-tool.jar /tmp/trace.txt /project/src /tmp/report");
    }
}
