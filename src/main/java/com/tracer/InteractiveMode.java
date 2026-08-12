package com.tracer;

import com.tracer.model.ExecutionTrace;
import com.tracer.parser.TraceDataParser;
import com.tracer.parser.TraceFileReader;
import com.tracer.report.FlowReportGenerator;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Interactive CLI mode for execution trace analysis.
 */
public class InteractiveMode {

    private static final Scanner SCANNER = new Scanner(System.in);

    public void run() throws Exception {
        System.out.println();
        System.out.println("  Welcome to Interactive Mode!");
        System.out.println("  I will guide you step by step.");
        System.out.println();

        // Step 1 - select trace file
        File traceFile = selectTraceFile();
        if (traceFile == null) {
            System.out.println("[!] No trace file selected. Exiting.");
            return;
        }

        System.out.println();
        System.out.println("[1/3] Parsing trace data...");
        TraceFileReader reader = new TraceFileReader();
        ExecutionTrace trace = reader.readTraceFile(traceFile);
        System.out.println("      Found " + trace.getMethodCalls().size() + " method calls.");

        if (trace.getMethodCalls().isEmpty()) {
            System.out.println("[!] No trace data found.");
            return;
        }

        // Output dir
        File outputDir = promptOutputDir();

        System.out.println("[2/3] Generating HTML flow report...");
        FlowReportGenerator generator = new FlowReportGenerator();
        File report = generator.generate(trace, outputDir);

        System.out.println("[3/3] Done!");
        System.out.println();
        System.out.println("  \u2705 Report saved to:");
        System.out.println("     " + report.getAbsolutePath());
        System.out.println();

        offerOpenInBrowser(report);
    }

    private File selectTraceFile() {
        System.out.println("[Step 1/2] Select your trace file:");
        System.out.println();

        List<File> candidates = scanForTraceFiles();

        if (!candidates.isEmpty()) {
            System.out.println("  Found " + candidates.size() + " trace file(s):");
            System.out.println();
            for (int i = 0; i < candidates.size(); i++) {
                File f = candidates.get(i);
                System.out.printf("  [%d] %s%n", i + 1, f.getAbsolutePath());
            }
            System.out.println("  [0] Enter path manually");
            System.out.println();
            System.out.print("  Select [1-" + candidates.size() + "] or 0 for manual: ");
            int choice = readIntInput(0, candidates.size());
            if (choice > 0) return candidates.get(choice - 1);
        }

        System.out.print("  Enter full path to trace file: ");
        String path = SCANNER.nextLine().trim();
        File f = new File(path);
        return f.exists() ? f : null;
    }

    private List<File> scanForTraceFiles() {
        List<File> result = new ArrayList<>();
        File cwd = new File(System.getProperty("user.dir"));
        scanRecursively(cwd, new String[]{".trace", ".txt", ".log"}, 3, result);
        result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result.size() > 10 ? result.subList(0, 10) : result;
    }

    private void scanRecursively(File dir, String[] extensions, int maxDepth, List<File> result) {
        if (maxDepth <= 0 || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile()) {
                for (String ext : extensions) {
                    if (f.getName().endsWith(ext)) {
                        result.add(f);
                    }
                }
            } else if (f.isDirectory()) {
                scanRecursively(f, extensions, maxDepth - 1, result);
            }
        }
    }

    private File promptOutputDir() {
        String defaultDir = System.getProperty("user.dir") + File.separator + "flow-report";
        System.out.println();
        System.out.println("[Step 2/2] Output directory for the flow report:");
        System.out.println("  Press ENTER to use default: " + defaultDir);
        System.out.print("  > ");
        String input = SCANNER.nextLine().trim();
        File dir = new File(input.isEmpty() ? defaultDir : input);
        dir.mkdirs();
        return dir;
    }

    private void offerOpenInBrowser(File report) {
        System.out.print("  Open report in browser now? [Y/n]: ");
        String answer = SCANNER.nextLine().trim().toLowerCase();
        if (answer.isEmpty() || answer.startsWith("y")) {
            try {
                String uri = "file://" + report.getAbsolutePath();
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(uri));
                    System.out.println("  \u2705 Opened in browser!");
                } else {
                    System.out.println("  Please open: " + uri);
                }
            } catch (Exception e) {
                System.out.println("  Please open: file://" + report.getAbsolutePath());
            }
        } else {
            System.out.println("  Report: file://" + report.getAbsolutePath());
        }
        System.out.println();
        System.out.println("  Thank you for using Coverage Flow Tool! \ud83c\udf89");
    }

    private int readIntInput(int min, int max) {
        while (true) {
            try {
                int val = Integer.parseInt(SCANNER.nextLine().trim());
                if (val >= min && val <= max) return val;
                System.out.print("  Please enter " + min + "-" + max + ": ");
            } catch (NumberFormatException e) {
                System.out.print("  Invalid input, enter a number: ");
            }
        }
    }
}
