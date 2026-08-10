package com.tracer.report;

import com.tracer.analyzer.CallChainAnalyzer;
import com.tracer.classifier.LayerClassifier;
import com.tracer.model.CallEdge;
import com.tracer.model.ClassCoverage;
import com.tracer.model.LayerType;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Generates an HTML report.
 * If source lines are available: draws a REAL business flow diagram
 * (Controller -> Service -> Repository) based on actual call edges
 * extracted from source code.
 * Fallback: flat class list when no source / no edges found.
 */
public class FlowReportGenerator {

    private static final int MERMAID_MAX_TEXT_SIZE = 500_000;
    private static final int MAX_FLAT_NODES        = 80;

    private static final LayerClassifier  CLASSIFIER = new LayerClassifier();
    private static final CallChainAnalyzer ANALYZER   = new CallChainAnalyzer();

    private static final List<LayerType> LAYER_ORDER = List.of(
        LayerType.CONTROLLER, LayerType.SERVICE, LayerType.REPOSITORY,
        LayerType.COMPONENT,  LayerType.ENTITY,  LayerType.UTIL, LayerType.UNKNOWN);

    public File generate(List<ClassCoverage> coverages, File outputDir) throws Exception {
        outputDir.mkdirs();
        File f = new File(outputDir, "flow-report.html");
        CLASSIFIER.classifyAll(coverages);
        Files.writeString(f.toPath(), buildHtml(coverages));
        return f;
    }

