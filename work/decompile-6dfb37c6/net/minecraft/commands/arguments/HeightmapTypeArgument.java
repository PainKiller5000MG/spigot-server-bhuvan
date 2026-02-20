package net.minecraft.commands.arguments;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.Heightmap;

public class HeightmapTypeArgument extends StringRepresentableArgument<Heightmap.Types> {

    private static final Codec<Heightmap.Types> LOWER_CASE_CODEC = StringRepresentable.<Heightmap.Types>fromEnumWithMapping(HeightmapTypeArgument::keptTypes, (s) -> {
        return s.toLowerCase(Locale.ROOT);
    });

    private static Heightmap.Types[] keptTypes() {
        return (Heightmap.Types[]) Arrays.stream(Heightmap.Types.values()).filter(Heightmap.Types::keepAfterWorldgen).toArray((i) -> {
            return new Heightmap.Types[i];
        });
    }

    private HeightmapTypeArgument() {
        super(HeightmapTypeArgument.LOWER_CASE_CODEC, HeightmapTypeArgument::keptTypes);
    }

    public static HeightmapTypeArgument heightmap() {
        return new HeightmapTypeArgument();
    }

    public static Heightmap.Types getHeightmap(CommandContext<CommandSourceStack> context, String name) {
        return (Heightmap.Types) context.getArgument(name, Heightmap.Types.class);
    }

    @Override
    protected String convertId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
