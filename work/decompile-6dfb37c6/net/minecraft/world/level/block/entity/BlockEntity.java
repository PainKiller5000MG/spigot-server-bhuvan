package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class BlockEntity implements DebugValueSource {

    private static final Codec<BlockEntityType<?>> TYPE_CODEC = BuiltInRegistries.BLOCK_ENTITY_TYPE.byNameCodec();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BlockEntityType<?> type;
    protected @Nullable Level level;
    protected final BlockPos worldPosition;
    protected boolean remove;
    private BlockState blockState;
    private DataComponentMap components;

    public BlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState) {
        this.components = DataComponentMap.EMPTY;
        this.type = type;
        this.worldPosition = worldPosition.immutable();
        this.validateBlockState(blockState);
        this.blockState = blockState;
    }

    private void validateBlockState(BlockState blockState) {
        if (!this.isValidBlockState(blockState)) {
            String s = this.getNameForReporting();

            throw new IllegalStateException("Invalid block entity " + s + " state at " + String.valueOf(this.worldPosition) + ", got " + String.valueOf(blockState));
        }
    }

    public boolean isValidBlockState(BlockState blockState) {
        return this.type.isValid(blockState);
    }

    public static BlockPos getPosFromTag(ChunkPos base, CompoundTag entityTag) {
        int i = entityTag.getIntOr("x", 0);
        int j = entityTag.getIntOr("y", 0);
        int k = entityTag.getIntOr("z", 0);
        int l = SectionPos.blockToSectionCoord(i);
        int i1 = SectionPos.blockToSectionCoord(k);

        if (l != base.x || i1 != base.z) {
            BlockEntity.LOGGER.warn("Block entity {} found in a wrong chunk, expected position from chunk {}", entityTag, base);
            i = base.getBlockX(SectionPos.sectionRelative(i));
            k = base.getBlockZ(SectionPos.sectionRelative(k));
        }

        return new BlockPos(i, j, k);
    }

    public @Nullable Level getLevel() {
        return this.level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public boolean hasLevel() {
        return this.level != null;
    }

    protected void loadAdditional(ValueInput input) {}

    public final void loadWithComponents(ValueInput input) {
        this.loadAdditional(input);
        this.components = (DataComponentMap) input.read("components", DataComponentMap.CODEC).orElse(DataComponentMap.EMPTY);
    }

    public final void loadCustomOnly(ValueInput input) {
        this.loadAdditional(input);
    }

    protected void saveAdditional(ValueOutput output) {}

    public final CompoundTag saveWithFullMetadata(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(this.problemPath(), BlockEntity.LOGGER)) {
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, registries);

            this.saveWithFullMetadata((ValueOutput) tagvalueoutput);
            return tagvalueoutput.buildResult();
        }
    }

    public void saveWithFullMetadata(ValueOutput output) {
        this.saveWithoutMetadata(output);
        this.saveMetadata(output);
    }

    public void saveWithId(ValueOutput output) {
        this.saveWithoutMetadata(output);
        this.saveId(output);
    }

    public final CompoundTag saveWithoutMetadata(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(this.problemPath(), BlockEntity.LOGGER)) {
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, registries);

            this.saveWithoutMetadata((ValueOutput) tagvalueoutput);
            return tagvalueoutput.buildResult();
        }
    }

    public void saveWithoutMetadata(ValueOutput output) {
        this.saveAdditional(output);
        output.store("components", DataComponentMap.CODEC, this.components);
    }

    public final CompoundTag saveCustomOnly(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(this.problemPath(), BlockEntity.LOGGER)) {
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, registries);

            this.saveCustomOnly((ValueOutput) tagvalueoutput);
            return tagvalueoutput.buildResult();
        }
    }

    public void saveCustomOnly(ValueOutput output) {
        this.saveAdditional(output);
    }

    private void saveId(ValueOutput output) {
        addEntityType(output, this.getType());
    }

    public static void addEntityType(ValueOutput output, BlockEntityType<?> type) {
        output.store("id", BlockEntity.TYPE_CODEC, type);
    }

    private void saveMetadata(ValueOutput output) {
        this.saveId(output);
        output.putInt("x", this.worldPosition.getX());
        output.putInt("y", this.worldPosition.getY());
        output.putInt("z", this.worldPosition.getZ());
    }

    public static @Nullable BlockEntity loadStatic(BlockPos pos, BlockState state, CompoundTag tag, HolderLookup.Provider registries) {
        BlockEntityType<?> blockentitytype = (BlockEntityType) tag.read("id", BlockEntity.TYPE_CODEC).orElse((Object) null);

        if (blockentitytype == null) {
            BlockEntity.LOGGER.error("Skipping block entity with invalid type: {}", tag.get("id"));
            return null;
        } else {
            BlockEntity blockentity;

            try {
                blockentity = blockentitytype.create(pos, state);
            } catch (Throwable throwable) {
                BlockEntity.LOGGER.error("Failed to create block entity {} for block {} at position {} ", new Object[]{blockentitytype, pos, state, throwable});
                return null;
            }

            try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(blockentity.problemPath(), BlockEntity.LOGGER)) {
                blockentity.loadWithComponents(TagValueInput.create(problemreporter_scopedcollector, registries, tag));
                return blockentity;
            } catch (Throwable throwable1) {
                BlockEntity.LOGGER.error("Failed to load data for block entity {} for block {} at position {}", new Object[]{blockentitytype, pos, state, throwable1});
                return null;
            }
        }
    }

    public void setChanged() {
        if (this.level != null) {
            setChanged(this.level, this.worldPosition, this.blockState);
        }

    }

    protected static void setChanged(Level level, BlockPos worldPosition, BlockState blockState) {
        level.blockEntityChanged(worldPosition);
        if (!blockState.isAir()) {
            level.updateNeighbourForOutputSignal(worldPosition, blockState.getBlock());
        }

    }

    public BlockPos getBlockPos() {
        return this.worldPosition;
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return null;
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    public boolean isRemoved() {
        return this.remove;
    }

    public void setRemoved() {
        this.remove = true;
    }

    public void clearRemoved() {
        this.remove = false;
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this instanceof Container container) {
            if (this.level != null) {
                Containers.dropContents(this.level, pos, container);
            }
        }

    }

    public boolean triggerEvent(int b0, int b1) {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory category) {
        category.setDetail("Name", this::getNameForReporting);
        BlockState blockstate = this.getBlockState();

        Objects.requireNonNull(blockstate);
        category.setDetail("Cached block", blockstate::toString);
        if (this.level == null) {
            category.setDetail("Block location", () -> {
                return String.valueOf(this.worldPosition) + " (world missing)";
            });
        } else {
            blockstate = this.level.getBlockState(this.worldPosition);
            Objects.requireNonNull(blockstate);
            category.setDetail("Actual block", blockstate::toString);
            CrashReportCategory.populateBlockLocationDetails(category, this.level, this.worldPosition);
        }

    }

    public String getNameForReporting() {
        String s = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(this.getType()));

        return s + " // " + this.getClass().getCanonicalName();
    }

    public BlockEntityType<?> getType() {
        return this.type;
    }

    /** @deprecated */
    @Deprecated
    public void setBlockState(BlockState blockState) {
        this.validateBlockState(blockState);
        this.blockState = blockState;
    }

    protected void applyImplicitComponents(DataComponentGetter components) {}

    public final void applyComponentsFromItemStack(ItemStack stack) {
        this.applyComponents(stack.getPrototype(), stack.getComponentsPatch());
    }

    public final void applyComponents(DataComponentMap prototype, DataComponentPatch patch) {
        final Set<DataComponentType<?>> set = new HashSet();

        set.add(DataComponents.BLOCK_ENTITY_DATA);
        set.add(DataComponents.BLOCK_STATE);
        final DataComponentMap datacomponentmap1 = PatchedDataComponentMap.fromPatch(prototype, patch);

        this.applyImplicitComponents(new DataComponentGetter() {
            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> type) {
                set.add(type);
                return (T) datacomponentmap1.get(type);
            }

            @Override
            public <T> T getOrDefault(DataComponentType<? extends T> type, T defaultValue) {
                set.add(type);
                return (T) datacomponentmap1.getOrDefault(type, defaultValue);
            }
        });
        Objects.requireNonNull(set);
        DataComponentPatch datacomponentpatch1 = patch.forget(set::contains);

        this.components = datacomponentpatch1.split().added();
    }

    protected void collectImplicitComponents(DataComponentMap.Builder components) {}

    /** @deprecated */
    @Deprecated
    public void removeComponentsFromTag(ValueOutput output) {}

    public final DataComponentMap collectComponents() {
        DataComponentMap.Builder datacomponentmap_builder = DataComponentMap.builder();

        datacomponentmap_builder.addAll(this.components);
        this.collectImplicitComponents(datacomponentmap_builder);
        return datacomponentmap_builder.build();
    }

    public DataComponentMap components() {
        return this.components;
    }

    public void setComponents(DataComponentMap components) {
        this.components = components;
    }

    public static @Nullable Component parseCustomNameSafe(ValueInput input, String name) {
        return (Component) input.read(name, ComponentSerialization.CODEC).orElse((Object) null);
    }

    public ProblemReporter.PathElement problemPath() {
        return new BlockEntity.BlockEntityPathElement(this);
    }

    @Override
    public void registerDebugValues(ServerLevel level, DebugValueSource.Registration registration) {}

    private static record BlockEntityPathElement(BlockEntity blockEntity) implements ProblemReporter.PathElement {

        @Override
        public String get() {
            String s = this.blockEntity.getNameForReporting();

            return s + "@" + String.valueOf(this.blockEntity.getBlockPos());
        }
    }
}
