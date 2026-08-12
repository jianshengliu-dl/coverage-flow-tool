package com.tracer.model;

import java.util.*;

/**
 * Represents a class node in the execution flow graph.
 * Used to build the business flow diagram.
 */
public class ClassNode {
    private String className;          // Full class name
    private String simpleClassName;    // Just the class name
    private String packageName;        // Package part
    private Set<ClassNode> callers;    // Classes that call this one
    private Set<ClassNode> callees;    // Classes that this one calls
    private Set<String> methodsCalled; // Methods called on this class
    private int invocationCount;       // How many times this class was invoked
    private int order;                 // Order of first appearance in trace

    public ClassNode(String className) {
        this.className = className;
        this.callers = new LinkedHashSet<>();
        this.callees = new LinkedHashSet<>();
        this.methodsCalled = new LinkedHashSet<>();
        this.invocationCount = 0;
        
        // Parse package and simple name
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            this.packageName = className.substring(0, lastDot);
            this.simpleClassName = className.substring(lastDot + 1);
        } else {
            this.packageName = "";
            this.simpleClassName = className;
        }
    }

    public void addCaller(ClassNode caller) {
        this.callers.add(caller);
    }

    public void addCallee(ClassNode callee) {
        this.callees.add(callee);
    }

    public void addMethodCalled(String method) {
        this.methodsCalled.add(method);
    }

    public void incrementInvocationCount() {
        this.invocationCount++;
    }

    // Getters
    public String getClassName() {
        return className;
    }

    public String getSimpleClassName() {
        return simpleClassName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Set<ClassNode> getCallers() {
        return callers;
    }

    public Set<ClassNode> getCallees() {
        return callees;
    }

    public Set<String> getMethodsCalled() {
        return methodsCalled;
    }

    public int getInvocationCount() {
        return invocationCount;
    }

    public void setInvocationCount(int count) {
        this.invocationCount = count;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassNode classNode = (ClassNode) o;
        return Objects.equals(className, classNode.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className);
    }

    @Override
    public String toString() {
        return "ClassNode{" +
                "className='" + className + '\'' +
                ", invocations=" + invocationCount +
                ", methodsCalled=" + methodsCalled.size() +
                '}';
    }
}
