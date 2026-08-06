package com.tracer.report;

import com.tracer.model.ClassCoverage;
import com.tracer.model.MethodCoverage;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Generates an HTML report with:
 * 1. Business flow diagram (Mermaid.js)
 * 2. Per-class line coverage with source code
 * 3. Summary statistics
 */
public class FlowReportGenerator {

    public File generate(List<ClassCoverage> coverages, File outputDir) throws Exception {
        outputDir.mkdirs();
        File reportFile = new File(outputDir, "flow-report.html");

        String html = buildHtml(coverages);
        Files.writeString(reportFile.toPath(), html);
        return reportFile;
    }

    private String buildHtml(List<ClassCoverage> coverages) {
        StringBuilder sb = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        int totalClasses = coverages.size();
        int totalCoveredLines = coverages.stream().mapToInt(ClassCoverage::getCoveredLineCount).sum();
        int totalLines = coverages.stream().mapToInt(ClassCoverage::getTotalLineCount).sum();
        double overallPct = totalLines == 0 ? 0 : (double) totalCoveredLines / totalLines * 100;

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"zh-CN\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>Coverage Flow Report</title>\n");
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js\"></script>\n");
        sb.append("<style>\n");
        sb.append(buildCss());
        sb.append("</style>\n</head>\n<body>\n");

        // Header
        sb.append("<div class=\"header\">\n");
        sb.append("  <h1>&#128269; Coverage Flow Report</h1>\n");
        sb.append("  <p>Generated: ").append(timestamp).append("</p>\n");
        sb.append("</div>\n");

        // Summary cards
        sb.append("<div class=\"summary\">\n");
        sb.append(summaryCard("Classes Covered", String.valueOf(totalClasses), "card-blue"));
        sb.append(summaryCard("Lines Executed", totalCoveredLines + " / " + totalLines, "card-green"));
        sb.append(summaryCard("Coverage Rate", String.format("%.1f%%", overallPct), overallPct >= 80 ? "card-green" : overallPct >= 50 ? "card-yellow" : "card-red"));
        sb.append("</div>\n");

        // Flow Diagram
        sb.append("<div class=\"section\">\n");
        sb.append("<h2>&#128336; Business Flow Diagram</h2>\n");
        sb.append("<div class=\"mermaid\">\n");
        sb.append(buildMermaidDiagram(coverages));
        sb.append("\n</div>\n</div>\n");

        // Class Navigation
        sb.append("<div class=\"section\">\n");
        sb.append("<h2>&#128196; Class Coverage Detail</h2>\n");
        sb.append("<div class=\"nav-list\">\n");
        for (ClassCoverage cc : coverages) {
            String anchor = cc.getClassName().replace('.', '-');
            sb.append("<a href=\"#").append(anchor).append("\">")
              .append(cc.getSimpleClassName())
              .append(" <span class=\"badge\">").append(String.format("%.0f%%", cc.getCoveragePercent())).append("</span>")
              .append("</a>\n");
        }
        sb.append("</div>\n</div>\n");

        // Per-class detail
        for (ClassCoverage cc : coverages) {
            sb.append(buildClassSection(cc));
        }

        // Scripts
        sb.append("<script>\n");
        sb.append("mermaid.initialize({ startOnLoad: true, theme: 'default', flowchart: { useMaxWidth: true } });\n");
        sb.append("function toggleSource(id) {\n");
        sb.append("  var el = document.getElementById(id);\n");
        sb.append("  el.style.display = el.style.display === 'none' ? 'block' : 'none';\n");
        sb.append("}\n");
        sb.append("</script>\n");
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    private String buildMermaidDiagram(List<ClassCoverage> coverages) {
        StringBuilder mermaid = new StringBuilder();
        mermaid.append("flowchart TD\n");
        mermaid.append("    START([\"&#9654; Execution Start\"])\n");

        String prevId = "START";
        for (int i = 0; i < coverages.size(); i++) {
            ClassCoverage cc = coverages.get(i);
            String nodeId = "CLASS" + i;
            String label = cc.getSimpleClassName() + "\\n" +
                           cc.getCoveredLineCount() + " lines executed";

            // Choose shape based on coverage
            String shape;
            if (cc.getCoveragePercent() >= 80) {
                shape = "[\"" + label + "\"]";
            } else if (cc.getCoveragePercent() >= 50) {
                shape = "(\"" + label + "\")";
            } else {
                shape = "{\"" + label + "\"}";
            }

            mermaid.append("    ").append(nodeId).append(shape).append("\n");
            mermaid.append("    ").append(prevId).append(" --> ").append(nodeId).append("\n");
            prevId = nodeId;

            // Add method nodes for covered methods
            if (!cc.getMethods().isEmpty()) {
                for (int j = 0; j < cc.getMethods().size() && j < 5; j++) {
                    MethodCoverage mc = cc.getMethods().get(j);
                    if (!mc.isCovered()) continue;
                    String methodId = nodeId + "_M" + j;
                    String methodLabel = mc.getMethodName() + "()";
                    mermaid.append("    ").append(methodId).append("[\"\uD83D\uDD27 ").append(methodLabel).append("\"]\n");
                    mermaid.append("    ").append(nodeId).append(" -.-> ").append(methodId).append("\n");
                }
            }
        }

        mermaid.append("    ").append(prevId).append(" --> END\n");
        mermaid.append("    END([\"&#9632; Execution End\"])\n");

        // Styling
        mermaid.append("    style START fill:#4CAF50,color:#fff,stroke:#388E3C\n");
        mermaid.append("    style END fill:#F44336,color:#fff,stroke:#D32F2F\n");
        for (int i = 0; i < coverages.size(); i++) {
            ClassCoverage cc = coverages.get(i);
            String color = cc.getCoveragePercent() >= 80 ? "#2196F3" :
                           cc.getCoveragePercent() >= 50 ? "#FF9800" : "#9E9E9E";
            mermaid.append("    style CLASS").append(i)
                   .append(" fill:").append(color)
                   .append(",color:#fff\n");
        }

        return mermaid.toString();
    }

    private String buildClassSection(ClassCoverage cc) {
        StringBuilder sb = new StringBuilder();
        String anchor = cc.getClassName().replace('.', '-');
        String sourceId = "src-" + anchor;

        sb.append("<div class=\"class-section\" id=\"").append(anchor).append("\">\n");
        sb.append("<div class=\"class-header\">\n");
        sb.append("  <h3>").append(cc.getSimpleClassName()).append("</h3>\n");
        sb.append("  <span class=\"class-name\">").append(cc.getClassName()).append("</span>\n");
        sb.append("  <div class=\"coverage-bar-wrap\">\n");
        sb.append("    <div class=\"coverage-bar\" style=\"width:").append(String.format("%.1f", cc.getCoveragePercent())).append("%\"></div>\n");
        sb.append("  </div>\n");
        sb.append("  <span class=\"pct\">").append(String.format("%.1f%%", cc.getCoveragePercent())).append(" (");
        sb.append(cc.getCoveredLineCount()).append("/").append(cc.getTotalLineCount()).append(" lines)</span>\n");
        sb.append("</div>\n");

        // Source code with line highlights
        if (!cc.getSourceLines().isEmpty()) {
            sb.append("<button class=\"toggle-btn\" onclick=\"toggleSource('")
              .append(sourceId).append("')\">&#128065; Toggle Source Code</button>\n");
            sb.append("<div id=\"").append(sourceId).append("\" class=\"source-view\">\n");
            sb.append("<table class=\"source-table\">\n");

            List<String> srcLines = cc.getSourceLines();
            Map<Integer, Boolean> lineMap = cc.getLineCoverageMap();

            for (int i = 0; i < srcLines.size(); i++) {
                int lineNum = i + 1;
                Boolean covered = lineMap.get(lineNum);
                String rowClass = covered == null ? "" : (covered ? "line-covered" : "line-missed");
                String indicator = covered == null ? "" : (covered ? "&#10003;" : "&#10007;");

                sb.append("<tr class=\"").append(rowClass).append("\">\n");
                sb.append("  <td class=\"line-num\">").append(lineNum).append("</td>\n");
                sb.append("  <td class=\"line-indicator\">").append(indicator).append("</td>\n");
                sb.append("  <td class=\"line-code\"><pre>")
                  .append(escapeHtml(srcLines.get(i)))
                  .append("</pre></td>\n");
                sb.append("</tr>\n");
            }
            sb.append("</table>\n</div>\n");
        } else {
            sb.append("<p class=\"no-source\">Source file not found.</p>\n");
        }

        // Line numbers executed
        sb.append("<div class=\"executed-lines\">\n");
        sb.append("<strong>Executed Lines:</strong> ");
        cc.getLineCoverageMap().entrySet().stream()
            .filter(Map.Entry::getValue)
            .forEach(e -> sb.append("<span class=\"line-badge\">").append(e.getKey()).append("</span>"));
        sb.append("\n</div>\n");

        sb.append("</div>\n");
        return sb.toString();
    }

    private String summaryCard(String title, String value, String cssClass) {
        return "<div class=\"card " + cssClass + "\">" +
               "<div class=\"card-title\">" + title + "</div>" +
               "<div class=\"card-value\">" + value + "</div>" +
               "</div>\n";
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String buildCss() {
        return "* { box-sizing: border-box; margin: 0; padding: 0; }\n" +
               "body { font-family: 'Segoe UI', Arial, sans-serif; background: #f5f5f5; color: #333; }\n" +
               ".header { background: linear-gradient(135deg, #1a237e, #283593); color: white; padding: 30px 40px; }\n" +
               ".header h1 { font-size: 2em; margin-bottom: 8px; }\n" +
               ".summary { display: flex; gap: 20px; padding: 24px 40px; flex-wrap: wrap; }\n" +
               ".card { flex: 1; min-width: 180px; border-radius: 12px; padding: 20px; color: white; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }\n" +
               ".card-title { font-size: 0.9em; opacity: 0.85; margin-bottom: 8px; }\n" +
               ".card-value { font-size: 2em; font-weight: bold; }\n" +
               ".card-blue { background: linear-gradient(135deg, #1976D2, #42A5F5); }\n" +
               ".card-green { background: linear-gradient(135deg, #388E3C, #66BB6A); }\n" +
               ".card-yellow { background: linear-gradient(135deg, #F57F17, #FFCA28); }\n" +
               ".card-red { background: linear-gradient(135deg, #C62828, #EF5350); }\n" +
               ".section { background: white; margin: 0 40px 24px; border-radius: 12px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }\n" +
               ".section h2 { font-size: 1.3em; margin-bottom: 16px; color: #1a237e; border-bottom: 2px solid #e8eaf6; padding-bottom: 8px; }\n" +
               ".nav-list { display: flex; flex-wrap: wrap; gap: 10px; }\n" +
               ".nav-list a { text-decoration: none; background: #e8eaf6; color: #1a237e; padding: 6px 14px; border-radius: 20px; font-size: 0.9em; transition: background 0.2s; }\n" +
               ".nav-list a:hover { background: #c5cae9; }\n" +
               ".badge { background: #1a237e; color: white; border-radius: 10px; padding: 2px 8px; font-size: 0.8em; margin-left: 4px; }\n" +
               ".class-section { background: white; margin: 0 40px 20px; border-radius: 12px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }\n" +
               ".class-header { margin-bottom: 16px; }\n" +
               ".class-header h3 { font-size: 1.2em; color: #1a237e; }\n" +
               ".class-name { font-size: 0.8em; color: #666; font-family: monospace; }\n" +
               ".coverage-bar-wrap { background: #eee; border-radius: 6px; height: 10px; margin: 8px 0; }\n" +
               ".coverage-bar { background: linear-gradient(90deg, #43A047, #66BB6A); height: 10px; border-radius: 6px; transition: width 0.5s; }\n" +
               ".pct { font-size: 0.9em; color: #555; }\n" +
               ".toggle-btn { background: #1a237e; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; margin-bottom: 12px; font-size: 0.9em; }\n" +
               ".toggle-btn:hover { background: #283593; }\n" +
               ".source-view { display: none; overflow-x: auto; border: 1px solid #e0e0e0; border-radius: 8px; }\n" +
               ".source-table { width: 100%; border-collapse: collapse; font-family: monospace; font-size: 0.85em; }\n" +
               ".source-table tr { border-bottom: 1px solid #f0f0f0; }\n" +
               ".line-covered { background: #E8F5E9; }\n" +
               ".line-missed { background: #FFEBEE; }\n" +
               ".line-num { color: #999; text-align: right; padding: 2px 10px; min-width: 45px; user-select: none; border-right: 1px solid #eee; }\n" +
               ".line-indicator { text-align: center; width: 20px; font-size: 0.8em; color: #777; }\n" +
               ".line-covered .line-indicator { color: #43A047; }\n" +
               ".line-missed .line-indicator { color: #E53935; }\n" +
               ".line-code pre { padding: 2px 10px; white-space: pre; }\n" +
               ".executed-lines { margin-top: 12px; font-size: 0.85em; }\n" +
               ".line-badge { display: inline-block; background: #e8f5e9; color: #2e7d32; border: 1px solid #a5d6a7; border-radius: 4px; padding: 1px 7px; margin: 2px; font-size: 0.85em; font-family: monospace; }\n" +
               ".no-source { color: #999; font-style: italic; padding: 10px 0; }\n" +
               ".mermaid { overflow-x: auto; padding: 10px; }\n" +
               "@media (max-width: 768px) { .summary { padding: 16px; } .section, .class-section { margin: 0 16px 16px; } }\n";
    }
}
