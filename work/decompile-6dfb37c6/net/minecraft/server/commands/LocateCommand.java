package net.minecraft.server.commands;

import com.google.common.base.Stopwatch;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

public class LocateCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_NOT_FOUND = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.locate.structure.not_found", object);
    });
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_INVALID = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.locate.structure.invalid", object);
    });
    private static final DynamicCommandExceptionType ERROR_BIOME_NOT_FOUND = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.locate.biome.not_found", object);
    });
    private static final DynamicCommandExceptionType ERROR_POI_NOT_FOUND = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.locate.poi.not_found", object);
    });
    private static final int MAX_STRUCTURE_SEARCH_RADIUS = 100;
    private static final int MAX_BIOME_SEARCH_RADIUS = 6400;
    private static final int BIOME_SAMPLE_RESOLUTION_HORIZONTAL = 32;
    private static final int BIOME_SAMPLE_RESOLUTION_VERTICAL = 64;
    private static final int POI_SEARCH_RADIUS = 256;

    public LocateCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("locate").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("structure").then(Commands.argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.STRUCTURE)).executes((commandcontext) -> {
            return locateStructure((CommandSourceStack) commandcontext.getSource(), ResourceOrTagKeyArgument.getResourceOrTagKey(commandcontext, "structure", Registries.STRUCTURE, LocateCommand.ERROR_STRUCTURE_INVALID));
        })))).then(Commands.literal("biome").then(Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(context, Registries.BIOME)).executes((commandcontext) -> {
            return locateBiome((CommandSourceStack) commandcontext.getSource(), ResourceOrTagArgument.getResourceOrTag(commandcontext, "biome", Registries.BIOME));
        })))).then(Commands.literal("poi").then(Commands.argument("poi", ResourceOrTagArgument.resourceOrTag(context, Registries.POINT_OF_INTEREST_TYPE)).executes((commandcontext) -> {
            return locatePoi((CommandSourceStack) commandcontext.getSource(), ResourceOrTagArgument.getResourceOrTag(commandcontext, "poi", Registries.POINT_OF_INTEREST_TYPE));
        }))));
    }

    private static Optional<? extends HolderSet.ListBacked<Structure>> getHolders(ResourceOrTagKeyArgument.Result<Structure> resourceOrTag, Registry<Structure> registry) {
        Either either = resourceOrTag.unwrap();
        Function function = (resourcekey) -> {
            return registry.get(resourcekey).map((holder) -> {
                return HolderSet.direct(holder);
            });
        };

        Objects.requireNonNull(registry);
        return (Optional) either.map(function, registry::get);
    }

    private static int locateStructure(CommandSourceStack source, ResourceOrTagKeyArgument.Result<Structure> resourceOrTag) throws CommandSyntaxException {
        Registry<Structure> registry = source.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        HolderSet<Structure> holderset = (HolderSet) getHolders(resourceOrTag, registry).orElseThrow(() -> {
            return LocateCommand.ERROR_STRUCTURE_INVALID.create(resourceOrTag.asPrintable());
        });
        BlockPos blockpos = BlockPos.containing(source.getPosition());
        ServerLevel serverlevel = source.getLevel();
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Pair<BlockPos, Holder<Structure>> pair = serverlevel.getChunkSource().getGenerator().findNearestMapStructure(serverlevel, holderset, blockpos, 100, false);

        stopwatch.stop();
        if (pair == null) {
            throw LocateCommand.ERROR_STRUCTURE_NOT_FOUND.create(resourceOrTag.asPrintable());
        } else {
            return showLocateResult(source, resourceOrTag, blockpos, pair, "commands.locate.structure.success", false, stopwatch.elapsed());
        }
    }

    private static int locateBiome(CommandSourceStack source, ResourceOrTagArgument.Result<Biome> elementOrTag) throws CommandSyntaxException {
        BlockPos blockpos = BlockPos.containing(source.getPosition());
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Pair<BlockPos, Holder<Biome>> pair = source.getLevel().findClosestBiome3d(elementOrTag, blockpos, 6400, 32, 64);

        stopwatch.stop();
        if (pair == null) {
            throw LocateCommand.ERROR_BIOME_NOT_FOUND.create(elementOrTag.asPrintable());
        } else {
            return showLocateResult(source, elementOrTag, blockpos, pair, "commands.locate.biome.success", true, stopwatch.elapsed());
        }
    }

    private static int locatePoi(CommandSourceStack source, ResourceOrTagArgument.Result<PoiType> resourceOrTag) throws CommandSyntaxException {
        BlockPos blockpos = BlockPos.containing(source.getPosition());
        ServerLevel serverlevel = source.getLevel();
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Optional<Pair<Holder<PoiType>, BlockPos>> optional = serverlevel.getPoiManager().findClosestWithType(resourceOrTag, blockpos, 256, PoiManager.Occupancy.ANY);

        stopwatch.stop();
        if (optional.isEmpty()) {
            throw LocateCommand.ERROR_POI_NOT_FOUND.create(resourceOrTag.asPrintable());
        } else {
            return showLocateResult(source, resourceOrTag, blockpos, ((Pair) optional.get()).swap(), "commands.locate.poi.success", false, stopwatch.elapsed());
        }
    }

    public static int showLocateResult(CommandSourceStack source, ResourceOrTagArgument.Result<?> name, BlockPos sourcePos, Pair<BlockPos, ? extends Holder<?>> found, String successMessageKey, boolean includeY, Duration taskDuration) {
        String s1 = (String) name.unwrap().map((holder_reference) -> {
            return name.asPrintable();
        }, (holderset_named) -> {
            String s2 = name.asPrintable();

            return s2 + " (" + ((Holder) found.getSecond()).getRegisteredName() + ")";
        });

        return showLocateResult(source, sourcePos, found, successMessageKey, includeY, s1, taskDuration);
    }

    public static int showLocateResult(CommandSourceStack source, ResourceOrTagKeyArgument.Result<?> name, BlockPos sourcePos, Pair<BlockPos, ? extends Holder<?>> found, String successMessageKey, boolean includeY, Duration taskDuration) {
        String s1 = (String) name.unwrap().map((resourcekey) -> {
            return resourcekey.identifier().toString();
        }, (tagkey) -> {
            String s2 = String.valueOf(tagkey.location());

            return "#" + s2 + " (" + ((Holder) found.getSecond()).getRegisteredName() + ")";
        });

        return showLocateResult(source, sourcePos, found, successMessageKey, includeY, s1, taskDuration);
    }

    private static int showLocateResult(CommandSourceStack source, BlockPos sourcePos, Pair<BlockPos, ? extends Holder<?>> found, String successMessageKey, boolean includeY, String foundName, Duration taskDuration) {
        BlockPos blockpos1 = (BlockPos) found.getFirst();
        int i = includeY ? Mth.floor(Mth.sqrt((float) sourcePos.distSqr(blockpos1))) : Mth.floor(dist(sourcePos.getX(), sourcePos.getZ(), blockpos1.getX(), blockpos1.getZ()));
        String s2 = includeY ? String.valueOf(blockpos1.getY()) : "~";
        Component component = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", blockpos1.getX(), s2, blockpos1.getZ())).withStyle((style) -> {
            return style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.SuggestCommand("/tp @s " + blockpos1.getX() + " " + s2 + " " + blockpos1.getZ())).withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip")));
        });

        source.sendSuccess(() -> {
            return Component.translatable(successMessageKey, foundName, component, i);
        }, false);
        LocateCommand.LOGGER.info("Locating element {} took {} ms", foundName, taskDuration.toMillis());
        return i;
    }

    private static float dist(int x1, int z1, int x2, int z2) {
        int i1 = x2 - x1;
        int j1 = z2 - z1;

        return Mth.sqrt((float) (i1 * i1 + j1 * j1));
    }
}
