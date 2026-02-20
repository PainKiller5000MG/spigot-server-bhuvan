package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.debug.DebugHiveInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class BeehiveBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_FLOWER_POS = "flower_pos";
    private static final String BEES = "bees";
    private static final List<String> IGNORED_BEE_TAGS = Arrays.asList("Air", "drop_chances", "equipment", "Brain", "CanPickUpLoot", "DeathTime", "fall_distance", "FallFlying", "Fire", "HurtByTimestamp", "HurtTime", "LeftHanded", "Motion", "NoGravity", "OnGround", "PortalCooldown", "Pos", "Rotation", "sleeping_pos", "CannotEnterHiveTicks", "TicksSincePollination", "CropsGrownSincePollination", "hive_pos", "Passengers", "leash", "UUID");
    public static final int MAX_OCCUPANTS = 3;
    private static final int MIN_TICKS_BEFORE_REENTERING_HIVE = 400;
    private static final int MIN_OCCUPATION_TICKS_NECTAR = 2400;
    public static final int MIN_OCCUPATION_TICKS_NECTARLESS = 600;
    private List<BeehiveBlockEntity.BeeData> stored = Lists.newArrayList();
    public @Nullable BlockPos savedFlowerPos;

    public BeehiveBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.BEEHIVE, worldPosition, blockState);
    }

    @Override
    public void setChanged() {
        if (this.isFireNearby()) {
            this.emptyAllLivingFromHive((Player) null, this.level.getBlockState(this.getBlockPos()), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }

        super.setChanged();
    }

    public boolean isFireNearby() {
        if (this.level == null) {
            return false;
        } else {
            for (BlockPos blockpos : BlockPos.betweenClosed(this.worldPosition.offset(-1, -1, -1), this.worldPosition.offset(1, 1, 1))) {
                if (this.level.getBlockState(blockpos).getBlock() instanceof FireBlock) {
                    return true;
                }
            }

            return false;
        }
    }

    public boolean isEmpty() {
        return this.stored.isEmpty();
    }

    public boolean isFull() {
        return this.stored.size() == 3;
    }

    public void emptyAllLivingFromHive(@Nullable Player player, BlockState state, BeehiveBlockEntity.BeeReleaseStatus releaseReason) {
        List<Entity> list = this.releaseAllOccupants(state, releaseReason);

        if (player != null) {
            for (Entity entity : list) {
                if (entity instanceof Bee) {
                    Bee bee = (Bee) entity;

                    if (player.position().distanceToSqr(entity.position()) <= 16.0D) {
                        if (!this.isSedated()) {
                            bee.setTarget(player);
                        } else {
                            bee.setStayOutOfHiveCountdown(400);
                        }
                    }
                }
            }
        }

    }

    private List<Entity> releaseAllOccupants(BlockState state, BeehiveBlockEntity.BeeReleaseStatus releaseStatus) {
        List<Entity> list = Lists.newArrayList();

        this.stored.removeIf((beehiveblockentity_beedata) -> {
            return releaseOccupant(this.level, this.worldPosition, state, beehiveblockentity_beedata.toOccupant(), list, releaseStatus, this.savedFlowerPos);
        });
        if (!list.isEmpty()) {
            super.setChanged();
        }

        return list;
    }

    @VisibleForDebug
    public int getOccupantCount() {
        return this.stored.size();
    }

    public static int getHoneyLevel(BlockState blockState) {
        return (Integer) blockState.getValue(BeehiveBlock.HONEY_LEVEL);
    }

    @VisibleForDebug
    public boolean isSedated() {
        return CampfireBlock.isSmokeyPos(this.level, this.getBlockPos());
    }

    public void addOccupant(Bee bee) {
        if (this.stored.size() < 3) {
            bee.stopRiding();
            bee.ejectPassengers();
            bee.dropLeash();
            this.storeBee(BeehiveBlockEntity.Occupant.of(bee));
            if (this.level != null) {
                if (bee.hasSavedFlowerPos() && (!this.hasSavedFlowerPos() || this.level.random.nextBoolean())) {
                    this.savedFlowerPos = bee.getSavedFlowerPos();
                }

                BlockPos blockpos = this.getBlockPos();

                this.level.playSound((Entity) null, (double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
                this.level.gameEvent(GameEvent.BLOCK_CHANGE, blockpos, GameEvent.Context.of(bee, this.getBlockState()));
            }

            bee.discard();
            super.setChanged();
        }
    }

    public void storeBee(BeehiveBlockEntity.Occupant occupant) {
        this.stored.add(new BeehiveBlockEntity.BeeData(occupant));
    }

    private static boolean releaseOccupant(Level level, BlockPos blockPos, BlockState state, BeehiveBlockEntity.Occupant beeData, @Nullable List<Entity> spawned, BeehiveBlockEntity.BeeReleaseStatus releaseStatus, @Nullable BlockPos savedFlowerPos) {
        if ((Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.BEES_STAY_IN_HIVE, blockPos) && releaseStatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
            return false;
        } else {
            Direction direction = (Direction) state.getValue(BeehiveBlock.FACING);
            BlockPos blockpos2 = blockPos.relative(direction);
            boolean flag = !level.getBlockState(blockpos2).getCollisionShape(level, blockpos2).isEmpty();

            if (flag && releaseStatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
                return false;
            } else {
                Entity entity = beeData.createEntity(level, blockPos);

                if (entity != null) {
                    if (entity instanceof Bee) {
                        Bee bee = (Bee) entity;

                        if (savedFlowerPos != null && !bee.hasSavedFlowerPos() && level.random.nextFloat() < 0.9F) {
                            bee.setSavedFlowerPos(savedFlowerPos);
                        }

                        if (releaseStatus == BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED) {
                            bee.dropOffNectar();
                            if (state.is(BlockTags.BEEHIVES, (blockbehaviour_blockstatebase) -> {
                                return blockbehaviour_blockstatebase.hasProperty(BeehiveBlock.HONEY_LEVEL);
                            })) {
                                int i = getHoneyLevel(state);

                                if (i < 5) {
                                    int j = level.random.nextInt(100) == 0 ? 2 : 1;

                                    if (i + j > 5) {
                                        --j;
                                    }

                                    level.setBlockAndUpdate(blockPos, (BlockState) state.setValue(BeehiveBlock.HONEY_LEVEL, i + j));
                                }
                            }
                        }

                        if (spawned != null) {
                            spawned.add(bee);
                        }

                        float f = entity.getBbWidth();
                        double d0 = flag ? 0.0D : 0.55D + (double) (f / 2.0F);
                        double d1 = (double) blockPos.getX() + 0.5D + d0 * (double) direction.getStepX();
                        double d2 = (double) blockPos.getY() + 0.5D - (double) (entity.getBbHeight() / 2.0F);
                        double d3 = (double) blockPos.getZ() + 0.5D + d0 * (double) direction.getStepZ();

                        entity.snapTo(d1, d2, d3, entity.getYRot(), entity.getXRot());
                    }

                    level.playSound((Entity) null, blockPos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(entity, level.getBlockState(blockPos)));
                    return level.addFreshEntity(entity);
                } else {
                    return false;
                }
            }
        }
    }

    private boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    private static void tickOccupants(Level level, BlockPos pos, BlockState state, List<BeehiveBlockEntity.BeeData> stored, @Nullable BlockPos savedFlowerPos) {
        boolean flag = false;
        Iterator<BeehiveBlockEntity.BeeData> iterator = stored.iterator();

        while (iterator.hasNext()) {
            BeehiveBlockEntity.BeeData beehiveblockentity_beedata = (BeehiveBlockEntity.BeeData) iterator.next();

            if (beehiveblockentity_beedata.tick()) {
                BeehiveBlockEntity.BeeReleaseStatus beehiveblockentity_beereleasestatus = beehiveblockentity_beedata.hasNectar() ? BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED : BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED;

                if (releaseOccupant(level, pos, state, beehiveblockentity_beedata.toOccupant(), (List) null, beehiveblockentity_beereleasestatus, savedFlowerPos)) {
                    flag = true;
                    iterator.remove();
                }
            }
        }

        if (flag) {
            setChanged(level, pos, state);
        }

    }

    public static void serverTick(Level level, BlockPos blockPos, BlockState state, BeehiveBlockEntity entity) {
        tickOccupants(level, blockPos, state, entity.stored, entity.savedFlowerPos);
        if (!entity.stored.isEmpty() && level.getRandom().nextDouble() < 0.005D) {
            double d0 = (double) blockPos.getX() + 0.5D;
            double d1 = (double) blockPos.getY();
            double d2 = (double) blockPos.getZ() + 0.5D;

            level.playSound((Entity) null, d0, d1, d2, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.stored.clear();
        ((List) input.read("bees", BeehiveBlockEntity.Occupant.LIST_CODEC).orElse(List.of())).forEach(this::storeBee);
        this.savedFlowerPos = (BlockPos) input.read("flower_pos", BlockPos.CODEC).orElse((Object) null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("bees", BeehiveBlockEntity.Occupant.LIST_CODEC, this.getBees());
        output.storeNullable("flower_pos", BlockPos.CODEC, this.savedFlowerPos);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.stored.clear();
        List<BeehiveBlockEntity.Occupant> list = ((Bees) components.getOrDefault(DataComponents.BEES, Bees.EMPTY)).bees();

        list.forEach(this::storeBee);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.BEES, new Bees(this.getBees()));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("bees");
    }

    private List<BeehiveBlockEntity.Occupant> getBees() {
        return this.stored.stream().map(BeehiveBlockEntity.BeeData::toOccupant).toList();
    }

    @Override
    public void registerDebugValues(ServerLevel level, DebugValueSource.Registration registration) {
        registration.register(DebugSubscriptions.BEE_HIVES, () -> {
            return DebugHiveInfo.pack(this);
        });
    }

    public static enum BeeReleaseStatus {

        HONEY_DELIVERED, BEE_RELEASED, EMERGENCY;

        private BeeReleaseStatus() {}
    }

    private static class BeeData {

        private final BeehiveBlockEntity.Occupant occupant;
        private int ticksInHive;

        private BeeData(BeehiveBlockEntity.Occupant occupant) {
            this.occupant = occupant;
            this.ticksInHive = occupant.ticksInHive();
        }

        public boolean tick() {
            return this.ticksInHive++ > this.occupant.minTicksInHive;
        }

        public BeehiveBlockEntity.Occupant toOccupant() {
            return new BeehiveBlockEntity.Occupant(this.occupant.entityData, this.ticksInHive, this.occupant.minTicksInHive);
        }

        public boolean hasNectar() {
            return this.occupant.entityData.getUnsafe().getBooleanOr("HasNectar", false);
        }
    }

    public static record Occupant(TypedEntityData<EntityType<?>> entityData, int ticksInHive, int minTicksInHive) {

        public static final Codec<BeehiveBlockEntity.Occupant> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(TypedEntityData.codec(EntityType.CODEC).fieldOf("entity_data").forGetter(BeehiveBlockEntity.Occupant::entityData), Codec.INT.fieldOf("ticks_in_hive").forGetter(BeehiveBlockEntity.Occupant::ticksInHive), Codec.INT.fieldOf("min_ticks_in_hive").forGetter(BeehiveBlockEntity.Occupant::minTicksInHive)).apply(instance, BeehiveBlockEntity.Occupant::new);
        });
        public static final Codec<List<BeehiveBlockEntity.Occupant>> LIST_CODEC = BeehiveBlockEntity.Occupant.CODEC.listOf();
        public static final StreamCodec<RegistryFriendlyByteBuf, BeehiveBlockEntity.Occupant> STREAM_CODEC = StreamCodec.composite(TypedEntityData.streamCodec(EntityType.STREAM_CODEC), BeehiveBlockEntity.Occupant::entityData, ByteBufCodecs.VAR_INT, BeehiveBlockEntity.Occupant::ticksInHive, ByteBufCodecs.VAR_INT, BeehiveBlockEntity.Occupant::minTicksInHive, BeehiveBlockEntity.Occupant::new);

        public static BeehiveBlockEntity.Occupant of(Entity entity) {
            BeehiveBlockEntity.Occupant beehiveblockentity_occupant;

            try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(entity.problemPath(), BeehiveBlockEntity.LOGGER)) {
                TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, entity.registryAccess());

                entity.save(tagvalueoutput);
                List list = BeehiveBlockEntity.IGNORED_BEE_TAGS;

                Objects.requireNonNull(tagvalueoutput);
                list.forEach(tagvalueoutput::discard);
                CompoundTag compoundtag = tagvalueoutput.buildResult();
                boolean flag = compoundtag.getBooleanOr("HasNectar", false);

                beehiveblockentity_occupant = new BeehiveBlockEntity.Occupant(TypedEntityData.of(entity.getType(), compoundtag), 0, flag ? 2400 : 600);
            }

            return beehiveblockentity_occupant;
        }

        public static BeehiveBlockEntity.Occupant create(int ticksInHive) {
            return new BeehiveBlockEntity.Occupant(TypedEntityData.of(EntityType.BEE, new CompoundTag()), ticksInHive, 600);
        }

        public @Nullable Entity createEntity(Level level, BlockPos hivePos) {
            CompoundTag compoundtag = this.entityData.copyTagWithoutId();
            List list = BeehiveBlockEntity.IGNORED_BEE_TAGS;

            Objects.requireNonNull(compoundtag);
            list.forEach(compoundtag::remove);
            Entity entity = EntityType.loadEntityRecursive(this.entityData.type(), compoundtag, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);

            if (entity != null && entity.getType().is(EntityTypeTags.BEEHIVE_INHABITORS)) {
                entity.setNoGravity(true);
                if (entity instanceof Bee) {
                    Bee bee = (Bee) entity;

                    bee.setHivePos(hivePos);
                    setBeeReleaseData(this.ticksInHive, bee);
                }

                return entity;
            } else {
                return null;
            }
        }

        private static void setBeeReleaseData(int ticksInHive, Bee bee) {
            int j = bee.getAge();

            if (j < 0) {
                bee.setAge(Math.min(0, j + ticksInHive));
            } else if (j > 0) {
                bee.setAge(Math.max(0, j - ticksInHive));
            }

            bee.setInLoveTime(Math.max(0, bee.getInLoveTime() - ticksInHive));
        }
    }
}
