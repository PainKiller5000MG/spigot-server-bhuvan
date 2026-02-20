package net.minecraft.world.entity.item;

import com.mojang.logging.LogUtils;
import java.util.function.Predicate;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class FallingBlockEntity extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockState DEFAULT_BLOCK_STATE = Blocks.SAND.defaultBlockState();
    private static final int DEFAULT_TIME = 0;
    private static final float DEFAULT_FALL_DAMAGE_PER_DISTANCE = 0.0F;
    private static final int DEFAULT_MAX_FALL_DAMAGE = 40;
    private static final boolean DEFAULT_DROP_ITEM = true;
    private static final boolean DEFAULT_CANCEL_DROP = false;
    private BlockState blockState;
    public int time;
    public boolean dropItem;
    public boolean cancelDrop;
    public boolean hurtEntities;
    public int fallDamageMax;
    public float fallDamagePerDistance;
    public @Nullable CompoundTag blockData;
    public boolean forceTickAfterTeleportToDuplicate;
    protected static final EntityDataAccessor<BlockPos> DATA_START_POS = SynchedEntityData.<BlockPos>defineId(FallingBlockEntity.class, EntityDataSerializers.BLOCK_POS);

    public FallingBlockEntity(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
        this.blockState = FallingBlockEntity.DEFAULT_BLOCK_STATE;
        this.time = 0;
        this.dropItem = true;
        this.cancelDrop = false;
        this.fallDamageMax = 40;
        this.fallDamagePerDistance = 0.0F;
    }

    private FallingBlockEntity(Level level, double x, double y, double z, BlockState blockState) {
        this(EntityType.FALLING_BLOCK, level);
        this.blockState = blockState;
        this.blocksBuilding = true;
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setStartPos(this.blockPosition());
    }

    public static FallingBlockEntity fall(Level level, BlockPos pos, BlockState state) {
        FallingBlockEntity fallingblockentity = new FallingBlockEntity(level, (double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D, state.hasProperty(BlockStateProperties.WATERLOGGED) ? (BlockState) state.setValue(BlockStateProperties.WATERLOGGED, false) : state);

        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
        level.addFreshEntity(fallingblockentity);
        return fallingblockentity;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!this.isInvulnerableToBase(source)) {
            this.markHurt();
        }

        return false;
    }

    public void setStartPos(BlockPos pos) {
        this.entityData.set(FallingBlockEntity.DATA_START_POS, pos);
    }

    public BlockPos getStartPos() {
        return (BlockPos) this.entityData.get(FallingBlockEntity.DATA_START_POS);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(FallingBlockEntity.DATA_START_POS, BlockPos.ZERO);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04D;
    }

    @Override
    public void tick() {
        if (this.blockState.isAir()) {
            this.discard();
        } else {
            Block block = this.blockState.getBlock();

            ++this.time;
            this.applyGravity();
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.applyEffectsFromBlocks();
            this.handlePortal();
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                if (this.isAlive() || this.forceTickAfterTeleportToDuplicate) {
                    BlockPos blockpos = this.blockPosition();
                    boolean flag = this.blockState.getBlock() instanceof ConcretePowderBlock;
                    boolean flag1 = flag && this.level().getFluidState(blockpos).is(FluidTags.WATER);
                    double d0 = this.getDeltaMovement().lengthSqr();

                    if (flag && d0 > 1.0D) {
                        BlockHitResult blockhitresult = this.level().clip(new ClipContext(new Vec3(this.xo, this.yo, this.zo), this.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, this));

                        if (blockhitresult.getType() != HitResult.Type.MISS && this.level().getFluidState(blockhitresult.getBlockPos()).is(FluidTags.WATER)) {
                            blockpos = blockhitresult.getBlockPos();
                            flag1 = true;
                        }
                    }

                    if (!this.onGround() && !flag1) {
                        if (this.time > 100 && (blockpos.getY() <= this.level().getMinY() || blockpos.getY() > this.level().getMaxY()) || this.time > 600) {
                            if (this.dropItem && (Boolean) serverlevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
                                this.spawnAtLocation(serverlevel, (ItemLike) block);
                            }

                            this.discard();
                        }
                    } else {
                        BlockState blockstate = this.level().getBlockState(blockpos);

                        this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
                        if (!blockstate.is(Blocks.MOVING_PISTON)) {
                            if (!this.cancelDrop) {
                                boolean flag2 = blockstate.canBeReplaced((BlockPlaceContext) (new DirectionalPlaceContext(this.level(), blockpos, Direction.DOWN, ItemStack.EMPTY, Direction.UP)));
                                boolean flag3 = FallingBlock.isFree(this.level().getBlockState(blockpos.below())) && (!flag || !flag1);
                                boolean flag4 = this.blockState.canSurvive(this.level(), blockpos) && !flag3;

                                if (flag2 && flag4) {
                                    if (this.blockState.hasProperty(BlockStateProperties.WATERLOGGED) && this.level().getFluidState(blockpos).getType() == Fluids.WATER) {
                                        this.blockState = (BlockState) this.blockState.setValue(BlockStateProperties.WATERLOGGED, true);
                                    }

                                    if (this.level().setBlock(blockpos, this.blockState, 3)) {
                                        serverlevel.getChunkSource().chunkMap.sendToTrackingPlayers(this, new ClientboundBlockUpdatePacket(blockpos, this.level().getBlockState(blockpos)));
                                        this.discard();
                                        if (block instanceof Fallable) {
                                            Fallable fallable = (Fallable) block;

                                            fallable.onLand(this.level(), blockpos, this.blockState, blockstate, this);
                                        }

                                        if (this.blockData != null && this.blockState.hasBlockEntity()) {
                                            BlockEntity blockentity = this.level().getBlockEntity(blockpos);

                                            if (blockentity != null) {
                                                try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(blockentity.problemPath(), FallingBlockEntity.LOGGER)) {
                                                    RegistryAccess registryaccess = this.level().registryAccess();
                                                    TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, registryaccess);

                                                    blockentity.saveWithoutMetadata((ValueOutput) tagvalueoutput);
                                                    CompoundTag compoundtag = tagvalueoutput.buildResult();

                                                    this.blockData.forEach((s, tag) -> {
                                                        compoundtag.put(s, tag.copy());
                                                    });
                                                    blockentity.loadWithComponents(TagValueInput.create(problemreporter_scopedcollector, registryaccess, compoundtag));
                                                } catch (Exception exception) {
                                                    FallingBlockEntity.LOGGER.error("Failed to load block entity from falling block", exception);
                                                }

                                                blockentity.setChanged();
                                            }
                                        }
                                    } else if (this.dropItem && (Boolean) serverlevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
                                        this.discard();
                                        this.callOnBrokenAfterFall(block, blockpos);
                                        this.spawnAtLocation(serverlevel, (ItemLike) block);
                                    }
                                } else {
                                    this.discard();
                                    if (this.dropItem && (Boolean) serverlevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
                                        this.callOnBrokenAfterFall(block, blockpos);
                                        this.spawnAtLocation(serverlevel, (ItemLike) block);
                                    }
                                }
                            } else {
                                this.discard();
                                this.callOnBrokenAfterFall(block, blockpos);
                            }
                        }
                    }
                }
            }

            this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        }
    }

    public void callOnBrokenAfterFall(Block block, BlockPos pos) {
        if (block instanceof Fallable) {
            ((Fallable) block).onBrokenAfterFall(this.level(), pos, this);
        }

    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        if (!this.hurtEntities) {
            return false;
        } else {
            int i = Mth.ceil(fallDistance - 1.0D);

            if (i < 0) {
                return false;
            } else {
                Predicate<Entity> predicate = EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE);
                Block block = this.blockState.getBlock();
                DamageSource damagesource1;

                if (block instanceof Fallable) {
                    Fallable fallable = (Fallable) block;

                    damagesource1 = fallable.getFallDamageSource(this);
                } else {
                    damagesource1 = this.damageSources().fallingBlock(this);
                }

                DamageSource damagesource2 = damagesource1;
                float f1 = (float) Math.min(Mth.floor((float) i * this.fallDamagePerDistance), this.fallDamageMax);

                this.level().getEntities(this, this.getBoundingBox(), predicate).forEach((entity) -> {
                    entity.hurt(damagesource2, f1);
                });
                boolean flag = this.blockState.is(BlockTags.ANVIL);

                if (flag && f1 > 0.0F && this.random.nextFloat() < 0.05F + (float) i * 0.05F) {
                    BlockState blockstate = AnvilBlock.damage(this.blockState);

                    if (blockstate == null) {
                        this.cancelDrop = true;
                    } else {
                        this.blockState = blockstate;
                    }
                }

                return false;
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("BlockState", BlockState.CODEC, this.blockState);
        output.putInt("Time", this.time);
        output.putBoolean("DropItem", this.dropItem);
        output.putBoolean("HurtEntities", this.hurtEntities);
        output.putFloat("FallHurtAmount", this.fallDamagePerDistance);
        output.putInt("FallHurtMax", this.fallDamageMax);
        if (this.blockData != null) {
            output.store("TileEntityData", CompoundTag.CODEC, this.blockData);
        }

        output.putBoolean("CancelDrop", this.cancelDrop);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.blockState = (BlockState) input.read("BlockState", BlockState.CODEC).orElse(FallingBlockEntity.DEFAULT_BLOCK_STATE);
        this.time = input.getIntOr("Time", 0);
        boolean flag = this.blockState.is(BlockTags.ANVIL);

        this.hurtEntities = input.getBooleanOr("HurtEntities", flag);
        this.fallDamagePerDistance = input.getFloatOr("FallHurtAmount", 0.0F);
        this.fallDamageMax = input.getIntOr("FallHurtMax", 40);
        this.dropItem = input.getBooleanOr("DropItem", true);
        this.blockData = (CompoundTag) input.read("TileEntityData", CompoundTag.CODEC).orElse((Object) null);
        this.cancelDrop = input.getBooleanOr("CancelDrop", false);
    }

    public void setHurtsEntities(float damagePerDistance, int damageMax) {
        this.hurtEntities = true;
        this.fallDamagePerDistance = damagePerDistance;
        this.fallDamageMax = damageMax;
    }

    public void disableDrop() {
        this.cancelDrop = true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory category) {
        super.fillCrashReportCategory(category);
        category.setDetail("Immitating BlockState", this.blockState.toString());
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.minecraft.falling_block_type", this.blockState.getBlock().getName());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity, Block.getId(this.getBlockState()));
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.blockState = Block.stateById(packet.getData());
        this.blocksBuilding = true;
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();

        this.setPos(d0, d1, d2);
        this.setStartPos(this.blockPosition());
    }

    @Override
    public @Nullable Entity teleport(TeleportTransition transition) {
        ResourceKey<Level> resourcekey = transition.newLevel().dimension();
        ResourceKey<Level> resourcekey1 = this.level().dimension();
        boolean flag = (resourcekey1 == Level.END || resourcekey == Level.END) && resourcekey1 != resourcekey;
        Entity entity = super.teleport(transition);

        this.forceTickAfterTeleportToDuplicate = entity != null && flag;
        return entity;
    }
}
