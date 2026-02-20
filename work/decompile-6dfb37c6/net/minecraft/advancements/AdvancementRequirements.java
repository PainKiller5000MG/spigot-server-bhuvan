package net.minecraft.advancements;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.network.FriendlyByteBuf;

public record AdvancementRequirements(List<List<String>> requirements) {

    public static final Codec<AdvancementRequirements> CODEC = Codec.STRING.listOf().listOf().xmap(AdvancementRequirements::new, AdvancementRequirements::requirements);
    public static final AdvancementRequirements EMPTY = new AdvancementRequirements(List.of());

    public AdvancementRequirements(FriendlyByteBuf input) {
        this(input.readList((friendlybytebuf1) -> {
            return friendlybytebuf1.readList(FriendlyByteBuf::readUtf);
        }));
    }

    public void write(FriendlyByteBuf output) {
        output.writeCollection(this.requirements, (friendlybytebuf1, list) -> {
            friendlybytebuf1.writeCollection(list, FriendlyByteBuf::writeUtf);
        });
    }

    public static AdvancementRequirements allOf(Collection<String> criteria) {
        return new AdvancementRequirements(criteria.stream().map(List::of).toList());
    }

    public static AdvancementRequirements anyOf(Collection<String> criteria) {
        return new AdvancementRequirements(List.of(List.copyOf(criteria)));
    }

    public int size() {
        return this.requirements.size();
    }

    public boolean test(Predicate<String> predicate) {
        if (this.requirements.isEmpty()) {
            return false;
        } else {
            for (List<String> list : this.requirements) {
                if (!anyMatch(list, predicate)) {
                    return false;
                }
            }

            return true;
        }
    }

    public int count(Predicate<String> predicate) {
        int i = 0;

        for (List<String> list : this.requirements) {
            if (anyMatch(list, predicate)) {
                ++i;
            }
        }

        return i;
    }

    private static boolean anyMatch(List<String> criteria, Predicate<String> predicate) {
        for (String s : criteria) {
            if (predicate.test(s)) {
                return true;
            }
        }

        return false;
    }

    public DataResult<AdvancementRequirements> validate(Set<String> expectedCriteria) {
        Set<String> set1 = new ObjectOpenHashSet();

        for (List<String> list : this.requirements) {
            if (list.isEmpty() && expectedCriteria.isEmpty()) {
                return DataResult.error(() -> {
                    return "Requirement entry cannot be empty";
                });
            }

            set1.addAll(list);
        }

        if (!expectedCriteria.equals(set1)) {
            Set<String> set2 = Sets.difference(expectedCriteria, set1);
            Set<String> set3 = Sets.difference(set1, expectedCriteria);

            return DataResult.error(() -> {
                String s = String.valueOf(set2);

                return "Advancement completion requirements did not exactly match specified criteria. Missing: " + s + ". Unknown: " + String.valueOf(set3);
            });
        } else {
            return DataResult.success(this);
        }
    }

    public boolean isEmpty() {
        return this.requirements.isEmpty();
    }

    public String toString() {
        return this.requirements.toString();
    }

    public Set<String> names() {
        Set<String> set = new ObjectOpenHashSet();

        for (List<String> list : this.requirements) {
            set.addAll(list);
        }

        return set;
    }

    public interface Strategy {

        AdvancementRequirements.Strategy AND = AdvancementRequirements::allOf;
        AdvancementRequirements.Strategy OR = AdvancementRequirements::anyOf;

        AdvancementRequirements create(Collection<String> criteria);
    }
}
