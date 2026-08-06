package com.tracer.report;

import com.tracer.model.ClassCoverage;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Generates an HTML report with:
 *  1. Summary cards
 *  2. Flat execution-flow diagram: each covered class as one node,
 *     chained in the order they appear (alphabetical by class name),
 *     colour-coded by coverage %, pan & zoom enabled.
 *
 *  No subgraph grouping / no layer classification.
 */
public class FlowReportGenerator {

    private static final int MERMAID_MAX_TEXT_SIZE = 500_000;
    /** Max nodes rendered in the diagram; remainder shown as a summary node */
    private static final int MAX_DIAGRAM_NODES = 60;

    // ────────────────────────────────────────────────────────────────
    public File generate(List<ClassCoverage> coverages, File outputDir) throws Exception {
        outputDir.mkdirs();
        File reportFile = new File(outputDir, "flow-report.html");
        Files.writeString(reportFile.toPath(), buildHtml(coverages));
        return reportFile;
    }

    // ────────────────────────────────────────────────────────────────
    private String buildHtml(List<ClassCoverage> coverages) {
        StringBuilder sb = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        int total    = coverages.size();
        int covLines = coverages.stream().mapToInt(ClassCoverage::getCoveredLineCount).sum();
        int totLines = coverages.stream().mapToInt(ClassCoverage::getTotalLineCount).sum();
        double pct   = totLines == 0 ? 0 : covLines * 100.0 / totLines;

        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
          .append("<title>Coverage Flow Report</title>\n")
          .append("<script src=\"https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js\"></script>\n")
          .append("<script src=\"https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.1/dist/svg-pan-zoom.min.js\"></script>\n")
          .append("<style>\n").append(buildCss()).append("</style>\n")
          .append("</head>\n<body>\n");

        // ── header ──
        sb.append("<div class=\"header\">\n")
          .append("  <h1>&#128269; Coverage Flow Report</h1>\n")
          .append("  <p>Generated: ").append(timestamp).append("</p>\n")
          .append("</div>\n");

        // ── summary cards ──
        sb.append("<div class=\"summary\">\n")
          .append(card("Classes Executed", String.valueOf(total), "card-blue"))
          .append(card("Lines Executed", covLines + " / " + totLines, "card-green"))
          .append(card("Coverage Rate", String.format("%.1f%%", pct),
                  pct >= 80 ? "card-green" : pct >= 50 ? "card-yellow" : "card-red"))
          .append("</div>\n");

        // ── colour legend ──
        sb.append("<div class=\"section\">\n")
          .append("<h2>&#127881; Coverage Legend</h2>\n")
          .append("<div class=\"legend\">\n")
          .append(legendItem("#1565C0", "#E3F2FD", "&#9632; &ge;80%  High coverage"))
          .append(legendItem("#F57F17", "#FFF8E1", "&#9632; 50-79%  Medium coverage"))
          .append(legendItem("#B71C1C", "#FFEBEE", "&#9632; &lt;50%  Low coverage"))
          .append("</div>\n</div>\n");

        // ── flow diagram ──
        sb.append("<div class=\"section diagram-section\">\n")
          .append("<div class=\"diagram-toolbar\">\n")
          .append("  <h2>&#128336; Class Execution Flow</h2>\n")
          .append("  <div class=\"zoom-btns\">\n")
          .append("    <button onclick=\"pz.zoomIn()\"  title=\"Zoom in\">&#43;</button>\n")
          .append("    <button onclick=\"pz.zoomOut()\" title=\"Zoom out\">&#8722;</button>\n")
          .append("    <button onclick=\"pz.resetZoom();pz.center()\" title=\"Reset\">&#8635;</button>\n")
          .append("  </div>\n")
          .append("</div>\n")
          .append("<div id=\"dc\">\n")
          .append("  <div class=\"mermaid\" id=\"diag\">\n")
          .append(buildFlatMermaid(coverages))
          .append("\n  </div>\n")
          .append("</div>\n</div>\n");

        // ── scripts ──
        sb.append("<script>\n")
          .append("mermaid.initialize({\n")
          .append("  startOnLoad: false,\n")
          .append("  theme: 'base',\n")
          .append("  maxTextSize: ").append(MERMAID_MAX_TEXT_SIZE).append(",\n")
          .append("  themeVariables: { primaryColor:'#E3F2FD', fontSize:'15px',\n")
          .append("                    nodeBorder:'#90A4AE', mainBkg:'#E3F2FD' },\n")
          .append("  flowchart: { useMaxWidth:false, htmlLabels:true, curve:'linear',\n")
          .append("               nodeSpacing:50, rankSpacing:70 }\n")
          .append("});\n\n")
          .append("var pz;\n")
          .append("mermaid.run({ nodes:[document.getElementById('diag')] }).then(function(){\n")
          .append("  var s = document.querySelector('#dc svg');\n")
          .append("  if (!s) return;\n")
          .append("  s.setAttribute('width','100%');\n")
          .append("  s.setAttribute('height','100%');\n")
          .append("  s.style.maxWidth = 'none';\n")
          .append("  pz = svgPanZoom(s, {\n")
          .append("    zoomEnabled:true, controlIconsEnabled:false,\n")
          .append("    fit:true, center:true, minZoom:0.05, maxZoom:20,\n")
          .append("    zoomScaleSensitivity:0.3\n")
          .append("  });\n")
          .append("  pz.fit(); pz.center();\n")
          .append("});\n")
          .append("</script>\n</body>\n</html>");

        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    //  FLAT MERMAID  –  one node per class, linear chain TD
    // ════════════════════════════════════════════════════════════════
    private String buildFlatMermaid(List<ClassCoverage> coverages) {
        // Use LR (left-to-right) when few classes; TD (top-down) for many
        String direction = coverages.size() <= 15 ? "LR" : "TD";

        StringBuilder m = new StringBuilder();
        m.append("flowchart ").append(direction).append("\n");

        // START
        m.append("  START([\"&#9654; Start\"])\n");

        boolean truncated = coverages.size() > MAX_DIAGRAM_NODES;
        List<ClassCoverage> shown = truncated
                ? coverages.subList(0, MAX_DIAGRAM_NODES) : coverages;

        String prevId = "START";
        for (int i = 0; i < shown.size(); i++) {
            ClassCoverage cc = shown.get(i);
            String nodeId = "N" + i;
            String shape  = nodeShape(nodeId, cc);
            m.append("  ").append(shape).append("\n");
            m.append("  ").append(prevId).append(" --> ").append(nodeId).append("\n");
            prevId = nodeId;
        }

        // If truncated, add a summary node
        if (truncated) {
            int remaining = coverages.size() - MAX_DIAGRAM_NODES;
            m.append("  MORE[\"... ").append(remaining).append(" more classes ...\"]\n");
            m.append("  ").append(prevId).append(" --> MORE\n");
            prevId = "MORE";
        }

        // END
        m.append("  END([\"&#9632; End\"])\n");
        m.append("  ").append(prevId).append(" --> END\n\n");

        // ── per-node styles ──
        m.append("  style START fill:#43A047,stroke:#2E7D32,color:#fff\n");
        m.append("  style END   fill:#E53935,stroke:#B71C1C,color:#fff\n");
        if (truncated) {
            m.append("  style MORE fill:#ECEFF1,stroke:#90A4AE,color:#546E7A\n");
        }

        for (int i = 0; i < shown.size(); i++) {
            ClassCoverage cc = shown.get(i);
            double p = cc.getCoveragePercent();
            String fill, stroke, color;
            if (p >= 80) {
                fill = "#BBDEFB"; stroke = "#1565C0"; color = "#0D47A1";
            } else if (p >= 50) {
                fill = "#FFF9C4"; stroke = "#F9A825"; color = "#E65100";
            } else {
                fill = "#FFCDD2"; stroke = "#C62828"; color = "#B71C1C";
            }
            m.append("  style N").append(i)
             .append(" fill:").append(fill)
             .append(",stroke:").append(stroke)
             .append(",color:").append(color)
             .append(",font-size:14px\n");
        }

        return m.toString();
    }

    /**
     * Build a single Mermaid node declaration.
     * Shape:
     *   rectangle  [" ... "]  – coverage >= 80%
     *   rounded    (" ... ")  – coverage 50–79%
     *   diamond    {" ... "}  – coverage < 50%
     *
     * Label: SimpleClassName\ncoverage% (lines executed)
     */
    private String nodeShape(String id, ClassCoverage cc) {
        String name  = cc.getSimpleClassName();
        String label = name + "\\n"
                     + String.format("%.0f%%", cc.getCoveragePercent())
                     + " (" + cc.getCoveredLineCount() + " lines)";

        if (cc.getCoveragePercent() >= 80) return id + "[\"" + label + "\"]";
        if (cc.getCoveragePercent() >= 50) return id + "(\"" + label + "\")";
        return id + "{\"" + label + "\"}";
    }

    // ════════════════════════════════════════════════════════════════
    //  CSS
    // ════════════════════════════════════════════════════════════════
    private String buildCss() {
        return "* { box-sizing:border-box; margin:0; padding:0; }\n"
             + "body { font-family:'Segoe UI',Arial,sans-serif; background:#f5f5f5; color:#333; }\n"
             + ".header { background:linear-gradient(135deg,#1a237e,#283593); color:#fff; padding:30px 40px; }\n"
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
             + ".legend { display:flex; flex-wrap:wrap; gap:16px; }\n"
             + ".legend-item { display:flex; align-items:center; gap:8px; padding:8px 16px; border-radius:8px; font-size:.95em; font-weight:500; }\n"
             + ".diagram-section { padding:0 40px 24px; }\n"
             + ".diagram-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }\n"
             + ".diagram-toolbar h2 { margin:0; border:none; padding:0; }\n"
             + ".zoom-btns button { font-size:1.3em; margin-left:6px; padding:4px 12px; border:1px solid #c5cae9; border-radius:6px; cursor:pointer; background:#e8eaf6; color:#1a237e; transition:background .2s; }\n"
             + ".zoom-btns button:hover { background:#c5cae9; }\n"
             + "#dc { width:100%; height:80vh; min-height:620px; border:2px solid #e8eaf6; border-radius:10px; overflow:hidden; background:#fafafa; cursor:grab; }\n"
             + "#dc:active { cursor:grabbing; }\n"
             + "#dc svg { display:block; width:100% !important; height:100% !important; }\n"
             + ".mermaid { width:100%; height:100%; }\n"
             + "@media(max-width:768px){ .summary{padding:16px} .section,.diagram-section{margin:0 16px 16px} }\n";
    }

    // ────────────────────────────────────────────────────────────────
    private String card(String title, String value, String cls) {
        return "<div class=\"card " + cls + "\">"
             + "<div class=\"card-title\">" + title + "</div>"
             + "<div class=\"card-value\">" + value + "</div></div>\n";
    }

    private String legendItem(String stroke, String fill, String label) {
        return "<div class=\"legend-item\" style=\"background:" + fill + ";border-left:4px solid " + stroke + "\">"
             + label + "</div>\n";
    }
}
