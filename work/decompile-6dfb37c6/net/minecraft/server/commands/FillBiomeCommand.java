package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.apache.commons.lang3.mutable.MutableInt;

public class FillBiomeCommand {

    public static final SimpleCommandExceptionType ERROR_NOT_LOADED = new SimpleCommandExceptionType(Component.translatable("argument.pos.unloaded"));
    private static final Dynamic2CommandExceptionType ERROR_VOLUME_TOO_LARGE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.fillbiome.toobig", object, object1);
    });

    public FillBiomeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("fillbiome").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos()).then(((RequiredArgumentBuilder) Commands.argument("biome", ResourceArgument.resource(context, Registries.BIOME)).executes((commandcontext) -> {
            return fill((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "from"), BlockPosArgument.getLoadedBlockPos(commandcontext, "to"), ResourceArgument.getResource(commandcontext, "biome", Registries.BIOME), (holder) -> {
                return true;
            });
        })).then(Commands.literal("replace").then(Commands.argument("filter", ResourceOrTagArgument.resourceOrTag(context, Registries.BIOME)).executes((commandcontext) -> {
            return fill((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "from"), BlockPosArgument.getLoadedBlockPos(commandcontext, "to"), ResourceArgument.getResource(commandcontext, "biome", Registries.BIOME), ResourceOrTagArgument.getResourceOrTag(commandcontext, "filter", Registries.BIOME));
        })))))));
    }

    private static int quantize(int blockCoord) {
        return QuartPos.toBlock(QuartPos.fromBlock(blockCoord));
    }

    private static BlockPos quantize(BlockPos block) {
        return new BlockPos(quantize(block.getX()), quantize(block.getY()), quantize(block.getZ()));
    }

    private static BiomeResolver makeResolver(MutableInt count, ChunkAccess chunk, BoundingBox region, Holder<Biome> toFill, Predicate<Holder<Biome>> filter) {
        return (i, j, k, climate_sampler) -> {
            int l = QuartPos.toBlock(i);
            int i1 = QuartPos.toBlock(j);
            int j1 = QuartPos.toBlock(k);
            Holder<Biome> holder1 = chunk.getNoiseBiome(i, j, k);

            if (region.isInside(l, i1, j1) && filter.test(holder1)) {
                count.increment();
                return toFill;
            } else {
                return holder1;
            }
        };
    }

    public static Either<Integer, CommandSyntaxException> fill(ServerLevel level, BlockPos rawFrom, BlockPos rawTo, Holder<Biome> biome) {
        return fill(level, rawFrom, rawTo, biome, (holder1) -> {
            return true;
        }, (supplier) -> {
        });
    }

    public static Either<Integer, CommandSyntaxException> fill(ServerLevel level, BlockPos rawFrom, BlockPos rawTo, Holder<Biome> biome, Predicate<Holder<Biome>> filter, Consumer<Supplier<Component>> successMessageConsumer) {
        BlockPos blockpos2 = quantize(rawFrom);
        BlockPos blockpos3 = quantize(rawTo);
        BoundingBox boundingbox = BoundingBox.fromCorners(blockpos2, blockpos3);
        int i = boundingbox.getXSpan() * boundingbox.getYSpan() * boundingbox.getZSpan();
        int j = (Integer) level.getGameRules().get(GameRules.MAX_BLOCK_MODIFICATIONS);

        if (i > j) {
            return Either.right(FillBiomeCommand.ERROR_VOLUME_TOO_LARGE.create(j, i));
        } else {
            List<ChunkAccess> list = new ArrayList();

            for (int k = SectionPos.blockToSectionCoord(boundingbox.minZ()); k <= SectionPos.blockToSectionCoord(boundingbox.maxZ()); ++k) {
                for (int l = SectionPos.blockToSectionCoord(boundingbox.minX()); l <= SectionPos.blockToSectionCoord(boundingbox.maxX()); ++l) {
                    ChunkAccess chunkaccess = level.getChunk(l, k, ChunkStatus.FULL, false);

                    if (chunkaccess == null) {
                        return Either.right(FillBiomeCommand.ERROR_NOT_LOADED.create());
                    }

                    list.add(chunkaccess);
                }
            }

            MutableInt mutableint = new MutableInt(0);

            for (ChunkAccess chunkaccess1 : list) {
                chunkaccess1.fillBiomesFromNoise(makeResolver(mutableint, chunkaccess1, boundingbox, biome, filter), level.getChunkSource().randomState().sampler());
                chunkaccess1.markUnsaved();
            }

            level.getChunkSource().chunkMap.resendBiomesForChunks(list);
            successMessageConsumer.accept((Supplier) () -> {
                return Component.translatable("commands.fillbiome.success.count", mutableint.intValue(), boundingbox.minX(), boundingbox.minY(), boundingbox.minZ(), boundingbox.maxX(), boundingbox.maxY(), boundingbox.maxZ());
            });
            return Either.left(mutableint.intValue());
        }
    }

    private static int fill(CommandSourceStack source, BlockPos rawFrom, BlockPos rawTo, Holder.Reference<Biome> biome, Predicate<Holder<Biome>> filter) throws CommandSyntaxException {
        Either<Integer, CommandSyntaxException> either = fill(source.getLevel(), rawFrom, rawTo, biome, filter, (supplier) -> {
            source.sendSuccess(supplier, true);
        });
        Optional<CommandSyntaxException> optional = either.right();

        if (optional.isPresent()) {
            throw (CommandSyntaxException) optional.get();
        } else {
            return (Integer) either.left().get();
        }
    }
}
