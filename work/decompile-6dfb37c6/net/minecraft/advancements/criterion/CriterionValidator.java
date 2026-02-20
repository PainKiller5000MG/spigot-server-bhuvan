package net.minecraft.advancements.criterion;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public class CriterionValidator {

    private final ProblemReporter reporter;
    private final HolderGetter.Provider lootData;

    public CriterionValidator(ProblemReporter reporter, HolderGetter.Provider lootData) {
        this.reporter = reporter;
        this.lootData = lootData;
    }

    public void validateEntity(Optional<ContextAwarePredicate> predicate, String fieldName) {
        predicate.ifPresent((contextawarepredicate) -> {
            this.validateEntity(contextawarepredicate, fieldName);
        });
    }

    public void validateEntities(List<ContextAwarePredicate> predicates, String fieldName) {
        this.validate(predicates, LootContextParamSets.ADVANCEMENT_ENTITY, fieldName);
    }

    public void validateEntity(ContextAwarePredicate predicate, String fieldName) {
        this.validate(predicate, LootContextParamSets.ADVANCEMENT_ENTITY, fieldName);
    }

    public void validate(ContextAwarePredicate predicate, ContextKeySet params, String fieldName) {
        predicate.validate(new ValidationContext(this.reporter.forChild(new ProblemReporter.FieldPathElement(fieldName)), params, this.lootData));
    }

    public void validate(List<ContextAwarePredicate> predicates, ContextKeySet params, String fieldName) {
        for (int i = 0; i < predicates.size(); ++i) {
            ContextAwarePredicate contextawarepredicate = (ContextAwarePredicate) predicates.get(i);

            contextawarepredicate.validate(new ValidationContext(this.reporter.forChild(new ProblemReporter.IndexedFieldPathElement(fieldName, i)), params, this.lootData));
        }

    }
}
