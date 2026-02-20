package net.minecraft.world.level;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;

public class LocalMobCapCalculator {

    private final Long2ObjectMap<List<ServerPlayer>> playersNearChunk = new Long2ObjectOpenHashMap();
    private final Map<ServerPlayer, LocalMobCapCalculator.MobCounts> playerMobCounts = Maps.newHashMap();
    private final ChunkMap chunkMap;

    public LocalMobCapCalculator(ChunkMap chunkMap) {
        this.chunkMap = chunkMap;
    }

    private List<ServerPlayer> getPlayersNear(ChunkPos pos) {
        return (List) this.playersNearChunk.computeIfAbsent(pos.toLong(), (i) -> {
            return this.chunkMap.getPlayersCloseForSpawning(pos);
        });
    }

    public void addMob(ChunkPos pos, MobCategory category) {
        for (ServerPlayer serverplayer : this.getPlayersNear(pos)) {
            ((LocalMobCapCalculator.MobCounts) this.playerMobCounts.computeIfAbsent(serverplayer, (serverplayer1) -> {
                return new LocalMobCapCalculator.MobCounts();
            })).add(category);
        }

    }

    public boolean canSpawn(MobCategory mobCategory, ChunkPos pos) {
        for (ServerPlayer serverplayer : this.getPlayersNear(pos)) {
            LocalMobCapCalculator.MobCounts localmobcapcalculator_mobcounts = (LocalMobCapCalculator.MobCounts) this.playerMobCounts.get(serverplayer);

            if (localmobcapcalculator_mobcounts == null || localmobcapcalculator_mobcounts.canSpawn(mobCategory)) {
                return true;
            }
        }

        return false;
    }

    private static class MobCounts {

        private final Object2IntMap<MobCategory> counts = new Object2IntOpenHashMap(MobCategory.values().length);

        private MobCounts() {}

        public void add(MobCategory category) {
            this.counts.computeInt(category, (mobcategory1, integer) -> {
                return integer == null ? 1 : integer + 1;
            });
        }

        public boolean canSpawn(MobCategory category) {
            return this.counts.getOrDefault(category, 0) < category.getMaxInstancesPerChunk();
        }
    }
}
