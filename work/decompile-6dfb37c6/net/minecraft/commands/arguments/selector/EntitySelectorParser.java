package net.minecraft.commands.arguments.selector;

import com.google.common.primitives.Doubles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSetSupplier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EntitySelectorParser {

    public static final char SYNTAX_SELECTOR_START = '@';
    private static final char SYNTAX_OPTIONS_START = '[';
    private static final char SYNTAX_OPTIONS_END = ']';
    public static final char SYNTAX_OPTIONS_KEY_VALUE_SEPARATOR = '=';
    private static final char SYNTAX_OPTIONS_SEPARATOR = ',';
    public static final char SYNTAX_NOT = '!';
    public static final char SYNTAX_TAG = '#';
    private static final char SELECTOR_NEAREST_PLAYER = 'p';
    private static final char SELECTOR_ALL_PLAYERS = 'a';
    private static final char SELECTOR_RANDOM_PLAYERS = 'r';
    private static final char SELECTOR_CURRENT_ENTITY = 's';
    private static final char SELECTOR_ALL_ENTITIES = 'e';
    private static final char SELECTOR_NEAREST_ENTITY = 'n';
    public static final SimpleCommandExceptionType ERROR_INVALID_NAME_OR_UUID = new SimpleCommandExceptionType(Component.translatable("argument.entity.invalid"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_SELECTOR_TYPE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.entity.selector.unknown", object);
    });
    public static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED = new SimpleCommandExceptionType(Component.translatable("argument.entity.selector.not_allowed"));
    public static final SimpleCommandExceptionType ERROR_MISSING_SELECTOR_TYPE = new SimpleCommandExceptionType(Component.translatable("argument.entity.selector.missing"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_OPTIONS = new SimpleCommandExceptionType(Component.translatable("argument.entity.options.unterminated"));
    public static final DynamicCommandExceptionType ERROR_EXPECTED_OPTION_VALUE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.entity.options.valueless", object);
    });
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_NEAREST = (vec3, list) -> {
        list.sort((entity, entity1) -> {
            return Doubles.compare(entity.distanceToSqr(vec3), entity1.distanceToSqr(vec3));
        });
    };
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_FURTHEST = (vec3, list) -> {
        list.sort((entity, entity1) -> {
            return Doubles.compare(entity1.distanceToSqr(vec3), entity.distanceToSqr(vec3));
        });
    };
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_RANDOM = (vec3, list) -> {
        Collections.shuffle(list);
    };
    public static final BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> SUGGEST_NOTHING = (suggestionsbuilder, consumer) -> {
        return suggestionsbuilder.buildFuture();
    };
    private final StringReader reader;
    private final boolean allowSelectors;
    private int maxResults;
    private boolean includesEntities;
    private boolean worldLimited;
    private MinMaxBounds.@Nullable Doubles distance;
    private MinMaxBounds.@Nullable Ints level;
    private @Nullable Double x;
    private @Nullable Double y;
    private @Nullable Double z;
    private @Nullable Double deltaX;
    private @Nullable Double deltaY;
    private @Nullable Double deltaZ;
    private MinMaxBounds.@Nullable FloatDegrees rotX;
    private MinMaxBounds.@Nullable FloatDegrees rotY;
    private final List<Predicate<Entity>> predicates = new ArrayList();
    private BiConsumer<Vec3, List<? extends Entity>> order;
    private boolean currentEntity;
    private @Nullable String playerName;
    private int startPosition;
    private @Nullable UUID entityUUID;
    private BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestions;
    private boolean hasNameEquals;
    private boolean hasNameNotEquals;
    private boolean isLimited;
    private boolean isSorted;
    private boolean hasGamemodeEquals;
    private boolean hasGamemodeNotEquals;
    private boolean hasTeamEquals;
    private boolean hasTeamNotEquals;
    private @Nullable EntityType<?> type;
    private boolean typeInverse;
    private boolean hasScores;
    private boolean hasAdvancements;
    private boolean usesSelectors;

    public EntitySelectorParser(StringReader reader, boolean allowSelectors) {
        this.order = EntitySelector.ORDER_ARBITRARY;
        this.suggestions = EntitySelectorParser.SUGGEST_NOTHING;
        this.reader = reader;
        this.allowSelectors = allowSelectors;
    }

    public static <S> boolean allowSelectors(S source) {
        boolean flag;

        if (source instanceof PermissionSetSupplier permissionsetsupplier) {
            if (permissionsetsupplier.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS)) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    /** @deprecated */
    @Deprecated
    public static boolean allowSelectors(PermissionSetSupplier source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS);
    }

    public EntitySelector getSelector() {
        AABB aabb;

        if (this.deltaX == null && this.deltaY == null && this.deltaZ == null) {
            if (this.distance != null && this.distance.max().isPresent()) {
                double d0 = (Double) this.distance.max().get();

                aabb = new AABB(-d0, -d0, -d0, d0 + 1.0D, d0 + 1.0D, d0 + 1.0D);
            } else {
                aabb = null;
            }
        } else {
            aabb = this.createAabb(this.deltaX == null ? 0.0D : this.deltaX, this.deltaY == null ? 0.0D : this.deltaY, this.deltaZ == null ? 0.0D : this.deltaZ);
        }

        Function<Vec3, Vec3> function;

        if (this.x == null && this.y == null && this.z == null) {
            function = (vec3) -> {
                return vec3;
            };
        } else {
            function = (vec3) -> {
                return new Vec3(this.x == null ? vec3.x : this.x, this.y == null ? vec3.y : this.y, this.z == null ? vec3.z : this.z);
            };
        }

        return new EntitySelector(this.maxResults, this.includesEntities, this.worldLimited, List.copyOf(this.predicates), this.distance, function, aabb, this.order, this.currentEntity, this.playerName, this.entityUUID, this.type, this.usesSelectors);
    }

    private AABB createAabb(double x, double y, double z) {
        boolean flag = x < 0.0D;
        boolean flag1 = y < 0.0D;
        boolean flag2 = z < 0.0D;
        double d3 = flag ? x : 0.0D;
        double d4 = flag1 ? y : 0.0D;
        double d5 = flag2 ? z : 0.0D;
        double d6 = (flag ? 0.0D : x) + 1.0D;
        double d7 = (flag1 ? 0.0D : y) + 1.0D;
        double d8 = (flag2 ? 0.0D : z) + 1.0D;

        return new AABB(d3, d4, d5, d6, d7, d8);
    }

    private void finalizePredicates() {
        if (this.rotX != null) {
            this.predicates.add(this.createRotationPredicate(this.rotX, Entity::getXRot));
        }

        if (this.rotY != null) {
            this.predicates.add(this.createRotationPredicate(this.rotY, Entity::getYRot));
        }

        if (this.level != null) {
            this.predicates.add((Predicate) (entity) -> {
                boolean flag;

                if (entity instanceof ServerPlayer serverplayer) {
                    if (this.level.matches(serverplayer.experienceLevel)) {
                        flag = true;
                        return flag;
                    }
                }

                flag = false;
                return flag;
            });
        }

    }

    private Predicate<Entity> createRotationPredicate(MinMaxBounds.FloatDegrees range, ToFloatFunction<Entity> function) {
        float f = Mth.wrapDegrees((Float) range.min().orElse(0.0F));
        float f1 = Mth.wrapDegrees((Float) range.max().orElse(359.0F));

        return (entity) -> {
            float f2 = Mth.wrapDegrees(function.applyAsFloat(entity));

            return f > f1 ? f2 >= f || f2 <= f1 : f2 >= f && f2 <= f1;
        };
    }

    protected void parseSelector() throws CommandSyntaxException {
        this.usesSelectors = true;
        this.suggestions = this::suggestSelector;
        if (!this.reader.canRead()) {
            throw EntitySelectorParser.ERROR_MISSING_SELECTOR_TYPE.createWithContext(this.reader);
        } else {
            int i = this.reader.getCursor();
            char c0 = this.reader.read();
            boolean flag;

            switch (c0) {
                case 'a':
                    this.maxResults = Integer.MAX_VALUE;
                    this.includesEntities = false;
                    this.order = EntitySelector.ORDER_ARBITRARY;
                    this.limitToType(EntityType.PLAYER);
                    flag = false;
                    break;
                case 'b':
                case 'c':
                case 'd':
                case 'f':
                case 'g':
                case 'h':
                case 'i':
                case 'j':
                case 'k':
                case 'l':
                case 'm':
                case 'o':
                case 'q':
                default:
                    this.reader.setCursor(i);
                    throw EntitySelectorParser.ERROR_UNKNOWN_SELECTOR_TYPE.createWithContext(this.reader, "@" + String.valueOf(c0));
                case 'e':
                    this.maxResults = Integer.MAX_VALUE;
                    this.includesEntities = true;
                    this.order = EntitySelector.ORDER_ARBITRARY;
                    flag = true;
                    break;
                case 'n':
                    this.maxResults = 1;
                    this.includesEntities = true;
                    this.order = EntitySelectorParser.ORDER_NEAREST;
                    flag = true;
                    break;
                case 'p':
                    this.maxResults = 1;
                    this.includesEntities = false;
                    this.order = EntitySelectorParser.ORDER_NEAREST;
                    this.limitToType(EntityType.PLAYER);
                    flag = false;
                    break;
                case 'r':
                    this.maxResults = 1;
                    this.includesEntities = false;
                    this.order = EntitySelectorParser.ORDER_RANDOM;
                    this.limitToType(EntityType.PLAYER);
                    flag = false;
                    break;
                case 's':
                    this.maxResults = 1;
                    this.includesEntities = true;
                    this.currentEntity = true;
                    flag = false;
            }

            if (flag) {
                this.predicates.add(Entity::isAlive);
            }

            this.suggestions = this::suggestOpenOptions;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.reader.skip();
                this.suggestions = this::suggestOptionsKeyOrClose;
                this.parseOptions();
            }

        }
    }

    protected void parseNameOrUUID() throws CommandSyntaxException {
        if (this.reader.canRead()) {
            this.suggestions = this::suggestName;
        }

        int i = this.reader.getCursor();
        String s = this.reader.readString();

        try {
            this.entityUUID = UUID.fromString(s);
            this.includesEntities = true;
        } catch (IllegalArgumentException illegalargumentexception) {
            if (s.isEmpty() || s.length() > 16) {
                this.reader.setCursor(i);
                throw EntitySelectorParser.ERROR_INVALID_NAME_OR_UUID.createWithContext(this.reader);
            }

            this.includesEntities = false;
            this.playerName = s;
        }

        this.maxResults = 1;
    }

    protected void parseOptions() throws CommandSyntaxException {
        this.suggestions = this::suggestOptionsKey;
        this.reader.skipWhitespace();

        while (true) {
            if (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace();
                int i = this.reader.getCursor();
                String s = this.reader.readString();
                EntitySelectorOptions.Modifier entityselectoroptions_modifier = EntitySelectorOptions.get(this, s, i);

                this.reader.skipWhitespace();
                if (!this.reader.canRead() || this.reader.peek() != '=') {
                    this.reader.setCursor(i);
                    throw EntitySelectorParser.ERROR_EXPECTED_OPTION_VALUE.createWithContext(this.reader, s);
                }

                this.reader.skip();
                this.reader.skipWhitespace();
                this.suggestions = EntitySelectorParser.SUGGEST_NOTHING;
                entityselectoroptions_modifier.handle(this);
                this.reader.skipWhitespace();
                this.suggestions = this::suggestOptionsNextOrClose;
                if (!this.reader.canRead()) {
                    continue;
                }

                if (this.reader.peek() == ',') {
                    this.reader.skip();
                    this.suggestions = this::suggestOptionsKey;
                    continue;
                }

                if (this.reader.peek() != ']') {
                    throw EntitySelectorParser.ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(this.reader);
                }
            }

            if (this.reader.canRead()) {
                this.reader.skip();
                this.suggestions = EntitySelectorParser.SUGGEST_NOTHING;
                return;
            }

            throw EntitySelectorParser.ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(this.reader);
        }
    }

    public boolean shouldInvertValue() {
        this.reader.skipWhitespace();
        if (this.reader.canRead() && this.reader.peek() == '!') {
            this.reader.skip();
            this.reader.skipWhitespace();
            return true;
        } else {
            return false;
        }
    }

    public boolean isTag() {
        this.reader.skipWhitespace();
        if (this.reader.canRead() && this.reader.peek() == '#') {
            this.reader.skip();
            this.reader.skipWhitespace();
            return true;
        } else {
            return false;
        }
    }

    public StringReader getReader() {
        return this.reader;
    }

    public void addPredicate(Predicate<Entity> predicate) {
        this.predicates.add(predicate);
    }

    public void setWorldLimited() {
        this.worldLimited = true;
    }

    public MinMaxBounds.@Nullable Doubles getDistance() {
        return this.distance;
    }

    public void setDistance(MinMaxBounds.Doubles distance) {
        this.distance = distance;
    }

    public MinMaxBounds.@Nullable Ints getLevel() {
        return this.level;
    }

    public void setLevel(MinMaxBounds.Ints level) {
        this.level = level;
    }

    public MinMaxBounds.@Nullable FloatDegrees getRotX() {
        return this.rotX;
    }

    public void setRotX(MinMaxBounds.FloatDegrees rotX) {
        this.rotX = rotX;
    }

    public MinMaxBounds.@Nullable FloatDegrees getRotY() {
        return this.rotY;
    }

    public void setRotY(MinMaxBounds.FloatDegrees rotY) {
        this.rotY = rotY;
    }

    public @Nullable Double getX() {
        return this.x;
    }

    public @Nullable Double getY() {
        return this.y;
    }

    public @Nullable Double getZ() {
        return this.z;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public void setDeltaX(double deltaX) {
        this.deltaX = deltaX;
    }

    public void setDeltaY(double deltaY) {
        this.deltaY = deltaY;
    }

    public void setDeltaZ(double deltaZ) {
        this.deltaZ = deltaZ;
    }

    public @Nullable Double getDeltaX() {
        return this.deltaX;
    }

    public @Nullable Double getDeltaY() {
        return this.deltaY;
    }

    public @Nullable Double getDeltaZ() {
        return this.deltaZ;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public void setIncludesEntities(boolean includesEntities) {
        this.includesEntities = includesEntities;
    }

    public BiConsumer<Vec3, List<? extends Entity>> getOrder() {
        return this.order;
    }

    public void setOrder(BiConsumer<Vec3, List<? extends Entity>> order) {
        this.order = order;
    }

    public EntitySelector parse() throws CommandSyntaxException {
        this.startPosition = this.reader.getCursor();
        this.suggestions = this::suggestNameOrSelector;
        if (this.reader.canRead() && this.reader.peek() == '@') {
            if (!this.allowSelectors) {
                throw EntitySelectorParser.ERROR_SELECTORS_NOT_ALLOWED.createWithContext(this.reader);
            }

            this.reader.skip();
            this.parseSelector();
        } else {
            this.parseNameOrUUID();
        }

        this.finalizePredicates();
        return this.getSelector();
    }

    private static void fillSelectorSuggestions(SuggestionsBuilder builder) {
        builder.suggest("@p", Component.translatable("argument.entity.selector.nearestPlayer"));
        builder.suggest("@a", Component.translatable("argument.entity.selector.allPlayers"));
        builder.suggest("@r", Component.translatable("argument.entity.selector.randomPlayer"));
        builder.suggest("@s", Component.translatable("argument.entity.selector.self"));
        builder.suggest("@e", Component.translatable("argument.entity.selector.allEntities"));
        builder.suggest("@n", Component.translatable("argument.entity.selector.nearestEntity"));
    }

    private CompletableFuture<Suggestions> suggestNameOrSelector(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> names) {
        names.accept(builder);
        if (this.allowSelectors) {
            fillSelectorSuggestions(builder);
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestName(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> names) {
        SuggestionsBuilder suggestionsbuilder1 = builder.createOffset(this.startPosition);

        names.accept(suggestionsbuilder1);
        return builder.add(suggestionsbuilder1).buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSelector(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> names) {
        SuggestionsBuilder suggestionsbuilder1 = builder.createOffset(builder.getStart() - 1);

        fillSelectorSuggestions(suggestionsbuilder1);
        builder.add(suggestionsbuilder1);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenOptions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> names) {
        builder.suggest(String.valueOf('['));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsKeyOrClose(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> names) {
        builder.suggest(String.valueOf(']'));
        EntitySelectorOptions.suggestNames(this, builder);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsKey(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> names) {
        EntitySelectorOptions.suggestNames(this, builder);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsNextOrClose(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> names) {
        builder.suggest(String.valueOf(','));
        builder.suggest(String.valueOf(']'));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestEquals(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> names) {
        builder.suggest(String.valueOf('='));
        return builder.buildFuture();
    }

    public boolean isCurrentEntity() {
        return this.currentEntity;
    }

    public void setSuggestions(BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestions) {
        this.suggestions = suggestions;
    }

    public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> names) {
        return (CompletableFuture) this.suggestions.apply(builder.createOffset(this.reader.getCursor()), names);
    }

    public boolean hasNameEquals() {
        return this.hasNameEquals;
    }

    public void setHasNameEquals(boolean hasNameEquals) {
        this.hasNameEquals = hasNameEquals;
    }

    public boolean hasNameNotEquals() {
        return this.hasNameNotEquals;
    }

    public void setHasNameNotEquals(boolean hasNameNotEquals) {
        this.hasNameNotEquals = hasNameNotEquals;
    }

    public boolean isLimited() {
        return this.isLimited;
    }

    public void setLimited(boolean limited) {
        this.isLimited = limited;
    }

    public boolean isSorted() {
        return this.isSorted;
    }

    public void setSorted(boolean sorted) {
        this.isSorted = sorted;
    }

    public boolean hasGamemodeEquals() {
        return this.hasGamemodeEquals;
    }

    public void setHasGamemodeEquals(boolean hasGamemodeEquals) {
        this.hasGamemodeEquals = hasGamemodeEquals;
    }

    public boolean hasGamemodeNotEquals() {
        return this.hasGamemodeNotEquals;
    }

    public void setHasGamemodeNotEquals(boolean hasGamemodeNotEquals) {
        this.hasGamemodeNotEquals = hasGamemodeNotEquals;
    }

    public boolean hasTeamEquals() {
        return this.hasTeamEquals;
    }

    public void setHasTeamEquals(boolean hasTeamEquals) {
        this.hasTeamEquals = hasTeamEquals;
    }

    public boolean hasTeamNotEquals() {
        return this.hasTeamNotEquals;
    }

    public void setHasTeamNotEquals(boolean hasTeamNotEquals) {
        this.hasTeamNotEquals = hasTeamNotEquals;
    }

    public void limitToType(EntityType<?> type) {
        this.type = type;
    }

    public void setTypeLimitedInversely() {
        this.typeInverse = true;
    }

    public boolean isTypeLimited() {
        return this.type != null;
    }

    public boolean isTypeLimitedInversely() {
        return this.typeInverse;
    }

    public boolean hasScores() {
        return this.hasScores;
    }

    public void setHasScores(boolean hasScores) {
        this.hasScores = hasScores;
    }

    public boolean hasAdvancements() {
        return this.hasAdvancements;
    }

    public void setHasAdvancements(boolean hasAdvancements) {
        this.hasAdvancements = hasAdvancements;
    }
}
