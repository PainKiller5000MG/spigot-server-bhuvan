package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.JsonOps;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.FileUtil;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

public class DataPackCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_PACK = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.datapack.unknown", object);
    });
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_ENABLED = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.datapack.enable.failed", object);
    });
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_DISABLED = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.datapack.disable.failed", object);
    });
    private static final DynamicCommandExceptionType ERROR_CANNOT_DISABLE_FEATURE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.datapack.disable.failed.feature", object);
    });
    private static final Dynamic2CommandExceptionType ERROR_PACK_FEATURES_NOT_ENABLED = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.datapack.enable.failed.no_flags", object, object1);
    });
    private static final DynamicCommandExceptionType ERROR_PACK_INVALID_NAME = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.datapack.create.invalid_name", object);
    });
    private static final DynamicCommandExceptionType ERROR_PACK_INVALID_FULL_NAME = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.datapack.create.invalid_full_name", object);
    });
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_EXISTS = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.datapack.create.already_exists", object);
    });
    private static final Dynamic2CommandExceptionType ERROR_PACK_METADATA_ENCODE_FAILURE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.datapack.create.metadata_encode_failure", object, object1);
    });
    private static final DynamicCommandExceptionType ERROR_PACK_IO_FAILURE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.datapack.create.io_failure", object);
    });
    private static final SuggestionProvider<CommandSourceStack> SELECTED_PACKS = (commandcontext, suggestionsbuilder) -> {
        return SharedSuggestionProvider.suggest(((CommandSourceStack) commandcontext.getSource()).getServer().getPackRepository().getSelectedIds().stream().map(StringArgumentType::escapeIfRequired), suggestionsbuilder);
    };
    private static final SuggestionProvider<CommandSourceStack> UNSELECTED_PACKS = (commandcontext, suggestionsbuilder) -> {
        PackRepository packrepository = ((CommandSourceStack) commandcontext.getSource()).getServer().getPackRepository();
        Collection<String> collection = packrepository.getSelectedIds();
        FeatureFlagSet featureflagset = ((CommandSourceStack) commandcontext.getSource()).enabledFeatures();

        return SharedSuggestionProvider.suggest(packrepository.getAvailablePacks().stream().filter((pack) -> {
            return pack.getRequestedFeatures().isSubsetOf(featureflagset);
        }).map(Pack::getId).filter((s) -> {
            return !collection.contains(s);
        }).map(StringArgumentType::escapeIfRequired), suggestionsbuilder);
    };

    public DataPackCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("datapack").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("enable").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("name", StringArgumentType.string()).suggests(DataPackCommand.UNSELECTED_PACKS).executes((commandcontext) -> {
            return enablePack((CommandSourceStack) commandcontext.getSource(), getPack(commandcontext, "name", true), (list, pack) -> {
                pack.getDefaultPosition().insert(list, pack, Pack::selectionConfig, false);
            });
        })).then(Commands.literal("after").then(Commands.argument("existing", StringArgumentType.string()).suggests(DataPackCommand.SELECTED_PACKS).executes((commandcontext) -> {
            return enablePack((CommandSourceStack) commandcontext.getSource(), getPack(commandcontext, "name", true), (list, pack) -> {
                list.add(list.indexOf(getPack(commandcontext, "existing", false)) + 1, pack);
            });
        })))).then(Commands.literal("before").then(Commands.argument("existing", StringArgumentType.string()).suggests(DataPackCommand.SELECTED_PACKS).executes((commandcontext) -> {
            return enablePack((CommandSourceStack) commandcontext.getSource(), getPack(commandcontext, "name", true), (list, pack) -> {
                list.add(list.indexOf(getPack(commandcontext, "existing", false)), pack);
            });
        })))).then(Commands.literal("last").executes((commandcontext) -> {
            return enablePack((CommandSourceStack) commandcontext.getSource(), getPack(commandcontext, "name", true), List::add);
        }))).then(Commands.literal("first").executes((commandcontext) -> {
            return enablePack((CommandSourceStack) commandcontext.getSource(), getPack(commandcontext, "name", true), (list, pack) -> {
                list.add(0, pack);
            });
        }))))).then(Commands.literal("disable").then(Commands.argument("name", StringArgumentType.string()).suggests(DataPackCommand.SELECTED_PACKS).executes((commandcontext) -> {
            return disablePack((CommandSourceStack) commandcontext.getSource(), getPack(commandcontext, "name", false));
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("list").executes((commandcontext) -> {
            return listPacks((CommandSourceStack) commandcontext.getSource());
        })).then(Commands.literal("available").executes((commandcontext) -> {
            return listAvailablePacks((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("enabled").executes((commandcontext) -> {
            return listEnabledPacks((CommandSourceStack) commandcontext.getSource());
        })))).then(((LiteralArgumentBuilder) Commands.literal("create").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))).then(Commands.argument("id", StringArgumentType.string()).then(Commands.argument("description", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return createPack((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "id"), ComponentArgument.getResolvedComponent(commandcontext, "description"));
        })))));
    }

    private static int createPack(CommandSourceStack source, String id, Component description) throws CommandSyntaxException {
        Path path = source.getServer().getWorldPath(LevelResource.DATAPACK_DIR);

        if (!FileUtil.isValidPathSegment(id)) {
            throw DataPackCommand.ERROR_PACK_INVALID_NAME.create(id);
        } else if (!FileUtil.isPathPartPortable(id)) {
            throw DataPackCommand.ERROR_PACK_INVALID_FULL_NAME.create(id);
        } else {
            Path path1 = path.resolve(id);

            if (Files.exists(path1, new LinkOption[0])) {
                throw DataPackCommand.ERROR_PACK_ALREADY_EXISTS.create(id);
            } else {
                PackMetadataSection packmetadatasection = new PackMetadataSection(description, SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).minorRange());
                DataResult<JsonElement> dataresult = PackMetadataSection.SERVER_TYPE.codec().encodeStart(JsonOps.INSTANCE, packmetadatasection);
                Optional<DataResult.Error<JsonElement>> optional = dataresult.error();

                if (optional.isPresent()) {
                    throw DataPackCommand.ERROR_PACK_METADATA_ENCODE_FAILURE.create(id, ((Error) optional.get()).message());
                } else {
                    JsonObject jsonobject = new JsonObject();

                    jsonobject.add(PackMetadataSection.SERVER_TYPE.name(), (JsonElement) dataresult.getOrThrow());

                    try {
                        Files.createDirectory(path1);
                        Files.createDirectory(path1.resolve(PackType.SERVER_DATA.getDirectory()));

                        try (BufferedWriter bufferedwriter = Files.newBufferedWriter(path1.resolve("pack.mcmeta"), StandardCharsets.UTF_8); JsonWriter jsonwriter = new JsonWriter(bufferedwriter);) {
                            jsonwriter.setSerializeNulls(false);
                            jsonwriter.setIndent("  ");
                            GsonHelper.writeValue(jsonwriter, jsonobject, (Comparator) null);
                        }
                    } catch (IOException ioexception) {
                        DataPackCommand.LOGGER.warn("Failed to create pack at {}", path.toAbsolutePath(), ioexception);
                        throw DataPackCommand.ERROR_PACK_IO_FAILURE.create(id);
                    }

                    source.sendSuccess(() -> {
                        return Component.translatable("commands.datapack.create.success", id);
                    }, true);
                    return 1;
                }
            }
        }
    }

    private static int enablePack(CommandSourceStack source, Pack unopened, DataPackCommand.Inserter inserter) throws CommandSyntaxException {
        PackRepository packrepository = source.getServer().getPackRepository();
        List<Pack> list = Lists.newArrayList(packrepository.getSelectedPacks());

        inserter.apply(list, unopened);
        source.sendSuccess(() -> {
            return Component.translatable("commands.datapack.modify.enable", unopened.getChatLink(true));
        }, true);
        ReloadCommand.reloadPacks((Collection) list.stream().map(Pack::getId).collect(Collectors.toList()), source);
        return list.size();
    }

    private static int disablePack(CommandSourceStack source, Pack unopened) {
        PackRepository packrepository = source.getServer().getPackRepository();
        List<Pack> list = Lists.newArrayList(packrepository.getSelectedPacks());

        list.remove(unopened);
        source.sendSuccess(() -> {
            return Component.translatable("commands.datapack.modify.disable", unopened.getChatLink(true));
        }, true);
        ReloadCommand.reloadPacks((Collection) list.stream().map(Pack::getId).collect(Collectors.toList()), source);
        return list.size();
    }

    private static int listPacks(CommandSourceStack source) {
        return listEnabledPacks(source) + listAvailablePacks(source);
    }

    private static int listAvailablePacks(CommandSourceStack source) {
        PackRepository packrepository = source.getServer().getPackRepository();

        packrepository.reload();
        Collection<Pack> collection = packrepository.getSelectedPacks();
        Collection<Pack> collection1 = packrepository.getAvailablePacks();
        FeatureFlagSet featureflagset = source.enabledFeatures();
        List<Pack> list = collection1.stream().filter((pack) -> {
            return !collection.contains(pack) && pack.getRequestedFeatures().isSubsetOf(featureflagset);
        }).toList();

        if (list.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.datapack.list.available.none");
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.datapack.list.available.success", list.size(), ComponentUtils.formatList(list, (pack) -> {
                    return pack.getChatLink(false);
                }));
            }, false);
        }

        return list.size();
    }

    private static int listEnabledPacks(CommandSourceStack source) {
        PackRepository packrepository = source.getServer().getPackRepository();

        packrepository.reload();
        Collection<? extends Pack> collection = packrepository.getSelectedPacks();

        if (collection.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.datapack.list.enabled.none");
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.datapack.list.enabled.success", collection.size(), ComponentUtils.formatList(collection, (pack) -> {
                    return pack.getChatLink(true);
                }));
            }, false);
        }

        return collection.size();
    }

    private static Pack getPack(CommandContext<CommandSourceStack> context, String name, boolean enabling) throws CommandSyntaxException {
        String s1 = StringArgumentType.getString(context, name);
        PackRepository packrepository = ((CommandSourceStack) context.getSource()).getServer().getPackRepository();
        Pack pack = packrepository.getPack(s1);

        if (pack == null) {
            throw DataPackCommand.ERROR_UNKNOWN_PACK.create(s1);
        } else {
            boolean flag1 = packrepository.getSelectedPacks().contains(pack);

            if (enabling && flag1) {
                throw DataPackCommand.ERROR_PACK_ALREADY_ENABLED.create(s1);
            } else if (!enabling && !flag1) {
                throw DataPackCommand.ERROR_PACK_ALREADY_DISABLED.create(s1);
            } else {
                FeatureFlagSet featureflagset = ((CommandSourceStack) context.getSource()).enabledFeatures();
                FeatureFlagSet featureflagset1 = pack.getRequestedFeatures();

                if (!enabling && !featureflagset1.isEmpty() && pack.getPackSource() == PackSource.FEATURE) {
                    throw DataPackCommand.ERROR_CANNOT_DISABLE_FEATURE.create(s1);
                } else if (!featureflagset1.isSubsetOf(featureflagset)) {
                    throw DataPackCommand.ERROR_PACK_FEATURES_NOT_ENABLED.create(s1, FeatureFlags.printMissingFlags(featureflagset, featureflagset1));
                } else {
                    return pack;
                }
            }
        }
    }

    private interface Inserter {

        void apply(List<Pack> selected, Pack pack) throws CommandSyntaxException;
    }
}
