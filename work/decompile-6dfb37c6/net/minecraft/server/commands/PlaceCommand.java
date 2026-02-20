package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Optional;
import net.minecraft.IdentifierException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.TemplateMirrorArgument;
import net.minecraft.commands.arguments.TemplateRotationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class PlaceCommand {

    private static final SimpleCommandExceptionType ERROR_FEATURE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.place.feature.failed"));
    private static final SimpleCommandExceptionType ERROR_JIGSAW_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.place.jigsaw.failed"));
    private static final SimpleCommandExceptionType ERROR_STRUCTURE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.place.structure.failed"));
    private static final DynamicCommandExceptionType ERROR_TEMPLATE_INVALID = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.place.template.invalid", object);
    });
    private static final SimpleCommandExceptionType ERROR_TEMPLATE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.place.template.failed"));
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TEMPLATES = (commandcontext, suggestionsbuilder) -> {
        StructureTemplateManager structuretemplatemanager = ((CommandSourceStack) commandcontext.getSource()).getLevel().getStructureManager();

        return SharedSuggestionProvider.suggestResource(structuretemplatemanager.listTemplates(), suggestionsbuilder);
    };

    public PlaceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("place").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("feature").then(((RequiredArgumentBuilder) Commands.argument("feature", ResourceKeyArgument.key(Registries.CONFIGURED_FEATURE)).executes((commandcontext) -> {
            return placeFeature((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getConfiguredFeature(commandcontext, "feature"), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()));
        })).then(Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return placeFeature((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getConfiguredFeature(commandcontext, "feature"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"));
        }))))).then(Commands.literal("jigsaw").then(Commands.argument("pool", ResourceKeyArgument.key(Registries.TEMPLATE_POOL)).then(Commands.argument("target", IdentifierArgument.id()).then(((RequiredArgumentBuilder) Commands.argument("max_depth", IntegerArgumentType.integer(1, 20)).executes((commandcontext) -> {
            return placeJigsaw((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getStructureTemplatePool(commandcontext, "pool"), IdentifierArgument.getId(commandcontext, "target"), IntegerArgumentType.getInteger(commandcontext, "max_depth"), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()));
        })).then(Commands.argument("position", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return placeJigsaw((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getStructureTemplatePool(commandcontext, "pool"), IdentifierArgument.getId(commandcontext, "target"), IntegerArgumentType.getInteger(commandcontext, "max_depth"), BlockPosArgument.getLoadedBlockPos(commandcontext, "position"));
        }))))))).then(Commands.literal("structure").then(((RequiredArgumentBuilder) Commands.argument("structure", ResourceKeyArgument.key(Registries.STRUCTURE)).executes((commandcontext) -> {
            return placeStructure((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getStructure(commandcontext, "structure"), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()));
        })).then(Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return placeStructure((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getStructure(commandcontext, "structure"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"));
        }))))).then(Commands.literal("template").then(((RequiredArgumentBuilder) Commands.argument("template", IdentifierArgument.id()).suggests(PlaceCommand.SUGGEST_TEMPLATES).executes((commandcontext) -> {
            return placeTemplate((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "template"), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()), Rotation.NONE, Mirror.NONE, 1.0F, 0, false);
        })).then(((RequiredArgumentBuilder) Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return placeTemplate((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), Rotation.NONE, Mirror.NONE, 1.0F, 0, false);
        })).then(((RequiredArgumentBuilder) Commands.argument("rotation", TemplateRotationArgument.templateRotation()).executes((commandcontext) -> {
            return placeTemplate((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), TemplateRotationArgument.getRotation(commandcontext, "rotation"), Mirror.NONE, 1.0F, 0, false);
        })).then(((RequiredArgumentBuilder) Commands.argument("mirror", TemplateMirrorArgument.templateMirror()).executes((commandcontext) -> {
            return placeTemplate((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), TemplateRotationArgument.getRotation(commandcontext, "rotation"), TemplateMirrorArgument.getMirror(commandcontext, "mirror"), 1.0F, 0, false);
        })).then(((RequiredArgumentBuilder) Commands.argument("integrity", FloatArgumentType.floatArg(0.0F, 1.0F)).executes((commandcontext) -> {
            return placeTemplate((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), TemplateRotationArgument.getRotation(commandcontext, "rotation"), TemplateMirrorArgument.getMirror(commandcontext, "mirror"), FloatArgumentType.getFloat(commandcontext, "integrity"), 0, false);
        })).then(((RequiredArgumentBuilder) Commands.argument("seed", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return placeTemplate((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), TemplateRotationArgument.getRotation(commandcontext, "rotation"), TemplateMirrorArgument.getMirror(commandcontext, "mirror"), FloatArgumentType.getFloat(commandcontext, "integrity"), IntegerArgumentType.getInteger(commandcontext, "seed"), false);
        })).then(Commands.literal("strict").executes((commandcontext) -> {
            return placeTemplate((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), TemplateRotationArgument.getRotation(commandcontext, "rotation"), TemplateMirrorArgument.getMirror(commandcontext, "mirror"), FloatArgumentType.getFloat(commandcontext, "integrity"), IntegerArgumentType.getInteger(commandcontext, "seed"), true);
        }))))))))));
    }

    public static int placeFeature(CommandSourceStack source, Holder.Reference<ConfiguredFeature<?, ?>> featureHolder, BlockPos pos) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();
        ConfiguredFeature<?, ?> configuredfeature = (ConfiguredFeature) featureHolder.value();
        ChunkPos chunkpos = new ChunkPos(pos);

        checkLoaded(serverlevel, new ChunkPos(chunkpos.x - 1, chunkpos.z - 1), new ChunkPos(chunkpos.x + 1, chunkpos.z + 1));
        if (!configuredfeature.place(serverlevel, serverlevel.getChunkSource().getGenerator(), serverlevel.getRandom(), pos)) {
            throw PlaceCommand.ERROR_FEATURE_FAILED.create();
        } else {
            String s = featureHolder.key().identifier().toString();

            source.sendSuccess(() -> {
                return Component.translatable("commands.place.feature.success", s, pos.getX(), pos.getY(), pos.getZ());
            }, true);
            return 1;
        }
    }

    public static int placeJigsaw(CommandSourceStack source, Holder<StructureTemplatePool> pool, Identifier target, int maxDepth, BlockPos pos) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();
        ChunkPos chunkpos = new ChunkPos(pos);

        checkLoaded(serverlevel, chunkpos, chunkpos);
        if (!JigsawPlacement.generateJigsaw(serverlevel, pool, target, maxDepth, pos, false)) {
            throw PlaceCommand.ERROR_JIGSAW_FAILED.create();
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.place.jigsaw.success", pos.getX(), pos.getY(), pos.getZ());
            }, true);
            return 1;
        }
    }

    public static int placeStructure(CommandSourceStack source, Holder.Reference<Structure> structureHolder, BlockPos pos) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();
        Structure structure = (Structure) structureHolder.value();
        ChunkGenerator chunkgenerator = serverlevel.getChunkSource().getGenerator();
        StructureStart structurestart = structure.generate(structureHolder, serverlevel.dimension(), source.registryAccess(), chunkgenerator, chunkgenerator.getBiomeSource(), serverlevel.getChunkSource().randomState(), serverlevel.getStructureManager(), serverlevel.getSeed(), new ChunkPos(pos), 0, serverlevel, (holder) -> {
            return true;
        });

        if (!structurestart.isValid()) {
            throw PlaceCommand.ERROR_STRUCTURE_FAILED.create();
        } else {
            BoundingBox boundingbox = structurestart.getBoundingBox();
            ChunkPos chunkpos = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.minX()), SectionPos.blockToSectionCoord(boundingbox.minZ()));
            ChunkPos chunkpos1 = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.maxX()), SectionPos.blockToSectionCoord(boundingbox.maxZ()));

            checkLoaded(serverlevel, chunkpos, chunkpos1);
            ChunkPos.rangeClosed(chunkpos, chunkpos1).forEach((chunkpos2) -> {
                structurestart.placeInChunk(serverlevel, serverlevel.structureManager(), chunkgenerator, serverlevel.getRandom(), new BoundingBox(chunkpos2.getMinBlockX(), serverlevel.getMinY(), chunkpos2.getMinBlockZ(), chunkpos2.getMaxBlockX(), serverlevel.getMaxY() + 1, chunkpos2.getMaxBlockZ()), chunkpos2);
            });
            String s = structureHolder.key().identifier().toString();

            source.sendSuccess(() -> {
                return Component.translatable("commands.place.structure.success", s, pos.getX(), pos.getY(), pos.getZ());
            }, true);
            return 1;
        }
    }

    public static int placeTemplate(CommandSourceStack source, Identifier template, BlockPos pos, Rotation rotation, Mirror mirror, float integrity, int seed, boolean strict) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();
        StructureTemplateManager structuretemplatemanager = serverlevel.getStructureManager();

        Optional<StructureTemplate> optional;

        try {
            optional = structuretemplatemanager.get(template);
        } catch (IdentifierException identifierexception) {
            throw PlaceCommand.ERROR_TEMPLATE_INVALID.create(template);
        }

        if (optional.isEmpty()) {
            throw PlaceCommand.ERROR_TEMPLATE_INVALID.create(template);
        } else {
            StructureTemplate structuretemplate = (StructureTemplate) optional.get();

            checkLoaded(serverlevel, new ChunkPos(pos), new ChunkPos(pos.offset(structuretemplate.getSize())));
            StructurePlaceSettings structureplacesettings = (new StructurePlaceSettings()).setMirror(mirror).setRotation(rotation).setKnownShape(strict);

            if (integrity < 1.0F) {
                structureplacesettings.clearProcessors().addProcessor(new BlockRotProcessor(integrity)).setRandom(StructureBlockEntity.createRandom((long) seed));
            }

            boolean flag1 = structuretemplate.placeInWorld(serverlevel, pos, pos, structureplacesettings, StructureBlockEntity.createRandom((long) seed), 2 | (strict ? 816 : 0));

            if (!flag1) {
                throw PlaceCommand.ERROR_TEMPLATE_FAILED.create();
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.place.template.success", Component.translationArg(template), pos.getX(), pos.getY(), pos.getZ());
                }, true);
                return 1;
            }
        }
    }

    private static void checkLoaded(ServerLevel level, ChunkPos chunkMin, ChunkPos chunkMax) throws CommandSyntaxException {
        if (ChunkPos.rangeClosed(chunkMin, chunkMax).filter((chunkpos2) -> {
            return !level.isLoaded(chunkpos2.getWorldPosition());
        }).findAny().isPresent()) {
            throw BlockPosArgument.ERROR_NOT_LOADED.create();
        }
    }
}
