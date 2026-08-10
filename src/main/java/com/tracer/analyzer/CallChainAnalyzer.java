package com.tracer.analyzer;

import com.tracer.model.CallEdge;
import com.tracer.model.ClassCoverage;

import java.util.*;
import java.util.regex.*;

/**
 * Analyzes Java source lines stored in ClassCoverage objects to extract
 * real method-level call edges between covered classes.
 *
 * Strategy (regex-based, no external library needed):
 *  1. Scan field declarations  -> build fieldName -> SimpleClassName map
 *  2. Track current method name as we scan lines
 *  3. For each EXECUTED source line inside a method:
 *     - Look for  fieldName.methodName(  or  ClassName.methodName(  patterns
 *     - Resolve to a covered class -> emit CallEdge
 */
public class CallChainAnalyzer {

    // Method declaration: captures method name in group 1
    private static final Pattern METHOD_DECL = Pattern.compile(
        "^\\s*(?:(?:public|private|protected|static|final|synchronized|abstract|default|native)\\s+)*"
        + "(?:[\\w<>\\[\\],?\\s]+?)\\s+(\\w+)\\s*\\(");

    // Field declaration: Type fieldName; or Type fieldName =
    private static final Pattern FIELD_DECL = Pattern.compile(
        "^\\s*(?:(?:private|protected|public|static|final|volatile|transient)\\s+)*"
        + "([A-Z]\\w+)(?:<[^>]*>)?\\s+(\\w+)\\s*[;=,)]");

    // Method call: receiver.method(
    private static final Pattern METHOD_CALL = Pattern.compile(
        "(?<![\\w.])([a-zA-Z_$][\\w$]*)\\.(\\w+)\\s*\\(");

    private static final Set<String> NOISE_RECEIVERS = new HashSet<>(Arrays.asList(
        "logger", "log", "system", "string", "integer", "long", "double", "boolean",
        "math", "objects", "optional", "list", "map", "set", "arraylist", "hashmap",
        "collections", "arrays", "stringbuilder", "stringutils", "collectionutils",
        "this", "super", "stream", "collectors", "localdatetime", "localdate",
        "instant", "duration", "thread", "object"
    ));

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
        "if", "else", "for", "while", "do", "switch", "case", "return", "try",
        "catch", "finally", "throw", "throws", "new", "this", "super", "class",
        "interface", "enum", "extends", "implements", "import", "package",
        "void", "int", "long", "double", "float", "boolean", "byte", "char", "short",
        "static", "final", "abstract", "public", "private", "protected", "synchronized"
    ));

    public List<CallEdge> analyze(List<ClassCoverage> coverages) {
        Map<String, ClassCoverage> bySimpleName = new LinkedHashMap<>();
        for (ClassCoverage cc : coverages) {
            bySimpleName.put(cc.getSimpleClassName(), cc);
        }

        Set<CallEdge> edges = new LinkedHashSet<>();
        for (ClassCoverage caller : coverages) {
            if (caller.getSourceLines().isEmpty()) continue;
            try {
                analyzeClass(caller, bySimpleName, edges);
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(edges);
    }

    private void analyzeClass(ClassCoverage caller,
                               Map<String, ClassCoverage> bySimpleName,
                               Set<CallEdge> edges) {
        List<String> lines = caller.getSourceLines();
        Map<Integer, Boolean> covMap = caller.getLineCoverageMap();

        // Step 1: build field map
        Map<String, String> fieldTypes = buildFieldTypeMap(lines, bySimpleName);

        // Step 2: scan line by line
        String currentMethod = "<init>";
        boolean insideMethod = false;
        int braceDepth = 0;
        int classBraceDepth = -1;

        for (int i = 0; i < lines.size(); i++) {
            String raw  = lines.get(i);
            String line = raw.trim();
            int lineNum = i + 1;

            // Detect class opening brace
            if (classBraceDepth < 0) {
                if (line.contains("class ") || line.contains("interface ") || line.contains("enum ")) {
                    int opens  = countChar(line, '{');
                    int closes = countChar(line, '}');
                    if (opens > closes) {
                        classBraceDepth = opens - closes;
                        braceDepth = classBraceDepth;
                        continue;
                    }
                }
                braceDepth += countChar(line, '{') - countChar(line, '}');
                continue;
            }

            // Method declaration?
            String detected = detectMethodName(raw);
            if (detected != null && line.contains("{")) {
                currentMethod = detected;
                insideMethod  = true;
            }

            // Track brace depth
            braceDepth += countChar(line, '{') - countChar(line, '}');
            if (braceDepth <= classBraceDepth) {
                insideMethod = false;
            }

            if (!insideMethod) continue;

            // Only executed lines
            Boolean executed = covMap.get(lineNum);
            if (executed == null || !executed) continue;

            // Step 3: find method calls
            Matcher m = METHOD_CALL.matcher(raw);
            while (m.find()) {
                String receiver    = m.group(1);
                String calledMeth  = m.group(2);

                if (NOISE_RECEIVERS.contains(receiver.toLowerCase())) continue;
                if (JAVA_KEYWORDS.contains(receiver)) continue;
                if (receiver.length() <= 1) continue;

                String targetClass = resolveClass(receiver, fieldTypes, bySimpleName);
                if (targetClass == null) continue;
                if (targetClass.equals(caller.getSimpleClassName())) continue;

                edges.add(new CallEdge(
                    caller.getSimpleClassName(), currentMethod,
                    targetClass, calledMeth));
            }
        }
    }

    private Map<String, String> buildFieldTypeMap(List<String> lines,
                                                   Map<String, ClassCoverage> bySimpleName) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String raw : lines) {
            Matcher m = FIELD_DECL.matcher(raw);
            if (m.find()) {
                String type  = m.group(1);
                String field = m.group(2);
                if (bySimpleName.containsKey(type)) map.put(field, type);
            }
        }
        return map;
    }

    private String detectMethodName(String line) {
        String t = line.trim();
        if (t.startsWith("@") || t.startsWith("//") || t.startsWith("*")) return null;
        if (t.contains("class ") || t.contains("interface ") || t.contains("enum ")) return null;
        if (!t.contains("(")) return null;
        Matcher m = METHOD_DECL.matcher(line);
        if (m.find()) {
            String name = m.group(1);
            if (JAVA_KEYWORDS.contains(name)) return null;
            return name;
        }
        return null;
    }

    private String resolveClass(String receiver,
                                 Map<String, String> fieldTypes,
                                 Map<String, ClassCoverage> bySimpleName) {
        if (bySimpleName.containsKey(receiver)) return receiver;  // static call
        return fieldTypes.get(receiver);                          // field call
    }

    private int countChar(String s, char c) {
        int n = 0;
        for (char ch : s.toCharArray()) if (ch == c) n++;
        return n;
    }
}
