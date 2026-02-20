package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class VillagerDataFix extends NamedEntityFix {

    public VillagerDataFix(Schema schema, String entityType) {
        super(schema, false, "Villager profession data fix (" + entityType + ")", References.ENTITY, entityType);
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        Dynamic<?> dynamic = (Dynamic) entity.get(DSL.remainderFinder());

        return entity.set(DSL.remainderFinder(), dynamic.remove("Profession").remove("Career").remove("CareerLevel").set("VillagerData", dynamic.createMap(ImmutableMap.of(dynamic.createString("type"), dynamic.createString("minecraft:plains"), dynamic.createString("profession"), dynamic.createString(upgradeData(dynamic.get("Profession").asInt(0), dynamic.get("Career").asInt(0))), dynamic.createString("level"), (Dynamic) DataFixUtils.orElse(dynamic.get("CareerLevel").result(), dynamic.createInt(1))))));
    }

    private static String upgradeData(int profession, int career) {
        return profession == 0 ? (career == 2 ? "minecraft:fisherman" : (career == 3 ? "minecraft:shepherd" : (career == 4 ? "minecraft:fletcher" : "minecraft:farmer"))) : (profession == 1 ? (career == 2 ? "minecraft:cartographer" : "minecraft:librarian") : (profession == 2 ? "minecraft:cleric" : (profession == 3 ? (career == 2 ? "minecraft:weaponsmith" : (career == 3 ? "minecraft:toolsmith" : "minecraft:armorer")) : (profession == 4 ? (career == 2 ? "minecraft:leatherworker" : "minecraft:butcher") : (profession == 5 ? "minecraft:nitwit" : "minecraft:none")))));
    }
}
