package net.minecraft.network.chat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

public class SubStringSource {

    private final String plainText;
    private final List<Style> charStyles;
    private final Int2IntFunction reverseCharModifier;

    private SubStringSource(String plainText, List<Style> charStyles, Int2IntFunction reverseCharModifier) {
        this.plainText = plainText;
        this.charStyles = ImmutableList.copyOf(charStyles);
        this.reverseCharModifier = reverseCharModifier;
    }

    public String getPlainText() {
        return this.plainText;
    }

    public List<FormattedCharSequence> substring(int start, int length, boolean reverse) {
        if (length == 0) {
            return ImmutableList.of();
        } else {
            List<FormattedCharSequence> list = Lists.newArrayList();
            Style style = (Style) this.charStyles.get(start);
            int k = start;

            for (int l = 1; l < length; ++l) {
                int i1 = start + l;
                Style style1 = (Style) this.charStyles.get(i1);

                if (!style1.equals(style)) {
                    String s = this.plainText.substring(k, i1);

                    list.add(reverse ? FormattedCharSequence.backward(s, style, this.reverseCharModifier) : FormattedCharSequence.forward(s, style));
                    style = style1;
                    k = i1;
                }
            }

            if (k < start + length) {
                String s1 = this.plainText.substring(k, start + length);

                list.add(reverse ? FormattedCharSequence.backward(s1, style, this.reverseCharModifier) : FormattedCharSequence.forward(s1, style));
            }

            return reverse ? Lists.reverse(list) : list;
        }
    }

    public static SubStringSource create(FormattedText text) {
        return create(text, (i) -> {
            return i;
        }, (s) -> {
            return s;
        });
    }

    public static SubStringSource create(FormattedText text, Int2IntFunction reverseCharModifier, UnaryOperator<String> shaper) {
        StringBuilder stringbuilder = new StringBuilder();
        List<Style> list = Lists.newArrayList();

        text.visit((style, s) -> {
            StringDecomposer.iterateFormatted(s, style, (i, style1, j) -> {
                stringbuilder.appendCodePoint(j);
                int k = Character.charCount(j);

                for (int l = 0; l < k; ++l) {
                    list.add(style1);
                }

                return true;
            });
            return Optional.empty();
        }, Style.EMPTY);
        return new SubStringSource((String) shaper.apply(stringbuilder.toString()), list, reverseCharModifier);
    }
}
