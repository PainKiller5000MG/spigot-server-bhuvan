package net.minecraft.stats;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.UnaryOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.RecipeBookType;

public final class RecipeBookSettings {

    public static final StreamCodec<FriendlyByteBuf, RecipeBookSettings> STREAM_CODEC = StreamCodec.composite(RecipeBookSettings.TypeSettings.STREAM_CODEC, (recipebooksettings) -> {
        return recipebooksettings.crafting;
    }, RecipeBookSettings.TypeSettings.STREAM_CODEC, (recipebooksettings) -> {
        return recipebooksettings.furnace;
    }, RecipeBookSettings.TypeSettings.STREAM_CODEC, (recipebooksettings) -> {
        return recipebooksettings.blastFurnace;
    }, RecipeBookSettings.TypeSettings.STREAM_CODEC, (recipebooksettings) -> {
        return recipebooksettings.smoker;
    }, RecipeBookSettings::new);
    public static final MapCodec<RecipeBookSettings> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(RecipeBookSettings.TypeSettings.CRAFTING_MAP_CODEC.forGetter((recipebooksettings) -> {
            return recipebooksettings.crafting;
        }), RecipeBookSettings.TypeSettings.FURNACE_MAP_CODEC.forGetter((recipebooksettings) -> {
            return recipebooksettings.furnace;
        }), RecipeBookSettings.TypeSettings.BLAST_FURNACE_MAP_CODEC.forGetter((recipebooksettings) -> {
            return recipebooksettings.blastFurnace;
        }), RecipeBookSettings.TypeSettings.SMOKER_MAP_CODEC.forGetter((recipebooksettings) -> {
            return recipebooksettings.smoker;
        })).apply(instance, RecipeBookSettings::new);
    });
    private RecipeBookSettings.TypeSettings crafting;
    private RecipeBookSettings.TypeSettings furnace;
    private RecipeBookSettings.TypeSettings blastFurnace;
    private RecipeBookSettings.TypeSettings smoker;

    public RecipeBookSettings() {
        this(RecipeBookSettings.TypeSettings.DEFAULT, RecipeBookSettings.TypeSettings.DEFAULT, RecipeBookSettings.TypeSettings.DEFAULT, RecipeBookSettings.TypeSettings.DEFAULT);
    }

    private RecipeBookSettings(RecipeBookSettings.TypeSettings crafting, RecipeBookSettings.TypeSettings furnace, RecipeBookSettings.TypeSettings blastFurnace, RecipeBookSettings.TypeSettings smoker) {
        this.crafting = crafting;
        this.furnace = furnace;
        this.blastFurnace = blastFurnace;
        this.smoker = smoker;
    }

    @VisibleForTesting
    public RecipeBookSettings.TypeSettings getSettings(RecipeBookType type) {
        RecipeBookSettings.TypeSettings recipebooksettings_typesettings;

        switch (type) {
            case CRAFTING:
                recipebooksettings_typesettings = this.crafting;
                break;
            case FURNACE:
                recipebooksettings_typesettings = this.furnace;
                break;
            case BLAST_FURNACE:
                recipebooksettings_typesettings = this.blastFurnace;
                break;
            case SMOKER:
                recipebooksettings_typesettings = this.smoker;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return recipebooksettings_typesettings;
    }

    private void updateSettings(RecipeBookType recipeBookType, UnaryOperator<RecipeBookSettings.TypeSettings> operator) {
        switch (recipeBookType) {
            case CRAFTING:
                this.crafting = (RecipeBookSettings.TypeSettings) operator.apply(this.crafting);
                break;
            case FURNACE:
                this.furnace = (RecipeBookSettings.TypeSettings) operator.apply(this.furnace);
                break;
            case BLAST_FURNACE:
                this.blastFurnace = (RecipeBookSettings.TypeSettings) operator.apply(this.blastFurnace);
                break;
            case SMOKER:
                this.smoker = (RecipeBookSettings.TypeSettings) operator.apply(this.smoker);
        }

    }

    public boolean isOpen(RecipeBookType type) {
        return this.getSettings(type).open;
    }

    public void setOpen(RecipeBookType type, boolean open) {
        this.updateSettings(type, (recipebooksettings_typesettings) -> {
            return recipebooksettings_typesettings.setOpen(open);
        });
    }

    public boolean isFiltering(RecipeBookType type) {
        return this.getSettings(type).filtering;
    }

    public void setFiltering(RecipeBookType type, boolean filtering) {
        this.updateSettings(type, (recipebooksettings_typesettings) -> {
            return recipebooksettings_typesettings.setFiltering(filtering);
        });
    }

    public RecipeBookSettings copy() {
        return new RecipeBookSettings(this.crafting, this.furnace, this.blastFurnace, this.smoker);
    }

    public void replaceFrom(RecipeBookSettings other) {
        this.crafting = other.crafting;
        this.furnace = other.furnace;
        this.blastFurnace = other.blastFurnace;
        this.smoker = other.smoker;
    }

    public static record TypeSettings(boolean open, boolean filtering) {

        public static final RecipeBookSettings.TypeSettings DEFAULT = new RecipeBookSettings.TypeSettings(false, false);
        public static final MapCodec<RecipeBookSettings.TypeSettings> CRAFTING_MAP_CODEC = codec("isGuiOpen", "isFilteringCraftable");
        public static final MapCodec<RecipeBookSettings.TypeSettings> FURNACE_MAP_CODEC = codec("isFurnaceGuiOpen", "isFurnaceFilteringCraftable");
        public static final MapCodec<RecipeBookSettings.TypeSettings> BLAST_FURNACE_MAP_CODEC = codec("isBlastingFurnaceGuiOpen", "isBlastingFurnaceFilteringCraftable");
        public static final MapCodec<RecipeBookSettings.TypeSettings> SMOKER_MAP_CODEC = codec("isSmokerGuiOpen", "isSmokerFilteringCraftable");
        public static final StreamCodec<ByteBuf, RecipeBookSettings.TypeSettings> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, RecipeBookSettings.TypeSettings::open, ByteBufCodecs.BOOL, RecipeBookSettings.TypeSettings::filtering, RecipeBookSettings.TypeSettings::new);

        public String toString() {
            return "[open=" + this.open + ", filtering=" + this.filtering + "]";
        }

        public RecipeBookSettings.TypeSettings setOpen(boolean open) {
            return new RecipeBookSettings.TypeSettings(open, this.filtering);
        }

        public RecipeBookSettings.TypeSettings setFiltering(boolean filtering) {
            return new RecipeBookSettings.TypeSettings(this.open, filtering);
        }

        private static MapCodec<RecipeBookSettings.TypeSettings> codec(String openFieldName, String filteringFieldName) {
            return RecordCodecBuilder.mapCodec((instance) -> {
                return instance.group(Codec.BOOL.optionalFieldOf(openFieldName, false).forGetter(RecipeBookSettings.TypeSettings::open), Codec.BOOL.optionalFieldOf(filteringFieldName, false).forGetter(RecipeBookSettings.TypeSettings::filtering)).apply(instance, RecipeBookSettings.TypeSettings::new);
            });
        }
    }
}
