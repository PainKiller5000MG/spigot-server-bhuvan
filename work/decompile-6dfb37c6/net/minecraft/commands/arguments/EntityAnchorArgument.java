package net.minecraft.commands.arguments;

import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EntityAnchorArgument implements ArgumentType<EntityAnchorArgument.Anchor> {

    private static final Collection<String> EXAMPLES = Arrays.asList("eyes", "feet");
    private static final DynamicCommandExceptionType ERROR_INVALID = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.anchor.invalid", object);
    });

    public EntityAnchorArgument() {}

    public static EntityAnchorArgument.Anchor getAnchor(CommandContext<CommandSourceStack> context, String name) {
        return (EntityAnchorArgument.Anchor) context.getArgument(name, EntityAnchorArgument.Anchor.class);
    }

    public static EntityAnchorArgument anchor() {
        return new EntityAnchorArgument();
    }

    public EntityAnchorArgument.Anchor parse(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        String s = reader.readUnquotedString();
        EntityAnchorArgument.Anchor entityanchorargument_anchor = EntityAnchorArgument.Anchor.getByName(s);

        if (entityanchorargument_anchor == null) {
            reader.setCursor(i);
            throw EntityAnchorArgument.ERROR_INVALID.createWithContext(reader, s);
        } else {
            return entityanchorargument_anchor;
        }
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(EntityAnchorArgument.Anchor.BY_NAME.keySet(), builder);
    }

    public Collection<String> getExamples() {
        return EntityAnchorArgument.EXAMPLES;
    }

    public static enum Anchor {

        FEET("feet", (vec3, entity) -> {
            return vec3;
        }), EYES("eyes", (vec3, entity) -> {
            return new Vec3(vec3.x, vec3.y + (double) entity.getEyeHeight(), vec3.z);
        });

        private static final Map<String, EntityAnchorArgument.Anchor> BY_NAME = (Map) Util.make(Maps.newHashMap(), (hashmap) -> {
            for (EntityAnchorArgument.Anchor entityanchorargument_anchor : values()) {
                hashmap.put(entityanchorargument_anchor.name, entityanchorargument_anchor);
            }

        });
        private final String name;
        private final BiFunction<Vec3, Entity, Vec3> transform;

        private Anchor(String name, BiFunction<Vec3, Entity, Vec3> transform) {
            this.name = name;
            this.transform = transform;
        }

        public static EntityAnchorArgument.@Nullable Anchor getByName(String name) {
            return (EntityAnchorArgument.Anchor) EntityAnchorArgument.Anchor.BY_NAME.get(name);
        }

        public Vec3 apply(Entity entity) {
            return (Vec3) this.transform.apply(entity.position(), entity);
        }

        public Vec3 apply(CommandSourceStack source) {
            Entity entity = source.getEntity();

            return entity == null ? source.getPosition() : (Vec3) this.transform.apply(source.getPosition(), entity);
        }
    }
}
