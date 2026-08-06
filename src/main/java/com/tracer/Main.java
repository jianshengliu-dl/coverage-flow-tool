package com.tracer;

import com.tracer.parser.IdeaCoverageParser;
import com.tracer.model.ClassCoverage;
import com.tracer.report.FlowReportGenerator;

import java.io.File;
import java.util.List;

/**
 * Main entry point for Coverage Flow Tool
 *
 * Interactive mode (no args): auto-scan IDEA .ic files and guide user step by step
 * Direct mode (with args):    java -jar coverage-flow-tool.jar <coverage.ic> <source-root> <output-dir> [pkg-filter]
 */
public class Main {

    public static void main(String[] args) throws Exception {
        printBanner();

        if (args.length == 0) {
            // ✅ Interactive mode
            InteractiveMode interactive = new InteractiveMode();
            interactive.run();
        } else if (args.length >= 3) {
            // ✅ Direct CLI mode
            runDirect(args);
        } else {
            printUsage();
            System.exit(1);
        }
    }

    private static void runDirect(String[] args) throws Exception {
        String coverageFile  = args[0];
        String sourceRoot    = args[1];
        String outputDir     = args[2];
        String filterPackage = args.length > 3 ? args[3] : "";

        System.out.println("Coverage File : " + coverageFile);
        System.out.println("Source Root   : " + sourceRoot);
        System.out.println("Output Dir    : " + outputDir);
        System.out.println("Filter Package: " + (filterPackage.isEmpty() ? "(all)" : filterPackage));
        System.out.println("----------------------------------------");

        System.out.println("[1/3] Parsing coverage data...");
        IdeaCoverageParser parser = new IdeaCoverageParser(filterPackage);
        List<ClassCoverage> coverages = parser.parse(new File(coverageFile), new File(sourceRoot));
        System.out.println("      Found " + coverages.size() + " covered classes.");

        System.out.println("[2/3] Generating flow report...");
        FlowReportGenerator generator = new FlowReportGenerator();
        File report = generator.generate(coverages, new File(outputDir));

        System.out.println("[3/3] Done!");
        System.out.println("========================================");
        System.out.println("✅ Report: " + report.getAbsolutePath());
        System.out.println("   Open  : file://" + report.getAbsolutePath());
        System.out.println("========================================");
    }

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     Coverage Flow Tool  v1.1.0           ║");
        System.out.println("║     IDEA Code Coverage → Flow Report     ║");
        System.out.println("╚══════════════════════════════════════════╝");
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  Interactive : java -jar coverage-flow-tool.jar");
        System.out.println("  Direct      : java -jar coverage-flow-tool.jar <coverage.ic> <source-root> <output-dir> [pkg]");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar coverage-flow-tool.jar " +
                           "~/.idea/system/coverage/MyApp.ic " +
                           "/project/src/main/java /tmp/report com.example");
    }
}
