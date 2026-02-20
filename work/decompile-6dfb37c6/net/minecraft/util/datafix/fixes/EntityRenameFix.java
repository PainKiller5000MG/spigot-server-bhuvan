package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TaggedChoice;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DynamicOps;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public abstract class EntityRenameFix extends DataFix {

    protected final String name;

    public EntityRenameFix(String name, Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
        this.name = name;
    }

    public TypeRewriteRule makeRule() {
        TaggedChoice.TaggedChoiceType<String> taggedchoice_taggedchoicetype = this.getInputSchema().findChoiceType(References.ENTITY);
        TaggedChoice.TaggedChoiceType<String> taggedchoice_taggedchoicetype1 = this.getOutputSchema().findChoiceType(References.ENTITY);
        Function<String, Type<?>> function = Util.memoize((s) -> {
            Type<?> type = (Type) taggedchoice_taggedchoicetype.types().get(s);

            return ExtraDataFixUtils.patchSubType(type, taggedchoice_taggedchoicetype, taggedchoice_taggedchoicetype1);
        });

        return this.fixTypeEverywhere(this.name, taggedchoice_taggedchoicetype, taggedchoice_taggedchoicetype1, (dynamicops) -> {
            return (pair) -> {
                String s = (String) pair.getFirst();
                Type<?> type = (Type) function.apply(s);
                Pair<String, Typed<?>> pair1 = this.fix(s, this.getEntity(pair.getSecond(), dynamicops, type));
                Type<?> type1 = (Type) taggedchoice_taggedchoicetype1.types().get(pair1.getFirst());

                if (!type1.equals(((Typed) pair1.getSecond()).getType(), true, true)) {
                    throw new IllegalStateException(String.format(Locale.ROOT, "Dynamic type check failed: %s not equal to %s", type1, ((Typed) pair1.getSecond()).getType()));
                } else {
                    return Pair.of((String) pair1.getFirst(), ((Typed) pair1.getSecond()).getValue());
                }
            };
        });
    }

    private <A> Typed<A> getEntity(Object input, DynamicOps<?> ops, Type<A> oldEntityType) {
        return new Typed(oldEntityType, ops, input);
    }

    protected abstract Pair<String, Typed<?>> fix(String name, Typed<?> entity);
}
