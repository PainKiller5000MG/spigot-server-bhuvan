package net.minecraft.network.chat;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Unit;

public interface FormattedText {

    Optional<Unit> STOP_ITERATION = Optional.of(Unit.INSTANCE);
    FormattedText EMPTY = new FormattedText() {
        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style parentStyle) {
            return Optional.empty();
        }
    };

    <T> Optional<T> visit(FormattedText.ContentConsumer<T> output);

    <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style parentStyle);

    static FormattedText of(final String text) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
                return output.accept(text);
            }

            @Override
            public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style parentStyle) {
                return output.accept(parentStyle, text);
            }
        };
    }

    static FormattedText of(final String text, final Style style) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
                return output.accept(text);
            }

            @Override
            public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style parentStyle) {
                return output.accept(style.applyTo(parentStyle), text);
            }
        };
    }

    static FormattedText composite(FormattedText... parts) {
        return composite((List) ImmutableList.copyOf(parts));
    }

    static FormattedText composite(final List<? extends FormattedText> parts) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
                for (FormattedText formattedtext : parts) {
                    Optional<T> optional = formattedtext.<T>visit(output);

                    if (optional.isPresent()) {
                        return optional;
                    }
                }

                return Optional.empty();
            }

            @Override
            public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style parentStyle) {
                for (FormattedText formattedtext : parts) {
                    Optional<T> optional = formattedtext.<T>visit(output, parentStyle);

                    if (optional.isPresent()) {
                        return optional;
                    }
                }

                return Optional.empty();
            }
        };
    }

    default String getString() {
        StringBuilder stringbuilder = new StringBuilder();

        this.visit((s) -> {
            stringbuilder.append(s);
            return Optional.empty();
        });
        return stringbuilder.toString();
    }

    public interface ContentConsumer<T> {

        Optional<T> accept(String contents);
    }

    public interface StyledContentConsumer<T> {

        Optional<T> accept(Style style, String contents);
    }
}
