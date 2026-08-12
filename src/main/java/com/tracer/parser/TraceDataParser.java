package com.tracer.parser;

import com.tracer.model.ExecutionTrace;
import com.tracer.model.MethodCall;

import java.io.*;
import java.util.*;

/**
 * Parses trace data from various sources (files, streams, etc.)
 * Converts raw trace data into ExecutionTrace objects.
 */
public class TraceDataParser {
    private static final String TRACE_RECORD_DELIMITER = "||";
    private static final String FIELD_SEPARATOR = "|";

    /**
     * Parse trace data from a file
     */
    public ExecutionTrace parseFromFile(File traceFile) throws IOException {
        ExecutionTrace trace = new ExecutionTrace();
        try (BufferedReader reader = new BufferedReader(new FileReader(traceFile))) {
            String line;
            int callOrder = 0;
            long firstTimestamp = -1;
            long lastTimestamp = -1;
            String entryClass = null;
            String entryMethod = null;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                MethodCall methodCall = parseMethodCallRecord(line, callOrder);
                if (methodCall != null) {
                    trace.addMethodCall(methodCall);
                    
                    if (callOrder == 0) {
                        entryClass = methodCall.getClassName();
                        entryMethod = methodCall.getMethodName();
                        firstTimestamp = methodCall.getTimestamp();
                    }
                    lastTimestamp = methodCall.getTimestamp();
                    callOrder++;
                }
            }

            trace.setEntryClass(entryClass);
            trace.setEntryMethod(entryMethod);
            trace.setStartTime(firstTimestamp);
            trace.setEndTime(lastTimestamp);
        }
        return trace;
    }

    /**
     * Parse trace data from a string
     */
    public ExecutionTrace parseFromString(String traceData) {
        ExecutionTrace trace = new ExecutionTrace();
        String[] records = traceData.split(TRACE_RECORD_DELIMITER);
        int callOrder = 0;
        long firstTimestamp = -1;
        String entryClass = null;
        String entryMethod = null;

        for (String record : records) {
            if (record.trim().isEmpty()) continue;

            MethodCall methodCall = parseMethodCallRecord(record, callOrder);
            if (methodCall != null) {
                trace.addMethodCall(methodCall);
                
                if (callOrder == 0) {
                    entryClass = methodCall.getClassName();
                    entryMethod = methodCall.getMethodName();
                    firstTimestamp = methodCall.getTimestamp();
                }
                callOrder++;
            }
        }

        trace.setEntryClass(entryClass);
        trace.setEntryMethod(entryMethod);
        trace.setStartTime(firstTimestamp);
        trace.setEndTime(System.currentTimeMillis());
        
        return trace;
    }

    /**
     * Parse a single method call record
     * Format: className|methodName|methodSignature|timestamp|duration|depth|threadName
     */
    private MethodCall parseMethodCallRecord(String record, int callOrder) {
        String[] parts = record.split("\\" + FIELD_SEPARATOR);
        if (parts.length < 5) {
            return null; // Invalid record
        }

        try {
            String className = parts[0].trim();
            String methodName = parts[1].trim();
            String methodSignature = parts.length > 2 ? parts[2].trim() : "";
            long timestamp = Long.parseLong(parts[3].trim());
            long duration = parts.length > 4 ? Long.parseLong(parts[4].trim()) : 0;
            int depth = parts.length > 5 ? Integer.parseInt(parts[5].trim()) : 0;
            String threadName = parts.length > 6 ? parts[6].trim() : "Thread-" + Thread.currentThread().getId();

            MethodCall call = new MethodCall(className, methodName, methodSignature, timestamp, callOrder, depth);
            call.setDuration(duration);
            call.setThreadName(threadName);
            return call;
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse record: " + record);
            return null;
        }
    }

    /**
     * Validate if trace data is well-formed
     */
    public boolean validateTrace(ExecutionTrace trace) {
        if (trace.getMethodCalls().isEmpty()) {
            return false;
        }

        List<MethodCall> calls = trace.getMethodCalls();
        for (int i = 0; i < calls.size(); i++) {
            MethodCall call = calls.get(i);
            if (call.getCallOrder() != i) {
                return false; // Order mismatch
            }
        }
        return true;
    }
}
