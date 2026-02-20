package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.CheckReturnValue;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public class ComponentUtils {

    public static final String DEFAULT_SEPARATOR_TEXT = ", ";
    public static final Component DEFAULT_SEPARATOR = Component.literal(", ").withStyle(ChatFormatting.GRAY);
    public static final Component DEFAULT_NO_STYLE_SEPARATOR = Component.literal(", ");

    public ComponentUtils() {}

    @CheckReturnValue
    public static MutableComponent mergeStyles(MutableComponent component, Style style) {
        if (style.isEmpty()) {
            return component;
        } else {
            Style style1 = component.getStyle();

            return style1.isEmpty() ? component.setStyle(style) : (style1.equals(style) ? component : component.setStyle(style1.applyTo(style)));
        }
    }

    @CheckReturnValue
    public static Component mergeStyles(Component component, Style style) {
        if (style.isEmpty()) {
            return component;
        } else {
            Style style1 = component.getStyle();

            return (Component) (style1.isEmpty() ? component.copy().setStyle(style) : (style1.equals(style) ? component : component.copy().setStyle(style1.applyTo(style))));
        }
    }

    public static Optional<MutableComponent> updateForEntity(@Nullable CommandSourceStack source, Optional<Component> component, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        return component.isPresent() ? Optional.of(updateForEntity(source, (Component) component.get(), entity, recursionDepth)) : Optional.empty();
    }

    public static MutableComponent updateForEntity(@Nullable CommandSourceStack source, Component component, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        if (recursionDepth > 100) {
            return component.copy();
        } else {
            MutableComponent mutablecomponent = component.getContents().resolve(source, entity, recursionDepth + 1);

            for (Component component1 : component.getSiblings()) {
                mutablecomponent.append((Component) updateForEntity(source, component1, entity, recursionDepth + 1));
            }

            return mutablecomponent.withStyle(resolveStyle(source, component.getStyle(), entity, recursionDepth));
        }
    }

    private static Style resolveStyle(@Nullable CommandSourceStack source, Style style, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        HoverEvent hoverevent = style.getHoverEvent();

        if (hoverevent instanceof HoverEvent.ShowText hoverevent_showtext) {
            HoverEvent.ShowText hoverevent_showtext1 = hoverevent_showtext;

            try {
                component = hoverevent_showtext1.value();
            } catch (Throwable throwable) {
                throw new MatchException(throwable.toString(), throwable);
            }

            HoverEvent hoverevent1 = component;

            hoverevent1 = new HoverEvent.ShowText(updateForEntity(source, hoverevent1, entity, recursionDepth + 1));
            return style.withHoverEvent(hoverevent1);
        } else {
            return style;
        }
    }

    public static Component formatList(Collection<String> values) {
        return formatAndSortList(values, (s) -> {
            return Component.literal(s).withStyle(ChatFormatting.GREEN);
        });
    }

    public static <T extends Comparable<T>> Component formatAndSortList(Collection<T> values, Function<T, Component> formatter) {
        if (values.isEmpty()) {
            return CommonComponents.EMPTY;
        } else if (values.size() == 1) {
            return (Component) formatter.apply((Comparable) values.iterator().next());
        } else {
            List<T> list = Lists.newArrayList(values);

            list.sort(Comparable::compareTo);
            return formatList(list, formatter);
        }
    }

    public static <T> Component formatList(Collection<? extends T> values, Function<T, Component> formatter) {
        return formatList(values, ComponentUtils.DEFAULT_SEPARATOR, formatter);
    }

    public static <T> MutableComponent formatList(Collection<? extends T> values, Optional<? extends Component> separator, Function<T, Component> formatter) {
        return formatList(values, (Component) DataFixUtils.orElse(separator, ComponentUtils.DEFAULT_SEPARATOR), formatter);
    }

    public static Component formatList(Collection<? extends Component> values, Component separator) {
        return formatList(values, separator, Function.identity());
    }

    public static <T> MutableComponent formatList(Collection<? extends T> values, Component separator, Function<T, Component> formatter) {
        if (values.isEmpty()) {
            return Component.empty();
        } else if (values.size() == 1) {
            return ((Component) formatter.apply(values.iterator().next())).copy();
        } else {
            MutableComponent mutablecomponent = Component.empty();
            boolean flag = true;

            for (T t0 : values) {
                if (!flag) {
                    mutablecomponent.append(separator);
                }

                mutablecomponent.append((Component) formatter.apply(t0));
                flag = false;
            }

            return mutablecomponent;
        }
    }

    public static MutableComponent wrapInSquareBrackets(Component inner) {
        return Component.translatable("chat.square_brackets", inner);
    }

    public static Component fromMessage(Message message) {
        if (message instanceof Component component) {
            return component;
        } else {
            return Component.literal(message.getString());
        }
    }

    public static boolean isTranslationResolvable(@Nullable Component component) {
        if (component != null) {
            ComponentContents componentcontents = component.getContents();

            if (componentcontents instanceof TranslatableContents) {
                TranslatableContents translatablecontents = (TranslatableContents) componentcontents;
                String s = translatablecontents.getKey();
                String s1 = translatablecontents.getFallback();

                return s1 != null || Language.getInstance().has(s);
            }
        }

        return true;
    }

    public static MutableComponent copyOnClickText(String text) {
        return wrapInSquareBrackets(Component.literal(text).withStyle((style) -> {
            return style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.CopyToClipboard(text)).withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click"))).withInsertion(text);
        }));
    }
}
