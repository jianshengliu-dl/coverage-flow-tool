package com.tracer.analyzer;

import com.tracer.model.ClassNode;
import com.tracer.model.ExecutionTrace;
import com.tracer.model.MethodCall;

import java.util.*;

/**
 * Builds a graph of class relationships.
 * Used to visualize the call flow between classes.
 */
public class ClassGraphBuilder {
    private Map<String, ClassNode> nodes;
    private List<ClassEdge> edges;
    private ExecutionTrace trace;

    public static class ClassEdge {
        public ClassNode from;
        public ClassNode to;
        public int weight; // Number of times this call occurs
        public Set<String> methods; // Methods called

        public ClassEdge(ClassNode from, ClassNode to) {
            this.from = from;
            this.to = to;
            this.weight = 1;
            this.methods = new LinkedHashSet<>();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassEdge edge = (ClassEdge) o;
            return Objects.equals(from.getClassName(), edge.from.getClassName()) &&
                   Objects.equals(to.getClassName(), edge.to.getClassName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(from.getClassName(), to.getClassName());
        }
    }

    public ClassGraphBuilder(ExecutionTrace trace) {
        this.trace = trace;
        this.nodes = new LinkedHashMap<>();
        this.edges = new ArrayList<>();
    }

    /**
     * Build the class graph from execution trace
     */
    public void build() {
        List<MethodCall> calls = trace.getMethodCalls();
        if (calls.isEmpty()) return;

        // Create nodes for all classes
        for (MethodCall call : calls) {
            String className = call.getClassName();
            if (!nodes.containsKey(className)) {
                nodes.put(className, new ClassNode(className));
            }
        }

        // Build edges based on method call sequence
        for (int i = 0; i < calls.size() - 1; i++) {
            MethodCall current = calls.get(i);
            MethodCall next = calls.get(i + 1);

            // If next call is deeper, it means current calls next
            if (next.getDepth() > current.getDepth()) {
                ClassNode fromNode = nodes.get(current.getClassName());
                ClassNode toNode = nodes.get(next.getClassName());

                addEdge(fromNode, toNode, next.getMethodName());
            }
        }
    }

    /**
     * Add or update an edge between two classes
     */
    private void addEdge(ClassNode from, ClassNode to, String method) {
        ClassEdge edge = new ClassEdge(from, to);
        int existingIndex = edges.indexOf(edge);

        if (existingIndex >= 0) {
            ClassEdge existing = edges.get(existingIndex);
            existing.weight++;
            existing.methods.add(method);
        } else {
            edge.methods.add(method);
            edges.add(edge);
            from.addCallee(to);
            to.addCaller(from);
        }
    }

    /**
     * Get all nodes in the graph
     */
    public Collection<ClassNode> getNodes() {
        return nodes.values();
    }

    /**
     * Get all edges in the graph
     */
    public List<ClassEdge> getEdges() {
        return edges;
    }

    /**
     * Get root nodes (entry points with no callers)
     */
    public List<ClassNode> getRootNodes() {
        List<ClassNode> roots = new ArrayList<>();
        for (ClassNode node : nodes.values()) {
            if (node.getCallers().isEmpty()) {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * Get leaf nodes (exit points with no callees)
     */
    public List<ClassNode> getLeafNodes() {
        List<ClassNode> leaves = new ArrayList<>();
        for (ClassNode node : nodes.values()) {
            if (node.getCallees().isEmpty()) {
                leaves.add(node);
            }
        }
        return leaves;
    }

    /**
     * Get node by class name
     */
    public ClassNode getNode(String className) {
        return nodes.get(className);
    }

    /**
     * Get nodes sorted by invocation count
     */
    public List<ClassNode> getNodesByInvocationCount() {
        List<ClassNode> sorted = new ArrayList<>(nodes.values());
        sorted.sort((a, b) -> Integer.compare(b.getInvocationCount(), a.getInvocationCount()));
        return sorted;
    }

    /**
     * Calculate graph statistics
     */
    public GraphStatistics getStatistics() {
        GraphStatistics stats = new GraphStatistics();
        stats.nodeCount = nodes.size();
        stats.edgeCount = edges.size();
        stats.rootCount = getRootNodes().size();
        stats.leafCount = getLeafNodes().size();
        stats.totalCalls = trace.getTotalMethodCalls();
        return stats;
    }

    public static class GraphStatistics {
        public int nodeCount;
        public int edgeCount;
        public int rootCount;
        public int leafCount;
        public int totalCalls;

        @Override
        public String toString() {
            return "GraphStatistics{" +
                    "classes=" + nodeCount +
                    ", edges=" + edgeCount +
                    ", roots=" + rootCount +
                    ", leaves=" + leafCount +
                    ", totalCalls=" + totalCalls +
                    '}';
        }
    }
}
