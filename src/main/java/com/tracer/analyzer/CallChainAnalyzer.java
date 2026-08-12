package com.tracer.analyzer;

import com.tracer.model.ExecutionTrace;
import com.tracer.model.MethodCall;
import com.tracer.model.ClassNode;

import java.util.*;

/**
 * Analyzes method call chains from execution trace.
 * Builds class relationships and call flow.
 */
public class CallChainAnalyzer {
    private ExecutionTrace trace;
    private Map<String, ClassNode> classNodeMap;
    private List<MethodCall> callChain;

    public CallChainAnalyzer(ExecutionTrace trace) {
        this.trace = trace;
        this.classNodeMap = new LinkedHashMap<>();
        this.callChain = trace.getMethodCalls();
    }

    /**
     * Analyze the entire execution trace and build class relationships
     */
    public Map<String, ClassNode> analyze() {
        if (callChain.isEmpty()) {
            return classNodeMap;
        }

        // First pass: create all class nodes
        for (MethodCall call : callChain) {
            String className = call.getClassName();
            if (!classNodeMap.containsKey(className)) {
                ClassNode node = new ClassNode(className);
                node.setOrder(classNodeMap.size());
                classNodeMap.put(className, node);
            }
        }

        // Second pass: build relationships
        for (int i = 0; i < callChain.size() - 1; i++) {
            MethodCall current = callChain.get(i);
            MethodCall next = callChain.get(i + 1);

            ClassNode currentClass = classNodeMap.get(current.getClassName());
            ClassNode nextClass = classNodeMap.get(next.getClassName());

            // If depth increases, current calls next
            if (next.getDepth() > current.getDepth()) {
                currentClass.addCallee(nextClass);
                nextClass.addCaller(currentClass);
            }

            currentClass.addMethodCalled(current.getMethodName());
        }

        // Record last method
        if (!callChain.isEmpty()) {
            MethodCall lastCall = callChain.get(callChain.size() - 1);
            ClassNode lastClass = classNodeMap.get(lastCall.getClassName());
            lastClass.addMethodCalled(lastCall.getMethodName());
        }

        // Count invocations
        for (MethodCall call : callChain) {
            ClassNode node = classNodeMap.get(call.getClassName());
            node.incrementInvocationCount();
        }

        return classNodeMap;
    }

    /**
     * Get classes sorted by invocation frequency (most called first)
     */
    public List<ClassNode> getClassesByInvocationCount() {
        List<ClassNode> sorted = new ArrayList<>(classNodeMap.values());
        sorted.sort((a, b) -> Integer.compare(b.getInvocationCount(), a.getInvocationCount()));
        return sorted;
    }

    /**
     * Get entry point class (first class in execution)
     */
    public ClassNode getEntryClass() {
        String entryClassName = trace.getEntryClass();
        return classNodeMap.get(entryClassName);
    }

    /**
     * Get exit point class (last class in execution)
     */
    public ClassNode getExitClass() {
        if (callChain.isEmpty()) return null;
        String exitClassName = callChain.get(callChain.size() - 1).getClassName();
        return classNodeMap.get(exitClassName);
    }

    /**
     * Find call path between two classes
     */
    public List<ClassNode> findCallPath(String fromClass, String toClass) {
        ClassNode start = classNodeMap.get(fromClass);
        ClassNode end = classNodeMap.get(toClass);

        if (start == null || end == null) {
            return new ArrayList<>();
        }

        // BFS to find shortest path
        Queue<ClassNode> queue = new LinkedList<>();
        Map<ClassNode, ClassNode> parent = new HashMap<>();
        Set<ClassNode> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            ClassNode current = queue.poll();
            if (current.equals(end)) {
                // Reconstruct path
                List<ClassNode> path = new ArrayList<>();
                ClassNode node = end;
                while (node != null) {
                    path.add(0, node);
                    node = parent.get(node);
                }
                return path;
            }

            for (ClassNode callee : current.getCallees()) {
                if (!visited.contains(callee)) {
                    visited.add(callee);
                    parent.put(callee, current);
                    queue.add(callee);
                }
            }
        }

        return new ArrayList<>(); // No path found
    }

    /**
     * Get all classes that directly or indirectly call a given class
     */
    public Set<ClassNode> getCallers(String className) {
        ClassNode target = classNodeMap.get(className);
        Set<ClassNode> result = new HashSet<>();
        if (target == null) return result;

        Queue<ClassNode> queue = new LinkedList<>(target.getCallers());
        while (!queue.isEmpty()) {
            ClassNode caller = queue.poll();
            if (result.add(caller)) {
                queue.addAll(caller.getCallers());
            }
        }
        return result;
    }

    /**
     * Get all classes that are directly or indirectly called by a given class
     */
    public Set<ClassNode> getCallees(String className) {
        ClassNode target = classNodeMap.get(className);
        Set<ClassNode> result = new HashSet<>();
        if (target == null) return result;

        Queue<ClassNode> queue = new LinkedList<>(target.getCallees());
        while (!queue.isEmpty()) {
            ClassNode callee = queue.poll();
            if (result.add(callee)) {
                queue.addAll(callee.getCallees());
            }
        }
        return result;
    }

    public Map<String, ClassNode> getClassNodeMap() {
        return classNodeMap;
    }
}
