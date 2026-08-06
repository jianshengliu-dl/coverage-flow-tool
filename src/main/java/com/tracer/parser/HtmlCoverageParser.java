package com.tracer.parser;

import com.tracer.model.ClassCoverage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Parses IDEA-generated HTML coverage reports.
 *
 * IDEA export structure:
 *   <report-root>/
 *     index.html
 *     <com.example.handler>/          <- package dir (named after package)
 *       index.html                    <- package summary, lists source-N.html links
 *       sources/
 *         source-1.html              <- actual source with coverage marks  <-- WE PARSE THIS
 *         source-2.html
 *
 * Coverage markers inside source-N.html:
 *   <b class="nc">...</b>  -> NOT covered  (nc = not covered)
 *   <b class="fc">...</b>  -> FULLY covered (fc = fully covered)
 *   &nbsp; prefix, no <b>  -> not executable (comments, blank lines, imports)
 *
 * Class name is derived from the <h1> title in the source-N.html:
 *   "Coverage Summary for Class: CustomsCompHandler (com.psa.tos.gms.customs.handler)"
 *    -> com.psa.tos.gms.customs.handler.CustomsCompHandler
 */
public class HtmlCoverageParser {

    private final String filterPackage;

    // Matches: "Coverage Summary for Class: ClassName (com.example.pkg)"
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "Coverage Summary for Class:\\s*([\\w$]+)\\s*\\(([\\w.]+)\\)",
            Pattern.CASE_INSENSITIVE);

    // Matches <b class="nc"> or <b class="fc"> ... </b>
    private static final Pattern BOLD_PATTERN = Pattern.compile(
            "<b\\s+class=\"(nc|fc)\">(.*?)</b>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Matches each "line block" in the source listing.
    // Each logical source line in IDEA HTML is represented as one line of text
    // that may start with &nbsp; and/or contain <b class="nc/fc">...</b>.
    // We split on newlines and analyse each line.

    public HtmlCoverageParser(String filterPackage) {
        this.filterPackage = filterPackage == null ? "" : filterPackage;
    }

    /**
     * @param htmlReportDir  root directory of the IDEA HTML coverage report (contains index.html)
     * @param sourceRoot     project source root (e.g. src/main/java), may be null
     */
    public List<ClassCoverage> parse(File htmlReportDir, File sourceRoot) throws Exception {
        if (!htmlReportDir.exists() || !htmlReportDir.isDirectory()) {
            throw new FileNotFoundException("HTML report directory not found: " + htmlReportDir.getAbsolutePath());
        }

        List<ClassCoverage> result = new ArrayList<>();
        scanForSourceFiles(htmlReportDir, sourceRoot, result);
        result.sort(Comparator.comparing(ClassCoverage::getClassName));
        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Walk directory tree looking for sources/source-N.html files
    // ─────────────────────────────────────────────────────────────────
    private void scanForSourceFiles(File dir, File sourceRoot, List<ClassCoverage> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                scanForSourceFiles(child, sourceRoot, result);
            } else if (child.isFile() && child.getName().endsWith(".html")
                    && child.getParentFile().getName().equalsIgnoreCase("sources")) {
                // This is a sources/source-N.html file
                try {
                    ClassCoverage cc = parseSourceHtml(child, sourceRoot);
                    if (cc != null && cc.getTotalLineCount() > 0) {
                        // Apply package filter
                        if (!filterPackage.isEmpty() && !cc.getClassName().startsWith(filterPackage)) continue;
                        result.add(cc);
                    }
                } catch (Exception e) {
                    // skip unparseable files silently
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Parse a single sources/source-N.html file
    // ─────────────────────────────────────────────────────────────────
    private ClassCoverage parseSourceHtml(File htmlFile, File sourceRoot) throws Exception {
        String content = new String(Files.readAllBytes(htmlFile.toPath()));

        // ── 1. Derive class name from <h1> title ──────────────────────
        String className = extractClassName(content);
        if (className == null) {
            // Fallback: try to get it from breadcrumb or <title>
            className = extractClassNameFromTitle(content);
        }
        if (className == null) return null;

        ClassCoverage cc = new ClassCoverage(className);

        // ── 2. Find the <pre><code ...> block that holds the source ──
        //    Everything between <code ...> and </code>
        int codeStart = content.indexOf("<code");
        int codeEnd   = content.lastIndexOf("</code>");
        if (codeStart < 0 || codeEnd < 0) return null;
        String codeBlock = content.substring(codeStart, codeEnd);

        // ── 3. Split into lines and analyse each one ──────────────────
        // Each source line in IDEA HTML ends with a real newline \n.
        // A line may look like:
        //   "&nbsp;some import statement"              -> not executable
        //   "<b class=\"nc\">&nbsp;public void foo()"  -> not covered
        //   "<b class=\"fc\">&nbsp;    return x;"       -> covered  (rare in this format)
        //   "&nbsp;<b class=\"nc\">    doSomething();</b>"  -> not covered
        String[] rawLines = codeBlock.split("\n");

        int lineNumber = 0;
        for (String raw : rawLines) {
            // Skip the opening <code ...> tag line itself
            if (raw.contains("<code") || raw.trim().isEmpty()) continue;
            lineNumber++;

            String trimmed = raw.trim();

            // Check for coverage markers
            if (trimmed.contains("class=\"nc\"")) {
                cc.addLineCoverage(lineNumber, false); // not covered
            } else if (trimmed.contains("class=\"fc\"")) {
                cc.addLineCoverage(lineNumber, true);  // fully covered
            }
            // Lines with no <b class=...> are non-executable (imports, blank, comments)
            // We don't record them so they don't skew totals.
        }

        if (cc.getTotalLineCount() == 0) return null;

        // ── 4. Load source lines from source root ─────────────────────
        if (sourceRoot != null && sourceRoot.exists()) {
            String sourcePath = className.replace('.', '/') + ".java";
            // Handle inner classes: Foo$Bar -> Foo.java
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

    // ─────────────────────────────────────────────────────────────────
    //  Extract FQN from <h1>Coverage Summary for Class: Foo (com.example)</h1>
    // ─────────────────────────────────────────────────────────────────
    private String extractClassName(String content) {
        Matcher m = TITLE_PATTERN.matcher(content);
        if (m.find()) {
            String simpleName = m.group(1).trim();
            String packageName = m.group(2).trim();
            return packageName + "." + simpleName;
        }
        return null;
    }

    // Fallback: parse <title>Coverage Report > ClassName</title>
    // combined with breadcrumb package name
    private String extractClassNameFromTitle(String content) {
        // <title>Coverage Report > ClassName</title>
        Pattern titleTag = Pattern.compile("<title>[^>]*>\\s*([\\w$]+)\\s*</title>", Pattern.CASE_INSENSITIVE);
        Matcher tm = titleTag.matcher(content);
        String simpleName = tm.find() ? tm.group(1).trim() : null;
        if (simpleName == null) return null;

        // breadcrumb: <a href="../index.html">com.example.pkg</a>
        Pattern breadcrumb = Pattern.compile(
                "<a\\s+href=\"\\.\\./index\\.html\">([\\w.]+)</a>",
                Pattern.CASE_INSENSITIVE);
        Matcher bm = breadcrumb.matcher(content);
        String pkg = bm.find() ? bm.group(1).trim() : null;

        return pkg != null ? pkg + "." + simpleName : simpleName;
    }
}
