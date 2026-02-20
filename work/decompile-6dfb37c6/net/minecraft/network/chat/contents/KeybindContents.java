package net.minecraft.network.chat.contents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

public class KeybindContents implements ComponentContents {

    public static final MapCodec<KeybindContents> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.STRING.fieldOf("keybind").forGetter((keybindcontents) -> {
            return keybindcontents.name;
        })).apply(instance, KeybindContents::new);
    });
    private final String name;
    private @Nullable Supplier<Component> nameResolver;

    public KeybindContents(String name) {
        this.name = name;
    }

    private Component getNestedComponent() {
        if (this.nameResolver == null) {
            this.nameResolver = (Supplier) KeybindResolver.keyResolver.apply(this.name);
        }

        return (Component) this.nameResolver.get();
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
        return this.getNestedComponent().visit(output);
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style currentStyle) {
        return this.getNestedComponent().visit(output, currentStyle);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            boolean flag;

            if (o instanceof KeybindContents) {
                KeybindContents keybindcontents = (KeybindContents) o;

                if (this.name.equals(keybindcontents.name)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.name.hashCode();
    }

    public String toString() {
        return "keybind{" + this.name + "}";
    }

    public String getName() {
        return this.name;
    }

    @Override
    public MapCodec<KeybindContents> codec() {
        return KeybindContents.MAP_CODEC;
    }
}
