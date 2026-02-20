package net.minecraft.network.chat.contents;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.ObjectInfo;
import net.minecraft.network.chat.contents.objects.ObjectInfos;

public record ObjectContents(ObjectInfo contents) implements ComponentContents {

    private static final String PLACEHOLDER = Character.toString('\ufffc');
    public static final MapCodec<ObjectContents> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(ObjectInfos.CODEC.forGetter(ObjectContents::contents)).apply(instance, ObjectContents::new);
    });

    @Override
    public MapCodec<ObjectContents> codec() {
        return ObjectContents.MAP_CODEC;
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
        return output.accept(this.contents.description());
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style currentStyle) {
        return output.accept(currentStyle.withFont(this.contents.fontDescription()), ObjectContents.PLACEHOLDER);
    }
}
