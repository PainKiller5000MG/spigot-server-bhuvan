package net.minecraft.world.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EquipmentTable;

public record SpawnData(CompoundTag entityToSpawn, Optional<SpawnData.CustomSpawnRules> customSpawnRules, Optional<EquipmentTable> equipment) {

    public static final String ENTITY_TAG = "entity";
    public static final Codec<SpawnData> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(CompoundTag.CODEC.fieldOf("entity").forGetter((spawndata) -> {
            return spawndata.entityToSpawn;
        }), SpawnData.CustomSpawnRules.CODEC.optionalFieldOf("custom_spawn_rules").forGetter((spawndata) -> {
            return spawndata.customSpawnRules;
        }), EquipmentTable.CODEC.optionalFieldOf("equipment").forGetter((spawndata) -> {
            return spawndata.equipment;
        })).apply(instance, SpawnData::new);
    });
    public static final Codec<WeightedList<SpawnData>> LIST_CODEC = WeightedList.codec(SpawnData.CODEC);

    public SpawnData() {
        this(new CompoundTag(), Optional.empty(), Optional.empty());
    }

    public SpawnData {
        Optional<Identifier> optional = entityToSpawn.read("id", Identifier.CODEC);

        if (optional.isPresent()) {
            entityToSpawn.store("id", Identifier.CODEC, (Identifier) optional.get());
        } else {
            entityToSpawn.remove("id");
        }

    }

    public CompoundTag getEntityToSpawn() {
        return this.entityToSpawn;
    }

    public Optional<SpawnData.CustomSpawnRules> getCustomSpawnRules() {
        return this.customSpawnRules;
    }

    public Optional<EquipmentTable> getEquipment() {
        return this.equipment;
    }

    public static record CustomSpawnRules(InclusiveRange<Integer> blockLightLimit, InclusiveRange<Integer> skyLightLimit) {

        private static final InclusiveRange<Integer> LIGHT_RANGE = new InclusiveRange<Integer>(0, 15);
        public static final Codec<SpawnData.CustomSpawnRules> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(lightLimit("block_light_limit").forGetter((spawndata_customspawnrules) -> {
                return spawndata_customspawnrules.blockLightLimit;
            }), lightLimit("sky_light_limit").forGetter((spawndata_customspawnrules) -> {
                return spawndata_customspawnrules.skyLightLimit;
            })).apply(instance, SpawnData.CustomSpawnRules::new);
        });

        private static DataResult<InclusiveRange<Integer>> checkLightBoundaries(InclusiveRange<Integer> range) {
            return !SpawnData.CustomSpawnRules.LIGHT_RANGE.contains(range) ? DataResult.error(() -> {
                return "Light values must be withing range " + String.valueOf(SpawnData.CustomSpawnRules.LIGHT_RANGE);
            }) : DataResult.success(range);
        }

        private static MapCodec<InclusiveRange<Integer>> lightLimit(String name) {
            return InclusiveRange.INT.lenientOptionalFieldOf(name, SpawnData.CustomSpawnRules.LIGHT_RANGE).validate(SpawnData.CustomSpawnRules::checkLightBoundaries);
        }

        public boolean isValidPosition(BlockPos blockSpawnPos, ServerLevel level) {
            return this.blockLightLimit.isValueInRange(level.getBrightness(LightLayer.BLOCK, blockSpawnPos)) && this.skyLightLimit.isValueInRange(level.getBrightness(LightLayer.SKY, blockSpawnPos));
        }
    }
}
