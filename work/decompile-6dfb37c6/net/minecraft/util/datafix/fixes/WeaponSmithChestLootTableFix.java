package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class WeaponSmithChestLootTableFix extends NamedEntityFix {

    public WeaponSmithChestLootTableFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "WeaponSmithChestLootTableFix", References.BLOCK_ENTITY, "minecraft:chest");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), (dynamic) -> {
            String s = dynamic.get("LootTable").asString("");

            return s.equals("minecraft:chests/village_blacksmith") ? dynamic.set("LootTable", dynamic.createString("minecraft:chests/village/village_weaponsmith")) : dynamic;
        });
    }
}
