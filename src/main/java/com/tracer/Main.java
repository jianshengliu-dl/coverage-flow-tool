package com.tracer;

import com.tracer.parser.IdeaCoverageParser;
import com.tracer.model.ClassCoverage;
import com.tracer.report.FlowReportGenerator;

import java.io.File;
import java.util.List;

/**
 * Main entry point for Coverage Flow Tool
 * Usage: java -jar coverage-flow-tool.jar <coverage-file.ic> <source-root> <output-dir>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }

        String coverageFile = args[0];
        String sourceRoot   = args[1];
        String outputDir    = args[2];
        String filterPackage = args.length > 3 ? args[3] : "";

        System.out.println("========================================");
        System.out.println("   Coverage Flow Tool v1.0.0");
        System.out.println("========================================");
        System.out.println("Coverage File : " + coverageFile);
        System.out.println("Source Root   : " + sourceRoot);
        System.out.println("Output Dir    : " + outputDir);
        System.out.println("Filter Package: " + (filterPackage.isEmpty() ? "(all)" : filterPackage));
        System.out.println("----------------------------------------");

        // 1. Parse coverage data
        System.out.println("[1/3] Parsing coverage data...");
        IdeaCoverageParser parser = new IdeaCoverageParser(filterPackage);
        List<ClassCoverage> coverages = parser.parse(new File(coverageFile), new File(sourceRoot));
        System.out.println("      Found " + coverages.size() + " covered classes.");

        // 2. Generate report
        System.out.println("[2/3] Generating flow report...");
        FlowReportGenerator generator = new FlowReportGenerator();
        File report = generator.generate(coverages, new File(outputDir));

        // 3. Done
        System.out.println("[3/3] Done!");
        System.out.println("========================================");
        System.out.println("Report generated: " + report.getAbsolutePath());
        System.out.println("Open in browser : file://" + report.getAbsolutePath());
        System.out.println("========================================");
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar coverage-flow-tool.jar <coverage.ic> <source-root> <output-dir> [package-filter]");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar coverage-flow-tool.jar \
            ~/.idea/system/coverage/MyApp.ic \
            /project/src/main/java \
            /tmp/flow-report \
            com.example.service");
    }
}
