package com.tracer.classifier;

import com.tracer.model.ClassCoverage;
import com.tracer.model.LayerType;

import java.util.List;

/**
 * Classifies each covered class into an architectural layer
 * by inspecting:
 *   1. Class simple name suffix  (e.g. UserController  → CONTROLLER)
 *   2. Package name segments     (e.g. …service.impl  → SERVICE)
 *   3. Source file annotations   (reads first ~60 lines looking for @RestController etc.)
 */
public class LayerClassifier {

    public LayerType classify(ClassCoverage cc) {
        String name = cc.getSimpleClassName().toLowerCase();
        String pkg  = cc.getClassName().toLowerCase();

        // ── 1. Name-suffix heuristics ─────────────────────────────────
        if (name.endsWith("controller") || name.endsWith("resource") || name.endsWith("endpoint"))
            return LayerType.CONTROLLER;

        if (name.endsWith("serviceimpl") || name.endsWith("service"))
            return LayerType.SERVICE;

        if (name.endsWith("repository") || name.endsWith("repo")
                || name.endsWith("dao") || name.endsWith("mapper") || name.endsWith("jparepository"))
            return LayerType.REPOSITORY;

        if (name.endsWith("entity") || name.endsWith("model") || name.endsWith("domain") || name.endsWith("po"))
            return LayerType.ENTITY;

        if (name.endsWith("component") || name.endsWith("handler")
                || name.endsWith("interceptor") || name.endsWith("filter") || name.endsWith("listener"))
            return LayerType.COMPONENT;

        if (name.endsWith("util") || name.endsWith("utils") || name.endsWith("helper")
                || name.endsWith("constant") || name.endsWith("constants") || name.endsWith("config"))
            return LayerType.UTIL;

        // ── 2. Package-path heuristics ────────────────────────────────
        if (pkg.contains(".controller") || pkg.contains(".controllers") || pkg.contains(".web") || pkg.contains(".api"))
            return LayerType.CONTROLLER;

        if (pkg.contains(".service") || pkg.contains(".services") || pkg.contains(".biz"))
            return LayerType.SERVICE;

        if (pkg.contains(".repository") || pkg.contains(".repositories")
                || pkg.contains(".dao") || pkg.contains(".mapper") || pkg.contains(".persistence"))
            return LayerType.REPOSITORY;

        if (pkg.contains(".entity") || pkg.contains(".entities")
                || pkg.contains(".model") || pkg.contains(".domain") || pkg.contains(".po"))
            return LayerType.ENTITY;

        if (pkg.contains(".component") || pkg.contains(".components")
                || pkg.contains(".handler") || pkg.contains(".filter"))
            return LayerType.COMPONENT;

        if (pkg.contains(".util") || pkg.contains(".utils") || pkg.contains(".helper") || pkg.contains(".config"))
            return LayerType.UTIL;

        // ── 3. Annotation scan in source lines ────────────────────────
        if (!cc.getSourceLines().isEmpty()) {
            int scanLimit = Math.min(60, cc.getSourceLines().size());
            for (int i = 0; i < scanLimit; i++) {
                String line = cc.getSourceLines().get(i).trim();
                if (line.startsWith("@RestController") || line.startsWith("@Controller") || line.startsWith("@RequestMapping"))
                    return LayerType.CONTROLLER;
                if (line.startsWith("@Service"))
                    return LayerType.SERVICE;
                if (line.startsWith("@Repository") || line.startsWith("@Mapper"))
                    return LayerType.REPOSITORY;
                if (line.startsWith("@Component") || line.startsWith("@EventListener"))
                    return LayerType.COMPONENT;
                if (line.startsWith("@Entity") || line.startsWith("@Table"))
                    return LayerType.ENTITY;
            }
        }

        return LayerType.UNKNOWN;
    }

    /**
     * Convenience: classify all and set layerType on each ClassCoverage
     */
    public void classifyAll(List<ClassCoverage> coverages) {
        for (ClassCoverage cc : coverages) {
            cc.setLayerType(classify(cc));
        }
    }
}
