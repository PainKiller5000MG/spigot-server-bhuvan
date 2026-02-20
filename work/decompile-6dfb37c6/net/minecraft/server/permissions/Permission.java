package net.minecraft.server.permissions;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public interface Permission {

    Codec<Permission> FULL_CODEC = BuiltInRegistries.PERMISSION_TYPE.byNameCodec().dispatch(Permission::codec, (mapcodec) -> {
        return mapcodec;
    });
    Codec<Permission> CODEC = Codec.either(Permission.FULL_CODEC, Identifier.CODEC).xmap((either) -> {
        return (Permission) either.map((permission) -> {
            return permission;
        }, Permission.Atom::create);
    }, (permission) -> {
        Either either;

        if (permission instanceof Permission.Atom permission_atom) {
            either = Either.right(permission_atom.id());
        } else {
            either = Either.left(permission);
        }

        return either;
    });

    MapCodec<? extends Permission> codec();

    public static record Atom(Identifier id) implements Permission {

        public static final MapCodec<Permission.Atom> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Identifier.CODEC.fieldOf("id").forGetter(Permission.Atom::id)).apply(instance, Permission.Atom::new);
        });

        @Override
        public MapCodec<Permission.Atom> codec() {
            return Permission.Atom.MAP_CODEC;
        }

        public static Permission.Atom create(String name) {
            return create(Identifier.withDefaultNamespace(name));
        }

        public static Permission.Atom create(Identifier id) {
            return new Permission.Atom(id);
        }
    }

    public static record HasCommandLevel(PermissionLevel level) implements Permission {

        public static final MapCodec<Permission.HasCommandLevel> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(PermissionLevel.CODEC.fieldOf("level").forGetter(Permission.HasCommandLevel::level)).apply(instance, Permission.HasCommandLevel::new);
        });

        @Override
        public MapCodec<Permission.HasCommandLevel> codec() {
            return Permission.HasCommandLevel.MAP_CODEC;
        }
    }
}
