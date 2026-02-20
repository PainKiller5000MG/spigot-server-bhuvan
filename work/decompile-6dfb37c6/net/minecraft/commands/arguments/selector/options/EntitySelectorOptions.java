package net.minecraft.commands.arguments.selector.options;

import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;

public class EntitySelectorOptions {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, EntitySelectorOptions.Option> OPTIONS = Maps.newHashMap();
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_OPTION = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.entity.options.unknown", object);
    });
    public static final DynamicCommandExceptionType ERROR_INAPPLICABLE_OPTION = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.entity.options.inapplicable", object);
    });
    public static final SimpleCommandExceptionType ERROR_RANGE_NEGATIVE = new SimpleCommandExceptionType(Component.translatable("argument.entity.options.distance.negative"));
    public static final SimpleCommandExceptionType ERROR_LEVEL_NEGATIVE = new SimpleCommandExceptionType(Component.translatable("argument.entity.options.level.negative"));
    public static final SimpleCommandExceptionType ERROR_LIMIT_TOO_SMALL = new SimpleCommandExceptionType(Component.translatable("argument.entity.options.limit.toosmall"));
    public static final DynamicCommandExceptionType ERROR_SORT_UNKNOWN = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.entity.options.sort.irreversible", object);
    });
    public static final DynamicCommandExceptionType ERROR_GAME_MODE_INVALID = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.entity.options.mode.invalid", object);
    });
    public static final DynamicCommandExceptionType ERROR_ENTITY_TYPE_INVALID = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.entity.options.type.invalid", object);
    });

    public EntitySelectorOptions() {}

    private static void register(String name, EntitySelectorOptions.Modifier modifier, Predicate<EntitySelectorParser> predicate, Component description) {
        EntitySelectorOptions.OPTIONS.put(name, new EntitySelectorOptions.Option(modifier, predicate, description));
    }

    public static void bootStrap() {
        if (EntitySelectorOptions.OPTIONS.isEmpty()) {
            register("name", (entityselectorparser) -> {
                int i = entityselectorparser.getReader().getCursor();
                boolean flag = entityselectorparser.shouldInvertValue();
                String s = entityselectorparser.getReader().readString();

                if (entityselectorparser.hasNameNotEquals() && !flag) {
                    entityselectorparser.getReader().setCursor(i);
                    throw EntitySelectorOptions.ERROR_INAPPLICABLE_OPTION.createWithContext(entityselectorparser.getReader(), "name");
                } else {
                    if (flag) {
                        entityselectorparser.setHasNameNotEquals(true);
                    } else {
                        entityselectorparser.setHasNameEquals(true);
                    }

                    entityselectorparser.addPredicate((entity) -> {
                        return entity.getPlainTextName().equals(s) != flag;
                    });
                }
            }, (entityselectorparser) -> {
                return !entityselectorparser.hasNameEquals();
            }, Component.translatable("argument.entity.options.name.description"));
            register("distance", (entityselectorparser) -> {
                int i = entityselectorparser.getReader().getCursor();
                MinMaxBounds.Doubles minmaxbounds_doubles = MinMaxBounds.Doubles.fromReader(entityselectorparser.getReader());

                if ((!minmaxbounds_doubles.min().isPresent() || (Double) minmaxbounds_doubles.min().get() >= 0.0D) && (!minmaxbounds_doubles.max().isPresent() || (Double) minmaxbounds_doubles.max().get() >= 0.0D)) {
                    entityselectorparser.setDistance(minmaxbounds_doubles);
                    entityselectorparser.setWorldLimited();
                } else {
                    entityselectorparser.getReader().setCursor(i);
                    throw EntitySelectorOptions.ERROR_RANGE_NEGATIVE.createWithContext(entityselectorparser.getReader());
                }
            }, (entityselectorparser) -> {
                return entityselectorparser.getDistance() == null;
            }, Component.translatable("argument.entity.options.distance.description"));
            register("level", (entityselectorparser) -> {
                int i = entityselectorparser.getReader().getCursor();
                MinMaxBounds.Ints minmaxbounds_ints = MinMaxBounds.Ints.fromReader(entityselectorparser.getReader());

                if ((!minmaxbounds_ints.min().isPresent() || (Integer) minmaxbounds_ints.min().get() >= 0) && (!minmaxbounds_ints.max().isPresent() || (Integer) minmaxbounds_ints.max().get() >= 0)) {
                    entityselectorparser.setLevel(minmaxbounds_ints);
                    entityselectorparser.setIncludesEntities(false);
                } else {
                    entityselectorparser.getReader().setCursor(i);
                    throw EntitySelectorOptions.ERROR_LEVEL_NEGATIVE.createWithContext(entityselectorparser.getReader());
                }
            }, (entityselectorparser) -> {
                return entityselectorparser.getLevel() == null;
            }, Component.translatable("argument.entity.options.level.description"));
            register("x", (entityselectorparser) -> {
                entityselectorparser.setWorldLimited();
                entityselectorparser.setX(entityselectorparser.getReader().readDouble());
            }, (entityselectorparser) -> {
                return entityselectorparser.getX() == null;
            }, Component.translatable("argument.entity.options.x.description"));
            register("y", (entityselectorparser) -> {
                entityselectorparser.setWorldLimited();
                entityselectorparser.setY(entityselectorparser.getReader().readDouble());
            }, (entityselectorparser) -> {
                return entityselectorparser.getY() == null;
            }, Component.translatable("argument.entity.options.y.description"));
            register("z", (entityselectorparser) -> {
                entityselectorparser.setWorldLimited();
                entityselectorparser.setZ(entityselectorparser.getReader().readDouble());
            }, (entityselectorparser) -> {
                return entityselectorparser.getZ() == null;
            }, Component.translatable("argument.entity.options.z.description"));
            register("dx", (entityselectorparser) -> {
                entityselectorparser.setWorldLimited();
                entityselectorparser.setDeltaX(entityselectorparser.getReader().readDouble());
            }, (entityselectorparser) -> {
                return entityselectorparser.getDeltaX() == null;
            }, Component.translatable("argument.entity.options.dx.description"));
            register("dy", (entityselectorparser) -> {
                entityselectorparser.setWorldLimited();
                entityselectorparser.setDeltaY(entityselectorparser.getReader().readDouble());
            }, (entityselectorparser) -> {
                return entityselectorparser.getDeltaY() == null;
            }, Component.translatable("argument.entity.options.dy.description"));
            register("dz", (entityselectorparser) -> {
                entityselectorparser.setWorldLimited();
                entityselectorparser.setDeltaZ(entityselectorparser.getReader().readDouble());
            }, (entityselectorparser) -> {
                return entityselectorparser.getDeltaZ() == null;
            }, Component.translatable("argument.entity.options.dz.description"));
            register("x_rotation", (entityselectorparser) -> {
                entityselectorparser.setRotX(MinMaxBounds.FloatDegrees.fromReader(entityselectorparser.getReader()));
            }, (entityselectorparser) -> {
                return entityselectorparser.getRotX() == null;
            }, Component.translatable("argument.entity.options.x_rotation.description"));
            register("y_rotation", (entityselectorparser) -> {
                entityselectorparser.setRotY(MinMaxBounds.FloatDegrees.fromReader(entityselectorparser.getReader()));
            }, (entityselectorparser) -> {
                return entityselectorparser.getRotY() == null;
            }, Component.translatable("argument.entity.options.y_rotation.description"));
            register("limit", (entityselectorparser) -> {
                int i = entityselectorparser.getReader().getCursor();
                int j = entityselectorparser.getReader().readInt();

                if (j < 1) {
                    entityselectorparser.getReader().setCursor(i);
                    throw EntitySelectorOptions.ERROR_LIMIT_TOO_SMALL.createWithContext(entityselectorparser.getReader());
                } else {
                    entityselectorparser.setMaxResults(j);
                    entityselectorparser.setLimited(true);
                }
            }, (entityselectorparser) -> {
                return !entityselectorparser.isCurrentEntity() && !entityselectorparser.isLimited();
            }, Component.translatable("argument.entity.options.limit.description"));
            register("sort", (entityselectorparser) -> {
                int i = entityselectorparser.getReader().getCursor();
                String s = entityselectorparser.getReader().readUnquotedString();

                entityselectorparser.setSuggestions((suggestionsbuilder, consumer) -> {
                    return SharedSuggestionProvider.suggest(Arrays.asList("nearest", "furthest", "random", "arbitrary"), suggestionsbuilder);
                });
                BiConsumer biconsumer;

                switch (s) {
                    case "nearest":
                        biconsumer = EntitySelectorParser.ORDER_NEAREST;
                        break;
                    case "furthest":
                        biconsumer = EntitySelectorParser.ORDER_FURTHEST;
                        break;
                    case "random":
                        biconsumer = EntitySelectorParser.ORDER_RANDOM;
                        break;
                    case "arbitrary":
                        biconsumer = EntitySelector.ORDER_ARBITRARY;
                        break;
                    default:
                        entityselectorparser.getReader().setCursor(i);
                        throw EntitySelectorOptions.ERROR_SORT_UNKNOWN.createWithContext(entityselectorparser.getReader(), s);
                }

                entityselectorparser.setOrder(biconsumer);
                entityselectorparser.setSorted(true);
            }, (entityselectorparser) -> {
                return !entityselectorparser.isCurrentEntity() && !entityselectorparser.isSorted();
            }, Component.translatable("argument.entity.options.sort.description"));
            register("gamemode", (entityselectorparser) -> {
                entityselectorparser.setSuggestions((suggestionsbuilder, consumer) -> {
                    String s = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);
                    boolean flag = !entityselectorparser.hasGamemodeNotEquals();
                    boolean flag1 = true;

                    if (!s.isEmpty()) {
                        if (s.charAt(0) == '!') {
                            flag = false;
                            s = s.substring(1);
                        } else {
                            flag1 = false;
                        }
                    }

                    for (GameType gametype : GameType.values()) {
                        if (gametype.getName().toLowerCase(Locale.ROOT).startsWith(s)) {
                            if (flag1) {
                                suggestionsbuilder.suggest("!" + gametype.getName());
                            }

                            if (flag) {
                                suggestionsbuilder.suggest(gametype.getName());
                            }
                        }
                    }

                    return suggestionsbuilder.buildFuture();
                });
                int i = entityselectorparser.getReader().getCursor();
                boolean flag = entityselectorparser.shouldInvertValue();

                if (entityselectorparser.hasGamemodeNotEquals() && !flag) {
                    entityselectorparser.getReader().setCursor(i);
                    throw EntitySelectorOptions.ERROR_INAPPLICABLE_OPTION.createWithContext(entityselectorparser.getReader(), "gamemode");
                } else {
                    String s = entityselectorparser.getReader().readUnquotedString();
                    GameType gametype = GameType.byName(s, (GameType) null);

                    if (gametype == null) {
                        entityselectorparser.getReader().setCursor(i);
                        throw EntitySelectorOptions.ERROR_GAME_MODE_INVALID.createWithContext(entityselectorparser.getReader(), s);
                    } else {
                        entityselectorparser.setIncludesEntities(false);
                        entityselectorparser.addPredicate((entity) -> {
                            if (entity instanceof ServerPlayer serverplayer) {
                                GameType gametype1 = serverplayer.gameMode();

                                return gametype1 == gametype ^ flag;
                            } else {
                                return false;
                            }
                        });
                        if (flag) {
                            entityselectorparser.setHasGamemodeNotEquals(true);
                        } else {
                            entityselectorparser.setHasGamemodeEquals(true);
                        }

                    }
                }
            }, (entityselectorparser) -> {
                return !entityselectorparser.hasGamemodeEquals();
            }, Component.translatable("argument.entity.options.gamemode.description"));
            register("team", (entityselectorparser) -> {
                boolean flag = entityselectorparser.shouldInvertValue();
                String s = entityselectorparser.getReader().readUnquotedString();

                entityselectorparser.addPredicate((entity) -> {
                    Team team = entity.getTeam();
                    String s1 = team == null ? "" : team.getName();

                    return s1.equals(s) != flag;
                });
                if (flag) {
                    entityselectorparser.setHasTeamNotEquals(true);
                } else {
                    entityselectorparser.setHasTeamEquals(true);
                }

            }, (entityselectorparser) -> {
                return !entityselectorparser.hasTeamEquals();
            }, Component.translatable("argument.entity.options.team.description"));
            register("type", (entityselectorparser) -> {
                entityselectorparser.setSuggestions((suggestionsbuilder, consumer) -> {
                    SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), suggestionsbuilder, String.valueOf('!'));
                    SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.getTags().map((holderset_named) -> {
                        return holderset_named.key().location();
                    }), suggestionsbuilder, "!#");
                    if (!entityselectorparser.isTypeLimitedInversely()) {
                        SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), suggestionsbuilder);
                        SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.getTags().map((holderset_named) -> {
                            return holderset_named.key().location();
                        }), suggestionsbuilder, String.valueOf('#'));
                    }

                    return suggestionsbuilder.buildFuture();
                });
                int i = entityselectorparser.getReader().getCursor();
                boolean flag = entityselectorparser.shouldInvertValue();

                if (entityselectorparser.isTypeLimitedInversely() && !flag) {
                    entityselectorparser.getReader().setCursor(i);
                    throw EntitySelectorOptions.ERROR_INAPPLICABLE_OPTION.createWithContext(entityselectorparser.getReader(), "type");
                } else {
                    if (flag) {
                        entityselectorparser.setTypeLimitedInversely();
                    }

                    if (entityselectorparser.isTag()) {
                        TagKey<EntityType<?>> tagkey = TagKey.<EntityType<?>>create(Registries.ENTITY_TYPE, Identifier.read(entityselectorparser.getReader()));

                        entityselectorparser.addPredicate((entity) -> {
                            return entity.getType().is(tagkey) != flag;
                        });
                    } else {
                        Identifier identifier = Identifier.read(entityselectorparser.getReader());
                        EntityType<?> entitytype = (EntityType) BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElseThrow(() -> {
                            entityselectorparser.getReader().setCursor(i);
                            return EntitySelectorOptions.ERROR_ENTITY_TYPE_INVALID.createWithContext(entityselectorparser.getReader(), identifier.toString());
                        });

                        if (Objects.equals(EntityType.PLAYER, entitytype) && !flag) {
                            entityselectorparser.setIncludesEntities(false);
                        }

                        entityselectorparser.addPredicate((entity) -> {
                            return Objects.equals(entitytype, entity.getType()) != flag;
                        });
                        if (!flag) {
                            entityselectorparser.limitToType(entitytype);
                        }
                    }

                }
            }, (entityselectorparser) -> {
                return !entityselectorparser.isTypeLimited();
            }, Component.translatable("argument.entity.options.type.description"));
            register("tag", (entityselectorparser) -> {
                boolean flag = entityselectorparser.shouldInvertValue();
                String s = entityselectorparser.getReader().readUnquotedString();

                entityselectorparser.addPredicate((entity) -> {
                    return "".equals(s) ? entity.getTags().isEmpty() != flag : entity.getTags().contains(s) != flag;
                });
            }, (entityselectorparser) -> {
                return true;
            }, Component.translatable("argument.entity.options.tag.description"));
            register("nbt", (entityselectorparser) -> {
                boolean flag = entityselectorparser.shouldInvertValue();
                CompoundTag compoundtag = TagParser.parseCompoundAsArgument(entityselectorparser.getReader());

                entityselectorparser.addPredicate((entity) -> {
                    try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(entity.problemPath(), EntitySelectorOptions.LOGGER)) {
                        TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, entity.registryAccess());

                        entity.saveWithoutId(tagvalueoutput);
                        if (entity instanceof ServerPlayer serverplayer) {
                            ItemStack itemstack = serverplayer.getInventory().getSelectedItem();

                            if (!itemstack.isEmpty()) {
                                tagvalueoutput.store("SelectedItem", ItemStack.CODEC, itemstack);
                            }
                        }

                        return NbtUtils.compareNbt(compoundtag, tagvalueoutput.buildResult(), true) != flag;
                    }
                });
            }, (entityselectorparser) -> {
                return true;
            }, Component.translatable("argument.entity.options.nbt.description"));
            register("scores", (entityselectorparser) -> {
                StringReader stringreader = entityselectorparser.getReader();
                Map<String, MinMaxBounds.Ints> map = Maps.newHashMap();

                stringreader.expect('{');
                stringreader.skipWhitespace();

                while (stringreader.canRead() && stringreader.peek() != '}') {
                    stringreader.skipWhitespace();
                    String s = stringreader.readUnquotedString();

                    stringreader.skipWhitespace();
                    stringreader.expect('=');
                    stringreader.skipWhitespace();
                    MinMaxBounds.Ints minmaxbounds_ints = MinMaxBounds.Ints.fromReader(stringreader);

                    map.put(s, minmaxbounds_ints);
                    stringreader.skipWhitespace();
                    if (stringreader.canRead() && stringreader.peek() == ',') {
                        stringreader.skip();
                    }
                }

                stringreader.expect('}');
                if (!map.isEmpty()) {
                    entityselectorparser.addPredicate((entity) -> {
                        Scoreboard scoreboard = entity.level().getServer().getScoreboard();

                        for (Map.Entry<String, MinMaxBounds.Ints> map_entry : map.entrySet()) {
                            Objective objective = scoreboard.getObjective((String) map_entry.getKey());

                            if (objective == null) {
                                return false;
                            }

                            ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(entity, objective);

                            if (readonlyscoreinfo == null) {
                                return false;
                            }

                            if (!((MinMaxBounds.Ints) map_entry.getValue()).matches(readonlyscoreinfo.value())) {
                                return false;
                            }
                        }

                        return true;
                    });
                }

                entityselectorparser.setHasScores(true);
            }, (entityselectorparser) -> {
                return !entityselectorparser.hasScores();
            }, Component.translatable("argument.entity.options.scores.description"));
            register("advancements", (entityselectorparser) -> {
                StringReader stringreader = entityselectorparser.getReader();
                Map<Identifier, Predicate<AdvancementProgress>> map = Maps.newHashMap();

                stringreader.expect('{');
                stringreader.skipWhitespace();

                while (stringreader.canRead() && stringreader.peek() != '}') {
                    stringreader.skipWhitespace();
                    Identifier identifier = Identifier.read(stringreader);

                    stringreader.skipWhitespace();
                    stringreader.expect('=');
                    stringreader.skipWhitespace();
                    if (stringreader.canRead() && stringreader.peek() == '{') {
                        Map<String, Predicate<CriterionProgress>> map1 = Maps.newHashMap();

                        stringreader.skipWhitespace();
                        stringreader.expect('{');
                        stringreader.skipWhitespace();

                        while (stringreader.canRead() && stringreader.peek() != '}') {
                            stringreader.skipWhitespace();
                            String s = stringreader.readUnquotedString();

                            stringreader.skipWhitespace();
                            stringreader.expect('=');
                            stringreader.skipWhitespace();
                            boolean flag = stringreader.readBoolean();

                            map1.put(s, (Predicate) (criterionprogress) -> {
                                return criterionprogress.isDone() == flag;
                            });
                            stringreader.skipWhitespace();
                            if (stringreader.canRead() && stringreader.peek() == ',') {
                                stringreader.skip();
                            }
                        }

                        stringreader.skipWhitespace();
                        stringreader.expect('}');
                        stringreader.skipWhitespace();
                        map.put(identifier, (Predicate) (advancementprogress) -> {
                            for (Map.Entry<String, Predicate<CriterionProgress>> map_entry : map1.entrySet()) {
                                CriterionProgress criterionprogress = advancementprogress.getCriterion((String) map_entry.getKey());

                                if (criterionprogress == null || !((Predicate) map_entry.getValue()).test(criterionprogress)) {
                                    return false;
                                }
                            }

                            return true;
                        });
                    } else {
                        boolean flag1 = stringreader.readBoolean();

                        map.put(identifier, (Predicate) (advancementprogress) -> {
                            return advancementprogress.isDone() == flag1;
                        });
                    }

                    stringreader.skipWhitespace();
                    if (stringreader.canRead() && stringreader.peek() == ',') {
                        stringreader.skip();
                    }
                }

                stringreader.expect('}');
                if (!map.isEmpty()) {
                    entityselectorparser.addPredicate((entity) -> {
                        if (!(entity instanceof ServerPlayer serverplayer)) {
                            return false;
                        } else {
                            PlayerAdvancements playeradvancements = serverplayer.getAdvancements();
                            ServerAdvancementManager serveradvancementmanager = serverplayer.level().getServer().getAdvancements();

                            for (Map.Entry<Identifier, Predicate<AdvancementProgress>> map_entry : map.entrySet()) {
                                AdvancementHolder advancementholder = serveradvancementmanager.get((Identifier) map_entry.getKey());

                                if (advancementholder == null || !((Predicate) map_entry.getValue()).test(playeradvancements.getOrStartProgress(advancementholder))) {
                                    return false;
                                }
                            }

                            return true;
                        }
                    });
                    entityselectorparser.setIncludesEntities(false);
                }

                entityselectorparser.setHasAdvancements(true);
            }, (entityselectorparser) -> {
                return !entityselectorparser.hasAdvancements();
            }, Component.translatable("argument.entity.options.advancements.description"));
            register("predicate", (entityselectorparser) -> {
                boolean flag = entityselectorparser.shouldInvertValue();
                ResourceKey<LootItemCondition> resourcekey = ResourceKey.create(Registries.PREDICATE, Identifier.read(entityselectorparser.getReader()));

                entityselectorparser.addPredicate((entity) -> {
                    Level level = entity.level();

                    if (level instanceof ServerLevel serverlevel) {
                        Optional<LootItemCondition> optional = serverlevel.getServer().reloadableRegistries().lookup().get(resourcekey).map(Holder::value);

                        if (optional.isEmpty()) {
                            return false;
                        } else {
                            LootParams lootparams = (new LootParams.Builder(serverlevel)).withParameter(LootContextParams.THIS_ENTITY, entity).withParameter(LootContextParams.ORIGIN, entity.position()).create(LootContextParamSets.SELECTOR);
                            LootContext lootcontext = (new LootContext.Builder(lootparams)).create(Optional.empty());

                            lootcontext.pushVisitedElement(LootContext.createVisitedEntry((LootItemCondition) optional.get()));
                            return flag ^ ((LootItemCondition) optional.get()).test(lootcontext);
                        }
                    } else {
                        return false;
                    }
                });
            }, (entityselectorparser) -> {
                return true;
            }, Component.translatable("argument.entity.options.predicate.description"));
        }
    }

    public static EntitySelectorOptions.Modifier get(EntitySelectorParser parser, String key, int start) throws CommandSyntaxException {
        EntitySelectorOptions.Option entityselectoroptions_option = (EntitySelectorOptions.Option) EntitySelectorOptions.OPTIONS.get(key);

        if (entityselectoroptions_option != null) {
            if (entityselectoroptions_option.canUse.test(parser)) {
                return entityselectoroptions_option.modifier;
            } else {
                throw EntitySelectorOptions.ERROR_INAPPLICABLE_OPTION.createWithContext(parser.getReader(), key);
            }
        } else {
            parser.getReader().setCursor(start);
            throw EntitySelectorOptions.ERROR_UNKNOWN_OPTION.createWithContext(parser.getReader(), key);
        }
    }

    public static void suggestNames(EntitySelectorParser parser, SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (Map.Entry<String, EntitySelectorOptions.Option> map_entry : EntitySelectorOptions.OPTIONS.entrySet()) {
            if (((EntitySelectorOptions.Option) map_entry.getValue()).canUse.test(parser) && ((String) map_entry.getKey()).toLowerCase(Locale.ROOT).startsWith(s)) {
                builder.suggest((String) map_entry.getKey() + "=", ((EntitySelectorOptions.Option) map_entry.getValue()).description);
            }
        }

    }

    private static record Option(EntitySelectorOptions.Modifier modifier, Predicate<EntitySelectorParser> canUse, Component description) {

    }

    @FunctionalInterface
    public interface Modifier {

        void handle(EntitySelectorParser parser) throws CommandSyntaxException;
    }
}
