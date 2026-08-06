package com.tracer.report;

import com.tracer.classifier.LayerClassifier;
import com.tracer.model.ClassCoverage;
import com.tracer.model.LayerType;
import com.tracer.model.MethodCoverage;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates an HTML report with:
 *  1. Layered business-flow diagram  (Controller → Service → Repository …)
 *  2. Per-class line coverage with source code
 *  3. Summary statistics
 */
public class FlowReportGenerator {

    private static final LayerClassifier CLASSIFIER = new LayerClassifier();

    // Canonical layer rendering order
    private static final List<LayerType> LAYER_ORDER = List.of(
            LayerType.CONTROLLER,
            LayerType.SERVICE,
            LayerType.REPOSITORY,
            LayerType.COMPONENT,
            LayerType.ENTITY,
            LayerType.UTIL,
            LayerType.UNKNOWN
    );

    // ────────────────────────────────────────────────────────────────
    public File generate(List<ClassCoverage> coverages, File outputDir) throws Exception {
        outputDir.mkdirs();
        File reportFile = new File(outputDir, "flow-report.html");
        CLASSIFIER.classifyAll(coverages);
        Files.writeString(reportFile.toPath(), buildHtml(coverages));
        return reportFile;
    }

    // ────────────────────────────────────────────────────────────────
    private String buildHtml(List<ClassCoverage> coverages) {
        StringBuilder sb  = new StringBuilder();
        String timestamp  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        int totalClasses  = coverages.size();
        int coveredLines  = coverages.stream().mapToInt(ClassCoverage::getCoveredLineCount).sum();
        int totalLines    = coverages.stream().mapToInt(ClassCoverage::getTotalLineCount).sum();
        double pct        = totalLines == 0 ? 0 : coveredLines * 100.0 / totalLines;

        // ── head ──
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
          .append("<title>Coverage Flow Report</title>\n")
          .append("<script src=\"https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js\"></script>\n")
          .append("<style>\n").append(buildCss()).append("</style>\n")
          .append("</head>\n<body>\n");

        // ── header ──
        sb.append("<div class=\"header\">\n")
          .append("  <h1>&#128269; Coverage Flow Report</h1>\n")
          .append("  <p>Generated: ").append(timestamp).append("</p>\n")
          .append("</div>\n");

        // ── summary cards ──
        sb.append("<div class=\"summary\">\n")
          .append(summaryCard("Classes Covered", String.valueOf(totalClasses), "card-blue"))
          .append(summaryCard("Lines Executed",  coveredLines + " / " + totalLines, "card-green"))
          .append(summaryCard("Coverage Rate",   String.format("%.1f%%", pct),
                              pct >= 80 ? "card-green" : pct >= 50 ? "card-yellow" : "card-red"))
          .append("</div>\n");

        // ── layer legend ──
        sb.append(buildLayerLegend(coverages));

        // ── layered flow diagram ──
        sb.append("<div class=\"section\">\n")
          .append("<h2>&#128336; Business Flow Diagram  "
                + "<small style='font-size:0.6em;color:#666'>"
                + "Controller &#8594; Service &#8594; Repository</small></h2>\n")
          .append("<div class=\"mermaid\">\n")
          .append(buildLayeredMermaid(coverages))
          .append("\n</div>\n</div>\n");

        // ── class navigation ──
        sb.append("<div class=\"section\">\n"
                + "<h2>&#128196; Class Coverage Detail</h2>\n"
                + "<div class=\"nav-list\">\n");
        for (ClassCoverage cc : coverages) {
            String anchor = cc.getClassName().replace('.', '-');
            sb.append("<a href=\"#").append(anchor).append("\">")
              .append("<span class=\"layer-dot\" style=\"background:").append(cc.getLayerType().borderColor).append("\"></span>")
              .append(cc.getSimpleClassName())
              .append(" <span class=\"badge\">").append(String.format("%.0f%%", cc.getCoveragePercent())).append("</span>")
              .append("</a>\n");
        }
        sb.append("</div>\n</div>\n");

        // ── per-class detail ──
        for (ClassCoverage cc : coverages) sb.append(buildClassSection(cc));

        // ── scripts ──
        sb.append("<script>\n")
          .append("mermaid.initialize({startOnLoad:true,theme:'base',"
                + "themeVariables:{primaryColor:'#E3F2FD'},"
                + "flowchart:{useMaxWidth:true,htmlLabels:true}});\n")
          .append("function toggleSource(id){")
          .append("var e=document.getElementById(id);"
                + "e.style.display=e.style.display==='none'?'block':'none';}\n")
          .append("</script>\n</body>\n</html>");

        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    //  LAYERED MERMAID DIAGRAM
    // ════════════════════════════════════════════════════════════════
    private String buildLayeredMermaid(List<ClassCoverage> coverages) {
        // Group classes by layer
        Map<LayerType, List<ClassCoverage>> byLayer = new LinkedHashMap<>();
        for (LayerType lt : LAYER_ORDER) byLayer.put(lt, new ArrayList<>());
        for (ClassCoverage cc : coverages) byLayer.get(cc.getLayerType()).add(cc);

        StringBuilder m = new StringBuilder();
        m.append("flowchart TD\n");

        // START node
        m.append("    START([\"▶ Execution Start\"])\n");

        // Track previous layer's representative node IDs for cross-layer arrows
        List<String> prevLayerNodeIds = new ArrayList<>();
        prevLayerNodeIds.add("START");

        boolean firstLayer = true;
        for (LayerType lt : LAYER_ORDER) {
            List<ClassCoverage> classes = byLayer.get(lt);
            if (classes.isEmpty()) continue;

            String subgraphId = lt.name();
            // Mermaid subgraph for each layer
            m.append("\n    subgraph ").append(subgraphId)
             .append("[\"\"]")     // label set via style
             .append("\n");
            m.append("        direction TB\n");

            List<String> currentLayerFirstNodes = new ArrayList<>();

            for (int i = 0; i < classes.size(); i++) {
                ClassCoverage cc = classes.get(i);
                String nodeId    = lt.name() + "_" + i;
                String shape     = buildNodeShape(nodeId, cc);
                m.append("        ").append(shape).append("\n");

                // Horizontal chain inside layer (left → right)
                if (i > 0) {
                    m.append("        ").append(lt.name() + "_" + (i - 1))
                     .append(" --> ").append(nodeId).append("\n");
                } else {
                    currentLayerFirstNodes.add(nodeId);
                }

                // Method sub-nodes (dashed)
                buildMethodNodes(m, cc, nodeId);
            }

            m.append("    end\n");

            // Cross-layer arrows: every node in previous layer → first node of this layer
            for (String prev : prevLayerNodeIds) {
                for (String cur : currentLayerFirstNodes) {
                    m.append("    ").append(prev).append(" --> ").append(cur).append("\n");
                }
            }

            // The last node of this layer becomes the previous layer connection point
            prevLayerNodeIds.clear();
            prevLayerNodeIds.add(lt.name() + "_" + (classes.size() - 1));
            firstLayer = false;
        }

        // END node
        m.append("\n    END([\"■ Execution End\"])\n");
        for (String prev : prevLayerNodeIds) {
            m.append("    ").append(prev).append(" --> END\n");
        }

        // ── Styles ──
        m.append("\n");
        m.append("    style START fill:#4CAF50,color:#fff,stroke:#388E3C\n");
        m.append("    style END   fill:#F44336,color:#fff,stroke:#D32F2F\n");

        for (LayerType lt : LAYER_ORDER) {
            List<ClassCoverage> classes = byLayer.get(lt);
            for (int i = 0; i < classes.size(); i++) {
                String nodeId = lt.name() + "_" + i;
                m.append("    style ").append(nodeId)
                 .append(" fill:").append(lt.bgColor)
                 .append(",stroke:").append(lt.borderColor)
                 .append(",color:#111\n");
            }
        }

        return m.toString();
    }

    /** Builds a Mermaid node with shape depending on coverage % */
    private String buildNodeShape(String nodeId, ClassCoverage cc) {
        String icon  = cc.getLayerType().label.split(" ")[0]; // emoji
        String label = icon + " " + cc.getSimpleClassName() + "\\n"
                     + String.format("%.0f%% (%d lines)", cc.getCoveragePercent(), cc.getCoveredLineCount());
        if (cc.getCoveragePercent() >= 80)  return nodeId + "[\"" + label + "\"]";
        if (cc.getCoveragePercent() >= 50)  return nodeId + "(\"" + label + "\")";
        return nodeId + "{\"" + label + "\"}";
    }

    /** Adds up to 5 dashed method sub-nodes under a class node */
    private void buildMethodNodes(StringBuilder m, ClassCoverage cc, String nodeId) {
        if (cc.getMethods().isEmpty()) return;
        int count = 0;
        for (MethodCoverage mc : cc.getMethods()) {
            if (!mc.isCovered() || count >= 5) continue;
            String methodId = nodeId + "_M" + count;
            m.append("        ").append(methodId)
             .append("[\"🔧 ").append(mc.getMethodName()).append("()\"]\n");
            m.append("        ").append(nodeId).append(" -.-> ").append(methodId).append("\n");
            count++;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  LAYER LEGEND
    // ════════════════════════════════════════════════════════════════
    private String buildLayerLegend(List<ClassCoverage> coverages) {
        // Count classes per layer
        Map<LayerType, Long> counts = coverages.stream()
                .collect(Collectors.groupingBy(ClassCoverage::getLayerType, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"section legend-section\">\n")
          .append("<h2>&#127881; Layer Summary</h2>\n")
          .append("<div class=\"legend\">\n");

        for (LayerType lt : LAYER_ORDER) {
            long cnt = counts.getOrDefault(lt, 0L);
            if (cnt == 0) continue;
            sb.append("<div class=\"legend-item\" style=\"border-left:4px solid ")
              .append(lt.borderColor).append(";background:").append(lt.bgColor).append("\">\n")
              .append("  <span class=\"legend-icon\">").append(lt.label).append("</span>\n")
              .append("  <span class=\"legend-count\">").append(cnt).append(" class").append(cnt > 1 ? "es" : "").append("</span>\n")
              .append("</div>\n");
        }
        sb.append("</div>\n</div>\n");
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    //  PER-CLASS DETAIL SECTION
    // ════════════════════════════════════════════════════════════════
    private String buildClassSection(ClassCoverage cc) {
        StringBuilder sb  = new StringBuilder();
        String anchor     = cc.getClassName().replace('.', '-');
        String sourceId   = "src-" + anchor;
        LayerType lt      = cc.getLayerType();

        sb.append("<div class=\"class-section\" id=\"").append(anchor)
          .append("\" style=\"border-left:4px solid ").append(lt.borderColor).append("\">\n");

        // header
        sb.append("<div class=\"class-header\">\n")
          .append("  <span class=\"layer-tag\" style=\"background:").append(lt.bgColor)
          .append(";border:1px solid ").append(lt.borderColor).append(";\">")
          .append(lt.label).append("</span>\n")
          .append("  <h3>").append(cc.getSimpleClassName()).append("</h3>\n")
          .append("  <span class=\"class-name\">").append(cc.getClassName()).append("</span>\n")
          .append("  <div class=\"coverage-bar-wrap\">\n")
          .append("    <div class=\"coverage-bar\" style=\"width:").append(String.format("%.1f", cc.getCoveragePercent())).append("%;"
                + "background:linear-gradient(90deg,").append(lt.borderColor).append(",").append(lt.bgColor).append(")\"></div>\n")
          .append("  </div>\n")
          .append("  <span class=\"pct\">").append(String.format("%.1f%%", cc.getCoveragePercent()))
          .append(" (").append(cc.getCoveredLineCount()).append("/").append(cc.getTotalLineCount()).append(" lines)</span>\n")
          .append("</div>\n");

        // source code toggle
        if (!cc.getSourceLines().isEmpty()) {
            sb.append("<button class=\"toggle-btn\" style=\"background:").append(lt.borderColor)
              .append("\" onclick=\"toggleSource('").append(sourceId).append("')\">&#128065; Toggle Source</button>\n");
            sb.append("<div id=\"").append(sourceId).append("\" class=\"source-view\">\n")
              .append("<table class=\"source-table\">\n");

            List<String>          srcLines = cc.getSourceLines();
            Map<Integer, Boolean> lineMap  = cc.getLineCoverageMap();
            for (int i = 0; i < srcLines.size(); i++) {
                int     lineNum   = i + 1;
                Boolean covered   = lineMap.get(lineNum);
                String  rowClass  = covered == null ? "" : (covered ? "line-covered" : "line-missed");
                String  indicator = covered == null ? "" : (covered ? "&#10003;" : "&#10007;");
                sb.append("<tr class=\"").append(rowClass).append("\">")
                  .append("<td class=\"line-num\">").append(lineNum).append("</td>")
                  .append("<td class=\"line-indicator\">").append(indicator).append("</td>")
                  .append("<td class=\"line-code\"><pre>").append(escapeHtml(srcLines.get(i))).append("</pre></td>")
                  .append("</tr>\n");
            }
            sb.append("</table>\n</div>\n");
        } else {
            sb.append("<p class=\"no-source\">Source file not found.</p>\n");
        }

        // executed lines
        sb.append("<div class=\"executed-lines\">\n")
          .append("<strong>Executed Lines:</strong> ");
        cc.getLineCoverageMap().entrySet().stream()
          .filter(Map.Entry::getValue)
          .forEach(e -> sb.append("<span class=\"line-badge\">").append(e.getKey()).append("</span>"));
        sb.append("\n</div>\n</div>\n");

        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    //  CSS
    // ════════════════════════════════════════════════════════════════
    private String buildCss() {
        return "* { box-sizing: border-box; margin: 0; padding: 0; }\n"
             + "body { font-family: 'Segoe UI', Arial, sans-serif; background: #f5f5f5; color: #333; }\n"
             + ".header { background: linear-gradient(135deg,#1a237e,#283593); color:#fff; padding:30px 40px; }\n"
             + ".header h1 { font-size:2em; margin-bottom:8px; }\n"
             + ".summary { display:flex; gap:20px; padding:24px 40px; flex-wrap:wrap; }\n"
             + ".card { flex:1; min-width:180px; border-radius:12px; padding:20px; color:#fff; box-shadow:0 4px 12px rgba(0,0,0,.15); }\n"
             + ".card-title { font-size:.9em; opacity:.85; margin-bottom:8px; }\n"
             + ".card-value { font-size:2em; font-weight:bold; }\n"
             + ".card-blue   { background:linear-gradient(135deg,#1976D2,#42A5F5); }\n"
             + ".card-green  { background:linear-gradient(135deg,#388E3C,#66BB6A); }\n"
             + ".card-yellow { background:linear-gradient(135deg,#F57F17,#FFCA28); }\n"
             + ".card-red    { background:linear-gradient(135deg,#C62828,#EF5350); }\n"
             + ".section { background:#fff; margin:0 40px 24px; border-radius:12px; padding:24px; box-shadow:0 2px 8px rgba(0,0,0,.08); }\n"
             + ".section h2 { font-size:1.3em; margin-bottom:16px; color:#1a237e; border-bottom:2px solid #e8eaf6; padding-bottom:8px; }\n"
             + "/* Layer Legend */\n"
             + ".legend { display:flex; flex-wrap:wrap; gap:12px; }\n"
             + ".legend-item { display:flex; align-items:center; gap:10px; padding:10px 16px; border-radius:8px; min-width:160px; }\n"
             + ".legend-icon { font-size:1em; font-weight:600; }\n"
             + ".legend-count { font-size:.85em; color:#555; }\n"
             + "/* Nav */\n"
             + ".nav-list { display:flex; flex-wrap:wrap; gap:10px; }\n"
             + ".nav-list a { text-decoration:none; background:#e8eaf6; color:#1a237e; padding:6px 14px; border-radius:20px; font-size:.9em; transition:background .2s; display:flex; align-items:center; gap:6px; }\n"
             + ".nav-list a:hover { background:#c5cae9; }\n"
             + ".layer-dot { display:inline-block; width:10px; height:10px; border-radius:50%; }\n"
             + ".badge { background:#1a237e; color:#fff; border-radius:10px; padding:2px 8px; font-size:.8em; }\n"
             + "/* Class section */\n"
             + ".class-section { background:#fff; margin:0 40px 20px; border-radius:12px; padding:24px; box-shadow:0 2px 8px rgba(0,0,0,.08); }\n"
             + ".class-header { margin-bottom:16px; }\n"
             + ".layer-tag { display:inline-block; padding:3px 10px; border-radius:12px; font-size:.8em; margin-bottom:8px; }\n"
             + ".class-header h3 { font-size:1.2em; color:#1a237e; }\n"
             + ".class-name { font-size:.8em; color:#666; font-family:monospace; }\n"
             + ".coverage-bar-wrap { background:#eee; border-radius:6px; height:10px; margin:8px 0; }\n"
             + ".coverage-bar { height:10px; border-radius:6px; transition:width .5s; }\n"
             + ".pct { font-size:.9em; color:#555; }\n"
             + ".toggle-btn { color:#fff; border:none; padding:8px 16px; border-radius:6px; cursor:pointer; margin-bottom:12px; font-size:.9em; }\n"
             + ".toggle-btn:hover { filter:brightness(1.15); }\n"
             + ".source-view { display:none; overflow-x:auto; border:1px solid #e0e0e0; border-radius:8px; }\n"
             + ".source-table { width:100%; border-collapse:collapse; font-family:monospace; font-size:.85em; }\n"
             + ".source-table tr { border-bottom:1px solid #f0f0f0; }\n"
             + ".line-covered { background:#E8F5E9; }\n"
             + ".line-missed  { background:#FFEBEE; }\n"
             + ".line-num { color:#999; text-align:right; padding:2px 10px; min-width:45px; user-select:none; border-right:1px solid #eee; }\n"
             + ".line-indicator { text-align:center; width:20px; font-size:.8em; }\n"
             + ".line-covered .line-indicator { color:#43A047; }\n"
             + ".line-missed  .line-indicator { color:#E53935; }\n"
             + ".line-code pre { padding:2px 10px; white-space:pre; }\n"
             + ".executed-lines { margin-top:12px; font-size:.85em; }\n"
             + ".line-badge { display:inline-block; background:#e8f5e9; color:#2e7d32; border:1px solid #a5d6a7; border-radius:4px; padding:1px 7px; margin:2px; font-size:.85em; font-family:monospace; }\n"
             + ".no-source { color:#999; font-style:italic; padding:10px 0; }\n"
             + ".mermaid { overflow-x:auto; padding:10px; }\n"
             + "@media(max-width:768px){.summary{padding:16px}.section,.class-section{margin:0 16px 16px}}\n";
    }

    // ────────────────────────────────────────────────────────────────
    private String summaryCard(String title, String value, String cssClass) {
        return "<div class=\"card " + cssClass + "\">" +
               "<div class=\"card-title\">" + title + "</div>" +
               "<div class=\"card-value\">" + value + "</div></div>\n";
    }

    private String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
}
