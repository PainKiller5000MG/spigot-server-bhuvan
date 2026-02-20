package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class WallPropertyFix extends DataFix {

    private static final Set<String> WALL_BLOCKS = ImmutableSet.of("minecraft:andesite_wall", "minecraft:brick_wall", "minecraft:cobblestone_wall", "minecraft:diorite_wall", "minecraft:end_stone_brick_wall", "minecraft:granite_wall", new String[]{"minecraft:mossy_cobblestone_wall", "minecraft:mossy_stone_brick_wall", "minecraft:nether_brick_wall", "minecraft:prismarine_wall", "minecraft:red_nether_brick_wall", "minecraft:red_sandstone_wall", "minecraft:sandstone_wall", "minecraft:stone_brick_wall"});

    public WallPropertyFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("WallPropertyFix", this.getInputSchema().getType(References.BLOCK_STATE), (typed) -> {
            return typed.update(DSL.remainderFinder(), WallPropertyFix::upgradeBlockStateTag);
        });
    }

    private static String mapProperty(String value) {
        return "true".equals(value) ? "low" : "none";
    }

    private static <T> Dynamic<T> fixWallProperty(Dynamic<T> state, String property) {
        return state.update(property, (dynamic1) -> {
            Optional optional = dynamic1.asString().result().map(WallPropertyFix::mapProperty);

            Objects.requireNonNull(dynamic1);
            return (Dynamic) DataFixUtils.orElse(optional.map(dynamic1::createString), dynamic1);
        });
    }

    private static <T> Dynamic<T> upgradeBlockStateTag(Dynamic<T> state) {
        Optional optional = state.get("Name").asString().result();
        Set set = WallPropertyFix.WALL_BLOCKS;

        Objects.requireNonNull(set);
        boolean flag = optional.filter(set::contains).isPresent();

        return !flag ? state : state.update("Properties", (dynamic1) -> {
            Dynamic<?> dynamic2 = fixWallProperty(dynamic1, "east");

            dynamic2 = fixWallProperty(dynamic2, "west");
            dynamic2 = fixWallProperty(dynamic2, "north");
            return fixWallProperty(dynamic2, "south");
        });
    }
}
