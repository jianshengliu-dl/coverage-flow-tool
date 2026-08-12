package com.tracer.agent;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe recorder for method enter/exit events.
 * Each thread gets its own call stack; events are written to a shared list.
 *
 * Output format (JSON):
 * [
 *   {"type":"ENTER","class":"com.psa.Foo","method":"bar","thread":"http-nio-8080-1","time":1234567890123,"depth":0},
 *   {"type":"EXIT", "class":"com.psa.Foo","method":"bar","thread":"http-nio-8080-1","time":1234567890456,"depth":0}
 * ]
 */
public class TraceRecorder {

    private static String outputPath = "trace.json";

    /** Per-thread call depth */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    /** Per-thread name (captured once) */
    private static final ThreadLocal<String> THREAD_NAME =
        ThreadLocal.withInitial(() -> Thread.currentThread().getName());

    /** Global event list — lock-free append via ConcurrentLinkedQueue */
    private static final Queue<TraceEvent> EVENTS = new ConcurrentLinkedQueue<>();

    /** Limit total events to avoid OOM */
    private static final int MAX_EVENTS = 500_000;
    private static final AtomicLong EVENT_COUNT = new AtomicLong(0);

    public static void init(String path) {
        outputPath = path;
    }

    public static void enter(String className, String methodName) {
        if (EVENT_COUNT.get() >= MAX_EVENTS) return;
        int depth = DEPTH.get();
        DEPTH.set(depth + 1);
        EVENTS.add(new TraceEvent("ENTER", className, methodName, THREAD_NAME.get(),
                System.currentTimeMillis(), depth));
        EVENT_COUNT.incrementAndGet();
    }

    public static void exit(String className, String methodName) {
        if (EVENT_COUNT.get() >= MAX_EVENTS) return;
        int depth = Math.max(0, DEPTH.get() - 1);
        DEPTH.set(depth);
        EVENTS.add(new TraceEvent("EXIT", className, methodName, THREAD_NAME.get(),
                System.currentTimeMillis(), depth));
        EVENT_COUNT.incrementAndGet();
    }

    public static void flush() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            boolean first = true;
            for (TraceEvent e : EVENTS) {
                if (!first) sb.append(",\n");
                sb.append(e.toJson());
                first = false;
            }
            sb.append("\n]");
            Files.writeString(Path.of(outputPath), sb.toString());
        } catch (IOException ex) {
            System.err.println("[TracerAgent] Failed to write trace: " + ex.getMessage());
        }
    }

    // ----------------------------------------------------------------
    public static class TraceEvent {
        final String type, className, methodName, thread;
        final long   time;
        final int    depth;

        TraceEvent(String type, String className, String methodName,
                   String thread, long time, int depth) {
            this.type       = type;
            this.className  = className;
            this.methodName = methodName;
            this.thread     = thread;
            this.time       = time;
            this.depth      = depth;
        }

        String toJson() {
            return String.format(
                "  {\"type\":\"%s\",\"class\":\"%s\",\"method\":\"%s\","
                + "\"thread\":\"%s\",\"time\":%d,\"depth\":%d}",
                type, esc(className), esc(methodName), esc(thread), time, depth);
        }

        private static String esc(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
