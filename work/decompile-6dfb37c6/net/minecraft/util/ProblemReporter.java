package net.minecraft.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public interface ProblemReporter {

    ProblemReporter DISCARDING = new ProblemReporter() {
        @Override
        public ProblemReporter forChild(ProblemReporter.PathElement path) {
            return this;
        }

        @Override
        public void report(ProblemReporter.Problem problem) {}
    };

    ProblemReporter forChild(ProblemReporter.PathElement path);

    void report(ProblemReporter.Problem problem);

    public static record RootFieldPathElement(String name) implements ProblemReporter.PathElement {

        @Override
        public String get() {
            return this.name;
        }
    }

    public static record RootElementPathElement(ResourceKey<?> id) implements ProblemReporter.PathElement {

        @Override
        public String get() {
            String s = String.valueOf(this.id.identifier());

            return "{" + s + "@" + String.valueOf(this.id.registry()) + "}";
        }
    }

    public static record FieldPathElement(String name) implements ProblemReporter.PathElement {

        @Override
        public String get() {
            return "." + this.name;
        }
    }

    public static record IndexedFieldPathElement(String name, int index) implements ProblemReporter.PathElement {

        @Override
        public String get() {
            return "." + this.name + "[" + this.index + "]";
        }
    }

    public static record IndexedPathElement(int index) implements ProblemReporter.PathElement {

        @Override
        public String get() {
            return "[" + this.index + "]";
        }
    }

    public static record ElementReferencePathElement(ResourceKey<?> id) implements ProblemReporter.PathElement {

        @Override
        public String get() {
            String s = String.valueOf(this.id.identifier());

            return "->{" + s + "@" + String.valueOf(this.id.registry()) + "}";
        }
    }

    public static class Collector implements ProblemReporter {

        public static final ProblemReporter.PathElement EMPTY_ROOT = () -> {
            return "";
        };
        private final ProblemReporter.@Nullable Collector parent;
        private final ProblemReporter.PathElement element;
        private final Set<ProblemReporter.Collector.Entry> problems;

        public Collector() {
            this(ProblemReporter.Collector.EMPTY_ROOT);
        }

        public Collector(ProblemReporter.PathElement root) {
            this.parent = null;
            this.problems = new LinkedHashSet();
            this.element = root;
        }

        private Collector(ProblemReporter.Collector parent, ProblemReporter.PathElement path) {
            this.problems = parent.problems;
            this.parent = parent;
            this.element = path;
        }

        @Override
        public ProblemReporter forChild(ProblemReporter.PathElement path) {
            return new ProblemReporter.Collector(this, path);
        }

        @Override
        public void report(ProblemReporter.Problem problem) {
            this.problems.add(new ProblemReporter.Collector.Entry(this, problem));
        }

        public boolean isEmpty() {
            return this.problems.isEmpty();
        }

        public void forEach(BiConsumer<String, ProblemReporter.Problem> output) {
            List<ProblemReporter.PathElement> list = new ArrayList();
            StringBuilder stringbuilder = new StringBuilder();

            for (ProblemReporter.Collector.Entry problemreporter_collector_entry : this.problems) {
                for (ProblemReporter.Collector problemreporter_collector = problemreporter_collector_entry.source; problemreporter_collector != null; problemreporter_collector = problemreporter_collector.parent) {
                    list.add(problemreporter_collector.element);
                }

                for (int i = list.size() - 1; i >= 0; --i) {
                    stringbuilder.append(((ProblemReporter.PathElement) list.get(i)).get());
                }

                output.accept(stringbuilder.toString(), problemreporter_collector_entry.problem());
                stringbuilder.setLength(0);
                list.clear();
            }

        }

        public String getReport() {
            Multimap<String, ProblemReporter.Problem> multimap = HashMultimap.create();

            Objects.requireNonNull(multimap);
            this.forEach(multimap::put);
            return (String) multimap.asMap().entrySet().stream().map((java_util_map_entry) -> {
                String s = (String) java_util_map_entry.getKey();

                return " at " + s + ": " + (String) ((Collection) java_util_map_entry.getValue()).stream().map(ProblemReporter.Problem::description).collect(Collectors.joining("; "));
            }).collect(Collectors.joining("\n"));
        }

        public String getTreeReport() {
            List<ProblemReporter.PathElement> list = new ArrayList();
            ProblemReporter.Collector.ProblemTreeNode problemreporter_collector_problemtreenode = new ProblemReporter.Collector.ProblemTreeNode(this.element);

            for (ProblemReporter.Collector.Entry problemreporter_collector_entry : this.problems) {
                for (ProblemReporter.Collector problemreporter_collector = problemreporter_collector_entry.source; problemreporter_collector != this; problemreporter_collector = problemreporter_collector.parent) {
                    list.add(problemreporter_collector.element);
                }

                ProblemReporter.Collector.ProblemTreeNode problemreporter_collector_problemtreenode1 = problemreporter_collector_problemtreenode;

                for (int i = list.size() - 1; i >= 0; --i) {
                    problemreporter_collector_problemtreenode1 = problemreporter_collector_problemtreenode1.child((ProblemReporter.PathElement) list.get(i));
                }

                list.clear();
                problemreporter_collector_problemtreenode1.problems.add(problemreporter_collector_entry.problem);
            }

            return String.join("\n", problemreporter_collector_problemtreenode.getLines());
        }

        private static record Entry(ProblemReporter.Collector source, ProblemReporter.Problem problem) {

        }

        private static record ProblemTreeNode(ProblemReporter.PathElement element, List<ProblemReporter.Problem> problems, Map<ProblemReporter.PathElement, ProblemReporter.Collector.ProblemTreeNode> children) {

            public ProblemTreeNode(ProblemReporter.PathElement pathElement) {
                this(pathElement, new ArrayList(), new LinkedHashMap());
            }

            public ProblemReporter.Collector.ProblemTreeNode child(ProblemReporter.PathElement id) {
                return (ProblemReporter.Collector.ProblemTreeNode) this.children.computeIfAbsent(id, ProblemReporter.Collector.ProblemTreeNode::new);
            }

            public List<String> getLines() {
                int i = this.problems.size();
                int j = this.children.size();

                if (i == 0 && j == 0) {
                    return List.of();
                } else if (i == 0 && j == 1) {
                    List<String> list = new ArrayList();

                    this.children.forEach((problemreporter_pathelement, problemreporter_collector_problemtreenode) -> {
                        list.addAll(problemreporter_collector_problemtreenode.getLines());
                    });
                    String s = this.element.get();

                    list.set(0, s + (String) list.get(0));
                    return list;
                } else if (i == 1 && j == 0) {
                    String s1 = this.element.get();

                    return List.of(s1 + ": " + ((ProblemReporter.Problem) this.problems.getFirst()).description());
                } else {
                    List<String> list1 = new ArrayList();

                    this.children.forEach((problemreporter_pathelement, problemreporter_collector_problemtreenode) -> {
                        list1.addAll(problemreporter_collector_problemtreenode.getLines());
                    });
                    list1.replaceAll((s2) -> {
                        return "  " + s2;
                    });

                    for (ProblemReporter.Problem problemreporter_problem : this.problems) {
                        list1.add("  " + problemreporter_problem.description());
                    }

                    list1.addFirst(this.element.get() + ":");
                    return list1;
                }
            }
        }
    }

    public static class ScopedCollector extends ProblemReporter.Collector implements AutoCloseable {

        private final Logger logger;

        public ScopedCollector(Logger logger) {
            this.logger = logger;
        }

        public ScopedCollector(ProblemReporter.PathElement root, Logger logger) {
            super(root);
            this.logger = logger;
        }

        public void close() {
            if (!this.isEmpty()) {
                this.logger.warn("[{}] Serialization errors:\n{}", this.logger.getName(), this.getTreeReport());
            }

        }
    }

    @FunctionalInterface
    public interface PathElement {

        String get();
    }

    public interface Problem {

        String description();
    }
}
