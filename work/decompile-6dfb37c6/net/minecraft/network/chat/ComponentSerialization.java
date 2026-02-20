package net.minecraft.network.chat;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;

public class ComponentSerialization {

    public static final Codec<Component> CODEC = Codec.recursive("Component", ComponentSerialization::createCodec);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(ComponentSerialization.CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL_STREAM_CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs::optional);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(ComponentSerialization.CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> TRUSTED_OPTIONAL_STREAM_CODEC = ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs::optional);
    public static final StreamCodec<ByteBuf, Component> TRUSTED_CONTEXT_FREE_STREAM_CODEC = ByteBufCodecs.fromCodecTrusted(ComponentSerialization.CODEC);

    public ComponentSerialization() {}

    public static Codec<Component> flatRestrictedCodec(final int maxFlatSizex) {
        return new Codec<Component>() {
            public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
                return ComponentSerialization.CODEC.decode(ops, input).flatMap((pair) -> {
                    return this.isTooLarge(ops, (Component) pair.getFirst()) ? DataResult.error(() -> {
                        return "Component was too large: greater than max size " + maxFlatSizex;
                    }) : DataResult.success(pair);
                });
            }

            public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
                return ComponentSerialization.CODEC.encodeStart(ops, input);
            }

            private <T> boolean isTooLarge(DynamicOps<T> ops, Component input) {
                DataResult<JsonElement> dataresult = ComponentSerialization.CODEC.encodeStart(asJsonOps(ops), input);

                return dataresult.isSuccess() && GsonHelper.encodesLongerThan((JsonElement) dataresult.getOrThrow(), maxFlatSizex);
            }

            private static <T> DynamicOps<JsonElement> asJsonOps(DynamicOps<T> ops) {
                if (ops instanceof RegistryOps<T> registryops) {
                    return registryops.<JsonElement>withParent(JsonOps.INSTANCE);
                } else {
                    return JsonOps.INSTANCE;
                }
            }
        };
    }

    private static MutableComponent createFromList(List<Component> list) {
        MutableComponent mutablecomponent = ((Component) list.get(0)).copy();

        for (int i = 1; i < list.size(); ++i) {
            mutablecomponent.append((Component) list.get(i));
        }

        return mutablecomponent;
    }

    public static <T> MapCodec<T> createLegacyComponentMatcher(ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends T>> types, Function<T, MapCodec<? extends T>> codecGetter, String typeFieldName) {
        MapCodec<T> mapcodec = new ComponentSerialization.FuzzyCodec<T>(types.values(), codecGetter);
        MapCodec<T> mapcodec1 = types.codec(Codec.STRING).dispatchMap(typeFieldName, codecGetter, (mapcodec2) -> {
            return mapcodec2;
        });
        MapCodec<T> mapcodec2 = new ComponentSerialization.StrictEither<T>(typeFieldName, mapcodec1, mapcodec);

        return ExtraCodecs.orCompressed(mapcodec2, mapcodec1);
    }

    private static Codec<Component> createCodec(Codec<Component> topSerializer) {
        ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends ComponentContents>> extracodecs_lateboundidmapper = new ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends ComponentContents>>();

        bootstrap(extracodecs_lateboundidmapper);
        MapCodec<ComponentContents> mapcodec = createLegacyComponentMatcher(extracodecs_lateboundidmapper, ComponentContents::codec, "type");
        Codec<Component> codec1 = RecordCodecBuilder.create((instance) -> {
            return instance.group(mapcodec.forGetter(Component::getContents), ExtraCodecs.nonEmptyList(topSerializer.listOf()).optionalFieldOf("extra", List.of()).forGetter(Component::getSiblings), Style.Serializer.MAP_CODEC.forGetter(Component::getStyle)).apply(instance, MutableComponent::new);
        });

        return Codec.either(Codec.either(Codec.STRING, ExtraCodecs.nonEmptyList(topSerializer.listOf())), codec1).xmap((either) -> {
            return (Component) either.map((either1) -> {
                return (Component) either1.map(Component::literal, ComponentSerialization::createFromList);
            }, (component) -> {
                return component;
            });
        }, (component) -> {
            String s = component.tryCollapseToString();

            return s != null ? Either.left(Either.left(s)) : Either.right(component);
        });
    }

    private static void bootstrap(ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends ComponentContents>> contentTypes) {
        contentTypes.put("text", PlainTextContents.MAP_CODEC);
        contentTypes.put("translatable", TranslatableContents.MAP_CODEC);
        contentTypes.put("keybind", KeybindContents.MAP_CODEC);
        contentTypes.put("score", ScoreContents.MAP_CODEC);
        contentTypes.put("selector", SelectorContents.MAP_CODEC);
        contentTypes.put("nbt", NbtContents.MAP_CODEC);
        contentTypes.put("object", ObjectContents.MAP_CODEC);
    }

    private static class StrictEither<T> extends MapCodec<T> {

        private final String typeFieldName;
        private final MapCodec<T> typed;
        private final MapCodec<T> fuzzy;

        public StrictEither(String typeFieldName, MapCodec<T> typed, MapCodec<T> fuzzy) {
            this.typeFieldName = typeFieldName;
            this.typed = typed;
            this.fuzzy = fuzzy;
        }

        public <O> DataResult<T> decode(DynamicOps<O> ops, MapLike<O> input) {
            return input.get(this.typeFieldName) != null ? this.typed.decode(ops, input) : this.fuzzy.decode(ops, input);
        }

        public <O> RecordBuilder<O> encode(T input, DynamicOps<O> ops, RecordBuilder<O> prefix) {
            return this.fuzzy.encode(input, ops, prefix);
        }

        public <T1> Stream<T1> keys(DynamicOps<T1> ops) {
            return Stream.concat(this.typed.keys(ops), this.fuzzy.keys(ops)).distinct();
        }
    }

    private static class FuzzyCodec<T> extends MapCodec<T> {

        private final Collection<MapCodec<? extends T>> codecs;
        private final Function<T, ? extends MapEncoder<? extends T>> encoderGetter;

        public FuzzyCodec(Collection<MapCodec<? extends T>> codecs, Function<T, ? extends MapEncoder<? extends T>> encoderGetter) {
            this.codecs = codecs;
            this.encoderGetter = encoderGetter;
        }

        public <S> DataResult<T> decode(DynamicOps<S> ops, MapLike<S> input) {
            for (MapDecoder<? extends T> mapdecoder : this.codecs) {
                DataResult<? extends T> dataresult = mapdecoder.decode(ops, input);

                if (dataresult.result().isPresent()) {
                    return dataresult;
                }
            }

            return DataResult.error(() -> {
                return "No matching codec found";
            });
        }

        public <S> RecordBuilder<S> encode(T input, DynamicOps<S> ops, RecordBuilder<S> prefix) {
            MapEncoder<T> mapencoder = (MapEncoder) this.encoderGetter.apply(input);

            return mapencoder.encode(input, ops, prefix);
        }

        public <S> Stream<S> keys(DynamicOps<S> ops) {
            return this.codecs.stream().flatMap((mapcodec) -> {
                return mapcodec.keys(ops);
            }).distinct();
        }

        public String toString() {
            return "FuzzyCodec[" + String.valueOf(this.codecs) + "]";
        }
    }
}
