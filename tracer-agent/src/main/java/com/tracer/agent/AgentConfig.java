package com.tracer.agent;

/**
 * Parses agent arguments: output=trace.json,package=com.psa
 */
public class AgentConfig {

    private String basePackage = "";
    private String outputPath  = "trace.json";

    public static AgentConfig parse(String args) {
        AgentConfig cfg = new AgentConfig();
        if (args == null || args.isBlank()) return cfg;
        for (String part : args.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            switch (kv[0].trim().toLowerCase()) {
                case "package" -> cfg.basePackage = kv[1].trim().replace('.', '/');
                case "output"  -> cfg.outputPath  = kv[1].trim();
            }
        }
        return cfg;
    }

    public String getBasePackage() { return basePackage; }
    public String getOutputPath()  { return outputPath; }
}
