package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public final class MutableComponent implements Component {

    private final ComponentContents contents;
    private final List<Component> siblings;
    private Style style;
    private FormattedCharSequence visualOrderText;
    private @Nullable Language decomposedWith;

    MutableComponent(ComponentContents contents, List<Component> siblings, Style style) {
        this.visualOrderText = FormattedCharSequence.EMPTY;
        this.contents = contents;
        this.siblings = siblings;
        this.style = style;
    }

    public static MutableComponent create(ComponentContents contents) {
        return new MutableComponent(contents, Lists.newArrayList(), Style.EMPTY);
    }

    @Override
    public ComponentContents getContents() {
        return this.contents;
    }

    @Override
    public List<Component> getSiblings() {
        return this.siblings;
    }

    public MutableComponent setStyle(Style style) {
        this.style = style;
        return this;
    }

    @Override
    public Style getStyle() {
        return this.style;
    }

    public MutableComponent append(String text) {
        return text.isEmpty() ? this : this.append((Component) Component.literal(text));
    }

    public MutableComponent append(Component component) {
        this.siblings.add(component);
        return this;
    }

    public MutableComponent withStyle(UnaryOperator<Style> updater) {
        this.setStyle((Style) updater.apply(this.getStyle()));
        return this;
    }

    public MutableComponent withStyle(Style patch) {
        this.setStyle(patch.applyTo(this.getStyle()));
        return this;
    }

    public MutableComponent withStyle(ChatFormatting... formats) {
        this.setStyle(this.getStyle().applyFormats(formats));
        return this;
    }

    public MutableComponent withStyle(ChatFormatting format) {
        this.setStyle(this.getStyle().applyFormat(format));
        return this;
    }

    public MutableComponent withColor(int color) {
        this.setStyle(this.getStyle().withColor(color));
        return this;
    }

    public MutableComponent withoutShadow() {
        this.setStyle(this.getStyle().withoutShadow());
        return this;
    }

    @Override
    public FormattedCharSequence getVisualOrderText() {
        Language language = Language.getInstance();

        if (this.decomposedWith != language) {
            this.visualOrderText = language.getVisualOrder(this);
            this.decomposedWith = language;
        }

        return this.visualOrderText;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            boolean flag;

            if (o instanceof MutableComponent) {
                MutableComponent mutablecomponent = (MutableComponent) o;

                if (this.contents.equals(mutablecomponent.contents) && this.style.equals(mutablecomponent.style) && this.siblings.equals(mutablecomponent.siblings)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        int i = 1;

        i = 31 * i + this.contents.hashCode();
        i = 31 * i + this.style.hashCode();
        i = 31 * i + this.siblings.hashCode();
        return i;
    }

    public String toString() {
        StringBuilder stringbuilder = new StringBuilder(this.contents.toString());
        boolean flag = !this.style.isEmpty();
        boolean flag1 = !this.siblings.isEmpty();

        if (flag || flag1) {
            stringbuilder.append('[');
            if (flag) {
                stringbuilder.append("style=");
                stringbuilder.append(this.style);
            }

            if (flag && flag1) {
                stringbuilder.append(", ");
            }

            if (flag1) {
                stringbuilder.append("siblings=");
                stringbuilder.append(this.siblings);
            }

            stringbuilder.append(']');
        }

        return stringbuilder.toString();
    }
}
