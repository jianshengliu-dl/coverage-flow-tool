package com.tracer;

import com.tracer.model.ClassCoverage;
import com.tracer.parser.IdeaCoverageParser;
import com.tracer.report.FlowReportGenerator;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Interactive CLI mode:
 *  1. Auto-detect OS and scan IDEA system directories for .ic files
 *  2. Let user choose which .ic file to use
 *  3. Auto-detect source root from common project structures
 *  4. Prompt for package filter
 *  5. Generate report and optionally open in browser
 */
public class InteractiveMode {

    private static final Scanner SCANNER = new Scanner(System.in);

    // ─────────────────────────────────────────────
    //  Entry point
    // ─────────────────────────────────────────────
    public void run() throws Exception {
        System.out.println();
        System.out.println("  Welcome to Interactive Mode!");
        System.out.println("  I will guide you step by step.");
        System.out.println();

        // Step 1 – find .ic files
        File icFile = selectIcFile();
        if (icFile == null) {
            System.out.println("[!] No .ic file selected. Exiting.");
            return;
        }

        // Step 2 – source root
        File sourceRoot = selectSourceRoot();
        if (sourceRoot == null) {
            System.out.println("[!] No source root selected. Exiting.");
            return;
        }

        // Step 3 – package filter
        String packageFilter = promptPackageFilter();

        // Step 4 – output directory
        File outputDir = promptOutputDir();

        // Step 5 – generate
        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║  Generating Report...                ║");
        System.out.println("╚═════════���════════════════════════════╝");
        System.out.println("  IC File     : " + icFile.getAbsolutePath());
        System.out.println("  Source Root : " + sourceRoot.getAbsolutePath());
        System.out.println("  Filter Pkg  : " + (packageFilter.isEmpty() ? "(all)" : packageFilter));
        System.out.println("  Output Dir  : " + outputDir.getAbsolutePath());
        System.out.println();

        System.out.println("[1/3] Parsing coverage data...");
        IdeaCoverageParser parser = new IdeaCoverageParser(packageFilter);
        List<ClassCoverage> coverages = parser.parse(icFile, sourceRoot);
        System.out.println("      Found " + coverages.size() + " covered class(es).");

        System.out.println("[2/3] Generating HTML flow report...");
        FlowReportGenerator generator = new FlowReportGenerator();
        File report = generator.generate(coverages, outputDir);

        System.out.println("[3/3] Done!");
        System.out.println();
        System.out.println("  ✅ Report saved to:");
        System.out.println("     " + report.getAbsolutePath());
        System.out.println();

        // Step 6 – open in browser?
        offerOpenInBrowser(report);
    }

    // ─────────────────────────────────────────────
    //  Step 1: Select .ic file
    // ─────────────────────────────────────────────
    private File selectIcFile() {
        System.out.println("[Step 1/4] Scanning for IDEA coverage files (.ic)...");
        List<File> icFiles = scanForIcFiles();

        if (icFiles.isEmpty()) {
            System.out.println("  No .ic files found automatically.");
            System.out.println("  Please enter the full path to your .ic file manually:");
            System.out.print("  > ");
            String path = SCANNER.nextLine().trim();
            File f = new File(path);
            return f.exists() ? f : null;
        }

        System.out.println();
        System.out.println("  Found " + icFiles.size() + " coverage file(s):");
        System.out.println();
        for (int i = 0; i < icFiles.size(); i++) {
            File f = icFiles.get(i);
            System.out.printf("  [%d] %s%n", i + 1, f.getAbsolutePath());
            System.out.printf("      Size: %s  |  Last Modified: %s%n",
                    humanReadableSize(f.length()),
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(f.lastModified())));
            System.out.println();
        }
        System.out.println("  [0] Enter path manually");
        System.out.println();
        System.out.print("  Select [1-" + icFiles.size() + "] or 0 for manual input: ");

