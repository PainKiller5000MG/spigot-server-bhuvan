package net.minecraft.commands.arguments.blocks;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public class BlockPredicateArgument implements ArgumentType<BlockPredicateArgument.Result> {

    private static final Collection<String> EXAMPLES = Arrays.asList("stone", "minecraft:stone", "stone[foo=bar]", "#stone", "#stone[foo=bar]{baz=nbt}");
    private final HolderLookup<Block> blocks;

    public BlockPredicateArgument(CommandBuildContext context) {
        this.blocks = context.lookupOrThrow(Registries.BLOCK);
    }

    public static BlockPredicateArgument blockPredicate(CommandBuildContext context) {
        return new BlockPredicateArgument(context);
    }

    public BlockPredicateArgument.Result parse(StringReader reader) throws CommandSyntaxException {
        return parse(this.blocks, reader);
    }

    public static BlockPredicateArgument.Result parse(HolderLookup<Block> blocks, StringReader reader) throws CommandSyntaxException {
        return (BlockPredicateArgument.Result) BlockStateParser.parseForTesting(blocks, reader, true).map((blockstateparser_blockresult) -> {
            return new BlockPredicateArgument.BlockPredicate(blockstateparser_blockresult.blockState(), blockstateparser_blockresult.properties().keySet(), blockstateparser_blockresult.nbt());
        }, (blockstateparser_tagresult) -> {
            return new BlockPredicateArgument.TagPredicate(blockstateparser_tagresult.tag(), blockstateparser_tagresult.vagueProperties(), blockstateparser_tagresult.nbt());
        });
    }

    public static Predicate<BlockInWorld> getBlockPredicate(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return (Predicate) context.getArgument(name, BlockPredicateArgument.Result.class);
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return BlockStateParser.fillSuggestions(this.blocks, builder, true, true);
    }

    public Collection<String> getExamples() {
        return BlockPredicateArgument.EXAMPLES;
    }

    private static class BlockPredicate implements BlockPredicateArgument.Result {

        private final BlockState state;
        private final Set<Property<?>> properties;
        private final @Nullable CompoundTag nbt;

        public BlockPredicate(BlockState state, Set<Property<?>> properties, @Nullable CompoundTag nbt) {
            this.state = state;
            this.properties = properties;
            this.nbt = nbt;
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

                if (this.nbt == null) {
                    return true;
                } else {
                    BlockEntity blockentity = blockInWorld.getEntity();

                    return blockentity != null && NbtUtils.compareNbt(this.nbt, blockentity.saveWithFullMetadata((HolderLookup.Provider) blockInWorld.getLevel().registryAccess()), true);
                }
            }
        }

        @Override
        public boolean requiresNbt() {
            return this.nbt != null;
        }
    }

    private static class TagPredicate implements BlockPredicateArgument.Result {

        private final HolderSet<Block> tag;
        private final @Nullable CompoundTag nbt;
        private final Map<String, String> vagueProperties;

        private TagPredicate(HolderSet<Block> tag, Map<String, String> vagueProperties, @Nullable CompoundTag nbt) {
            this.tag = tag;
            this.vagueProperties = vagueProperties;
            this.nbt = nbt;
        }

        public boolean test(BlockInWorld blockInWorld) {
            BlockState blockstate = blockInWorld.getState();

            if (!blockstate.is(this.tag)) {
                return false;
            } else {
                for (Map.Entry<String, String> map_entry : this.vagueProperties.entrySet()) {
                    Property<?> property = blockstate.getBlock().getStateDefinition().getProperty((String) map_entry.getKey());

                    if (property == null) {
                        return false;
                    }

                    Comparable<?> comparable = (Comparable) property.getValue((String) map_entry.getValue()).orElse((Object) null);

                    if (comparable == null) {
                        return false;
                    }

                    if (blockstate.getValue(property) != comparable) {
                        return false;
                    }
                }

                if (this.nbt == null) {
                    return true;
                } else {
                    BlockEntity blockentity = blockInWorld.getEntity();

                    return blockentity != null && NbtUtils.compareNbt(this.nbt, blockentity.saveWithFullMetadata((HolderLookup.Provider) blockInWorld.getLevel().registryAccess()), true);
                }
            }
        }

        @Override
        public boolean requiresNbt() {
            return this.nbt != null;
        }
    }

    public interface Result extends Predicate<BlockInWorld> {

        boolean requiresNbt();
    }
}
