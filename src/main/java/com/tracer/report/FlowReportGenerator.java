package com.tracer.report;

import com.tracer.classifier.LayerClassifier;
import com.tracer.model.ClassCoverage;
import com.tracer.model.LayerType;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates an HTML report with:
 *  1. Summary cards
 *  2. Layer legend
 *  3. Large full-page flow diagram with pan & zoom (Class Detail section removed)
 */
public class FlowReportGenerator {

    private static final LayerClassifier CLASSIFIER = new LayerClassifier();
    private static final int MERMAID_MAX_TEXT_SIZE = 500_000;
    private static final int MAX_NODES_PER_LAYER   = 20;

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
        StringBuilder sb = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        int totalClasses = coverages.size();
        int coveredLines = coverages.stream().mapToInt(ClassCoverage::getCoveredLineCount).sum();
        int totalLines   = coverages.stream().mapToInt(ClassCoverage::getTotalLineCount).sum();
        double pct       = totalLines == 0 ? 0 : coveredLines * 100.0 / totalLines;

        // ── head ──
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
          .append("<title>Coverage Flow Report</title>\n")
          // Mermaid
          .append("<script src=\"https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js\"></script>\n")
          // svg-pan-zoom for drag/zoom on the diagram
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
          .append(summaryCard("Classes Covered", String.valueOf(totalClasses), "card-blue"))
          .append(summaryCard("Lines Executed",  coveredLines + " / " + totalLines, "card-green"))
          .append(summaryCard("Coverage Rate",   String.format("%.1f%%", pct),
                  pct >= 80 ? "card-green" : pct >= 50 ? "card-yellow" : "card-red"))
          .append("</div>\n");

        // ── layer legend ──
        sb.append(buildLayerLegend(coverages));

        // ── flow diagram (full-page, pan & zoom) ──
        sb.append("<div class=\"section diagram-section\">\n")
          .append("<div class=\"diagram-toolbar\">\n")
          .append("  <h2>&#128336; Business Flow Diagram")
          .append("  <small>Controller &#8594; Service &#8594; Repository</small></h2>\n")
          .append("  <div class=\"zoom-btns\">\n")
          .append("    <button onclick=\"panZoom.zoomIn()\"  title=\"Zoom in\" >&#43;</button>\n")
          .append("    <button onclick=\"panZoom.zoomOut()\" title=\"Zoom out\">&#8722;</button>\n")
          .append("    <button onclick=\"panZoom.resetZoom();panZoom.center()\" title=\"Reset\">&#8635;</button>\n")
          .append("  </div>\n")
          .append("</div>\n")
          .append("<div id=\"diagram-container\">\n")
          .append("  <div class=\"mermaid\" id=\"the-diagram\">\n")
          .append(buildLayeredMermaid(coverages))
          .append("\n  </div>\n")
          .append("</div>\n")
          .append("</div>\n");

        // ── scripts ──
        sb.append("<script>\n")
          // 1. Initialise Mermaid
          .append("mermaid.initialize({\n")
          .append("  startOnLoad: false,\n")          // we call run() manually after
          .append("  theme: 'base',\n")
          .append("  maxTextSize: ").append(MERMAID_MAX_TEXT_SIZE).append(",\n")
          .append("  themeVariables: { primaryColor: '#E3F2FD', fontSize: '16px' },\n")
          .append("  flowchart: { useMaxWidth: false, htmlLabels: true, curve: 'linear' }\n")
          .append("});\n\n")
          // 2. Render then attach pan-zoom
          .append("var panZoom;\n")
          .append("mermaid.run({ nodes: [document.getElementById('the-diagram')] }).then(function() {\n")
          .append("  var svgEl = document.querySelector('#diagram-container svg');\n")
          .append("  if (!svgEl) return;\n")
          // Make SVG fill its container
          .append("  svgEl.setAttribute('width',  '100%');\n")
          .append("  svgEl.setAttribute('height', '100%');\n")
          .append("  svgEl.style.maxWidth = 'none';\n")
          .append("  panZoom = svgPanZoom(svgEl, {\n")
          .append("    zoomEnabled: true,\n")
          .append("    controlIconsEnabled: false,\n")
          .append("    fit: true,\n")
          .append("    center: true,\n")
          .append("    minZoom: 0.1,\n")
          .append("    maxZoom: 10,\n")
          .append("    zoomScaleSensitivity: 0.3\n")
          .append("  });\n")
          .append("  panZoom.fit();\n")
          .append("  panZoom.center();\n")
          .append("});\n")
          .append("</script>\n")
          .append("</body>\n</html>");

        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    //  MERMAID DIAGRAM
    // ════════════════════════════════════════════════════════════════
    private String buildLayeredMermaid(List<ClassCoverage> coverages) {
        Map<LayerType, List<ClassCoverage>> byLayer = new LinkedHashMap<>();
        for (LayerType lt : LAYER_ORDER) byLayer.put(lt, new ArrayList<>());
        for (ClassCoverage cc : coverages) byLayer.get(cc.getLayerType()).add(cc);

        StringBuilder m = new StringBuilder();
        m.append("flowchart TD\n");
        m.append("    START([\"&#9654; Execution Start\"])\n");

        List<String> prevIds = new ArrayList<>();
        prevIds.add("START");

        for (LayerType lt : LAYER_ORDER) {
            List<ClassCoverage> all = byLayer.get(lt);
            if (all.isEmpty()) continue;

            boolean truncated = all.size() > MAX_NODES_PER_LAYER;
            List<ClassCoverage> classes = truncated ? all.subList(0, MAX_NODES_PER_LAYER) : all;
            String sgLabel = lt.label + (truncated ? " (showing " + MAX_NODES_PER_LAYER + "/" + all.size() + ")" : "");

            m.append("\n    subgraph ").append(lt.name())
             .append("[\"").append(sgLabel).append("\"]\n")
             .append("        direction LR\n");

            List<String> firstNodes = new ArrayList<>();
            for (int i = 0; i < classes.size(); i++) {
                ClassCoverage cc = classes.get(i);
                String nodeId = lt.name() + "_" + i;
                m.append("        ").append(buildNodeShape(nodeId, cc)).append("\n");
                if (i > 0) {
                    m.append("        ").append(lt.name() + "_" + (i - 1))
                     .append(" --> ").append(nodeId).append("\n");
                } else {
                    firstNodes.add(nodeId);
                }
            }
            m.append("    end\n");

            for (String prev : prevIds)
                for (String cur : firstNodes)
                    m.append("    ").append(prev).append(" --> ").append(cur).append("\n");

            prevIds.clear();
            prevIds.add(lt.name() + "_" + (classes.size() - 1));
        }

        m.append("\n    END([\"&#9632; Execution End\"])\n");
        for (String prev : prevIds)
            m.append("    ").append(prev).append(" --> END\n");

        // styles
        m.append("\n    style START fill:#4CAF50,color:#fff,stroke:#388E3C\n");
        m.append("    style END   fill:#F44336,color:#fff,stroke:#D32F2F\n");
        for (LayerType lt : LAYER_ORDER) {
            List<ClassCoverage> all = byLayer.get(lt);
            int cap = Math.min(all.size(), MAX_NODES_PER_LAYER);
            for (int i = 0; i < cap; i++) {
                m.append("    style ").append(lt.name()).append("_").append(i)
                 .append(" fill:").append(lt.bgColor)
                 .append(",stroke:").append(lt.borderColor)
                 .append(",color:#111,font-size:14px\n");
            }
        }
        return m.toString();
    }

