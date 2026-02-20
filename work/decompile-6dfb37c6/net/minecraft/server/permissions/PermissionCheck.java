package net.minecraft.server.permissions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;

public interface PermissionCheck {

    Codec<PermissionCheck> CODEC = BuiltInRegistries.PERMISSION_CHECK_TYPE.byNameCodec().dispatch(PermissionCheck::codec, (mapcodec) -> {
        return mapcodec;
    });

    boolean check(PermissionSet source);

    MapCodec<? extends PermissionCheck> codec();

    public static class AlwaysPass implements PermissionCheck {

        public static final PermissionCheck.AlwaysPass INSTANCE = new PermissionCheck.AlwaysPass();
        public static final MapCodec<PermissionCheck.AlwaysPass> MAP_CODEC = MapCodec.unit(PermissionCheck.AlwaysPass.INSTANCE);

        private AlwaysPass() {}

        @Override
        public boolean check(PermissionSet source) {
            return true;
        }

        @Override
        public MapCodec<PermissionCheck.AlwaysPass> codec() {
            return PermissionCheck.AlwaysPass.MAP_CODEC;
        }
    }

    public static record Require(Permission permission) implements PermissionCheck {

        public static final MapCodec<PermissionCheck.Require> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Permission.CODEC.fieldOf("permission").forGetter(PermissionCheck.Require::permission)).apply(instance, PermissionCheck.Require::new);
        });

        @Override
        public MapCodec<PermissionCheck.Require> codec() {
            return PermissionCheck.Require.MAP_CODEC;
        }

        @Override
        public boolean check(PermissionSet source) {
            return source.hasPermission(this.permission);
        }
    }
}
