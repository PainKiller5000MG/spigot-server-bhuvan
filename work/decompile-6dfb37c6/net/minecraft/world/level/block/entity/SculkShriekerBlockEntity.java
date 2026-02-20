package net.minecraft.world.level.block.entity;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SculkShriekerBlockEntity extends BlockEntity implements GameEventListener.Provider<VibrationSystem.Listener>, VibrationSystem {

    private static final int WARNING_SOUND_RADIUS = 10;
    private static final int WARDEN_SPAWN_ATTEMPTS = 20;
    private static final int WARDEN_SPAWN_RANGE_XZ = 5;
    private static final int WARDEN_SPAWN_RANGE_Y = 6;
    private static final int DARKNESS_RADIUS = 40;
    private static final int SHRIEKING_TICKS = 90;
    private static final Int2ObjectMap<SoundEvent> SOUND_BY_LEVEL = (Int2ObjectMap) Util.make(new Int2ObjectOpenHashMap(), (int2objectopenhashmap) -> {
        int2objectopenhashmap.put(1, SoundEvents.WARDEN_NEARBY_CLOSE);
        int2objectopenhashmap.put(2, SoundEvents.WARDEN_NEARBY_CLOSER);
        int2objectopenhashmap.put(3, SoundEvents.WARDEN_NEARBY_CLOSEST);
        int2objectopenhashmap.put(4, SoundEvents.WARDEN_LISTENING_ANGRY);
    });
    private static final int DEFAULT_WARNING_LEVEL = 0;
    public int warningLevel = 0;
    private final VibrationSystem.User vibrationUser = new SculkShriekerBlockEntity.VibrationUser();
    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.Listener vibrationListener = new VibrationSystem.Listener(this);

    public SculkShriekerBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.SCULK_SHRIEKER, worldPosition, blockState);
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.warningLevel = input.getIntOr("warning_level", 0);
        this.vibrationData = (VibrationSystem.Data) input.read("listener", VibrationSystem.Data.CODEC).orElseGet(VibrationSystem.Data::new);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("warning_level", this.warningLevel);
        output.store("listener", VibrationSystem.Data.CODEC, this.vibrationData);
    }

    public static @Nullable ServerPlayer tryGetPlayer(@Nullable Entity sourceEntity) {
        if (sourceEntity instanceof ServerPlayer serverplayer) {
            return serverplayer;
        } else {
            if (sourceEntity != null) {
                LivingEntity livingentity = sourceEntity.getControllingPassenger();

                if (livingentity instanceof ServerPlayer) {
                    ServerPlayer serverplayer1 = (ServerPlayer) livingentity;

                    return serverplayer1;
                }
            }

            if (sourceEntity instanceof Projectile projectile) {
                Entity entity1 = projectile.getOwner();

                if (entity1 instanceof ServerPlayer serverplayer2) {
                    return serverplayer2;
                }
            }

            if (sourceEntity instanceof ItemEntity itementity) {
                Entity entity2 = itementity.getOwner();

                if (entity2 instanceof ServerPlayer serverplayer3) {
                    return serverplayer3;
                }
            }

            return null;
        }
    }

    public void tryShriek(ServerLevel level, @Nullable ServerPlayer player) {
        if (player != null) {
            BlockState blockstate = this.getBlockState();

            if (!(Boolean) blockstate.getValue(SculkShriekerBlock.SHRIEKING)) {
                this.warningLevel = 0;
                if (!this.canRespond(level) || this.tryToWarn(level, player)) {
                    this.shriek(level, player);
                }
            }
        }
    }

    private boolean tryToWarn(ServerLevel level, ServerPlayer player) {
        OptionalInt optionalint = WardenSpawnTracker.tryWarn(level, this.getBlockPos(), player);

        optionalint.ifPresent((i) -> {
            this.warningLevel = i;
        });
        return optionalint.isPresent();
    }

    private void shriek(ServerLevel level, @Nullable Entity sourceEntity) {
        BlockPos blockpos = this.getBlockPos();
        BlockState blockstate = this.getBlockState();

        level.setBlock(blockpos, (BlockState) blockstate.setValue(SculkShriekerBlock.SHRIEKING, true), 2);
        level.scheduleTick(blockpos, blockstate.getBlock(), 90);
        level.levelEvent(3007, blockpos, 0);
        level.gameEvent(GameEvent.SHRIEK, blockpos, GameEvent.Context.of(sourceEntity));
    }

    private boolean canRespond(ServerLevel level) {
        return (Boolean) this.getBlockState().getValue(SculkShriekerBlock.CAN_SUMMON) && level.getDifficulty() != Difficulty.PEACEFUL && (Boolean) level.getGameRules().get(GameRules.SPAWN_WARDENS);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if ((Boolean) state.getValue(SculkShriekerBlock.SHRIEKING)) {
            Level level = this.level;

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                this.tryRespond(serverlevel);
            }
        }

    }

    public void tryRespond(ServerLevel level) {
        if (this.canRespond(level) && this.warningLevel > 0) {
            if (!this.trySummonWarden(level)) {
                this.playWardenReplySound(level);
            }

            Warden.applyDarknessAround(level, Vec3.atCenterOf(this.getBlockPos()), (Entity) null, 40);
        }

    }

    private void playWardenReplySound(Level level) {
        SoundEvent soundevent = (SoundEvent) SculkShriekerBlockEntity.SOUND_BY_LEVEL.get(this.warningLevel);

        if (soundevent != null) {
            BlockPos blockpos = this.getBlockPos();
            int i = blockpos.getX() + Mth.randomBetweenInclusive(level.random, -10, 10);
            int j = blockpos.getY() + Mth.randomBetweenInclusive(level.random, -10, 10);
            int k = blockpos.getZ() + Mth.randomBetweenInclusive(level.random, -10, 10);

            level.playSound((Entity) null, (double) i, (double) j, (double) k, soundevent, SoundSource.HOSTILE, 5.0F, 1.0F);
        }

    }

    private boolean trySummonWarden(ServerLevel level) {
        return this.warningLevel < 4 ? false : SpawnUtil.trySpawnMob(EntityType.WARDEN, EntitySpawnReason.TRIGGERED, level, this.getBlockPos(), 20, 5, 6, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, false).isPresent();
    }

    @Override
    public VibrationSystem.Listener getListener() {
        return this.vibrationListener;
    }

    private class VibrationUser implements VibrationSystem.User {

        private static final int LISTENER_RADIUS = 8;
        private final PositionSource positionSource;

        public VibrationUser() {
            this.positionSource = new BlockPositionSource(SculkShriekerBlockEntity.this.worldPosition);
        }

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.SHRIEKER_CAN_LISTEN;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, GameEvent.Context context) {
            return !(Boolean) SculkShriekerBlockEntity.this.getBlockState().getValue(SculkShriekerBlock.SHRIEKING) && SculkShriekerBlockEntity.tryGetPlayer(context.sourceEntity()) != null;
        }

        @Override
        public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, @Nullable Entity sourceEntity, @Nullable Entity projectileOwner, float receivingDistance) {
            SculkShriekerBlockEntity.this.tryShriek(level, SculkShriekerBlockEntity.tryGetPlayer(projectileOwner != null ? projectileOwner : sourceEntity));
        }

        @Override
        public void onDataChanged() {
            SculkShriekerBlockEntity.this.setChanged();
        }

        @Override
        public boolean requiresAdjacentChunksToBeTicking() {
            return true;
        }
    }
}
