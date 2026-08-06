package com.tracer;

import com.tracer.model.ClassCoverage;
import com.tracer.parser.HtmlCoverageParser;
import com.tracer.parser.IdeaCoverageParser;
import com.tracer.report.FlowReportGenerator;

import java.io.File;
import java.util.List;

/**
 * Main entry point for Coverage Flow Tool v1.2.0
 *
 * Interactive mode (no args):
 *   Guides user step-by-step, supports BOTH .ic and HTML report input.
 *
 * Direct mode:
 *   java -jar coverage-flow-tool.jar --ic   <file.ic>    <source-root> <out-dir> [pkg]
 *   java -jar coverage-flow-tool.jar --html <report-dir> <source-root> <out-dir> [pkg]
 *   java -jar coverage-flow-tool.jar        <file.ic>    <source-root> <out-dir> [pkg]  (legacy)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        printBanner();

        if (args.length == 0) {
            new InteractiveMode().run();
            return;
        }

        // Parse mode flag
        boolean htmlMode = false;
        String[] rest = args;
        if (args[0].equalsIgnoreCase("--html")) {
            htmlMode = true;
            rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        } else if (args[0].equalsIgnoreCase("--ic")) {
            rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        }

        if (rest.length < 2) {
            printUsage();
            System.exit(1);
        }

        String inputPath     = rest[0];
        String sourceRoot    = rest.length > 1 ? rest[1] : "";
        String outputDir     = rest.length > 2 ? rest[2] : "flow-report";
        String filterPackage = rest.length > 3 ? rest[3] : "";

        System.out.println("Mode        : " + (htmlMode ? "HTML Report" : ".ic File"));
        System.out.println("Input       : " + inputPath);
        System.out.println("Source Root : " + sourceRoot);
        System.out.println("Output Dir  : " + outputDir);
        System.out.println("Filter Pkg  : " + (filterPackage.isEmpty() ? "(all)" : filterPackage));
        System.out.println("----------------------------------------");

        System.out.println("[1/3] Parsing coverage data...");
        List<ClassCoverage> coverages;
        if (htmlMode) {
            HtmlCoverageParser parser = new HtmlCoverageParser(filterPackage);
            File srcRoot = sourceRoot.isEmpty() ? null : new File(sourceRoot);
            coverages = parser.parse(new File(inputPath), srcRoot);
        } else {
            IdeaCoverageParser parser = new IdeaCoverageParser(filterPackage);
            coverages = parser.parse(new File(inputPath), new File(sourceRoot));
        }
        System.out.println("      Found " + coverages.size() + " covered classes.");

        System.out.println("[2/3] Generating flow report...");
        FlowReportGenerator generator = new FlowReportGenerator();
        File report = generator.generate(coverages, new File(outputDir));

        System.out.println("[3/3] Done!");
        System.out.println("========================================");
        System.out.println("\u2705 Report: " + report.getAbsolutePath());
        System.out.println("   Open  : file://" + report.getAbsolutePath());
        System.out.println("========================================");
    }

    private static void printBanner() {
        System.out.println("\u2554" + "\u2550".repeat(42) + "\u2557");
        System.out.println("\u2551   Coverage Flow Tool  v1.2.0             \u2551");
        System.out.println("\u2551   IDEA Coverage \u2192 Business Flow Report   \u2551");
        System.out.println("\u2551   Supports: .ic file / HTML report        \u2551");
        System.out.println("\u255a" + "\u2550".repeat(42) + "\u255d");
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  Interactive : java -jar coverage-flow-tool.jar");
        System.out.println("  HTML mode   : java -jar coverage-flow-tool.jar --html <report-dir> <source-root> <out-dir> [pkg]");
        System.out.println("  IC mode     : java -jar coverage-flow-tool.jar --ic   <file.ic>    <source-root> <out-dir> [pkg]");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar coverage-flow-tool.jar --html /tmp/idea-coverage-html /project/src/main/java /tmp/report com.example");
        System.out.println("  java -jar coverage-flow-tool.jar --ic   ~/.idea/.../MyApp.ic     /project/src/main/java /tmp/report");
    }
}
