package com.tracer.parser;

import com.tracer.model.ClassCoverage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Parses IDEA-generated HTML coverage reports.
 *
 * IDEA exports coverage as a folder like:
 *   index.html
 *   com.example.service/
 *     index.html          <- package summary
 *     UserService.html    <- per-class detail
 *
 * Each per-class HTML contains a <table> where each <tr> has:
 *   - a line number
 *   - a CSS class: "covered" or "uncovered" (or "none" for non-executable)
 */
public class HtmlCoverageParser {

    private final String filterPackage;

    public HtmlCoverageParser(String filterPackage) {
        this.filterPackage = filterPackage == null ? "" : filterPackage;
    }

    /**
     * @param htmlReportDir  root directory of the IDEA HTML coverage report
     * @param sourceRoot     project source root (e.g. src/main/java), may be null
     */
    public List<ClassCoverage> parse(File htmlReportDir, File sourceRoot) throws Exception {
        if (!htmlReportDir.exists() || !htmlReportDir.isDirectory()) {
            throw new FileNotFoundException("HTML report directory not found: " + htmlReportDir.getAbsolutePath());
        }

        List<ClassCoverage> result = new ArrayList<>();

        // Walk all .html files under the report dir
        walkAndParse(htmlReportDir, htmlReportDir, sourceRoot, result);

        // Sort by class name
        result.sort(Comparator.comparing(ClassCoverage::getClassName));
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  Recursive walk
    // ─────────────────────────────────────────────────────────────
    private void walkAndParse(File root, File dir, File sourceRoot, List<ClassCoverage> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                walkAndParse(root, f, sourceRoot, result);
            } else if (f.isFile() && f.getName().endsWith(".html") && !f.getName().equals("index.html")) {
                try {
                    ClassCoverage cc = parseClassHtml(root, f, sourceRoot);
                    if (cc != null && cc.getCoveredLineCount() > 0) {
                        result.add(cc);
                    }
                } catch (Exception e) {
                    // skip unparseable files
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Parse one per-class HTML file
    // ─────────────────────────────────────────────────────────────
    private ClassCoverage parseClassHtml(File root, File htmlFile, File sourceRoot) throws Exception {
        String content = new String(Files.readAllBytes(htmlFile.toPath()));

        // Derive fully-qualified class name from relative path
        // e.g.  <root>/com.example.service/UserService.html
        //    -> com.example.service.UserService
        String relativePath = root.toURI().relativize(htmlFile.toURI()).getPath();
        // relativePath = "com.example.service/UserService.html"
        String className = relativePath
                .replace(".html", "")
                .replace("/", ".")
                .replace("\\", ".");

        // Remove any leading dots
        while (className.startsWith(".")) className = className.substring(1);

        // Apply package filter
        if (!filterPackage.isEmpty() && !className.startsWith(filterPackage)) {
            return null;
        }

        ClassCoverage cc = new ClassCoverage(className);

        // ── Strategy 1: parse <tr> rows with id="L<n>" and class="covered/uncovered" ──
        // IDEA HTML format:  <tr id="L42"><td ...>  or <tr class="covered" id="L42">
        Pattern rowPattern = Pattern.compile(
                "<tr[^>]*(?:id=\"L(\\d+)\"[^>]*class=\"([^\"]*)\"|class=\"([^\"]*?)\"[^>]*id=\"L(\\d+)\")",
                Pattern.CASE_INSENSITIVE);
        Matcher m = rowPattern.matcher(content);
        boolean foundRows = false;
        while (m.find()) {
            String lineStr  = m.group(1) != null ? m.group(1) : m.group(4);
            String cssClass = m.group(2) != null ? m.group(2) : m.group(3);
            if (lineStr == null || cssClass == null) continue;
            try {
                int lineNum = Integer.parseInt(lineStr.trim());
                boolean covered = cssClass.toLowerCase().contains("covered")
                               && !cssClass.toLowerCase().contains("un");
                cc.addLineCoverage(lineNum, covered);
                foundRows = true;
            } catch (NumberFormatException ignored) {}
        }

        // ── Strategy 2: look for <span class="covered">line content</span> ──
        if (!foundRows) {
            Pattern spanPattern = Pattern.compile(
                    "<span\\s+class=\"(covered|uncovered|none)\">(.*?)</span>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher sm = spanPattern.matcher(content);
            int lineNum = 1;
            while (sm.find()) {
                String cls  = sm.group(1).toLowerCase();
                boolean cov = cls.equals("covered");
                boolean exe = !cls.equals("none");
                if (exe) cc.addLineCoverage(lineNum, cov);
                lineNum++;
                foundRows = true;
            }
        }

        // ── Strategy 3: generic — count green/red highlighted lines ──
        if (!foundRows) {
            // Look for background-color style hints
            Pattern bgPattern = Pattern.compile(
                    "background(?:-color)?:\\s*(#[0-9a-fA-F]{3,6}|green|red|lime|#[Cc][Cc][Ff][Ff][Cc][Cc]|#[Ff][Ff][Cc][Cc][Cc][Cc])",
                    Pattern.CASE_INSENSITIVE);
            Matcher bg = bgPattern.matcher(content);
            int lineNum = 1;
            while (bg.find()) {
                String color = bg.group(1).toLowerCase();
                boolean covered = color.contains("green") || color.contains("lime")
                               || color.contains("ccffcc") || color.equals("#cfc");
                cc.addLineCoverage(lineNum, covered);
                lineNum++;
            }
        }

        if (cc.getTotalLineCount() == 0) return null;

        // ── Load source lines ──
        if (sourceRoot != null && sourceRoot.exists()) {
            String sourcePath = className.replace('.', '/') + ".java";
            if (sourcePath.contains("$")) {
                sourcePath = sourcePath.substring(0, sourcePath.indexOf('$')) + ".java";
            }
            File sourceFile = new File(sourceRoot, sourcePath);
            if (sourceFile.exists()) {
                cc.setSourceFile(sourceFile.getAbsolutePath());
                cc.setSourceLines(Files.readAllLines(sourceFile.toPath()));
            }
        }

        return cc;
    }
}