    // ----------------------------------------------------------------
    private String buildHtml(List<ClassCoverage> coverages) {
        String ts      = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        int total      = coverages.size();
        int covLines   = coverages.stream().mapToInt(ClassCoverage::getCoveredLineCount).sum();
        int totLines   = coverages.stream().mapToInt(ClassCoverage::getTotalLineCount).sum();
        double pct     = totLines == 0 ? 0 : covLines * 100.0 / totLines;

        List<CallEdge> edges    = ANALYZER.analyze(coverages);
        boolean        hasEdges = !edges.isEmpty();

        String diagramCode;
        String diagTitle;
        String diagSub;
        if (hasEdges) {
            diagramCode = buildCallChainMermaid(edges, coverages);
            diagTitle   = "&#128336; Business Flow Diagram";
            diagSub     = "" + edges.size() + " call edges detected across " + total + " classes";
        } else {
            diagramCode = buildFlatMermaid(coverages);
            diagTitle   = "&#128336; Class Execution Flow";
            diagSub     = "No call relationships detected (source code not loaded — showing flat class list)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
          .append("<title>Coverage Flow Report</title>\n")
          .append("<script src=\"https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js\"></script>\n")
          .append("<script src=\"https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.1/dist/svg-pan-zoom.min.js\"></script>\n")
          .append("<style>").append(css()).append("</style>\n")
          .append("</head>\n<body>\n");

        // header
        sb.append("<div class=\"hdr\"><h1>&#128269; Coverage Flow Report</h1><p>Generated: ")
          .append(ts).append("</p></div>\n");

        // cards
        sb.append("<div class=\"summary\">\n")
          .append(card("Classes",   String.valueOf(total), "cb"))
          .append(card("Executed Lines", covLines+" / "+totLines, "cg"))
          .append(card("Coverage",  String.format("%.1f%%",pct), pct>=80?"cg":pct>=50?"cy":"cr"))
          .append(card("Call Edges", hasEdges ? String.valueOf(edges.size()) : "N/A", hasEdges?"cb":"cy"))
          .append("</div>\n");

        // legend
        sb.append("<div class=\"sec\"><h2>&#127881; Legend</h2><div class=\"leg\">\n")
          .append(li("#1565C0","#E3F2FD","&#9632; &ge;80%  High coverage"))
          .append(li("#F57F17","#FFF8E1","&#9632; 50-79%  Medium coverage"))
          .append(li("#B71C1C","#FFEBEE","&#9632; &lt;50%  Low coverage"))
          .append("</div></div>\n");

        // diagram
        sb.append("<div class=\"sec diag-sec\">\n")
          .append("<div class=\"toolbar\">\n")
          .append("  <div><h2>").append(diagTitle).append("</h2>")
          .append("<p class=\"sub\">").append(diagSub).append("</p></div>\n")
          .append("  <div class=\"zbtn\">")
          .append("<button onclick=\"pz.zoomIn()\">+</button>")
          .append("<button onclick=\"pz.zoomOut()\">&#8722;</button>")
          .append("<button onclick=\"pz.resetZoom();pz.center()\">&#8635;</button>")
          .append("</div>\n</div>\n")
          .append("<div id=\"dc\"><div class=\"mermaid\" id=\"diag\">\n")
          .append(diagramCode)
          .append("\n</div></div></div>\n");

        // scripts
        sb.append("<script>\n")
          .append("mermaid.initialize({startOnLoad:false,theme:'base',")
          .append("maxTextSize:").append(MERMAID_MAX_TEXT_SIZE).append(",")
          .append("themeVariables:{primaryColor:'#E3F2FD',fontSize:'15px',nodeBorder:'#90A4AE'},")
          .append("flowchart:{useMaxWidth:false,htmlLabels:true,curve:'linear',nodeSpacing:60,rankSpacing:90}")
          .append("});\n")
          .append("var pz;\n")
          .append("mermaid.run({nodes:[document.getElementById('diag')]}).then(function(){\n")
          .append("  var s=document.querySelector('#dc svg'); if(!s)return;\n")
          .append("  s.setAttribute('width','100%'); s.setAttribute('height','100%'); s.style.maxWidth='none';\n")
          .append("  pz=svgPanZoom(s,{zoomEnabled:true,controlIconsEnabled:false,fit:true,center:true,minZoom:0.05,maxZoom:20,zoomScaleSensitivity:0.3});\n")
          .append("  pz.fit(); pz.center();\n")
          .append("});\n</script>\n</body>\n</html>");

        return sb.toString();
    }

    // ================================================================
    //  CALL-CHAIN DIAGRAM  (real business flow)
    // ================================================================
    private String buildCallChainMermaid(List<CallEdge> edges, List<ClassCoverage> coverages) {
        // Collect classes involved in edges
        Set<String> active = new LinkedHashSet<>();
        for (CallEdge e : edges) { active.add(e.getCallerClass()); active.add(e.getCalleeClass()); }

        Map<String, ClassCoverage> ccMap = new LinkedHashMap<>();
        for (ClassCoverage cc : coverages) ccMap.put(cc.getSimpleClassName(), cc);

        // Group by layer
        Map<LayerType, List<String>> byLayer = new LinkedHashMap<>();
        for (LayerType lt : LAYER_ORDER) byLayer.put(lt, new ArrayList<>());
        for (String cls : active) {
            ClassCoverage cc = ccMap.get(cls);
            LayerType lt = cc != null ? cc.getLayerType() : LayerType.UNKNOWN;
            byLayer.get(lt).add(cls);
        }

        // Node IDs
        Map<String, String> nid = new LinkedHashMap<>();
        int idx = 0;
        for (LayerType lt : LAYER_ORDER)
            for (String cls : byLayer.get(lt))
                nid.put(cls, "N" + idx++);

        StringBuilder m = new StringBuilder();
        m.append("flowchart TD\n");

        // Subgraphs per layer
        for (LayerType lt : LAYER_ORDER) {
            List<String> cls = byLayer.get(lt);
            if (cls.isEmpty()) continue;
            m.append("  subgraph ").append(lt.name())
             .append("[\"" + lt.label + "\"]\n    direction LR\n");
            for (String c : cls) {
                ClassCoverage cc = ccMap.get(c);
                String label = c + "\\n" + (cc != null ? String.format("%.0f%%", cc.getCoveragePercent()) : "?");
                m.append("    ").append(shape(nid.get(c), label, cc)).append("\n");
            }
            m.append("  end\n");
        }

        // Edges (deduplicated at class level, labeled with caller method)
        Set<String> drawn = new LinkedHashSet<>();
        for (CallEdge e : edges) {
            String from = nid.get(e.getCallerClass());
            String to   = nid.get(e.getCalleeClass());
            if (from == null || to == null || from.equals(to)) continue;
            String key = from + "->" + to;
            if (drawn.add(key)) {
                m.append("  ").append(from)
                 .append(" -->|\"").append(e.getCallerMethod()).append("()\"")
                 .append(to).append("\n");
            }
        }

        // Styles
        m.append("\n");
        for (String cls : active) {
            String[] c = colors(ccMap.get(cls));
            m.append("  style ").append(nid.get(cls))
             .append(" fill:").append(c[0]).append(",stroke:").append(c[1])
             .append(",color:").append(c[2]).append(",font-size:14px\n");
        }
        return m.toString();
    }

    // ================================================================
    //  FALLBACK FLAT DIAGRAM
    // ================================================================
    private String buildFlatMermaid(List<ClassCoverage> coverages) {
        String dir = coverages.size() <= 15 ? "LR" : "TD";
        StringBuilder m = new StringBuilder();
        m.append("flowchart ").append(dir).append("\n");
        m.append("  START([\"&#9654; Start\"])\n");

        boolean trunc = coverages.size() > MAX_FLAT_NODES;
        List<ClassCoverage> shown = trunc ? coverages.subList(0, MAX_FLAT_NODES) : coverages;
        String prev = "START";
        for (int i = 0; i < shown.size(); i++) {
            ClassCoverage cc = shown.get(i);
            String id = "N" + i;
            String lb = cc.getSimpleClassName() + "\\n" + String.format("%.0f%%", cc.getCoveragePercent());
            m.append("  ").append(shape(id, lb, cc)).append("\n");
            m.append("  ").append(prev).append(" --> ").append(id).append("\n");
            prev = id;
        }
        if (trunc) {
            m.append("  MORE[\"...").append(coverages.size()-MAX_FLAT_NODES).append(" more...\"]\n");
            m.append("  ").append(prev).append(" --> MORE\n"); prev = "MORE";
        }
        m.append("  END([\"&#9632; End\"])\n  ").append(prev).append(" --> END\n\n");
        m.append("  style START fill:#43A047,stroke:#2E7D32,color:#fff\n");
        m.append("  style END   fill:#E53935,stroke:#B71C1C,color:#fff\n");
        for (int i = 0; i < shown.size(); i++) {
            String[] c = colors(shown.get(i));
            m.append("  style N").append(i)
             .append(" fill:").append(c[0]).append(",stroke:").append(c[1])
             .append(",color:").append(c[2]).append(",font-size:14px\n");
        }
        return m.toString();
    }

    // ----------------------------------------------------------------
    private String shape(String id, String label, ClassCoverage cc) {
        double p = cc != null ? cc.getCoveragePercent() : 0;
        if (p >= 80) return id + "[\"" + label + "\"]";
        if (p >= 50) return id + "(\"" + label + "\")";
        return id + "{\"" + label + "\"}";
    }

    private String[] colors(ClassCoverage cc) {
        double p = cc != null ? cc.getCoveragePercent() : 0;
        if (p >= 80) return new String[]{"#BBDEFB","#1565C0","#0D47A1"};
        if (p >= 50) return new String[]{"#FFF9C4","#F9A825","#E65100"};
        return new String[]{"#FFCDD2","#C62828","#B71C1C"};
    }

    private String card(String t, String v, String cls) {
        return "<div class=\"card " + cls + "\"><div class=\"ct\">"
             + t + "</div><div class=\"cv\">" + v + "</div></div>\n";
    }

    private String li(String stroke, String fill, String label) {
        return "<div class=\"li\" style=\"background:" + fill
             + ";border-left:4px solid " + stroke + "\">" + label + "</div>\n";
    }

    private String css() {
        return "*{box-sizing:border-box;margin:0;padding:0}"
             + "body{font-family:'Segoe UI',Arial,sans-serif;background:#f5f5f5;color:#333}"
             + ".hdr{background:linear-gradient(135deg,#1a237e,#283593);color:#fff;padding:30px 40px}"
             + ".hdr h1{font-size:2em;margin-bottom:8px}"
             + ".summary{display:flex;gap:20px;padding:24px 40px;flex-wrap:wrap}"
             + ".card{flex:1;min-width:150px;border-radius:12px;padding:20px;color:#fff;box-shadow:0 4px 12px rgba(0,0,0,.15)}"
             + ".ct{font-size:.9em;opacity:.85;margin-bottom:8px}"
             + ".cv{font-size:2em;font-weight:bold}"
             + ".cb{background:linear-gradient(135deg,#1976D2,#42A5F5)}"
             + ".cg{background:linear-gradient(135deg,#388E3C,#66BB6A)}"
             + ".cy{background:linear-gradient(135deg,#F57F17,#FFCA28)}"
             + ".cr{background:linear-gradient(135deg,#C62828,#EF5350)}"
             + ".sec{background:#fff;margin:0 40px 24px;border-radius:12px;padding:24px;box-shadow:0 2px 8px rgba(0,0,0,.08)}"
             + ".sec h2{font-size:1.3em;margin-bottom:8px;color:#1a237e;border-bottom:2px solid #e8eaf6;padding-bottom:8px}"
             + ".leg{display:flex;flex-wrap:wrap;gap:12px;margin-top:12px}"
             + ".li{display:flex;align-items:center;gap:8px;padding:8px 16px;border-radius:8px;font-size:.95em;font-weight:500}"
             + ".diag-sec{padding:0 40px 24px}"
             + ".toolbar{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:12px}"
             + ".toolbar h2{margin:0;border:none;padding:0}"
             + ".sub{font-size:.85em;color:#666;margin-top:4px}"
             + ".zbtn button{font-size:1.3em;margin-left:6px;padding:4px 12px;border:1px solid #c5cae9;border-radius:6px;cursor:pointer;background:#e8eaf6;color:#1a237e}"
             + ".zbtn button:hover{background:#c5cae9}"
             + "#dc{width:100%;height:82vh;min-height:650px;border:2px solid #e8eaf6;border-radius:10px;overflow:hidden;background:#fafafa;cursor:grab}"
             + "#dc:active{cursor:grabbing}"
             + "#dc svg{display:block;width:100%!important;height:100%!important}"
             + ".mermaid{width:100%;height:100%}"
             + "@media(max-width:768px){.summary{padding:16px}.sec,.diag-sec{margin:0 16px 16px}}";
    }
}
