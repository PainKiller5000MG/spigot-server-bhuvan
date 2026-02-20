package net.minecraft.world.level.block.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class BeaconBlockEntity extends BlockEntity implements MenuProvider, Nameable, BeaconBeamOwner {

    private static final int MAX_LEVELS = 4;
    public static final List<List<Holder<MobEffect>>> BEACON_EFFECTS = List.of(List.of(MobEffects.SPEED, MobEffects.HASTE), List.of(MobEffects.RESISTANCE, MobEffects.JUMP_BOOST), List.of(MobEffects.STRENGTH), List.of(MobEffects.REGENERATION));
    private static final Set<Holder<MobEffect>> VALID_EFFECTS = (Set) BeaconBlockEntity.BEACON_EFFECTS.stream().flatMap(Collection::stream).collect(Collectors.toSet());
    public static final int DATA_LEVELS = 0;
    public static final int DATA_PRIMARY = 1;
    public static final int DATA_SECONDARY = 2;
    public static final int NUM_DATA_VALUES = 3;
    private static final int BLOCKS_CHECK_PER_TICK = 10;
    private static final Component DEFAULT_NAME = Component.translatable("container.beacon");
    private static final String TAG_PRIMARY = "primary_effect";
    private static final String TAG_SECONDARY = "secondary_effect";
    private List<BeaconBeamOwner.Section> beamSections = new ArrayList();
    private List<BeaconBeamOwner.Section> checkingBeamSections = new ArrayList();
    public int levels;
    private int lastCheckY;
    public @Nullable Holder<MobEffect> primaryPower;
    public @Nullable Holder<MobEffect> secondaryPower;
    public @Nullable Component name;
    public LockCode lockKey;
    private final ContainerData dataAccess;

    private static @Nullable Holder<MobEffect> filterEffect(@Nullable Holder<MobEffect> effect) {
        return BeaconBlockEntity.VALID_EFFECTS.contains(effect) ? effect : null;
    }

    public BeaconBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.BEACON, worldPosition, blockState);
        this.lockKey = LockCode.NO_LOCK;
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int dataId) {
                int j;

                switch (dataId) {
                    case 0:
                        j = BeaconBlockEntity.this.levels;
                        break;
                    case 1:
                        j = BeaconMenu.encodeEffect(BeaconBlockEntity.this.primaryPower);
                        break;
                    case 2:
                        j = BeaconMenu.encodeEffect(BeaconBlockEntity.this.secondaryPower);
                        break;
                    default:
                        j = 0;
                }

                return j;
            }

            @Override
            public void set(int dataId, int value) {
                switch (dataId) {
                    case 0:
                        BeaconBlockEntity.this.levels = value;
                        break;
                    case 1:
                        if (!BeaconBlockEntity.this.level.isClientSide() && !BeaconBlockEntity.this.beamSections.isEmpty()) {
                            BeaconBlockEntity.playSound(BeaconBlockEntity.this.level, BeaconBlockEntity.this.worldPosition, SoundEvents.BEACON_POWER_SELECT);
                        }

                        BeaconBlockEntity.this.primaryPower = BeaconBlockEntity.filterEffect(BeaconMenu.decodeEffect(value));
                        break;
                    case 2:
                        BeaconBlockEntity.this.secondaryPower = BeaconBlockEntity.filterEffect(BeaconMenu.decodeEffect(value));
                }

            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public static void tick(Level level, BlockPos pos, BlockState selfState, BeaconBlockEntity entity) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        BlockPos blockpos1;

        if (entity.lastCheckY < j) {
            blockpos1 = pos;
            entity.checkingBeamSections = Lists.newArrayList();
            entity.lastCheckY = pos.getY() - 1;
        } else {
            blockpos1 = new BlockPos(i, entity.lastCheckY + 1, k);
        }

        BeaconBeamOwner.Section beaconbeamowner_section = entity.checkingBeamSections.isEmpty() ? null : (BeaconBeamOwner.Section) entity.checkingBeamSections.get(entity.checkingBeamSections.size() - 1);
        int l = level.getHeight(Heightmap.Types.WORLD_SURFACE, i, k);

        for (int i1 = 0; i1 < 10 && blockpos1.getY() <= l; ++i1) {
            BlockState blockstate1 = level.getBlockState(blockpos1);
            Block block = blockstate1.getBlock();

            if (block instanceof BeaconBeamBlock beaconbeamblock) {
                int j1 = beaconbeamblock.getColor().getTextureDiffuseColor();

                if (entity.checkingBeamSections.size() <= 1) {
                    beaconbeamowner_section = new BeaconBeamOwner.Section(j1);
                    entity.checkingBeamSections.add(beaconbeamowner_section);
                } else if (beaconbeamowner_section != null) {
                    if (j1 == beaconbeamowner_section.getColor()) {
                        beaconbeamowner_section.increaseHeight();
                    } else {
                        beaconbeamowner_section = new BeaconBeamOwner.Section(ARGB.average(beaconbeamowner_section.getColor(), j1));
                        entity.checkingBeamSections.add(beaconbeamowner_section);
                    }
                }
            } else {
                if (beaconbeamowner_section == null || blockstate1.getLightBlock() >= 15 && !blockstate1.is(Blocks.BEDROCK)) {
                    entity.checkingBeamSections.clear();
                    entity.lastCheckY = l;
                    break;
                }

                beaconbeamowner_section.increaseHeight();
            }

            blockpos1 = blockpos1.above();
            ++entity.lastCheckY;
        }

        int k1 = entity.levels;

        if (level.getGameTime() % 80L == 0L) {
            if (!entity.beamSections.isEmpty()) {
                entity.levels = updateBase(level, i, j, k);
            }

            if (entity.levels > 0 && !entity.beamSections.isEmpty()) {
                applyEffects(level, pos, entity.levels, entity.primaryPower, entity.secondaryPower);
                playSound(level, pos, SoundEvents.BEACON_AMBIENT);
            }
        }

        if (entity.lastCheckY >= l) {
            entity.lastCheckY = level.getMinY() - 1;
            boolean flag = k1 > 0;

            entity.beamSections = entity.checkingBeamSections;
            if (!level.isClientSide()) {
                boolean flag1 = entity.levels > 0;

                if (!flag && flag1) {
                    playSound(level, pos, SoundEvents.BEACON_ACTIVATE);

                    for (ServerPlayer serverplayer : level.getEntitiesOfClass(ServerPlayer.class, (new AABB((double) i, (double) j, (double) k, (double) i, (double) (j - 4), (double) k)).inflate(10.0D, 5.0D, 10.0D))) {
                        CriteriaTriggers.CONSTRUCT_BEACON.trigger(serverplayer, entity.levels);
                    }
                } else if (flag && !flag1) {
                    playSound(level, pos, SoundEvents.BEACON_DEACTIVATE);
                }
            }
        }

    }

    private static int updateBase(Level level, int x, int y, int z) {
        int l = 0;

        for (int i1 = 1; i1 <= 4; l = i1++) {
            int j1 = y - i1;

            if (j1 < level.getMinY()) {
                break;
            }

            boolean flag = true;

            for (int k1 = x - i1; k1 <= x + i1 && flag; ++k1) {
                for (int l1 = z - i1; l1 <= z + i1; ++l1) {
                    if (!level.getBlockState(new BlockPos(k1, j1, l1)).is(BlockTags.BEACON_BASE_BLOCKS)) {
                        flag = false;
                        break;
                    }
                }
            }

            if (!flag) {
                break;
            }
        }

        return l;
    }

    @Override
    public void setRemoved() {
        playSound(this.level, this.worldPosition, SoundEvents.BEACON_DEACTIVATE);
        super.setRemoved();
    }

    private static void applyEffects(Level level, BlockPos worldPosition, int levels, @Nullable Holder<MobEffect> primaryPower, @Nullable Holder<MobEffect> secondaryPower) {
        if (!level.isClientSide() && primaryPower != null) {
            double d0 = (double) (levels * 10 + 10);
            int j = 0;

            if (levels >= 4 && Objects.equals(primaryPower, secondaryPower)) {
                j = 1;
            }

            int k = (9 + levels * 2) * 20;
            AABB aabb = (new AABB(worldPosition)).inflate(d0).expandTowards(0.0D, (double) level.getHeight(), 0.0D);
            List<Player> list = level.<Player>getEntitiesOfClass(Player.class, aabb);

            for (Player player : list) {
                player.addEffect(new MobEffectInstance(primaryPower, k, j, true, true));
            }

            if (levels >= 4 && !Objects.equals(primaryPower, secondaryPower) && secondaryPower != null) {
                for (Player player1 : list) {
                    player1.addEffect(new MobEffectInstance(secondaryPower, k, 0, true, true));
                }
            }

        }
    }

    public static void playSound(Level level, BlockPos worldPosition, SoundEvent event) {
        level.playSound((Entity) null, worldPosition, event, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public List<BeaconBeamOwner.Section> getBeamSections() {
        return (List<BeaconBeamOwner.Section>) (this.levels == 0 ? ImmutableList.of() : this.beamSections);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    private static void storeEffect(ValueOutput output, String field, @Nullable Holder<MobEffect> effect) {
        if (effect != null) {
            effect.unwrapKey().ifPresent((resourcekey) -> {
                output.putString(field, resourcekey.identifier().toString());
            });
        }

    }

    private static @Nullable Holder<MobEffect> loadEffect(ValueInput input, String field) {
        Optional optional = input.read(field, BuiltInRegistries.MOB_EFFECT.holderByNameCodec());
        Set set = BeaconBlockEntity.VALID_EFFECTS;

        Objects.requireNonNull(set);
        return (Holder) optional.filter(set::contains).orElse((Object) null);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.primaryPower = loadEffect(input, "primary_effect");
        this.secondaryPower = loadEffect(input, "secondary_effect");
        this.name = parseCustomNameSafe(input, "CustomName");
        this.lockKey = LockCode.fromTag(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        storeEffect(output, "primary_effect", this.primaryPower);
        storeEffect(output, "secondary_effect", this.secondaryPower);
        output.putInt("Levels", this.levels);
        output.storeNullable("CustomName", ComponentSerialization.CODEC, this.name);
        this.lockKey.addToTag(output);
    }

    public void setCustomName(@Nullable Component name) {
        this.name = name;
    }

    @Override
    public @Nullable Component getCustomName() {
        return this.name;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (this.lockKey.canUnlock(player)) {
            return new BeaconMenu(containerId, inventory, this.dataAccess, ContainerLevelAccess.create(this.level, this.getBlockPos()));
        } else {
            BaseContainerBlockEntity.sendChestLockedNotifications(this.getBlockPos().getCenter(), player, this.getDisplayName());
            return null;
        }
    }

    @Override
    public Component getDisplayName() {
        return this.getName();
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : BeaconBlockEntity.DEFAULT_NAME;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.name = (Component) components.get(DataComponents.CUSTOM_NAME);
        this.lockKey = (LockCode) components.getOrDefault(DataComponents.LOCK, LockCode.NO_LOCK);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.name);
        if (!this.lockKey.equals(LockCode.NO_LOCK)) {
            components.set(DataComponents.LOCK, this.lockKey);
        }

    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("CustomName");
        output.discard("lock");
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.lastCheckY = level.getMinY() - 1;
    }
}
