package net.minecraft.world.level.gameevent.vibrations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface VibrationSystem {

    List<ResourceKey<GameEvent>> RESONANCE_EVENTS = List.of(GameEvent.RESONATE_1.key(), GameEvent.RESONATE_2.key(), GameEvent.RESONATE_3.key(), GameEvent.RESONATE_4.key(), GameEvent.RESONATE_5.key(), GameEvent.RESONATE_6.key(), GameEvent.RESONATE_7.key(), GameEvent.RESONATE_8.key(), GameEvent.RESONATE_9.key(), GameEvent.RESONATE_10.key(), GameEvent.RESONATE_11.key(), GameEvent.RESONATE_12.key(), GameEvent.RESONATE_13.key(), GameEvent.RESONATE_14.key(), GameEvent.RESONATE_15.key());
    int NO_VIBRATION_FREQUENCY = 0;
    ToIntFunction<ResourceKey<GameEvent>> VIBRATION_FREQUENCY_FOR_EVENT = (ToIntFunction) Util.make(new Reference2IntOpenHashMap(), (reference2intopenhashmap) -> {
        reference2intopenhashmap.defaultReturnValue(0);
        reference2intopenhashmap.put(GameEvent.STEP.key(), 1);
        reference2intopenhashmap.put(GameEvent.SWIM.key(), 1);
        reference2intopenhashmap.put(GameEvent.FLAP.key(), 1);
        reference2intopenhashmap.put(GameEvent.PROJECTILE_LAND.key(), 2);
        reference2intopenhashmap.put(GameEvent.HIT_GROUND.key(), 2);
        reference2intopenhashmap.put(GameEvent.SPLASH.key(), 2);
        reference2intopenhashmap.put(GameEvent.ITEM_INTERACT_FINISH.key(), 3);
        reference2intopenhashmap.put(GameEvent.PROJECTILE_SHOOT.key(), 3);
        reference2intopenhashmap.put(GameEvent.INSTRUMENT_PLAY.key(), 3);
        reference2intopenhashmap.put(GameEvent.ENTITY_ACTION.key(), 4);
        reference2intopenhashmap.put(GameEvent.ELYTRA_GLIDE.key(), 4);
        reference2intopenhashmap.put(GameEvent.UNEQUIP.key(), 4);
        reference2intopenhashmap.put(GameEvent.ENTITY_DISMOUNT.key(), 5);
        reference2intopenhashmap.put(GameEvent.EQUIP.key(), 5);
        reference2intopenhashmap.put(GameEvent.ENTITY_INTERACT.key(), 6);
        reference2intopenhashmap.put(GameEvent.SHEAR.key(), 6);
        reference2intopenhashmap.put(GameEvent.ENTITY_MOUNT.key(), 6);
        reference2intopenhashmap.put(GameEvent.ENTITY_DAMAGE.key(), 7);
        reference2intopenhashmap.put(GameEvent.DRINK.key(), 8);
        reference2intopenhashmap.put(GameEvent.EAT.key(), 8);
        reference2intopenhashmap.put(GameEvent.CONTAINER_CLOSE.key(), 9);
        reference2intopenhashmap.put(GameEvent.BLOCK_CLOSE.key(), 9);
        reference2intopenhashmap.put(GameEvent.BLOCK_DEACTIVATE.key(), 9);
        reference2intopenhashmap.put(GameEvent.BLOCK_DETACH.key(), 9);
        reference2intopenhashmap.put(GameEvent.CONTAINER_OPEN.key(), 10);
        reference2intopenhashmap.put(GameEvent.BLOCK_OPEN.key(), 10);
        reference2intopenhashmap.put(GameEvent.BLOCK_ACTIVATE.key(), 10);
        reference2intopenhashmap.put(GameEvent.BLOCK_ATTACH.key(), 10);
        reference2intopenhashmap.put(GameEvent.PRIME_FUSE.key(), 10);
        reference2intopenhashmap.put(GameEvent.NOTE_BLOCK_PLAY.key(), 10);
        reference2intopenhashmap.put(GameEvent.BLOCK_CHANGE.key(), 11);
        reference2intopenhashmap.put(GameEvent.BLOCK_DESTROY.key(), 12);
        reference2intopenhashmap.put(GameEvent.FLUID_PICKUP.key(), 12);
        reference2intopenhashmap.put(GameEvent.BLOCK_PLACE.key(), 13);
        reference2intopenhashmap.put(GameEvent.FLUID_PLACE.key(), 13);
        reference2intopenhashmap.put(GameEvent.ENTITY_PLACE.key(), 14);
        reference2intopenhashmap.put(GameEvent.LIGHTNING_STRIKE.key(), 14);
        reference2intopenhashmap.put(GameEvent.TELEPORT.key(), 14);
        reference2intopenhashmap.put(GameEvent.ENTITY_DIE.key(), 15);
        reference2intopenhashmap.put(GameEvent.EXPLODE.key(), 15);

        for (int i = 1; i <= 15; ++i) {
            reference2intopenhashmap.put(getResonanceEventByFrequency(i), i);
        }

    });

    VibrationSystem.Data getVibrationData();

    VibrationSystem.User getVibrationUser();

    static int getGameEventFrequency(Holder<GameEvent> event) {
        return (Integer) event.unwrapKey().map(VibrationSystem::getGameEventFrequency).orElse(0);
    }

    static int getGameEventFrequency(ResourceKey<GameEvent> event) {
        return VibrationSystem.VIBRATION_FREQUENCY_FOR_EVENT.applyAsInt(event);
    }

    static ResourceKey<GameEvent> getResonanceEventByFrequency(int vibrationFrequency) {
        return (ResourceKey) VibrationSystem.RESONANCE_EVENTS.get(vibrationFrequency - 1);
    }

    static int getRedstoneStrengthForDistance(float distance, int listenerRadius) {
        double d0 = 15.0D / (double) listenerRadius;

        return Math.max(1, 15 - Mth.floor(d0 * (double) distance));
    }

    public static final class Data {

        public static Codec<VibrationSystem.Data> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(VibrationInfo.CODEC.lenientOptionalFieldOf("event").forGetter((vibrationsystem_data) -> {
                return Optional.ofNullable(vibrationsystem_data.currentVibration);
            }), VibrationSelector.CODEC.fieldOf("selector").forGetter(VibrationSystem.Data::getSelectionStrategy), ExtraCodecs.NON_NEGATIVE_INT.fieldOf("event_delay").orElse(0).forGetter(VibrationSystem.Data::getTravelTimeInTicks)).apply(instance, (optional, vibrationselector, integer) -> {
                return new VibrationSystem.Data((VibrationInfo) optional.orElse((Object) null), vibrationselector, integer, true);
            });
        });
        public static final String NBT_TAG_KEY = "listener";
        private @Nullable VibrationInfo currentVibration;
        private int travelTimeInTicks;
        private final VibrationSelector selectionStrategy;
        private boolean reloadVibrationParticle;

        private Data(@Nullable VibrationInfo currentVibration, VibrationSelector selectionStrategy, int travelTimeInTicks, boolean reloadVibrationParticle) {
            this.currentVibration = currentVibration;
            this.travelTimeInTicks = travelTimeInTicks;
            this.selectionStrategy = selectionStrategy;
            this.reloadVibrationParticle = reloadVibrationParticle;
        }

        public Data() {
            this((VibrationInfo) null, new VibrationSelector(), 0, false);
        }

        public VibrationSelector getSelectionStrategy() {
            return this.selectionStrategy;
        }

        public @Nullable VibrationInfo getCurrentVibration() {
            return this.currentVibration;
        }

        public void setCurrentVibration(@Nullable VibrationInfo currentVibration) {
            this.currentVibration = currentVibration;
        }

        public int getTravelTimeInTicks() {
            return this.travelTimeInTicks;
        }

        public void setTravelTimeInTicks(int travelTimeInTicks) {
            this.travelTimeInTicks = travelTimeInTicks;
        }

        public void decrementTravelTime() {
            this.travelTimeInTicks = Math.max(0, this.travelTimeInTicks - 1);
        }

        public boolean shouldReloadVibrationParticle() {
            return this.reloadVibrationParticle;
        }

        public void setReloadVibrationParticle(boolean reloadVibrationParticle) {
            this.reloadVibrationParticle = reloadVibrationParticle;
        }
    }

    public static class Listener implements GameEventListener {

        private final VibrationSystem system;

        public Listener(VibrationSystem system) {
            this.system = system;
        }

        @Override
        public PositionSource getListenerSource() {
            return this.system.getVibrationUser().getPositionSource();
        }

        @Override
        public int getListenerRadius() {
            return this.system.getVibrationUser().getListenerRadius();
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 sourcePosition) {
            VibrationSystem.Data vibrationsystem_data = this.system.getVibrationData();
            VibrationSystem.User vibrationsystem_user = this.system.getVibrationUser();

            if (vibrationsystem_data.getCurrentVibration() != null) {
                return false;
            } else if (!vibrationsystem_user.isValidVibration(event, context)) {
                return false;
            } else {
                Optional<Vec3> optional = vibrationsystem_user.getPositionSource().getPosition(level);

                if (optional.isEmpty()) {
                    return false;
                } else {
                    Vec3 vec31 = (Vec3) optional.get();

                    if (!vibrationsystem_user.canReceiveVibration(level, BlockPos.containing(sourcePosition), event, context)) {
                        return false;
                    } else if (isOccluded(level, sourcePosition, vec31)) {
                        return false;
                    } else {
                        this.scheduleVibration(level, vibrationsystem_data, event, context, sourcePosition, vec31);
                        return true;
                    }
                }
            }
        }

        public void forceScheduleVibration(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 origin) {
            this.system.getVibrationUser().getPositionSource().getPosition(level).ifPresent((vec31) -> {
                this.scheduleVibration(level, this.system.getVibrationData(), event, context, origin, vec31);
            });
        }

        private void scheduleVibration(ServerLevel level, VibrationSystem.Data data, Holder<GameEvent> event, GameEvent.Context context, Vec3 origin, Vec3 dest) {
            data.selectionStrategy.addCandidate(new VibrationInfo(event, (float) origin.distanceTo(dest), origin, context.sourceEntity()), level.getGameTime());
        }

        public static float distanceBetweenInBlocks(BlockPos origin, BlockPos dest) {
            return (float) Math.sqrt(origin.distSqr(dest));
        }

        private static boolean isOccluded(Level level, Vec3 origin, Vec3 dest) {
            Vec3 vec32 = new Vec3((double) Mth.floor(origin.x) + 0.5D, (double) Mth.floor(origin.y) + 0.5D, (double) Mth.floor(origin.z) + 0.5D);
            Vec3 vec33 = new Vec3((double) Mth.floor(dest.x) + 0.5D, (double) Mth.floor(dest.y) + 0.5D, (double) Mth.floor(dest.z) + 0.5D);

            for (Direction direction : Direction.values()) {
                Vec3 vec34 = vec32.relative(direction, (double) 1.0E-5F);

                if (level.isBlockInLine(new ClipBlockStateContext(vec34, vec33, (blockstate) -> {
                    return blockstate.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS);
                })).getType() != HitResult.Type.BLOCK) {
                    return false;
                }
            }

            return true;
        }
    }

    public interface Ticker {

        static void tick(Level level, VibrationSystem.Data data, VibrationSystem.User user) {
            if (level instanceof ServerLevel serverlevel) {
                if (data.currentVibration == null) {
                    trySelectAndScheduleVibration(serverlevel, data, user);
                }

                if (data.currentVibration != null) {
                    boolean flag = data.getTravelTimeInTicks() > 0;

                    tryReloadVibrationParticle(serverlevel, data, user);
                    data.decrementTravelTime();
                    if (data.getTravelTimeInTicks() <= 0) {
                        flag = receiveVibration(serverlevel, data, user, data.currentVibration);
                    }

                    if (flag) {
                        user.onDataChanged();
                    }

                }
            }
        }

        private static void trySelectAndScheduleVibration(ServerLevel serverLevel, VibrationSystem.Data data, VibrationSystem.User user) {
            data.getSelectionStrategy().chosenCandidate(serverLevel.getGameTime()).ifPresent((vibrationinfo) -> {
                data.setCurrentVibration(vibrationinfo);
                Vec3 vec3 = vibrationinfo.pos();

                data.setTravelTimeInTicks(user.calculateTravelTimeInTicks(vibrationinfo.distance()));
                serverLevel.sendParticles(new VibrationParticleOption(user.getPositionSource(), data.getTravelTimeInTicks()), vec3.x, vec3.y, vec3.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                user.onDataChanged();
                data.getSelectionStrategy().startOver();
            });
        }

        private static void tryReloadVibrationParticle(ServerLevel level, VibrationSystem.Data data, VibrationSystem.User user) {
            if (data.shouldReloadVibrationParticle()) {
                if (data.currentVibration == null) {
                    data.setReloadVibrationParticle(false);
                } else {
                    Vec3 vec3 = data.currentVibration.pos();
                    PositionSource positionsource = user.getPositionSource();
                    Vec3 vec31 = (Vec3) positionsource.getPosition(level).orElse(vec3);
                    int i = data.getTravelTimeInTicks();
                    int j = user.calculateTravelTimeInTicks(data.currentVibration.distance());
                    double d0 = 1.0D - (double) i / (double) j;
                    double d1 = Mth.lerp(d0, vec3.x, vec31.x);
                    double d2 = Mth.lerp(d0, vec3.y, vec31.y);
                    double d3 = Mth.lerp(d0, vec3.z, vec31.z);
                    boolean flag = level.sendParticles(new VibrationParticleOption(positionsource, i), d1, d2, d3, 1, 0.0D, 0.0D, 0.0D, 0.0D) > 0;

                    if (flag) {
                        data.setReloadVibrationParticle(false);
                    }

                }
            }
        }

        private static boolean receiveVibration(ServerLevel serverLevel, VibrationSystem.Data data, VibrationSystem.User user, VibrationInfo currentVibration) {
            BlockPos blockpos = BlockPos.containing(currentVibration.pos());
            BlockPos blockpos1 = (BlockPos) user.getPositionSource().getPosition(serverLevel).map(BlockPos::containing).orElse(blockpos);

            if (user.requiresAdjacentChunksToBeTicking() && !areAdjacentChunksTicking(serverLevel, blockpos1)) {
                return false;
            } else {
                user.onReceiveVibration(serverLevel, blockpos, currentVibration.gameEvent(), (Entity) currentVibration.getEntity(serverLevel).orElse((Object) null), (Entity) currentVibration.getProjectileOwner(serverLevel).orElse((Object) null), VibrationSystem.Listener.distanceBetweenInBlocks(blockpos, blockpos1));
                data.setCurrentVibration((VibrationInfo) null);
                return true;
            }
        }

        private static boolean areAdjacentChunksTicking(Level level, BlockPos listenerPos) {
            ChunkPos chunkpos = new ChunkPos(listenerPos);

            for (int i = chunkpos.x - 1; i <= chunkpos.x + 1; ++i) {
                for (int j = chunkpos.z - 1; j <= chunkpos.z + 1; ++j) {
                    if (!level.shouldTickBlocksAt(ChunkPos.asLong(i, j)) || level.getChunkSource().getChunkNow(i, j) == null) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public interface User {

        int getListenerRadius();

        PositionSource getPositionSource();

        boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, GameEvent.Context context);

        void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, @Nullable Entity sourceEntity, @Nullable Entity projectileOwner, float receivingDistance);

        default TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.VIBRATIONS;
        }

        default boolean canTriggerAvoidVibration() {
            return false;
        }

        default boolean requiresAdjacentChunksToBeTicking() {
            return false;
        }

        default int calculateTravelTimeInTicks(float distanceToDestination) {
            return Mth.floor(distanceToDestination);
        }

        default boolean isValidVibration(Holder<GameEvent> event, GameEvent.Context context) {
            if (!event.is(this.getListenableEvents())) {
                return false;
            } else {
                Entity entity = context.sourceEntity();

                if (entity != null) {
                    if (entity.isSpectator()) {
                        return false;
                    }

                    if (entity.isSteppingCarefully() && event.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) {
                        if (this.canTriggerAvoidVibration() && entity instanceof ServerPlayer) {
                            ServerPlayer serverplayer = (ServerPlayer) entity;

                            CriteriaTriggers.AVOID_VIBRATION.trigger(serverplayer);
                        }

                        return false;
                    }

                    if (entity.dampensVibrations()) {
                        return false;
                    }
                }

                return context.affectedState() != null ? !context.affectedState().is(BlockTags.DAMPENS_VIBRATIONS) : true;
            }
        }

        default void onDataChanged() {}
    }
}
