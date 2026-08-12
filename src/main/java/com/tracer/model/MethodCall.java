package com.tracer.model;

/**
 * Represents a single method call in the execution trace.
 * Captures: which class, which method, and when it was called.
 */
public class MethodCall {
    private String className;          // e.g. "com.example.UserService"
    private String methodName;         // e.g. "processUser"
    private String methodSignature;    // e.g. "void processUser(String id)"
    private long timestamp;            // When this method was called (ms)
    private long duration;             // How long method execution took (ms)
    private int callOrder;             // Sequential order in the trace
    private MethodCall caller;         // The method that called this one (parent)
    private int depth;                 // Call stack depth
    private String threadName;         // Which thread executed this

    public MethodCall() {}

    public MethodCall(String className, String methodName, String methodSignature, 
                     long timestamp, int callOrder, int depth) {
        this.className = className;
        this.methodName = methodName;
        this.methodSignature = methodSignature;
        this.timestamp = timestamp;
        this.callOrder = callOrder;
        this.depth = depth;
    }

    // Getters & Setters
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public void setMethodSignature(String methodSignature) {
        this.methodSignature = methodSignature;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public int getCallOrder() {
        return callOrder;
    }

    public void setCallOrder(int callOrder) {
        this.callOrder = callOrder;
    }

    public MethodCall getCaller() {
        return caller;
    }

    public void setCaller(MethodCall caller) {
        this.caller = caller;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public String getFullSignature() {
        return className + "." + methodName;
    }

    @Override
    public String toString() {
        return "[" + callOrder + "] " + className + "." + methodName + 
               " (depth=" + depth + ", duration=" + duration + "ms)";
    }
}
