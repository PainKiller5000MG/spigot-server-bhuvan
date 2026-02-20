package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class MobSpawnSettings {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float DEFAULT_CREATURE_SPAWN_PROBABILITY = 0.1F;
    public static final WeightedList<MobSpawnSettings.SpawnerData> EMPTY_MOB_LIST = WeightedList.<MobSpawnSettings.SpawnerData>of();
    public static final MobSpawnSettings EMPTY = (new MobSpawnSettings.Builder()).build();
    public static final MapCodec<MobSpawnSettings> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        RecordCodecBuilder recordcodecbuilder = Codec.floatRange(0.0F, 0.9999999F).optionalFieldOf("creature_spawn_probability", 0.1F).forGetter((mobspawnsettings) -> {
            return mobspawnsettings.creatureGenerationProbability;
        });
        Codec codec = MobCategory.CODEC;
        Codec codec1 = WeightedList.codec(MobSpawnSettings.SpawnerData.CODEC);
        Logger logger = MobSpawnSettings.LOGGER;

        Objects.requireNonNull(logger);
        return instance.group(recordcodecbuilder, Codec.simpleMap(codec, codec1.promotePartial(Util.prefix("Spawn data: ", logger::error)), StringRepresentable.keys(MobCategory.values())).fieldOf("spawners").forGetter((mobspawnsettings) -> {
            return mobspawnsettings.spawners;
        }), Codec.simpleMap(BuiltInRegistries.ENTITY_TYPE.byNameCodec(), MobSpawnSettings.MobSpawnCost.CODEC, BuiltInRegistries.ENTITY_TYPE).fieldOf("spawn_costs").forGetter((mobspawnsettings) -> {
            return mobspawnsettings.mobSpawnCosts;
        })).apply(instance, MobSpawnSettings::new);
    });
    private final float creatureGenerationProbability;
    private final Map<MobCategory, WeightedList<MobSpawnSettings.SpawnerData>> spawners;
    private final Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts;

    private MobSpawnSettings(float creatureGenerationProbability, Map<MobCategory, WeightedList<MobSpawnSettings.SpawnerData>> spawners, Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts) {
        this.creatureGenerationProbability = creatureGenerationProbability;
        this.spawners = ImmutableMap.copyOf(spawners);
        this.mobSpawnCosts = ImmutableMap.copyOf(mobSpawnCosts);
    }

    public WeightedList<MobSpawnSettings.SpawnerData> getMobs(MobCategory category) {
        return (WeightedList) this.spawners.getOrDefault(category, MobSpawnSettings.EMPTY_MOB_LIST);
    }

    public MobSpawnSettings.@Nullable MobSpawnCost getMobSpawnCost(EntityType<?> type) {
        return (MobSpawnSettings.MobSpawnCost) this.mobSpawnCosts.get(type);
    }

    public float getCreatureProbability() {
        return this.creatureGenerationProbability;
    }

    public static record SpawnerData(EntityType<?> type, int minCount, int maxCount) {

        public static final MapCodec<MobSpawnSettings.SpawnerData> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type").forGetter((mobspawnsettings_spawnerdata) -> {
                return mobspawnsettings_spawnerdata.type;
            }), ExtraCodecs.POSITIVE_INT.fieldOf("minCount").forGetter((mobspawnsettings_spawnerdata) -> {
                return mobspawnsettings_spawnerdata.minCount;
            }), ExtraCodecs.POSITIVE_INT.fieldOf("maxCount").forGetter((mobspawnsettings_spawnerdata) -> {
                return mobspawnsettings_spawnerdata.maxCount;
            })).apply(instance, MobSpawnSettings.SpawnerData::new);
        }).validate((mobspawnsettings_spawnerdata) -> {
            return mobspawnsettings_spawnerdata.minCount > mobspawnsettings_spawnerdata.maxCount ? DataResult.error(() -> {
                return "minCount needs to be smaller or equal to maxCount";
            }) : DataResult.success(mobspawnsettings_spawnerdata);
        });

        public SpawnerData(EntityType<?> type, int minCount, int maxCount) {
            type = type.getCategory() == MobCategory.MISC ? EntityType.PIG : type;
            this.type = type;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }

        public String toString() {
            String s = String.valueOf(EntityType.getKey(this.type));

            return s + "*(" + this.minCount + "-" + this.maxCount + ")";
        }
    }

    public static record MobSpawnCost(double energyBudget, double charge) {

        public static final Codec<MobSpawnSettings.MobSpawnCost> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.DOUBLE.fieldOf("energy_budget").forGetter((mobspawnsettings_mobspawncost) -> {
                return mobspawnsettings_mobspawncost.energyBudget;
            }), Codec.DOUBLE.fieldOf("charge").forGetter((mobspawnsettings_mobspawncost) -> {
                return mobspawnsettings_mobspawncost.charge;
            })).apply(instance, MobSpawnSettings.MobSpawnCost::new);
        });
    }

    public static class Builder {

        private final Map<MobCategory, WeightedList.Builder<MobSpawnSettings.SpawnerData>> spawners = Util.<MobCategory, WeightedList.Builder<MobSpawnSettings.SpawnerData>>makeEnumMap(MobCategory.class, (mobcategory) -> {
            return WeightedList.builder();
        });
        private final Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts = Maps.newLinkedHashMap();
        private float creatureGenerationProbability = 0.1F;

        public Builder() {}

        public MobSpawnSettings.Builder addSpawn(MobCategory category, int weight, MobSpawnSettings.SpawnerData spawnerData) {
            ((WeightedList.Builder) this.spawners.get(category)).add(spawnerData, weight);
            return this;
        }

        public MobSpawnSettings.Builder addMobCharge(EntityType<?> type, double charge, double energyBudget) {
            this.mobSpawnCosts.put(type, new MobSpawnSettings.MobSpawnCost(energyBudget, charge));
            return this;
        }

        public MobSpawnSettings.Builder creatureGenerationProbability(float creatureGenerationProbability) {
            this.creatureGenerationProbability = creatureGenerationProbability;
            return this;
        }

        public MobSpawnSettings build() {
            return new MobSpawnSettings(this.creatureGenerationProbability, (Map) this.spawners.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, (entry) -> {
                return ((WeightedList.Builder) entry.getValue()).build();
            })), ImmutableMap.copyOf(this.mobSpawnCosts));
        }
    }
}
