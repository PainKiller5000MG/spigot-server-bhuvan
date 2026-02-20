package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public abstract class BlockRenameFix extends DataFix {

    private final String name;

    public BlockRenameFix(Schema outputSchema, String name) {
        super(outputSchema, false);
        this.name = name;
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_NAME);
        Type<Pair<String, String>> type1 = DSL.named(References.BLOCK_NAME.typeName(), NamespacedSchema.namespacedString());

        if (!Objects.equals(type, type1)) {
            throw new IllegalStateException("block type is not what was expected.");
        } else {
            TypeRewriteRule typerewriterule = this.fixTypeEverywhere(this.name + " for block", type1, (dynamicops) -> {
                return (pair) -> {
                    return pair.mapSecond(this::renameBlock);
                };
            });
            TypeRewriteRule typerewriterule1 = this.fixTypeEverywhereTyped(this.name + " for block_state", this.getInputSchema().getType(References.BLOCK_STATE), (typed) -> {
                return typed.update(DSL.remainderFinder(), this::fixBlockState);
            });
            TypeRewriteRule typerewriterule2 = this.fixTypeEverywhereTyped(this.name + " for flat_block_state", this.getInputSchema().getType(References.FLAT_BLOCK_STATE), (typed) -> {
                return typed.update(DSL.remainderFinder(), (dynamic) -> {
                    Optional optional = dynamic.asString().result().map(this::fixFlatBlockState);

                    Objects.requireNonNull(dynamic);
                    return (Dynamic) DataFixUtils.orElse(optional.map(dynamic::createString), dynamic);
                });
            });

            return TypeRewriteRule.seq(typerewriterule, new TypeRewriteRule[]{typerewriterule1, typerewriterule2});
        }
    }

    private Dynamic<?> fixBlockState(Dynamic<?> tag) {
        Optional<String> optional = tag.get("Name").asString().result();

        return optional.isPresent() ? tag.set("Name", tag.createString(this.renameBlock((String) optional.get()))) : tag;
    }

    private String fixFlatBlockState(String string) {
        int i = string.indexOf(91);
        int j = string.indexOf(123);
        int k = string.length();

        if (i > 0) {
            k = i;
        }

        if (j > 0) {
            k = Math.min(k, j);
        }

        String s1 = string.substring(0, k);
        String s2 = this.renameBlock(s1);

        return s2 + string.substring(k);
    }

    protected abstract String renameBlock(String block);

    public static DataFix create(Schema outputSchema, String name, final Function<String, String> renamer) {
        return new BlockRenameFix(outputSchema, name) {
            @Override
            protected String renameBlock(String block) {
                return (String) renamer.apply(block);
            }
        };
    }
}
