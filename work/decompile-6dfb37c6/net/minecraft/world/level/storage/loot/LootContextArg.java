package net.minecraft.world.level.storage.loot;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public interface LootContextArg<R> {

    Codec<LootContextArg<Object>> ENTITY_OR_BLOCK = createArgCodec((lootcontextarg_argcodecbuilder) -> {
        return lootcontextarg_argcodecbuilder.anyOf(LootContext.EntityTarget.values()).anyOf(LootContext.BlockEntityTarget.values());
    });

    @Nullable
    R get(LootContext context);

    ContextKey<?> contextParam();

    static <U> LootContextArg<U> cast(LootContextArg<? extends U> original) {
        return original;
    }

    static <R> Codec<LootContextArg<R>> createArgCodec(UnaryOperator<LootContextArg.ArgCodecBuilder<R>> consumer) {
        return ((LootContextArg.ArgCodecBuilder) consumer.apply(new LootContextArg.ArgCodecBuilder())).build();
    }

    public interface Getter<T, R> extends LootContextArg<R> {

        @Nullable
        R get(T value);

        @Override
        ContextKey<? extends T> contextParam();

        @Override
        default @Nullable R get(LootContext context) {
            T t0 = (T) context.getOptionalParameter(this.contextParam());

            return (R) (t0 != null ? this.get(t0) : null);
        }
    }

    public interface SimpleGetter<T> extends LootContextArg<T> {

        @Override
        ContextKey<? extends T> contextParam();

        @Override
        default @Nullable T get(LootContext context) {
            return (T) context.getOptionalParameter(this.contextParam());
        }
    }

    public static final class ArgCodecBuilder<R> {

        private final ExtraCodecs.LateBoundIdMapper<String, LootContextArg<R>> sources = new ExtraCodecs.LateBoundIdMapper<String, LootContextArg<R>>();

        private ArgCodecBuilder() {}

        public <T> LootContextArg.ArgCodecBuilder<R> anyOf(T[] targets, Function<T, String> nameGetter, Function<T, ? extends LootContextArg<R>> argFactory) {
            for (T t0 : targets) {
                this.sources.put((String) nameGetter.apply(t0), (LootContextArg) argFactory.apply(t0));
            }

            return this;
        }

        public <T extends StringRepresentable> LootContextArg.ArgCodecBuilder<R> anyOf(T[] targets, Function<T, ? extends LootContextArg<R>> argFactory) {
            return this.anyOf(targets, StringRepresentable::getSerializedName, argFactory);
        }

        public <T extends StringRepresentable & LootContextArg<? extends R>> LootContextArg.ArgCodecBuilder<R> anyOf(T[] targets) {
            return this.anyOf(targets, (object) -> {
                return LootContextArg.cast((LootContextArg) object);
            });
        }

        public LootContextArg.ArgCodecBuilder<R> anyEntity(Function<? super ContextKey<? extends Entity>, ? extends LootContextArg<R>> function) {
            return this.anyOf(LootContext.EntityTarget.values(), (lootcontext_entitytarget) -> {
                return (LootContextArg) function.apply(lootcontext_entitytarget.contextParam());
            });
        }

        public LootContextArg.ArgCodecBuilder<R> anyBlockEntity(Function<? super ContextKey<? extends BlockEntity>, ? extends LootContextArg<R>> function) {
            return this.anyOf(LootContext.BlockEntityTarget.values(), (lootcontext_blockentitytarget) -> {
                return (LootContextArg) function.apply(lootcontext_blockentitytarget.contextParam());
            });
        }

        public LootContextArg.ArgCodecBuilder<R> anyItemStack(Function<? super ContextKey<? extends ItemStack>, ? extends LootContextArg<R>> function) {
            return this.anyOf(LootContext.ItemStackTarget.values(), (lootcontext_itemstacktarget) -> {
                return (LootContextArg) function.apply(lootcontext_itemstacktarget.contextParam());
            });
        }

        private Codec<LootContextArg<R>> build() {
            return this.sources.codec(Codec.STRING);
        }
    }
}
