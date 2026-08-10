package com.tracer.model;

/**
 * Represents a method-level call edge:
 *   callerClass.callerMethod  -->  calleeClass.calleeMethod
 */
public class CallEdge {
    private final String callerClass;
    private final String callerMethod;
    private final String calleeClass;
    private final String calleeMethod;

    public CallEdge(String callerClass, String callerMethod,
                    String calleeClass, String calleeMethod) {
        this.callerClass  = callerClass;
        this.callerMethod = callerMethod;
        this.calleeClass  = calleeClass;
        this.calleeMethod = calleeMethod;
    }

    public String getCallerClass()  { return callerClass; }
    public String getCallerMethod() { return callerMethod; }
    public String getCalleeClass()  { return calleeClass; }
    public String getCalleeMethod() { return calleeMethod; }

    @Override
    public String toString() {
        return callerClass + "." + callerMethod + "() --> " + calleeClass + "." + calleeMethod + "()";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallEdge)) return false;
        CallEdge e = (CallEdge) o;
        return callerClass.equals(e.callerClass) && callerMethod.equals(e.callerMethod)
            && calleeClass.equals(e.calleeClass) && calleeMethod.equals(e.calleeMethod);
    }

    @Override
    public int hashCode() {
        return (callerClass + callerMethod + calleeClass + calleeMethod).hashCode();
    }
}
