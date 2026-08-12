package com.tracer.parser;

import com.tracer.model.MethodCall;

/**
 * Parses individual method call records.
 * Handles various method call formats from different trace sources.
 */
public class MethodCallParser {

    /**
     * Parse a method call from a formatted string
     * Format: CLASS_NAME|METHOD_NAME|METHOD_SIGNATURE|TIMESTAMP|DURATION|DEPTH|THREAD_NAME
     */
    public MethodCall parseMethodCall(String record, int callOrder) {
        if (record == null || record.trim().isEmpty()) {
            return null;
        }

        String[] parts = record.split("\\|", -1);
        if (parts.length < 5) {
            return null; // Insufficient fields
        }

        try {
            String className = parts[0].trim();
            String methodName = parts[1].trim();
            String methodSignature = parts[2].trim();
            long timestamp = Long.parseLong(parts[3].trim());
            long duration = Long.parseLong(parts[4].trim());
            int depth = parts.length > 5 ? Integer.parseInt(parts[5].trim()) : 0;
            String threadName = parts.length > 6 ? parts[6].trim() : "main";

            MethodCall call = new MethodCall(className, methodName, methodSignature, timestamp, callOrder, depth);
            call.setDuration(duration);
            call.setThreadName(threadName);
            return call;
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse method call: " + record);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse method call from stack trace line
     * Format: at com.example.ClassName.methodName(FileName.java:123)
     */
    public MethodCall parseStackTraceLine(String stackLine, int callOrder) {
        if (!stackLine.contains("at ")) {
            return null;
        }

        try {
            // Extract class and method
            int atIndex = stackLine.indexOf("at ");
            String rest = stackLine.substring(atIndex + 3);
            int lastDotInMethod = rest.lastIndexOf('.');
            
            if (lastDotInMethod < 0) return null;

            String classAndMethod = rest.substring(0, lastDotInMethod);
            String fullMethod = rest.substring(lastDotInMethod + 1, rest.indexOf('('));

            int lastDotInClass = classAndMethod.lastIndexOf('.');
            String className = classAndMethod.substring(0, lastDotInClass);
            String methodName = fullMethod;

            MethodCall call = new MethodCall(className, methodName, "", System.currentTimeMillis(), callOrder, 0);
            return call;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract className and methodName from a fully qualified method reference
     */
    public static class MethodReference {
        public String className;
        public String methodName;
        public String methodSignature;

        public MethodReference(String className, String methodName, String methodSignature) {
            this.className = className;
            this.methodName = methodName;
            this.methodSignature = methodSignature;
        }
    }

    /**
     * Parse a fully qualified method reference
     * Format: com.example.ClassName.methodName
     */
    public MethodReference parseMethodReference(String fullMethodName) {
        if (fullMethodName == null || fullMethodName.trim().isEmpty()) {
            return null;
        }

        String trimmed = fullMethodName.trim();
        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot < 0) {
            return new MethodReference("", trimmed, "");
        }

        String className = trimmed.substring(0, lastDot);
        String methodName = trimmed.substring(lastDot + 1);

        // Remove signature if present
        int parenIndex = methodName.indexOf('(');
        String methodSignature = "";
        if (parenIndex > 0) {
            methodSignature = methodName.substring(parenIndex);
            methodName = methodName.substring(0, parenIndex);
        }

        return new MethodReference(className, methodName, methodSignature);
    }

    /**
     * Validate a method call record
     */
    public boolean validateMethodCall(MethodCall call) {
        if (call == null) return false;
        if (call.getClassName() == null || call.getClassName().isEmpty()) return false;
        if (call.getMethodName() == null || call.getMethodName().isEmpty()) return false;
        if (call.getTimestamp() < 0) return false;
        return true;
    }
}
