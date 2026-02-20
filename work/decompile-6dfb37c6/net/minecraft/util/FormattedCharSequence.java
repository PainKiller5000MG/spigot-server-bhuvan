package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.List;
import net.minecraft.network.chat.Style;

@FunctionalInterface
public interface FormattedCharSequence {

    FormattedCharSequence EMPTY = (formattedcharsink) -> {
        return true;
    };

    boolean accept(FormattedCharSink output);

    static FormattedCharSequence codepoint(int codepoint, Style style) {
        return (formattedcharsink) -> {
            return formattedcharsink.accept(0, style, codepoint);
        };
    }

    static FormattedCharSequence forward(String plainText, Style style) {
        return plainText.isEmpty() ? FormattedCharSequence.EMPTY : (formattedcharsink) -> {
            return StringDecomposer.iterate(plainText, style, formattedcharsink);
        };
    }

    static FormattedCharSequence forward(String plainText, Style style, Int2IntFunction modifier) {
        return plainText.isEmpty() ? FormattedCharSequence.EMPTY : (formattedcharsink) -> {
            return StringDecomposer.iterate(plainText, style, decorateOutput(formattedcharsink, modifier));
        };
    }

    static FormattedCharSequence backward(String plainText, Style style) {
        return plainText.isEmpty() ? FormattedCharSequence.EMPTY : (formattedcharsink) -> {
            return StringDecomposer.iterateBackwards(plainText, style, formattedcharsink);
        };
    }

    static FormattedCharSequence backward(String plainText, Style style, Int2IntFunction modifier) {
        return plainText.isEmpty() ? FormattedCharSequence.EMPTY : (formattedcharsink) -> {
            return StringDecomposer.iterateBackwards(plainText, style, decorateOutput(formattedcharsink, modifier));
        };
    }

    static FormattedCharSink decorateOutput(FormattedCharSink output, Int2IntFunction modifier) {
        return (i, style, j) -> {
            return output.accept(i, style, (Integer) modifier.apply(j));
        };
    }

    static FormattedCharSequence composite() {
        return FormattedCharSequence.EMPTY;
    }

    static FormattedCharSequence composite(FormattedCharSequence part) {
        return part;
    }

    static FormattedCharSequence composite(FormattedCharSequence first, FormattedCharSequence second) {
        return fromPair(first, second);
    }

    static FormattedCharSequence composite(FormattedCharSequence... parts) {
        return fromList(ImmutableList.copyOf(parts));
    }

    static FormattedCharSequence composite(List<FormattedCharSequence> parts) {
        int i = parts.size();

        switch (i) {
            case 0:
                return FormattedCharSequence.EMPTY;
            case 1:
                return (FormattedCharSequence) parts.get(0);
            case 2:
                return fromPair((FormattedCharSequence) parts.get(0), (FormattedCharSequence) parts.get(1));
            default:
                return fromList(ImmutableList.copyOf(parts));
        }
    }

    static FormattedCharSequence fromPair(FormattedCharSequence first, FormattedCharSequence second) {
        return (formattedcharsink) -> {
            return first.accept(formattedcharsink) && second.accept(formattedcharsink);
        };
    }

    static FormattedCharSequence fromList(List<FormattedCharSequence> partCopy) {
        return (formattedcharsink) -> {
            for (FormattedCharSequence formattedcharsequence : partCopy) {
                if (!formattedcharsequence.accept(formattedcharsink)) {
                    return false;
                }
            }

            return true;
        };
    }
}
