package com.tracer;

import com.tracer.model.ClassCoverage;
import com.tracer.parser.HtmlCoverageParser;
import com.tracer.parser.IdeaCoverageParser;
import com.tracer.report.FlowReportGenerator;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Interactive CLI mode.
 * Supports TWO input modes:
 *   A) IDEA .ic binary coverage file
 *   B) IDEA HTML coverage report directory  <-- NEW (for IDEA versions that skip .ic)
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

        // Step 1 – choose input mode
        InputMode inputMode = selectInputMode();

        List<ClassCoverage> coverages;

        if (inputMode == InputMode.IC_FILE) {
            // ── Mode A: .ic file ──
            File icFile = selectIcFile();
            if (icFile == null) { System.out.println("[!] No .ic file selected. Exiting."); return; }
            File sourceRoot = selectSourceRoot();
            if (sourceRoot == null) { System.out.println("[!] No source root selected. Exiting."); return; }
            String pkg = promptPackageFilter();
            printGeneratingHeader(icFile.getAbsolutePath(), sourceRoot.getAbsolutePath(), pkg);
            IdeaCoverageParser parser = new IdeaCoverageParser(pkg);
            coverages = parser.parse(icFile, sourceRoot);
        } else {
            // ── Mode B: HTML report directory ──
            File htmlDir = selectHtmlReportDir();
            if (htmlDir == null) { System.out.println("[!] No HTML report directory selected. Exiting."); return; }
            File sourceRoot = selectSourceRoot();
            String pkg = promptPackageFilter();
            printGeneratingHeader(htmlDir.getAbsolutePath(), sourceRoot == null ? "(none)" : sourceRoot.getAbsolutePath(), pkg);
            HtmlCoverageParser parser = new HtmlCoverageParser(pkg);
            coverages = parser.parse(htmlDir, sourceRoot);
        }

        System.out.println("      Found " + coverages.size() + " covered class(es).");

        if (coverages.isEmpty()) {
            System.out.println();
            System.out.println("  [!] No covered classes found.");
            System.out.println("      Tips:");
            System.out.println("        - Make sure you selected the correct report directory/file.");
            System.out.println("        - Try leaving the package filter blank.");
            System.out.println("        - For HTML mode: select the ROOT folder of the coverage report (contains index.html).");
            return;
        }

        // Output dir
        File outputDir = promptOutputDir();

        System.out.println("[2/3] Generating HTML flow report...");
        FlowReportGenerator generator = new FlowReportGenerator();
        File report = generator.generate(coverages, outputDir);

        System.out.println("[3/3] Done!");
        System.out.println();
        System.out.println("  \u2705 Report saved to:");
        System.out.println("     " + report.getAbsolutePath());
        System.out.println();

        offerOpenInBrowser(report);
    }

    // ─────────────────────────────────────────────
    //  Step 0: Choose input mode
    // ─────────────────────────────────────────────
    private enum InputMode { IC_FILE, HTML_REPORT }

    private InputMode selectInputMode() {
        System.out.println("[Step 1/5] Select your coverage input type:");
        System.out.println();
        System.out.println("  [1] IDEA .ic binary file");
        System.out.println("      (IntelliJ IDEA older versions, Run with Coverage -> .ic file generated)");
        System.out.println();
        System.out.println("  [2] IDEA HTML coverage report folder  <-- recommended if no .ic file");
        System.out.println("      (Run with Coverage -> right-click Coverage panel -> Generate Coverage Report)");
        System.out.println();
        System.out.print("  Select [1 or 2]: ");
        int choice = readIntInput(1, 2);
        return choice == 1 ? InputMode.IC_FILE : InputMode.HTML_REPORT;
    }

    // ─────────────────────────────────────────────
    //  Select HTML report directory  (Mode B)
    // ─────────────────────────────────────────────
    private File selectHtmlReportDir() {
        System.out.println();
        System.out.println("[Step 2/5] Select the IDEA HTML Coverage Report root directory:");
        System.out.println("  This is the folder that contains index.html.");
        System.out.println("  How to generate it in IDEA:");
        System.out.println("    1. Run your program with Coverage (Shield icon or right-click -> Run with Coverage)");
        System.out.println("    2. In the Coverage panel (right side), click the Export icon");
        System.out.println("       OR: Run -> Generate Coverage Report...");
        System.out.println("    3. Choose an output directory and click Generate");
        System.out.println();

        // Auto-scan for candidate HTML report dirs
        List<File> candidates = scanForHtmlReportDirs();

        if (!candidates.isEmpty()) {
            System.out.println("  Found " + candidates.size() + " candidate HTML report directory(s):");
            System.out.println();
            for (int i = 0; i < candidates.size(); i++) {
                File f = candidates.get(i);
                System.out.printf("  [%d] %s%n", i + 1, f.getAbsolutePath());
                System.out.printf("      Last Modified: %s%n",
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(f.lastModified())));
                System.out.println();
            }
            System.out.println("  [0] Enter path manually");
            System.out.println();
            System.out.print("  Select [1-" + candidates.size() + "] or 0 for manual: ");
            int choice = readIntInput(0, candidates.size());
            if (choice > 0) return candidates.get(choice - 1);
        } else {
            System.out.println("  No HTML report directories found automatically.");
        }

        System.out.print("  Enter full path to HTML report directory: ");
        String path = SCANNER.nextLine().trim();
        File f = new File(path);
        if (!f.exists() || !f.isDirectory()) {
            System.out.println("  [!] Directory not found: " + path);
            return null;
        }
        return f;
    }

    /**
     * Scan common locations where IDEA exports HTML coverage reports.
     */
    private List<File> scanForHtmlReportDirs() {
        List<File> result = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        String cwd  = System.getProperty("user.dir",  "");
        String os   = System.getProperty("os.name",   "").toLowerCase();

        List<String> searchPaths = new ArrayList<>(Arrays.asList(
            cwd,
            cwd + "/coverage-report",
            cwd + "/coverage",
            cwd + "/report",
            cwd + "/reports",
            cwd + "/build/reports/coverage",
            cwd + "/target/site",
            home + "/coverage-report",
            home + "/Desktop"
        ));

        // IDEA default export paths per OS
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                searchPaths.add(appData + "\\JetBrains");
            }
            searchPaths.add(home + "\\IdeaProjects");
        } else if (os.contains("mac")) {
            searchPaths.add(home + "/Library/Application Support/JetBrains");
            searchPaths.add(home + "/IdeaProjects");
        } else {
            searchPaths.add(home + "/.config/JetBrains");
            searchPaths.add(home + "/IdeaProjects");
        }

        for (String path : searchPaths) {
            scanForIndexHtml(new File(path), 4, result);
        }

        // Deduplicate and sort by last modified
        result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        List<File> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (File f : result) {
            if (seen.add(f.getAbsolutePath())) deduped.add(f);
        }
        return deduped.size() > 10 ? deduped.subList(0, 10) : deduped;
    }

    /**
     * Recursively find directories that contain both index.html and at least one *.html sub-file
     * (indicating a valid IDEA coverage HTML report root)
     */
    private void scanForIndexHtml(File dir, int maxDepth, List<File> result) {
        if (maxDepth <= 0 || !dir.exists() || !dir.isDirectory()) return;
        File indexHtml = new File(dir, "index.html");
        if (indexHtml.exists()) {
            // Verify it looks like a coverage report (has sub html files)
            File[] htmlFiles = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".html"));
            File[] subDirs   = dir.listFiles(File::isDirectory);
            boolean hasSubHtml = (subDirs != null) && Arrays.stream(subDirs)
                    .anyMatch(sd -> sd.listFiles(f -> f.isFile() && f.getName().endsWith(".html")) != null
                            && Objects.requireNonNull(sd.listFiles(f -> f.isFile() && f.getName().endsWith(".html"))).length > 0);
            if (hasSubHtml || (htmlFiles != null && htmlFiles.length > 1)) {
                result.add(dir);
                return; // don't recurse into a report dir
            }
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) return;
        for (File child : children) {
            scanForIndexHtml(child, maxDepth - 1, result);
        }
    }

    // ─────────────────────────────────────────────
    //  Select .ic file  (Mode A)
    // ─────────────────────────────────────────────
    private File selectIcFile() {
        System.out.println();
        System.out.println("[Step 2/5] Scanning for IDEA .ic coverage files...");
        List<File> icFiles = scanForIcFiles();

        if (icFiles.isEmpty()) {
            System.out.println("  No .ic files found automatically.");
            System.out.print("  Enter full path to .ic file: ");
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
        System.out.print("  Select [1-" + icFiles.size() + "] or 0 for manual: ");
        int choice = readIntInput(0, icFiles.size());
        if (choice == 0) {
            System.out.print("  Enter full path to .ic file: ");
            String path = SCANNER.nextLine().trim();
            File f = new File(path);
            return f.exists() ? f : null;
        }
        return icFiles.get(choice - 1);
    }

    private List<File> scanForIcFiles() {
        List<File> result = new ArrayList<>();
        for (File root : getIdeaSystemDirs()) {
            if (root.exists()) scanRecursively(root, ".ic", 5, result);
        }
        result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result.size() > 20 ? result.subList(0, 20) : result;
    }

    private List<File> getIdeaSystemDirs() {
        List<File> dirs = new ArrayList<>();
        String os   = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", "");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) addIdeaProductDirs(new File(appData, "JetBrains"), dirs);
            dirs.add(new File(home, ".IntelliJIdea"));
        } else if (os.contains("mac")) {
            addIdeaProductDirs(new File(home, "Library/Application Support/JetBrains"), dirs);
        } else {
            addIdeaProductDirs(new File(home, ".config/JetBrains"), dirs);
            dirs.add(new File(home, ".IntelliJIdea"));
        }
        dirs.add(new File(home, ".idea"));
        return dirs;
    }

    private void addIdeaProductDirs(File jetBrainsDir, List<File> dirs) {
        if (!jetBrainsDir.exists() || !jetBrainsDir.isDirectory()) return;
        File[] children = jetBrainsDir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName().toLowerCase();
            if (name.startsWith("intellijidea") || name.startsWith("ideaic") || name.startsWith("idea")) {
                dirs.add(child);
                dirs.add(new File(child, "system/coverage"));
            }
        }
    }

    private void scanRecursively(File dir, String ext, int maxDepth, List<File> result) {
        if (maxDepth <= 0 || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(ext)) result.add(f);
            else if (f.isDirectory()) scanRecursively(f, ext, maxDepth - 1, result);
        }
    }

    // ─────────────────────────────────────────────
    //  Step 3: Select source root
    // ─────────────────────────────────────────────
    private File selectSourceRoot() {
        System.out.println();
        System.out.println("[Step 3/5] Select your project source root (e.g. src/main/java):");
        System.out.println("  (Press ENTER to skip if source code is unavailable)");

        List<File> candidates = detectSourceRoots();
        if (!candidates.isEmpty()) {
            System.out.println();
            System.out.println("  Detected source roots:");
            for (int i = 0; i < candidates.size(); i++) {
                System.out.printf("  [%d] %s%n", i + 1, candidates.get(i).getAbsolutePath());
            }
            System.out.println("  [0] Enter path manually / skip");
            System.out.println();
            System.out.print("  Select [1-" + candidates.size() + "] or 0: ");
            int choice = readIntInput(0, candidates.size());
            if (choice > 0) return candidates.get(choice - 1);
        }

        System.out.print("  Enter source root path (or press ENTER to skip): ");
        String path = SCANNER.nextLine().trim();
        if (path.isEmpty()) return null;
        File f = new File(path);
        return f.exists() ? f : null;
    }

    private List<File> detectSourceRoots() {
        List<File> result = new ArrayList<>();
        File cwd = new File(System.getProperty("user.dir"));
        for (String rel : new String[]{"src/main/java", "src/main/kotlin", "src", "source", "app/src/main/java"}) {
            File f = new File(cwd, rel);
            if (f.exists() && f.isDirectory()) result.add(f);
        }
        return result;
    }

    // ─────────────────────────────────────────────
    //  Step 4: Package filter
    // ─────────────────────────────────────────────
    private String promptPackageFilter() {
        System.out.println();
        System.out.println("[Step 4/5] Package filter (optional):");
        System.out.println("  Only classes under this package will be included.");
        System.out.println("  Leave blank to include ALL classes.");
        System.out.print("  Package prefix (e.g. com.example.service): ");
        return SCANNER.nextLine().trim();
    }

    // ─────────────────────────────────────────────
    //  Step 5: Output directory
    // ─────────────────────────────────────────────
    private File promptOutputDir() {
        String defaultDir = System.getProperty("user.dir") + File.separator + "flow-report";
        System.out.println();
        System.out.println("[Step 5/5] Output directory for the flow report:");
        System.out.println("  Press ENTER to use default: " + defaultDir);
        System.out.print("  > ");
        String input = SCANNER.nextLine().trim();
        File dir = new File(input.isEmpty() ? defaultDir : input);
        dir.mkdirs();
        return dir;
    }

    // ─────────────────────────────────────────────
    //  Open in browser
    // ─────────────────────────────────────────────
    private void offerOpenInBrowser(File report) {
        System.out.print("  Open report in browser now? [Y/n]: ");
        String answer = SCANNER.nextLine().trim().toLowerCase();
        if (answer.isEmpty() || answer.startsWith("y")) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(report.toURI());
                    System.out.println("  \u2705 Opened in browser!");
                } else {
                    System.out.println("  Please open: file://" + report.getAbsolutePath());
                }
            } catch (IOException e) {
                System.out.println("  Please open: file://" + report.getAbsolutePath());
            }
        } else {
            System.out.println("  Report: file://" + report.getAbsolutePath());
        }
        System.out.println();
        System.out.println("  Thank you for using Coverage Flow Tool! \uD83C\uDF89");
    }

    // ─────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────
    private void printGeneratingHeader(String inputPath, String sourceRoot, String pkg) {
        System.out.println();
        System.out.println("\u2554" + "\u2550".repeat(46) + "\u2557");
        System.out.println("\u2551  Generating Report...                          \u2551");
        System.out.println("\u255a" + "\u2550".repeat(46) + "\u255d");
        System.out.println("  Input      : " + inputPath);
        System.out.println("  Source Root: " + sourceRoot);
        System.out.println("  Filter Pkg : " + (pkg.isEmpty() ? "(all)" : pkg));
        System.out.println();
        System.out.println("[1/3] Parsing coverage data...");
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

    private String humanReadableSize(long bytes) {
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1024*1024)  return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
