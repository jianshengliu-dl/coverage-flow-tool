package com.tracer.parser;

import com.tracer.model.ClassCoverage;
import com.tracer.model.MethodCoverage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Parses IDEA .ic coverage files and source files
 * IDEA .ic format is a ZIP containing:
 *   - "contents" file with binary coverage data
 *   - individual class files with hit counts
 */
public class IdeaCoverageParser {

    private final String filterPackage;

    public IdeaCoverageParser(String filterPackage) {
        this.filterPackage = filterPackage == null ? "" : filterPackage;
    }

    public List<ClassCoverage> parse(File coverageFile, File sourceRoot) throws Exception {
        List<ClassCoverage> result = new ArrayList<>();

        if (!coverageFile.exists()) {
            throw new FileNotFoundException("Coverage file not found: " + coverageFile.getAbsolutePath());
        }

        // IDEA .ic files are ZIP archives
        try (ZipFile zip = new ZipFile(coverageFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // Skip non-class entries
                if (entry.isDirectory()) continue;

                // Convert entry name to class name: com/example/Foo -> com.example.Foo
                String className = entryName.replace('/', '.').replace(".class", "");

                // Apply package filter
                if (!filterPackage.isEmpty() && !className.startsWith(filterPackage)) {
                    continue;
                }

                ClassCoverage classCoverage = new ClassCoverage(className);

                // Parse hit counts from entry
                try (InputStream is = zip.getInputStream(entry)) {
                    parseClassHits(is, classCoverage);
                } catch (Exception e) {
                    // Some entries may not be parseable, skip
                    continue;
                }

                if (classCoverage.getCoveredLineCount() == 0) continue;

                // Try to find source file
                String sourcePath = entryName.replace(".class", ".java");
                // Handle inner classes: Foo$Bar -> Foo.java
                if (sourcePath.contains("$")) {
                    sourcePath = sourcePath.substring(0, sourcePath.indexOf('$')) + ".java";
                }
                File sourceFile = new File(sourceRoot, sourcePath);
                if (sourceFile.exists()) {
                    classCoverage.setSourceFile(sourceFile.getAbsolutePath());
                    List<String> lines = Files.readAllLines(sourceFile.toPath());
                    classCoverage.setSourceLines(lines);
                }

                result.add(classCoverage);
            }
        } catch (ZipException e) {
            // Not a zip file, try plain binary format
            parsePlainFormat(coverageFile, sourceRoot, result);
        }

        // Sort by class name
        result.sort(Comparator.comparing(ClassCoverage::getClassName));
        return result;
    }

    /**
     * Parse binary hit data from a class entry in the .ic zip
     * Format: 4-byte int (line count), then for each line: 4-byte int (hit count)
     */
    private void parseClassHits(InputStream is, ClassCoverage classCoverage) throws IOException {
        DataInputStream dis = new DataInputStream(is);
        try {
            int lineCount = dis.readInt();
            if (lineCount <= 0 || lineCount > 100000) return;
            for (int i = 0; i < lineCount; i++) {
                int hits = dis.readInt();
                classCoverage.addLineCoverage(i + 1, hits > 0);
            }
        } catch (EOFException e) {
            // End of stream, partial data is ok
        }
    }

    /**
     * Fallback: try to parse as plain text format exported by IDEA
     */
    private void parsePlainFormat(File coverageFile, File sourceRoot, List<ClassCoverage> result) throws Exception {
        List<String> lines = Files.readAllLines(coverageFile.toPath());
        ClassCoverage current = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.endsWith("{")) {
                // Class declaration line
                String className = line.replace("{", "").trim();
                if (!filterPackage.isEmpty() && !className.startsWith(filterPackage)) {
                    current = null;
                    continue;
                }
                current = new ClassCoverage(className);
                result.add(current);
            } else if (line.contains(":") && current != null) {
                // Line coverage: "42: 1" means line 42 hit 1 time
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    try {
                        int lineNum = Integer.parseInt(parts[0].trim());
                        int hits = Integer.parseInt(parts[1].trim());
                        current.addLineCoverage(lineNum, hits > 0);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Load source files
        for (ClassCoverage cc : result) {
            String sourcePath = cc.getClassName().replace('.', '/') + ".java";
            if (sourcePath.contains("$")) {
                sourcePath = sourcePath.substring(0, sourcePath.indexOf('$')) + ".java";
            }
            File sourceFile = new File(sourceRoot, sourcePath);
            if (sourceFile.exists()) {
                cc.setSourceFile(sourceFile.getAbsolutePath());
                cc.setSourceLines(Files.readAllLines(sourceFile.toPath()));
            }
        }
    }
}
