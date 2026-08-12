package com.tracer.agent;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent entry point.
 * Usage:
 *   java -javaagent:tracer-agent.jar=output=trace.json,package=com.psa -jar your-app.jar
 */
public class TracerAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        AgentConfig config = AgentConfig.parse(agentArgs);
        System.out.println("[TracerAgent] started. package=" + config.getBasePackage()
                + " output=" + config.getOutputPath());
        TraceRecorder.init(config.getOutputPath());
        inst.addTransformer(new MethodTraceTransformer(config.getBasePackage()), false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[TracerAgent] writing trace file...");
            TraceRecorder.flush();
            System.out.println("[TracerAgent] done.");
        }));
    }

    /** Also support agentmain for attach API */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }
}
