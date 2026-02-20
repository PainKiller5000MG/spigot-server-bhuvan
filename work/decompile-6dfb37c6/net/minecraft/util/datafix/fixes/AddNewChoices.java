package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TaggedChoice;
import java.util.Locale;

public class AddNewChoices extends DataFix {

    private final String name;
    private final TypeReference type;

    public AddNewChoices(Schema outputSchema, String name, TypeReference type) {
        super(outputSchema, true);
        this.name = name;
        this.type = type;
    }

    public TypeRewriteRule makeRule() {
        TaggedChoice.TaggedChoiceType<?> taggedchoice_taggedchoicetype = this.getInputSchema().findChoiceType(this.type);
        TaggedChoice.TaggedChoiceType<?> taggedchoice_taggedchoicetype1 = this.getOutputSchema().findChoiceType(this.type);

        return this.cap(taggedchoice_taggedchoicetype, taggedchoice_taggedchoicetype1);
    }

    private <K> TypeRewriteRule cap(TaggedChoice.TaggedChoiceType<K> inputType, TaggedChoice.TaggedChoiceType<?> outputType) {
        if (inputType.getKeyType() != outputType.getKeyType()) {
            throw new IllegalStateException("Could not inject: key type is not the same");
        } else {
            return this.fixTypeEverywhere(this.name, inputType, outputType, (dynamicops) -> {
                return (pair) -> {
                    if (!outputType.hasType(pair.getFirst())) {
                        throw new IllegalArgumentException(String.format(Locale.ROOT, "%s: Unknown type %s in '%s'", this.name, pair.getFirst(), this.type.typeName()));
                    } else {
                        return pair;
                    }
                };
            });
        }
    }
}
