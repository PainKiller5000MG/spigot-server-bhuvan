package net.minecraft.network.chat.contents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public interface PlainTextContents extends ComponentContents {

    MapCodec<PlainTextContents> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.STRING.fieldOf("text").forGetter(PlainTextContents::text)).apply(instance, PlainTextContents::create);
    });
    PlainTextContents EMPTY = new PlainTextContents() {
        public String toString() {
            return "empty";
        }

        @Override
        public String text() {
            return "";
        }
    };

    static PlainTextContents create(String text) {
        return (PlainTextContents) (text.isEmpty() ? PlainTextContents.EMPTY : new PlainTextContents.LiteralContents(text));
    }

    String text();

    @Override
    default MapCodec<PlainTextContents> codec() {
        return PlainTextContents.MAP_CODEC;
    }

    public static record LiteralContents(String text) implements PlainTextContents {

        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
            return output.accept(this.text);
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style currentStyle) {
            return output.accept(currentStyle, this.text);
        }

        public String toString() {
            return "literal{" + this.text + "}";
        }
    }
}
