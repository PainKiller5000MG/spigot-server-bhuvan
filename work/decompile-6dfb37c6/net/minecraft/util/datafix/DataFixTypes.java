package net.minecraft.util.datafix;

import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Objects;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.fixes.References;

public enum DataFixTypes {

    LEVEL(References.LEVEL), LEVEL_SUMMARY(References.LIGHTWEIGHT_LEVEL), PLAYER(References.PLAYER), CHUNK(References.CHUNK), HOTBAR(References.HOTBAR), OPTIONS(References.OPTIONS), STRUCTURE(References.STRUCTURE), STATS(References.STATS), SAVED_DATA_COMMAND_STORAGE(References.SAVED_DATA_COMMAND_STORAGE), SAVED_DATA_FORCED_CHUNKS(References.SAVED_DATA_TICKETS), SAVED_DATA_MAP_DATA(References.SAVED_DATA_MAP_DATA), SAVED_DATA_MAP_INDEX(References.SAVED_DATA_MAP_INDEX), SAVED_DATA_RAIDS(References.SAVED_DATA_RAIDS), SAVED_DATA_RANDOM_SEQUENCES(References.SAVED_DATA_RANDOM_SEQUENCES), SAVED_DATA_SCOREBOARD(References.SAVED_DATA_SCOREBOARD), SAVED_DATA_STOPWATCHES(References.SAVED_DATA_STOPWATCHES), SAVED_DATA_STRUCTURE_FEATURE_INDICES(References.SAVED_DATA_STRUCTURE_FEATURE_INDICES), SAVED_DATA_WORLD_BORDER(References.SAVED_DATA_WORLD_BORDER), ADVANCEMENTS(References.ADVANCEMENTS), POI_CHUNK(References.POI_CHUNK), WORLD_GEN_SETTINGS(References.WORLD_GEN_SETTINGS), ENTITY_CHUNK(References.ENTITY_CHUNK), DEBUG_PROFILE(References.DEBUG_PROFILE);

    public static final Set<TypeReference> TYPES_FOR_LEVEL_LIST = Set.of(DataFixTypes.LEVEL_SUMMARY.type);
    private final TypeReference type;

    private DataFixTypes(TypeReference type) {
        this.type = type;
    }

    private static int currentVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    public <A> Codec<A> wrapCodec(final Codec<A> codec, final DataFixer dataFixer, final int defaultVersion) {
        return new Codec<A>() {
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return codec.encode(input, ops, prefix).flatMap((object) -> {
                    return ops.mergeToMap(object, ops.createString("DataVersion"), ops.createInt(DataFixTypes.currentVersion()));
                });
            }

            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                DataResult dataresult = ops.get(input, "DataVersion");

                Objects.requireNonNull(ops);
                int j = (Integer) dataresult.flatMap(ops::getNumberValue).map(Number::intValue).result().orElse(defaultVersion);
                Dynamic<T> dynamic = new Dynamic(ops, ops.remove(input, "DataVersion"));
                Dynamic<T> dynamic1 = DataFixTypes.this.updateToCurrentVersion(dataFixer, dynamic, j);

                return codec.decode(dynamic1);
            }
        };
    }

    public <T> Dynamic<T> update(DataFixer fixerUpper, Dynamic<T> input, int fromVersion, int toVersion) {
        return fixerUpper.update(this.type, input, fromVersion, toVersion);
    }

    public <T> Dynamic<T> updateToCurrentVersion(DataFixer fixerUpper, Dynamic<T> input, int dataVersion) {
        return this.update(fixerUpper, input, dataVersion, currentVersion());
    }

    public CompoundTag update(DataFixer fixer, CompoundTag tag, int fromVersion, int toVersion) {
        return (CompoundTag) this.update(fixer, new Dynamic(NbtOps.INSTANCE, tag), fromVersion, toVersion).getValue();
    }

    public CompoundTag updateToCurrentVersion(DataFixer fixer, CompoundTag tag, int fromVersion) {
        return this.update(fixer, tag, fromVersion, currentVersion());
    }
}
