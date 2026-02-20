package net.minecraft.world.level.gamerules;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jspecify.annotations.Nullable;

public final class GameRuleMap {

    public static final Codec<GameRuleMap> CODEC = Codec.dispatchedMap(BuiltInRegistries.GAME_RULE.byNameCodec(), GameRule::valueCodec).xmap(GameRuleMap::ofTrusted, GameRuleMap::map);
    private final Reference2ObjectMap<GameRule<?>, Object> map;

    private GameRuleMap(Reference2ObjectMap<GameRule<?>, Object> map) {
        this.map = map;
    }

    private static GameRuleMap ofTrusted(Map<GameRule<?>, Object> map) {
        return new GameRuleMap(new Reference2ObjectOpenHashMap(map));
    }

    public static GameRuleMap of() {
        return new GameRuleMap(new Reference2ObjectOpenHashMap());
    }

    public static GameRuleMap of(Stream<GameRule<?>> gameRuleTypeStream) {
        Reference2ObjectOpenHashMap<GameRule<?>, Object> reference2objectopenhashmap = new Reference2ObjectOpenHashMap();

        gameRuleTypeStream.forEach((gamerule) -> {
            reference2objectopenhashmap.put(gamerule, gamerule.defaultValue());
        });
        return new GameRuleMap(reference2objectopenhashmap);
    }

    public static GameRuleMap copyOf(GameRuleMap gameRuleMap) {
        return new GameRuleMap(new Reference2ObjectOpenHashMap(gameRuleMap.map));
    }

    public boolean has(GameRule<?> gameRule) {
        return this.map.containsKey(gameRule);
    }

    public <T> @Nullable T get(GameRule<T> gameRule) {
        return (T) this.map.get(gameRule);
    }

    public <T> void set(GameRule<T> gameRule, T value) {
        this.map.put(gameRule, value);
    }

    public <T> @Nullable T remove(GameRule<T> gameRule) {
        return (T) this.map.remove(gameRule);
    }

    public Set<GameRule<?>> keySet() {
        return this.map.keySet();
    }

    public int size() {
        return this.map.size();
    }

    public String toString() {
        return this.map.toString();
    }

    public GameRuleMap withOther(GameRuleMap other) {
        GameRuleMap gamerulemap1 = copyOf(this);

        gamerulemap1.setFromIf(other, (gamerule) -> {
            return true;
        });
        return gamerulemap1;
    }

    public void setFromIf(GameRuleMap other, Predicate<GameRule<?>> predicate) {
        for (GameRule<?> gamerule : other.keySet()) {
            if (predicate.test(gamerule)) {
                setGameRule(other, gamerule, this);
            }
        }

    }

    private static <T> void setGameRule(GameRuleMap other, GameRule<T> gameRule, GameRuleMap result) {
        result.set(gameRule, Objects.requireNonNull(other.get(gameRule)));
    }

    private Reference2ObjectMap<GameRule<?>, Object> map() {
        return this.map;
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj != null && obj.getClass() == this.getClass()) {
            GameRuleMap gamerulemap = (GameRuleMap) obj;

            return Objects.equals(this.map, gamerulemap.map);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.map});
    }

    public static class Builder {

        final Reference2ObjectMap<GameRule<?>, Object> map = new Reference2ObjectOpenHashMap();

        public Builder() {}

        public <T> GameRuleMap.Builder set(GameRule<T> gameRule, T value) {
            this.map.put(gameRule, value);
            return this;
        }

        public GameRuleMap build() {
            return new GameRuleMap(this.map);
        }
    }
}
