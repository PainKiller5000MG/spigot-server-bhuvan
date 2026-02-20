package net.minecraft.commands.arguments.blocks;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class BlockInput implements Predicate<BlockInWorld> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final BlockState state;
    private final Set<Property<?>> properties;
    private final @Nullable CompoundTag tag;

    public BlockInput(BlockState state, Set<Property<?>> properties, @Nullable CompoundTag tag) {
        this.state = state;
        this.properties = properties;
        this.tag = tag;
    }

    public BlockState getState() {
        return this.state;
    }

    public Set<Property<?>> getDefinedProperties() {
        return this.properties;
    }

    public boolean test(BlockInWorld blockInWorld) {
        BlockState blockstate = blockInWorld.getState();

        if (!blockstate.is(this.state.getBlock())) {
            return false;
        } else {
            for (Property<?> property : this.properties) {
                if (blockstate.getValue(property) != this.state.getValue(property)) {
                    return false;
                }
            }

            if (this.tag == null) {
                return true;
            } else {
                BlockEntity blockentity = blockInWorld.getEntity();

                return blockentity != null && NbtUtils.compareNbt(this.tag, blockentity.saveWithFullMetadata((HolderLookup.Provider) blockInWorld.getLevel().registryAccess()), true);
            }
        }
    }

    public boolean test(ServerLevel level, BlockPos pos) {
        return this.test(new BlockInWorld(level, pos, false));
    }

    public boolean place(ServerLevel level, BlockPos pos, @Block.UpdateFlags int update) {
        BlockState blockstate = (update & 16) != 0 ? this.state : Block.updateFromNeighbourShapes(this.state, level, pos);

        if (blockstate.isAir()) {
            blockstate = this.state;
        }

        blockstate = this.overwriteWithDefinedProperties(blockstate);
        boolean flag = false;

        if (level.setBlock(pos, blockstate, update)) {
            flag = true;
        }

        if (this.tag != null) {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity != null) {
                try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(BlockInput.LOGGER)) {
                    HolderLookup.Provider holderlookup_provider = level.registryAccess();
                    ProblemReporter problemreporter = problemreporter_scopedcollector.forChild(blockentity.problemPath());
                    TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter.forChild(() -> {
                        return "(before)";
                    }), holderlookup_provider);

                    blockentity.saveWithoutMetadata((ValueOutput) tagvalueoutput);
                    CompoundTag compoundtag = tagvalueoutput.buildResult();

                    blockentity.loadWithComponents(TagValueInput.create(problemreporter_scopedcollector, holderlookup_provider, this.tag));
                    TagValueOutput tagvalueoutput1 = TagValueOutput.createWithContext(problemreporter.forChild(() -> {
                        return "(after)";
                    }), holderlookup_provider);

                    blockentity.saveWithoutMetadata((ValueOutput) tagvalueoutput1);
                    CompoundTag compoundtag1 = tagvalueoutput1.buildResult();

                    if (!compoundtag1.equals(compoundtag)) {
                        flag = true;
                        blockentity.setChanged();
                        level.getChunkSource().blockChanged(pos);
                    }
                }
            }
        }

        return flag;
    }

    private BlockState overwriteWithDefinedProperties(BlockState state) {
        if (state == this.state) {
            return state;
        } else {
            for (Property<?> property : this.properties) {
                state = copyProperty(state, this.state, property);
            }

            return state;
        }
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState target, BlockState source, Property<T> property) {
        return (BlockState) target.trySetValue(property, source.getValue(property));
    }
}
