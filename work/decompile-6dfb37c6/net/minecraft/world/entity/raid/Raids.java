package net.minecraft.world.entity.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Raids extends SavedData {

    private static final String RAID_FILE_ID = "raids";
    public static final Codec<Raids> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Raids.RaidWithId.CODEC.listOf().optionalFieldOf("raids", List.of()).forGetter((raids) -> {
            return raids.raidMap.int2ObjectEntrySet().stream().map(Raids.RaidWithId::from).toList();
        }), Codec.INT.fieldOf("next_id").forGetter((raids) -> {
            return raids.nextId;
        }), Codec.INT.fieldOf("tick").forGetter((raids) -> {
            return raids.tick;
        })).apply(instance, Raids::new);
    });
    public static final SavedDataType<Raids> TYPE = new SavedDataType<Raids>("raids", Raids::new, Raids.CODEC, DataFixTypes.SAVED_DATA_RAIDS);
    public static final SavedDataType<Raids> TYPE_END = new SavedDataType<Raids>("raids_end", Raids::new, Raids.CODEC, DataFixTypes.SAVED_DATA_RAIDS);
    public final Int2ObjectMap<Raid> raidMap = new Int2ObjectOpenHashMap();
    private int nextId = 1;
    private int tick;

    public static SavedDataType<Raids> getType(Holder<DimensionType> type) {
        return type.is(BuiltinDimensionTypes.END) ? Raids.TYPE_END : Raids.TYPE;
    }

    public Raids() {
        this.setDirty();
    }

    private Raids(List<Raids.RaidWithId> raids, int nextId, int tick) {
        for (Raids.RaidWithId raids_raidwithid : raids) {
            this.raidMap.put(raids_raidwithid.id, raids_raidwithid.raid);
        }

        this.nextId = nextId;
        this.tick = tick;
    }

    public @Nullable Raid get(int raidId) {
        return (Raid) this.raidMap.get(raidId);
    }

    public OptionalInt getId(Raid raid) {
        ObjectIterator objectiterator = this.raidMap.int2ObjectEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Int2ObjectMap.Entry<Raid> int2objectmap_entry = (Entry) objectiterator.next();

            if (int2objectmap_entry.getValue() == raid) {
                return OptionalInt.of(int2objectmap_entry.getIntKey());
            }
        }

        return OptionalInt.empty();
    }

    public void tick(ServerLevel level) {
        ++this.tick;
        Iterator<Raid> iterator = this.raidMap.values().iterator();

        while (((Iterator) iterator).hasNext()) {
            Raid raid = (Raid) iterator.next();

            if (!(Boolean) level.getGameRules().get(GameRules.RAIDS)) {
                raid.stop();
            }

            if (raid.isStopped()) {
                iterator.remove();
                this.setDirty();
            } else {
                raid.tick(level);
            }
        }

        if (this.tick % 200 == 0) {
            this.setDirty();
        }

    }

    public static boolean canJoinRaid(Raider raider) {
        return raider.isAlive() && raider.canJoinRaid() && raider.getNoActionTime() <= 2400;
    }

    public @Nullable Raid createOrExtendRaid(ServerPlayer player, BlockPos raidPosition) {
        if (player.isSpectator()) {
            return null;
        } else {
            ServerLevel serverlevel = player.level();

            if (!(Boolean) serverlevel.getGameRules().get(GameRules.RAIDS)) {
                return null;
            } else if (!(Boolean) serverlevel.environmentAttributes().getValue(EnvironmentAttributes.CAN_START_RAID, raidPosition)) {
                return null;
            } else {
                List<PoiRecord> list = serverlevel.getPoiManager().getInRange((holder) -> {
                    return holder.is(PoiTypeTags.VILLAGE);
                }, raidPosition, 64, PoiManager.Occupancy.IS_OCCUPIED).toList();
                int i = 0;
                Vec3 vec3 = Vec3.ZERO;

                for (PoiRecord poirecord : list) {
                    BlockPos blockpos1 = poirecord.getPos();

                    vec3 = vec3.add((double) blockpos1.getX(), (double) blockpos1.getY(), (double) blockpos1.getZ());
                    ++i;
                }

                BlockPos blockpos2;

                if (i > 0) {
                    vec3 = vec3.scale(1.0D / (double) i);
                    blockpos2 = BlockPos.containing(vec3);
                } else {
                    blockpos2 = raidPosition;
                }

                Raid raid = this.getOrCreateRaid(serverlevel, blockpos2);

                if (!raid.isStarted() && !this.raidMap.containsValue(raid)) {
                    this.raidMap.put(this.getUniqueId(), raid);
                }

                if (!raid.isStarted() || raid.getRaidOmenLevel() < raid.getMaxRaidOmenLevel()) {
                    raid.absorbRaidOmen(player);
                }

                this.setDirty();
                return raid;
            }
        }
    }

    private Raid getOrCreateRaid(ServerLevel level, BlockPos pos) {
        Raid raid = level.getRaidAt(pos);

        return raid != null ? raid : new Raid(pos, level.getDifficulty());
    }

    public static Raids load(CompoundTag tag) {
        return (Raids) Raids.CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial().orElseGet(Raids::new);
    }

    private int getUniqueId() {
        return ++this.nextId;
    }

    public @Nullable Raid getNearbyRaid(BlockPos pos, int maxDistSqr) {
        Raid raid = null;
        double d0 = (double) maxDistSqr;
        ObjectIterator objectiterator = this.raidMap.values().iterator();

        while (objectiterator.hasNext()) {
            Raid raid1 = (Raid) objectiterator.next();
            double d1 = raid1.getCenter().distSqr(pos);

            if (raid1.isActive() && d1 < d0) {
                raid = raid1;
                d0 = d1;
            }
        }

        return raid;
    }

    @VisibleForDebug
    public List<BlockPos> getRaidCentersInChunk(ChunkPos chunkPos) {
        Stream stream = this.raidMap.values().stream().map(Raid::getCenter);

        Objects.requireNonNull(chunkPos);
        return stream.filter(chunkPos::contains).toList();
    }

    private static record RaidWithId(int id, Raid raid) {

        public static final Codec<Raids.RaidWithId> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.INT.fieldOf("id").forGetter(Raids.RaidWithId::id), Raid.MAP_CODEC.forGetter(Raids.RaidWithId::raid)).apply(instance, Raids.RaidWithId::new);
        });

        public static Raids.RaidWithId from(Int2ObjectMap.Entry<Raid> entry) {
            return new Raids.RaidWithId(entry.getIntKey(), (Raid) entry.getValue());
        }
    }
}