    private String buildNodeShape(String nodeId, ClassCoverage cc) {
        String icon  = cc.getLayerType().label.split(" ")[0];
        String label = icon + " " + cc.getSimpleClassName()
                     + "\\n" + String.format("%.0f%%", cc.getCoveragePercent());
        if (cc.getCoveragePercent() >= 80) return nodeId + "[\"" + label + "\"]";
        if (cc.getCoveragePercent() >= 50) return nodeId + "(\"" + label + "\")";
        return nodeId + "{\"" + label + "\"}";
    }

    // ════════════════════════════════════════════════════════════════
    //  LAYER LEGEND
    // ════════════════════════════════════════════════════════════════
    private String buildLayerLegend(List<ClassCoverage> coverages) {
        Map<LayerType, Long> counts = coverages.stream()
                .collect(Collectors.groupingBy(ClassCoverage::getLayerType, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"section\">\n<h2>&#127881; Layer Summary</h2>\n<div class=\"legend\">\n");
        for (LayerType lt : LAYER_ORDER) {
            long cnt = counts.getOrDefault(lt, 0L);
            if (cnt == 0) continue;
            sb.append("<div class=\"legend-item\" style=\"border-left:4px solid ")
              .append(lt.borderColor).append(";background:").append(lt.bgColor).append("\">\n")
              .append("  <span class=\"legend-icon\">").append(lt.label).append("</span>\n")
              .append("  <span class=\"legend-count\">").append(cnt)
              .append(" class").append(cnt > 1 ? "es" : "").append("</span>\n")
              .append("</div>\n");
        }
        sb.append("</div>\n</div>\n");
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
             + ".legend { display:flex; flex-wrap:wrap; gap:12px; }\n"
             + ".legend-item { display:flex; align-items:center; gap:10px; padding:10px 16px; border-radius:8px; min-width:160px; }\n"
             + ".legend-icon { font-size:1em; font-weight:600; }\n"
             + ".legend-count { font-size:.85em; color:#555; }\n"
             // diagram section: tall, fills browser width
             + ".diagram-section { padding:20px 40px 24px; }\n"
             + ".diagram-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }\n"
             + ".diagram-toolbar h2 { margin:0; border:none; padding:0; }\n"
             + ".diagram-toolbar h2 small { font-size:0.6em; color:#666; margin-left:8px; }\n"
             + ".zoom-btns button { font-size:1.3em; margin-left:6px; padding:4px 12px; border:1px solid #c5cae9;"
             +   " border-radius:6px; cursor:pointer; background:#e8eaf6; color:#1a237e; transition:background .2s; }\n"
             + ".zoom-btns button:hover { background:#c5cae9; }\n"
             // diagram container: large fixed height so diagram is clearly visible
             + "#diagram-container { width:100%; height:78vh; min-height:600px; border:2px solid #e8eaf6;"
             +   " border-radius:10px; overflow:hidden; background:#fafafa; cursor:grab; }\n"
             + "#diagram-container:active { cursor:grabbing; }\n"
             + "#diagram-container svg { display:block; width:100% !important; height:100% !important; }\n"
             + ".mermaid { width:100%; height:100%; }\n"
             + "@media(max-width:768px){ .summary{padding:16px} .section,.diagram-section{margin:0 16px 16px} }\n";
    }

    // ────────────────────────────────────────────────────────────────
    private String summaryCard(String title, String value, String cssClass) {
        return "<div class=\"card " + cssClass + "\">"
             + "<div class=\"card-title\">" + title + "</div>"
             + "<div class=\"card-value\">" + value + "</div></div>\n";
    }
}
