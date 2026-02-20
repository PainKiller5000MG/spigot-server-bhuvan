package net.minecraft.server.commands;

import com.google.common.base.Joiner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class ForceLoadCommand {

    private static final int MAX_CHUNK_LIMIT = 256;
    private static final Dynamic2CommandExceptionType ERROR_TOO_MANY_CHUNKS = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.forceload.toobig", object, object1);
    });
    private static final Dynamic2CommandExceptionType ERROR_NOT_TICKING = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.forceload.query.failure", object, object1);
    });
    private static final SimpleCommandExceptionType ERROR_ALL_ADDED = new SimpleCommandExceptionType(Component.translatable("commands.forceload.added.failure"));
    private static final SimpleCommandExceptionType ERROR_NONE_REMOVED = new SimpleCommandExceptionType(Component.translatable("commands.forceload.removed.failure"));

    public ForceLoadCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("forceload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("add").then(((RequiredArgumentBuilder) Commands.argument("from", ColumnPosArgument.columnPos()).executes((commandcontext) -> {
            return changeForceLoad((CommandSourceStack) commandcontext.getSource(), ColumnPosArgument.getColumnPos(commandcontext, "from"), ColumnPosArgument.getColumnPos(commandcontext, "from"), true);
        })).then(Commands.argument("to", ColumnPosArgument.columnPos()).executes((commandcontext) -> {
            return changeForceLoad((CommandSourceStack) commandcontext.getSource(), ColumnPosArgument.getColumnPos(commandcontext, "from"), ColumnPosArgument.getColumnPos(commandcontext, "to"), true);
        }))))).then(((LiteralArgumentBuilder) Commands.literal("remove").then(((RequiredArgumentBuilder) Commands.argument("from", ColumnPosArgument.columnPos()).executes((commandcontext) -> {
            return changeForceLoad((CommandSourceStack) commandcontext.getSource(), ColumnPosArgument.getColumnPos(commandcontext, "from"), ColumnPosArgument.getColumnPos(commandcontext, "from"), false);
        })).then(Commands.argument("to", ColumnPosArgument.columnPos()).executes((commandcontext) -> {
            return changeForceLoad((CommandSourceStack) commandcontext.getSource(), ColumnPosArgument.getColumnPos(commandcontext, "from"), ColumnPosArgument.getColumnPos(commandcontext, "to"), false);
        })))).then(Commands.literal("all").executes((commandcontext) -> {
            return removeAll((CommandSourceStack) commandcontext.getSource());
        })))).then(((LiteralArgumentBuilder) Commands.literal("query").executes((commandcontext) -> {
            return listForceLoad((CommandSourceStack) commandcontext.getSource());
        })).then(Commands.argument("pos", ColumnPosArgument.columnPos()).executes((commandcontext) -> {
            return queryForceLoad((CommandSourceStack) commandcontext.getSource(), ColumnPosArgument.getColumnPos(commandcontext, "pos"));
        }))));
    }

    private static int queryForceLoad(CommandSourceStack source, ColumnPos pos) throws CommandSyntaxException {
        ChunkPos chunkpos = pos.toChunkPos();
        ServerLevel serverlevel = source.getLevel();
        ResourceKey<Level> resourcekey = serverlevel.dimension();
        boolean flag = serverlevel.getForceLoadedChunks().contains(chunkpos.toLong());

        if (flag) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.forceload.query.success", Component.translationArg(chunkpos), Component.translationArg(resourcekey.identifier()));
            }, false);
            return 1;
        } else {
            throw ForceLoadCommand.ERROR_NOT_TICKING.create(chunkpos, resourcekey.identifier());
        }
    }

    private static int listForceLoad(CommandSourceStack source) {
        ServerLevel serverlevel = source.getLevel();
        ResourceKey<Level> resourcekey = serverlevel.dimension();
        LongSet longset = serverlevel.getForceLoadedChunks();
        int i = longset.size();

        if (i > 0) {
            String s = Joiner.on(", ").join(longset.stream().sorted().map(ChunkPos::new).map(ChunkPos::toString).iterator());

            if (i == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.forceload.list.single", Component.translationArg(resourcekey.identifier()), s);
                }, false);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.forceload.list.multiple", i, Component.translationArg(resourcekey.identifier()), s);
                }, false);
            }
        } else {
            source.sendFailure(Component.translatable("commands.forceload.added.none", Component.translationArg(resourcekey.identifier())));
        }

        return i;
    }

    private static int removeAll(CommandSourceStack source) {
        ServerLevel serverlevel = source.getLevel();
        ResourceKey<Level> resourcekey = serverlevel.dimension();
        LongSet longset = serverlevel.getForceLoadedChunks();

        longset.forEach((i) -> {
            serverlevel.setChunkForced(ChunkPos.getX(i), ChunkPos.getZ(i), false);
        });
        source.sendSuccess(() -> {
            return Component.translatable("commands.forceload.removed.all", Component.translationArg(resourcekey.identifier()));
        }, true);
        return 0;
    }

    private static int changeForceLoad(CommandSourceStack source, ColumnPos from, ColumnPos to, boolean add) throws CommandSyntaxException {
        int i = Math.min(from.x(), to.x());
        int j = Math.min(from.z(), to.z());
        int k = Math.max(from.x(), to.x());
        int l = Math.max(from.z(), to.z());

        if (i >= -30000000 && j >= -30000000 && k < 30000000 && l < 30000000) {
            int i1 = SectionPos.blockToSectionCoord(i);
            int j1 = SectionPos.blockToSectionCoord(j);
            int k1 = SectionPos.blockToSectionCoord(k);
            int l1 = SectionPos.blockToSectionCoord(l);
            long i2 = ((long) (k1 - i1) + 1L) * ((long) (l1 - j1) + 1L);

            if (i2 > 256L) {
                throw ForceLoadCommand.ERROR_TOO_MANY_CHUNKS.create(256, i2);
            } else {
                ServerLevel serverlevel = source.getLevel();
                ResourceKey<Level> resourcekey = serverlevel.dimension();
                ChunkPos chunkpos = null;
                int j2 = 0;

                for (int k2 = i1; k2 <= k1; ++k2) {
                    for (int l2 = j1; l2 <= l1; ++l2) {
                        boolean flag1 = serverlevel.setChunkForced(k2, l2, add);

                        if (flag1) {
                            ++j2;
                            if (chunkpos == null) {
                                chunkpos = new ChunkPos(k2, l2);
                            }
                        }
                    }
                }

                if (j2 == 0) {
                    throw (add ? ForceLoadCommand.ERROR_ALL_ADDED : ForceLoadCommand.ERROR_NONE_REMOVED).create();
                } else {
                    if (j2 == 1) {
                        source.sendSuccess(() -> {
                            return Component.translatable("commands.forceload." + (add ? "added" : "removed") + ".single", Component.translationArg(chunkpos), Component.translationArg(resourcekey.identifier()));
                        }, true);
                    } else {
                        ChunkPos chunkpos1 = new ChunkPos(i1, j1);
                        ChunkPos chunkpos2 = new ChunkPos(k1, l1);

                        source.sendSuccess(() -> {
                            return Component.translatable("commands.forceload." + (add ? "added" : "removed") + ".multiple", j2, Component.translationArg(resourcekey.identifier()), Component.translationArg(chunkpos1), Component.translationArg(chunkpos2));
                        }, true);
                    }

                    return j2;
                }
            }
        } else {
            throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
        }
    }
}
