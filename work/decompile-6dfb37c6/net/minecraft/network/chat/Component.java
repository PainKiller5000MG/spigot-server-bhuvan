package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.datafixers.util.Either;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.arguments.selector.SelectorPattern;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.data.DataSource;
import net.minecraft.network.chat.contents.objects.ObjectInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public interface Component extends Message, FormattedText {

    Style getStyle();

    ComponentContents getContents();

    @Override
    default String getString() {
        return FormattedText.super.getString();
    }

    default String getString(int limit) {
        StringBuilder stringbuilder = new StringBuilder();

        this.visit((s) -> {
            int j = limit - stringbuilder.length();

            if (j <= 0) {
                return Component.STOP_ITERATION;
            } else {
                stringbuilder.append(s.length() <= j ? s : s.substring(0, j));
                return Optional.empty();
            }
        });
        return stringbuilder.toString();
    }

    List<Component> getSiblings();

    default @Nullable String tryCollapseToString() {
        ComponentContents componentcontents = this.getContents();

        if (componentcontents instanceof PlainTextContents plaintextcontents) {
            if (this.getSiblings().isEmpty() && this.getStyle().isEmpty()) {
                return plaintextcontents.text();
            }
        }

        return null;
    }

    default MutableComponent plainCopy() {
        return MutableComponent.create(this.getContents());
    }

    default MutableComponent copy() {
        return new MutableComponent(this.getContents(), new ArrayList(this.getSiblings()), this.getStyle());
    }

    FormattedCharSequence getVisualOrderText();

    @Override
    default <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style parentStyle) {
        Style style1 = this.getStyle().applyTo(parentStyle);
        Optional<T> optional = this.getContents().<T>visit(output, style1);

        if (optional.isPresent()) {
            return optional;
        } else {
            for (Component component : this.getSiblings()) {
                Optional<T> optional1 = component.visit(output, style1);

                if (optional1.isPresent()) {
                    return optional1;
                }
            }

            return Optional.empty();
        }
    }

    @Override
    default <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
        Optional<T> optional = this.getContents().<T>visit(output);

        if (optional.isPresent()) {
            return optional;
        } else {
            for (Component component : this.getSiblings()) {
                Optional<T> optional1 = component.visit(output);

                if (optional1.isPresent()) {
                    return optional1;
                }
            }

            return Optional.empty();
        }
    }

    default List<Component> toFlatList() {
        return this.toFlatList(Style.EMPTY);
    }

    default List<Component> toFlatList(Style rootStyle) {
        List<Component> list = Lists.newArrayList();

        this.visit((style1, s) -> {
            if (!s.isEmpty()) {
                list.add(literal(s).withStyle(style1));
            }

            return Optional.empty();
        }, rootStyle);
        return list;
    }

    default boolean contains(Component other) {
        if (this.equals(other)) {
            return true;
        } else {
            List<Component> list = this.toFlatList();
            List<Component> list1 = other.toFlatList(this.getStyle());

            return Collections.indexOfSubList(list, list1) != -1;
        }
    }

    static Component nullToEmpty(@Nullable String text) {
        return (Component) (text != null ? literal(text) : CommonComponents.EMPTY);
    }

    static MutableComponent literal(String text) {
        return MutableComponent.create(PlainTextContents.create(text));
    }

    static MutableComponent translatable(String key) {
        return MutableComponent.create(new TranslatableContents(key, (String) null, TranslatableContents.NO_ARGS));
    }

    static MutableComponent translatable(String key, Object... args) {
        return MutableComponent.create(new TranslatableContents(key, (String) null, args));
    }

    static MutableComponent translatableEscape(String key, Object... args) {
        for (int i = 0; i < args.length; ++i) {
            Object object = args[i];

            if (!TranslatableContents.isAllowedPrimitiveArgument(object) && !(object instanceof Component)) {
                args[i] = String.valueOf(object);
            }
        }

        return translatable(key, args);
    }

    static MutableComponent translatableWithFallback(String key, @Nullable String fallback) {
        return MutableComponent.create(new TranslatableContents(key, fallback, TranslatableContents.NO_ARGS));
    }

    static MutableComponent translatableWithFallback(String key, @Nullable String fallback, Object... args) {
        return MutableComponent.create(new TranslatableContents(key, fallback, args));
    }

    static MutableComponent empty() {
        return MutableComponent.create(PlainTextContents.EMPTY);
    }

    static MutableComponent keybind(String name) {
        return MutableComponent.create(new KeybindContents(name));
    }

    static MutableComponent nbt(String nbtPath, boolean interpreting, Optional<Component> separator, DataSource dataSource) {
        return MutableComponent.create(new NbtContents(nbtPath, interpreting, separator, dataSource));
    }

    static MutableComponent score(SelectorPattern pattern, String objective) {
        return MutableComponent.create(new ScoreContents(Either.left(pattern), objective));
    }

    static MutableComponent score(String name, String objective) {
        return MutableComponent.create(new ScoreContents(Either.right(name), objective));
    }

    static MutableComponent selector(SelectorPattern pattern, Optional<Component> separator) {
        return MutableComponent.create(new SelectorContents(pattern, separator));
    }

    static MutableComponent object(ObjectInfo info) {
        return MutableComponent.create(new ObjectContents(info));
    }

    static Component translationArg(Date date) {
        return literal(date.toString());
    }

    static Component translationArg(Message message) {
        Object object;

        if (message instanceof Component component) {
            object = component;
        } else {
            object = literal(message.getString());
        }

        return (Component) object;
    }

    static Component translationArg(UUID uuid) {
        return literal(uuid.toString());
    }

    static Component translationArg(Identifier id) {
        return literal(id.toString());
    }

    static Component translationArg(ChunkPos chunkPos) {
        return literal(chunkPos.toString());
    }

    static Component translationArg(URI uri) {
        return literal(uri.toString());
    }
}