        int choice = readIntInput(0, icFiles.size());
        if (choice == 0) {
            System.out.print("  Enter full path to .ic file: ");
            String path = SCANNER.nextLine().trim();
            File f = new File(path);
            return f.exists() ? f : null;
        }
        return icFiles.get(choice - 1);
    }

    // ─────────────────────────────────────────────
    //  Scan OS-specific IDEA directories for .ic files
    // ─────────────────────────────────────────────
    private List<File> scanForIcFiles() {
        List<File> result = new ArrayList<>();
        List<File> searchRoots = getIdeaSystemDirs();

        System.out.println("  Scanning directories:");
        for (File root : searchRoots) {
            System.out.println("    " + root.getAbsolutePath());
            if (root.exists()) {
                scanRecursively(root, ".ic", 5, result);
            }
        }

        // Sort by last modified (newest first)
        result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        // Limit to 20 most recent
        return result.size() > 20 ? result.subList(0, 20) : result;
    }

    /**
     * Returns all candidate IDEA system/coverage directories for current OS
     */
    private List<File> getIdeaSystemDirs() {
        List<File> dirs = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", "");

        if (os.contains("win")) {
            // Windows: %APPDATA%\JetBrains\...
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                File jetBrains = new File(appData, "JetBrains");
                addIdeaProductDirs(jetBrains, dirs);
            }
            // Legacy location
            dirs.add(new File(home, ".IntelliJIdea"));
            dirs.add(new File(home, ".IdeaIC"));
        } else if (os.contains("mac")) {
            // macOS: ~/Library/Application Support/JetBrains/...
            File jetBrains = new File(home, "Library/Application Support/JetBrains");
            addIdeaProductDirs(jetBrains, dirs);
            // Legacy
            dirs.add(new File(home, "Library/Application Support/IntelliJIdea"));
            dirs.add(new File(home, "Library/Caches/IntelliJIdea"));
        } else {
            // Linux: ~/.config/JetBrains/... or ~/.IntelliJIdea.../system/coverage
            File jetBrains = new File(home, ".config/JetBrains");
            addIdeaProductDirs(jetBrains, dirs);
            // Legacy
            dirs.add(new File(home, ".IntelliJIdea"));
            dirs.add(new File(home, ".IdeaIC"));
        }

        // Also add common system/coverage sub-paths directly
        for (String extra : new String[]{
            home + "/.idea",
            home + "/IdeaProjects",
        }) {
            dirs.add(new File(extra));
        }

        return dirs;
    }

    /**
     * Under JetBrains dir, look for IntelliJIdea*, IdeaIC*, idea* subdirs
     */
    private void addIdeaProductDirs(File jetBrainsDir, List<File> dirs) {
        if (!jetBrainsDir.exists() || !jetBrainsDir.isDirectory()) return;
        File[] children = jetBrainsDir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName().toLowerCase();
            if (name.startsWith("intellijidea") || name.startsWith("ideaic")
                    || name.startsWith("idea") || name.startsWith("goland")
                    || name.startsWith("pycharm") || name.startsWith("webstorm")) {
                dirs.add(child);
                dirs.add(new File(child, "system/coverage"));
            }
        }
    }

    /**
     * Recursively scan a directory for files with given extension, up to maxDepth levels
     */
    private void scanRecursively(File dir, String ext, int maxDepth, List<File> result) {
        if (maxDepth <= 0 || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(ext)) {
                result.add(f);
            } else if (f.isDirectory()) {
                scanRecursively(f, ext, maxDepth - 1, result);
            }
        }
    }

    // ─────────────────────────────────────────────
    //  Step 2: Select source root
    // ─────────────────────────────────────────────
    private File selectSourceRoot() {
        System.out.println();
        System.out.println("[Step 2/4] Select your project source root (e.g. src/main/java):");

        List<File> candidates = detectSourceRoots();

        if (!candidates.isEmpty()) {
            System.out.println();
            System.out.println("  Detected candidate source roots:");
            for (int i = 0; i < candidates.size(); i++) {
                System.out.printf("  [%d] %s%n", i + 1, candidates.get(i).getAbsolutePath());
            }
            System.out.println("  [0] Enter path manually");
            System.out.println();
            System.out.print("  Select [1-" + candidates.size() + "] or 0 for manual: ");

            int choice = readIntInput(0, candidates.size());
            if (choice > 0) return candidates.get(choice - 1);
        }

        System.out.print("  Enter source root path: ");
        String path = SCANNER.nextLine().trim();
        File f = new File(path);
        return f.exists() ? f : null;
    }

    /**
     * Detect common Maven/Gradle source root paths relative to current working directory
     */
    private List<File> detectSourceRoots() {
        List<File> result = new ArrayList<>();
        File cwd = new File(System.getProperty("user.dir"));

        String[] candidates = {
            "src/main/java",
            "src/main/kotlin",
            "src",
            "source",
            "app/src/main/java"
        };

        for (String rel : candidates) {
            File f = new File(cwd, rel);
            if (f.exists() && f.isDirectory()) {
                result.add(f);
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────
    //  Step 3: Package filter
    // ─────────────────────────────────────────────
    private String promptPackageFilter() {
        System.out.println();
        System.out.println("[Step 3/4] Package filter (optional):");
        System.out.println("  Only classes under this package will be included in the report.");
        System.out.println("  Leave blank to include ALL classes.");
        System.out.print("  Package prefix (e.g. com.example.service): ");
        return SCANNER.nextLine().trim();
    }

    // ─────────────────────────────────────────────
    //  Step 4: Output directory
    // ─────────────────────────────────────────────
    private File promptOutputDir() {
        String defaultDir = System.getProperty("user.dir") + File.separator + "flow-report";
        System.out.println();
        System.out.println("[Step 4/4] Output directory for the HTML report:");
        System.out.println("  Press ENTER to use default: " + defaultDir);
        System.out.print("  > ");
        String input = SCANNER.nextLine().trim();
        String path = input.isEmpty() ? defaultDir : input;
        File dir = new File(path);
        dir.mkdirs();
        return dir;
    }

    // ─────────────────────────────────────────────
    //  Step 5: Open report in browser
    // ─────────────────────────────────────────────
    private void offerOpenInBrowser(File report) {
        System.out.println("  Open report in browser now? [Y/n]: ");
        System.out.print("  > ");
        String answer = SCANNER.nextLine().trim().toLowerCase();
        if (answer.isEmpty() || answer.startsWith("y")) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(report.toURI());
                    System.out.println("  ✅ Opened in browser!");
                } else {
                    System.out.println("  Cannot auto-open. Please open manually:");
                    System.out.println("  file://" + report.getAbsolutePath());
                }
            } catch (IOException e) {
                System.out.println("  Failed to open browser: " + e.getMessage());
                System.out.println("  Please open manually: file://" + report.getAbsolutePath());
            }
        } else {
            System.out.println("  Report path: file://" + report.getAbsolutePath());
        }
        System.out.println();
        System.out.println("  Thank you for using Coverage Flow Tool! 🎉");
    }

    // ─────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────
    private int readIntInput(int min, int max) {
        while (true) {
            try {
                String line = SCANNER.nextLine().trim();
                int val = Integer.parseInt(line);
                if (val >= min && val <= max) return val;
                System.out.print("  Please enter a number between " + min + " and " + max + ": ");
            } catch (NumberFormatException e) {
                System.out.print("  Invalid input. Please enter a number: ");
            }
        }
    }

    private String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
