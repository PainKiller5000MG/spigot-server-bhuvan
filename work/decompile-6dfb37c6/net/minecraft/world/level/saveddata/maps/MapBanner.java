package net.minecraft.world.level.saveddata.maps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public record MapBanner(BlockPos pos, DyeColor color, Optional<Component> name) {

    public static final Codec<MapBanner> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(BlockPos.CODEC.fieldOf("pos").forGetter(MapBanner::pos), DyeColor.CODEC.lenientOptionalFieldOf("color", DyeColor.WHITE).forGetter(MapBanner::color), ComponentSerialization.CODEC.lenientOptionalFieldOf("name").forGetter(MapBanner::name)).apply(instance, MapBanner::new);
    });

    public static @Nullable MapBanner fromWorld(BlockGetter level, BlockPos pos) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof BannerBlockEntity bannerblockentity) {
            DyeColor dyecolor = bannerblockentity.getBaseColor();
            Optional<Component> optional = Optional.ofNullable(bannerblockentity.getCustomName());

            return new MapBanner(pos, dyecolor, optional);
        } else {
            return null;
        }
    }

    public Holder<MapDecorationType> getDecoration() {
        Holder holder;

        switch (this.color) {
            case WHITE:
                holder = MapDecorationTypes.WHITE_BANNER;
                break;
            case ORANGE:
                holder = MapDecorationTypes.ORANGE_BANNER;
                break;
            case MAGENTA:
                holder = MapDecorationTypes.MAGENTA_BANNER;
                break;
            case LIGHT_BLUE:
                holder = MapDecorationTypes.LIGHT_BLUE_BANNER;
                break;
            case YELLOW:
                holder = MapDecorationTypes.YELLOW_BANNER;
                break;
            case LIME:
                holder = MapDecorationTypes.LIME_BANNER;
                break;
            case PINK:
                holder = MapDecorationTypes.PINK_BANNER;
                break;
            case GRAY:
                holder = MapDecorationTypes.GRAY_BANNER;
                break;
            case LIGHT_GRAY:
                holder = MapDecorationTypes.LIGHT_GRAY_BANNER;
                break;
            case CYAN:
                holder = MapDecorationTypes.CYAN_BANNER;
                break;
            case PURPLE:
                holder = MapDecorationTypes.PURPLE_BANNER;
                break;
            case BLUE:
                holder = MapDecorationTypes.BLUE_BANNER;
                break;
            case BROWN:
                holder = MapDecorationTypes.BROWN_BANNER;
                break;
            case GREEN:
                holder = MapDecorationTypes.GREEN_BANNER;
                break;
            case RED:
                holder = MapDecorationTypes.RED_BANNER;
                break;
            case BLACK:
                holder = MapDecorationTypes.BLACK_BANNER;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return holder;
    }

    public String getId() {
        int i = this.pos.getX();

        return "banner-" + i + "," + this.pos.getY() + "," + this.pos.getZ();
    }
}
