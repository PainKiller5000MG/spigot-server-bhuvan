package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.BitSet;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public class FilterMask {

    public static final Codec<FilterMask> CODEC = StringRepresentable.fromEnum(FilterMask.Type::values).dispatch(FilterMask::type, FilterMask.Type::codec);
    public static final FilterMask FULLY_FILTERED = new FilterMask(new BitSet(0), FilterMask.Type.FULLY_FILTERED);
    public static final FilterMask PASS_THROUGH = new FilterMask(new BitSet(0), FilterMask.Type.PASS_THROUGH);
    public static final Style FILTERED_STYLE = Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.filtered")));
    private static final MapCodec<FilterMask> PASS_THROUGH_CODEC = MapCodec.unit(FilterMask.PASS_THROUGH);
    private static final MapCodec<FilterMask> FULLY_FILTERED_CODEC = MapCodec.unit(FilterMask.FULLY_FILTERED);
    private static final MapCodec<FilterMask> PARTIALLY_FILTERED_CODEC = ExtraCodecs.BIT_SET.xmap(FilterMask::new, FilterMask::mask).fieldOf("value");
    private static final char HASH = '#';
    private final BitSet mask;
    private final FilterMask.Type type;

    private FilterMask(BitSet mask, FilterMask.Type type) {
        this.mask = mask;
        this.type = type;
    }

    private FilterMask(BitSet mask) {
        this.mask = mask;
        this.type = FilterMask.Type.PARTIALLY_FILTERED;
    }

    public FilterMask(int length) {
        this(new BitSet(length), FilterMask.Type.PARTIALLY_FILTERED);
    }

    private FilterMask.Type type() {
        return this.type;
    }

    private BitSet mask() {
        return this.mask;
    }

    public static FilterMask read(FriendlyByteBuf input) {
        FilterMask.Type filtermask_type = (FilterMask.Type) input.readEnum(FilterMask.Type.class);
        FilterMask filtermask;

        switch (filtermask_type.ordinal()) {
            case 0:
                filtermask = FilterMask.PASS_THROUGH;
                break;
            case 1:
                filtermask = FilterMask.FULLY_FILTERED;
                break;
            case 2:
                filtermask = new FilterMask(input.readBitSet(), FilterMask.Type.PARTIALLY_FILTERED);
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return filtermask;
    }

    public static void write(FriendlyByteBuf output, FilterMask mask) {
        output.writeEnum(mask.type);
        if (mask.type == FilterMask.Type.PARTIALLY_FILTERED) {
            output.writeBitSet(mask.mask);
        }

    }

    public void setFiltered(int index) {
        this.mask.set(index);
    }

    public @Nullable String apply(String text) {
        String s1;

        switch (this.type.ordinal()) {
            case 0:
                s1 = text;
                break;
            case 1:
                s1 = null;
                break;
            case 2:
                char[] achar = text.toCharArray();

                for (int i = 0; i < achar.length && i < this.mask.length(); ++i) {
                    if (this.mask.get(i)) {
                        achar[i] = '#';
                    }
                }

                s1 = new String(achar);
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return s1;
    }

    public @Nullable Component applyWithFormatting(String text) {
        MutableComponent mutablecomponent;

        switch (this.type.ordinal()) {
            case 0:
                mutablecomponent = Component.literal(text);
                break;
            case 1:
                mutablecomponent = null;
                break;
            case 2:
                MutableComponent mutablecomponent1 = Component.empty();
                int i = 0;
                boolean flag = this.mask.get(0);

                while (true) {
                    int j = flag ? this.mask.nextClearBit(i) : this.mask.nextSetBit(i);

                    j = j < 0 ? text.length() : j;
                    if (j == i) {
                        mutablecomponent = mutablecomponent1;
                        return mutablecomponent;
                    }

                    if (flag) {
                        mutablecomponent1.append((Component) Component.literal(StringUtils.repeat('#', j - i)).withStyle(FilterMask.FILTERED_STYLE));
                    } else {
                        mutablecomponent1.append(text.substring(i, j));
                    }

                    flag = !flag;
                    i = j;
                }
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return mutablecomponent;
    }

    public boolean isEmpty() {
        return this.type == FilterMask.Type.PASS_THROUGH;
    }

    public boolean isFullyFiltered() {
        return this.type == FilterMask.Type.FULLY_FILTERED;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            FilterMask filtermask = (FilterMask) o;

            return this.mask.equals(filtermask.mask) && this.type == filtermask.type;
        } else {
            return false;
        }
    }

    public int hashCode() {
        int i = this.mask.hashCode();

        i = 31 * i + this.type.hashCode();
        return i;
    }

    private static enum Type implements StringRepresentable {

        PASS_THROUGH("pass_through", () -> {
            return FilterMask.PASS_THROUGH_CODEC;
        }), FULLY_FILTERED("fully_filtered", () -> {
            return FilterMask.FULLY_FILTERED_CODEC;
        }), PARTIALLY_FILTERED("partially_filtered", () -> {
            return FilterMask.PARTIALLY_FILTERED_CODEC;
        });

        private final String serializedName;
        private final Supplier<MapCodec<FilterMask>> codec;

        private Type(String serializedName, Supplier<MapCodec<FilterMask>> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }

        private MapCodec<FilterMask> codec() {
            return (MapCodec) this.codec.get();
        }
    }
}
