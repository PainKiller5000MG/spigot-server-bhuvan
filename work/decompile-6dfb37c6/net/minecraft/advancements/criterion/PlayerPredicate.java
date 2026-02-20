package net.minecraft.advancements.criterion;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2BooleanMaps;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record PlayerPredicate(MinMaxBounds.Ints level, GameTypePredicate gameType, List<PlayerPredicate.StatMatcher<?>> stats, Object2BooleanMap<ResourceKey<Recipe<?>>> recipes, Map<Identifier, PlayerPredicate.AdvancementPredicate> advancements, Optional<EntityPredicate> lookingAt, Optional<InputPredicate> input) implements EntitySubPredicate {

    public static final int LOOKING_AT_RANGE = 100;
    public static final MapCodec<PlayerPredicate> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(MinMaxBounds.Ints.CODEC.optionalFieldOf("level", MinMaxBounds.Ints.ANY).forGetter(PlayerPredicate::level), GameTypePredicate.CODEC.optionalFieldOf("gamemode", GameTypePredicate.ANY).forGetter(PlayerPredicate::gameType), PlayerPredicate.StatMatcher.CODEC.listOf().optionalFieldOf("stats", List.of()).forGetter(PlayerPredicate::stats), ExtraCodecs.object2BooleanMap(Recipe.KEY_CODEC).optionalFieldOf("recipes", Object2BooleanMaps.emptyMap()).forGetter(PlayerPredicate::recipes), Codec.unboundedMap(Identifier.CODEC, PlayerPredicate.AdvancementPredicate.CODEC).optionalFieldOf("advancements", Map.of()).forGetter(PlayerPredicate::advancements), EntityPredicate.CODEC.optionalFieldOf("looking_at").forGetter(PlayerPredicate::lookingAt), InputPredicate.CODEC.optionalFieldOf("input").forGetter(PlayerPredicate::input)).apply(instance, PlayerPredicate::new);
    });

    @Override
    public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
        if (!(entity instanceof ServerPlayer serverplayer)) {
            return false;
        } else if (!this.level.matches(serverplayer.experienceLevel)) {
            return false;
        } else if (!this.gameType.matches(serverplayer.gameMode())) {
            return false;
        } else {
            StatsCounter statscounter = serverplayer.getStats();

            for (PlayerPredicate.StatMatcher<?> playerpredicate_statmatcher : this.stats) {
                if (!playerpredicate_statmatcher.matches(statscounter)) {
                    return false;
                }
            }

            ServerRecipeBook serverrecipebook = serverplayer.getRecipeBook();
            ObjectIterator objectiterator = this.recipes.object2BooleanEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Object2BooleanMap.Entry<ResourceKey<Recipe<?>>> object2booleanmap_entry = (Entry) objectiterator.next();

                if (serverrecipebook.contains((ResourceKey) object2booleanmap_entry.getKey()) != object2booleanmap_entry.getBooleanValue()) {
                    return false;
                }
            }

            if (!this.advancements.isEmpty()) {
                PlayerAdvancements playeradvancements = serverplayer.getAdvancements();
                ServerAdvancementManager serveradvancementmanager = serverplayer.level().getServer().getAdvancements();

                for (Map.Entry<Identifier, PlayerPredicate.AdvancementPredicate> map_entry : this.advancements.entrySet()) {
                    AdvancementHolder advancementholder = serveradvancementmanager.get((Identifier) map_entry.getKey());

                    if (advancementholder == null || !((PlayerPredicate.AdvancementPredicate) map_entry.getValue()).test(playeradvancements.getOrStartProgress(advancementholder))) {
                        return false;
                    }
                }
            }

            if (this.lookingAt.isPresent()) {
                Vec3 vec31 = serverplayer.getEyePosition();
                Vec3 vec32 = serverplayer.getViewVector(1.0F);
                Vec3 vec33 = vec31.add(vec32.x * 100.0D, vec32.y * 100.0D, vec32.z * 100.0D);
                EntityHitResult entityhitresult = ProjectileUtil.getEntityHitResult(serverplayer.level(), serverplayer, vec31, vec33, (new AABB(vec31, vec33)).inflate(1.0D), (entity1) -> {
                    return !entity1.isSpectator();
                }, 0.0F);

                if (entityhitresult == null || entityhitresult.getType() != HitResult.Type.ENTITY) {
                    return false;
                }

                Entity entity1 = entityhitresult.getEntity();

                if (!((EntityPredicate) this.lookingAt.get()).matches(serverplayer, entity1) || !serverplayer.hasLineOfSight(entity1)) {
                    return false;
                }
            }

            if (this.input.isPresent() && !((InputPredicate) this.input.get()).matches(serverplayer.getLastClientInput())) {
                return false;
            } else {
                return true;
            }
        }
    }

    @Override
    public MapCodec<PlayerPredicate> codec() {
        return EntitySubPredicates.PLAYER;
    }

    private interface AdvancementPredicate extends Predicate<AdvancementProgress> {

        Codec<PlayerPredicate.AdvancementPredicate> CODEC = Codec.either(PlayerPredicate.AdvancementDonePredicate.CODEC, PlayerPredicate.AdvancementCriterionsPredicate.CODEC).xmap(Either::unwrap, (playerpredicate_advancementpredicate) -> {
            if (playerpredicate_advancementpredicate instanceof PlayerPredicate.AdvancementDonePredicate playerpredicate_advancementdonepredicate) {
                return Either.left(playerpredicate_advancementdonepredicate);
            } else if (playerpredicate_advancementpredicate instanceof PlayerPredicate.AdvancementCriterionsPredicate playerpredicate_advancementcriterionspredicate) {
                return Either.right(playerpredicate_advancementcriterionspredicate);
            } else {
                throw new UnsupportedOperationException();
            }
        });
    }

    private static record AdvancementDonePredicate(boolean state) implements PlayerPredicate.AdvancementPredicate {

        public static final Codec<PlayerPredicate.AdvancementDonePredicate> CODEC = Codec.BOOL.xmap(PlayerPredicate.AdvancementDonePredicate::new, PlayerPredicate.AdvancementDonePredicate::state);

        public boolean test(AdvancementProgress progress) {
            return progress.isDone() == this.state;
        }
    }

    private static record AdvancementCriterionsPredicate(Object2BooleanMap<String> criterions) implements PlayerPredicate.AdvancementPredicate {

        public static final Codec<PlayerPredicate.AdvancementCriterionsPredicate> CODEC = ExtraCodecs.object2BooleanMap(Codec.STRING).xmap(PlayerPredicate.AdvancementCriterionsPredicate::new, PlayerPredicate.AdvancementCriterionsPredicate::criterions);

        public boolean test(AdvancementProgress progress) {
            ObjectIterator objectiterator = this.criterions.object2BooleanEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Object2BooleanMap.Entry<String> object2booleanmap_entry = (Entry) objectiterator.next();
                CriterionProgress criterionprogress = progress.getCriterion((String) object2booleanmap_entry.getKey());

                if (criterionprogress == null || criterionprogress.isDone() != object2booleanmap_entry.getBooleanValue()) {
                    return false;
                }
            }

            return true;
        }
    }

    private static record StatMatcher<T>(StatType<T> type, Holder<T> value, MinMaxBounds.Ints range, Supplier<Stat<T>> stat) {

        public static final Codec<PlayerPredicate.StatMatcher<?>> CODEC = BuiltInRegistries.STAT_TYPE.byNameCodec().dispatch(PlayerPredicate.StatMatcher::type, PlayerPredicate.StatMatcher::createTypedCodec);

        public StatMatcher(StatType<T> type, Holder<T> value, MinMaxBounds.Ints range) {
            this(type, value, range, Suppliers.memoize(() -> {
                return type.get(value.value());
            }));
        }

        private static <T> MapCodec<PlayerPredicate.StatMatcher<T>> createTypedCodec(StatType<T> type) {
            return RecordCodecBuilder.mapCodec((instance) -> {
                return instance.group(type.getRegistry().holderByNameCodec().fieldOf("stat").forGetter(PlayerPredicate.StatMatcher::value), MinMaxBounds.Ints.CODEC.optionalFieldOf("value", MinMaxBounds.Ints.ANY).forGetter(PlayerPredicate.StatMatcher::range)).apply(instance, (holder, minmaxbounds_ints) -> {
                    return new PlayerPredicate.StatMatcher(type, holder, minmaxbounds_ints);
                });
            });
        }

        public boolean matches(StatsCounter counter) {
            return this.range.matches(counter.getValue((Stat) this.stat.get()));
        }
    }

    public static class Builder {

        private MinMaxBounds.Ints level;
        private GameTypePredicate gameType;
        private final ImmutableList.Builder<PlayerPredicate.StatMatcher<?>> stats;
        private final Object2BooleanMap<ResourceKey<Recipe<?>>> recipes;
        private final Map<Identifier, PlayerPredicate.AdvancementPredicate> advancements;
        private Optional<EntityPredicate> lookingAt;
        private Optional<InputPredicate> input;

        public Builder() {
            this.level = MinMaxBounds.Ints.ANY;
            this.gameType = GameTypePredicate.ANY;
            this.stats = ImmutableList.builder();
            this.recipes = new Object2BooleanOpenHashMap();
            this.advancements = Maps.newHashMap();
            this.lookingAt = Optional.empty();
            this.input = Optional.empty();
        }

        public static PlayerPredicate.Builder player() {
            return new PlayerPredicate.Builder();
        }

        public PlayerPredicate.Builder setLevel(MinMaxBounds.Ints level) {
            this.level = level;
            return this;
        }

        public <T> PlayerPredicate.Builder addStat(StatType<T> type, Holder.Reference<T> value, MinMaxBounds.Ints range) {
            this.stats.add(new PlayerPredicate.StatMatcher(type, value, range));
            return this;
        }

        public PlayerPredicate.Builder addRecipe(ResourceKey<Recipe<?>> recipe, boolean present) {
            this.recipes.put(recipe, present);
            return this;
        }

        public PlayerPredicate.Builder setGameType(GameTypePredicate gameType) {
            this.gameType = gameType;
            return this;
        }

        public PlayerPredicate.Builder setLookingAt(EntityPredicate.Builder lookingAt) {
            this.lookingAt = Optional.of(lookingAt.build());
            return this;
        }

        public PlayerPredicate.Builder checkAdvancementDone(Identifier advancement, boolean isDone) {
            this.advancements.put(advancement, new PlayerPredicate.AdvancementDonePredicate(isDone));
            return this;
        }

        public PlayerPredicate.Builder checkAdvancementCriterions(Identifier advancement, Map<String, Boolean> criterions) {
            this.advancements.put(advancement, new PlayerPredicate.AdvancementCriterionsPredicate(new Object2BooleanOpenHashMap(criterions)));
            return this;
        }

        public PlayerPredicate.Builder hasInput(InputPredicate input) {
            this.input = Optional.of(input);
            return this;
        }

        public PlayerPredicate build() {
            return new PlayerPredicate(this.level, this.gameType, this.stats.build(), this.recipes, this.advancements, this.lookingAt, this.input);
        }
    }
}
