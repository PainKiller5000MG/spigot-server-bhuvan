package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.context.ContextKeySet;

public class ValidationContext {

    private final ProblemReporter reporter;
    private final ContextKeySet contextKeySet;
    private final Optional<HolderGetter.Provider> resolver;
    private final Set<ResourceKey<?>> visitedElements;

    public ValidationContext(ProblemReporter reporter, ContextKeySet contextKeySet, HolderGetter.Provider resolver) {
        this(reporter, contextKeySet, Optional.of(resolver), Set.of());
    }

    public ValidationContext(ProblemReporter reporter, ContextKeySet contextKeySet) {
        this(reporter, contextKeySet, Optional.empty(), Set.of());
    }

    private ValidationContext(ProblemReporter reporter, ContextKeySet contextKeySet, Optional<HolderGetter.Provider> resolver, Set<ResourceKey<?>> visitedElements) {
        this.reporter = reporter;
        this.contextKeySet = contextKeySet;
        this.resolver = resolver;
        this.visitedElements = visitedElements;
    }

    public ValidationContext forChild(ProblemReporter.PathElement subContext) {
        return new ValidationContext(this.reporter.forChild(subContext), this.contextKeySet, this.resolver, this.visitedElements);
    }

    public ValidationContext enterElement(ProblemReporter.PathElement subContext, ResourceKey<?> element) {
        Set<ResourceKey<?>> set = ImmutableSet.builder().addAll(this.visitedElements).add(element).build();

        return new ValidationContext(this.reporter.forChild(subContext), this.contextKeySet, this.resolver, set);
    }

    public boolean hasVisitedElement(ResourceKey<?> element) {
        return this.visitedElements.contains(element);
    }

    public void reportProblem(ProblemReporter.Problem description) {
        this.reporter.report(description);
    }

    public void validateContextUsage(LootContextUser lootContextUser) {
        Set<ContextKey<?>> set = lootContextUser.getReferencedContextParams();
        Set<ContextKey<?>> set1 = Sets.difference(set, this.contextKeySet.allowed());

        if (!set1.isEmpty()) {
            this.reporter.report(new ValidationContext.ParametersNotProvidedProblem(set1));
        }

    }

    public HolderGetter.Provider resolver() {
        return (HolderGetter.Provider) this.resolver.orElseThrow(() -> {
            return new UnsupportedOperationException("References not allowed");
        });
    }

    public boolean allowsReferences() {
        return this.resolver.isPresent();
    }

    public ValidationContext setContextKeySet(ContextKeySet contextKeySet) {
        return new ValidationContext(this.reporter, contextKeySet, this.resolver, this.visitedElements);
    }

    public ProblemReporter reporter() {
        return this.reporter;
    }

    public static record ParametersNotProvidedProblem(Set<ContextKey<?>> notProvided) implements ProblemReporter.Problem {

        @Override
        public String description() {
            return "Parameters " + String.valueOf(this.notProvided) + " are not provided in this context";
        }
    }

    public static record ReferenceNotAllowedProblem(ResourceKey<?> referenced) implements ProblemReporter.Problem {

        @Override
        public String description() {
            String s = String.valueOf(this.referenced.identifier());

            return "Reference to " + s + " of type " + String.valueOf(this.referenced.registry()) + " was used, but references are not allowed";
        }
    }

    public static record RecursiveReferenceProblem(ResourceKey<?> referenced) implements ProblemReporter.Problem {

        @Override
        public String description() {
            String s = String.valueOf(this.referenced.identifier());

            return s + " of type " + String.valueOf(this.referenced.registry()) + " is recursively called";
        }
    }

    public static record MissingReferenceProblem(ResourceKey<?> referenced) implements ProblemReporter.Problem {

        @Override
        public String description() {
            String s = String.valueOf(this.referenced.identifier());

            return "Missing element " + s + " of type " + String.valueOf(this.referenced.registry());
        }
    }
}
