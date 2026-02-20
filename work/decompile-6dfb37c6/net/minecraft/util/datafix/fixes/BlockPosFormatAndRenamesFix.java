package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class BlockPosFormatAndRenamesFix extends DataFix {

    private static final List<String> PATROLLING_MOBS = List.of("minecraft:witch", "minecraft:ravager", "minecraft:pillager", "minecraft:illusioner", "minecraft:evoker", "minecraft:vindicator");

    public BlockPosFormatAndRenamesFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    private Typed<?> fixFields(Typed<?> typed, Map<String, String> fields) {
        return typed.update(DSL.remainderFinder(), (dynamic) -> {
            for (Map.Entry<String, String> map_entry : fields.entrySet()) {
                dynamic = dynamic.renameAndFixField((String) map_entry.getKey(), (String) map_entry.getValue(), ExtraDataFixUtils::fixBlockPos);
            }

            return dynamic;
        });
    }

    private <T> Dynamic<T> fixMapSavedData(Dynamic<T> data) {
        return data.update("frames", (dynamic1) -> {
            return dynamic1.createList(dynamic1.asStream().map((dynamic2) -> {
                dynamic2 = dynamic2.renameAndFixField("Pos", "pos", ExtraDataFixUtils::fixBlockPos);
                dynamic2 = dynamic2.renameField("Rotation", "rotation");
                dynamic2 = dynamic2.renameField("EntityId", "entity_id");
                return dynamic2;
            }));
        }).update("banners", (dynamic1) -> {
            return dynamic1.createList(dynamic1.asStream().map((dynamic2) -> {
                dynamic2 = dynamic2.renameField("Pos", "pos");
                dynamic2 = dynamic2.renameField("Color", "color");
                dynamic2 = dynamic2.renameField("Name", "name");
                return dynamic2;
            }));
        });
    }

    public TypeRewriteRule makeRule() {
        List<TypeRewriteRule> list = new ArrayList();

        this.addEntityRules(list);
        this.addBlockEntityRules(list);
        list.add(this.writeFixAndRead("BlockPos format for map frames", this.getInputSchema().getType(References.SAVED_DATA_MAP_DATA), this.getOutputSchema().getType(References.SAVED_DATA_MAP_DATA), (dynamic) -> {
            return dynamic.update("data", this::fixMapSavedData);
        }));
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);

        list.add(this.fixTypeEverywhereTyped("BlockPos format for compass target", type, ItemStackTagFix.createFixer(type, "minecraft:compass"::equals, (typed) -> {
            return typed.update(DSL.remainderFinder(), (dynamic) -> {
                return dynamic.update("LodestonePos", ExtraDataFixUtils::fixBlockPos);
            });
        })));
        return TypeRewriteRule.seq(list);
    }

    private void addEntityRules(List<TypeRewriteRule> rules) {
        rules.add(this.createEntityFixer(References.ENTITY, "minecraft:bee", Map.of("HivePos", "hive_pos", "FlowerPos", "flower_pos")));
        rules.add(this.createEntityFixer(References.ENTITY, "minecraft:end_crystal", Map.of("BeamTarget", "beam_target")));
        rules.add(this.createEntityFixer(References.ENTITY, "minecraft:wandering_trader", Map.of("WanderTarget", "wander_target")));

        for (String s : BlockPosFormatAndRenamesFix.PATROLLING_MOBS) {
            rules.add(this.createEntityFixer(References.ENTITY, s, Map.of("PatrolTarget", "patrol_target")));
        }

        rules.add(this.fixTypeEverywhereTyped("BlockPos format in Leash for mobs", this.getInputSchema().getType(References.ENTITY), (typed) -> {
            return typed.update(DSL.remainderFinder(), (dynamic) -> {
                return dynamic.renameAndFixField("Leash", "leash", ExtraDataFixUtils::fixBlockPos);
            });
        }));
    }

    private void addBlockEntityRules(List<TypeRewriteRule> rules) {
        rules.add(this.createEntityFixer(References.BLOCK_ENTITY, "minecraft:beehive", Map.of("FlowerPos", "flower_pos")));
        rules.add(this.createEntityFixer(References.BLOCK_ENTITY, "minecraft:end_gateway", Map.of("ExitPortal", "exit_portal")));
    }

    private TypeRewriteRule createEntityFixer(TypeReference type, String entityName, Map<String, String> fields) {
        String s1 = "BlockPos format in " + String.valueOf(fields.keySet()) + " for " + entityName + " (" + type.typeName() + ")";
        OpticFinder<?> opticfinder = DSL.namedChoice(entityName, this.getInputSchema().getChoiceType(type, entityName));

        return this.fixTypeEverywhereTyped(s1, this.getInputSchema().getType(type), (typed) -> {
            return typed.updateTyped(opticfinder, (typed1) -> {
                return this.fixFields(typed1, fields);
            });
        });
    }
}
