package net.minecraft.network.codec;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.util.Mth;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

public interface ByteBufCodecs {

    int MAX_INITIAL_COLLECTION_SIZE = 65536;
    StreamCodec<ByteBuf, Boolean> BOOL = new StreamCodec<ByteBuf, Boolean>() {
        public Boolean decode(ByteBuf input) {
            return input.readBoolean();
        }

        public void encode(ByteBuf output, Boolean value) {
            output.writeBoolean(value);
        }
    };
    StreamCodec<ByteBuf, Byte> BYTE = new StreamCodec<ByteBuf, Byte>() {
        public Byte decode(ByteBuf input) {
            return input.readByte();
        }

        public void encode(ByteBuf output, Byte value) {
            output.writeByte(value);
        }
    };
    StreamCodec<ByteBuf, Float> ROTATION_BYTE = ByteBufCodecs.BYTE.map(Mth::unpackDegrees, Mth::packDegrees);
    StreamCodec<ByteBuf, Short> SHORT = new StreamCodec<ByteBuf, Short>() {
        public Short decode(ByteBuf input) {
            return input.readShort();
        }

        public void encode(ByteBuf output, Short value) {
            output.writeShort(value);
        }
    };
    StreamCodec<ByteBuf, Integer> UNSIGNED_SHORT = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf input) {
            return input.readUnsignedShort();
        }

        public void encode(ByteBuf output, Integer value) {
            output.writeShort(value);
        }
    };
    StreamCodec<ByteBuf, Integer> INT = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf input) {
            return input.readInt();
        }

        public void encode(ByteBuf output, Integer value) {
            output.writeInt(value);
        }
    };
    StreamCodec<ByteBuf, Integer> VAR_INT = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf input) {
            return VarInt.read(input);
        }

        public void encode(ByteBuf output, Integer value) {
            VarInt.write(output, value);
        }
    };
    StreamCodec<ByteBuf, OptionalInt> OPTIONAL_VAR_INT = ByteBufCodecs.VAR_INT.map((integer) -> {
        return integer == 0 ? OptionalInt.empty() : OptionalInt.of(integer - 1);
    }, (optionalint) -> {
        return optionalint.isPresent() ? optionalint.getAsInt() + 1 : 0;
    });
    StreamCodec<ByteBuf, Long> LONG = new StreamCodec<ByteBuf, Long>() {
        public Long decode(ByteBuf input) {
            return input.readLong();
        }

        public void encode(ByteBuf output, Long value) {
            output.writeLong(value);
        }
    };
    StreamCodec<ByteBuf, Long> VAR_LONG = new StreamCodec<ByteBuf, Long>() {
        public Long decode(ByteBuf input) {
            return VarLong.read(input);
        }

        public void encode(ByteBuf output, Long value) {
            VarLong.write(output, value);
        }
    };
    StreamCodec<ByteBuf, Float> FLOAT = new StreamCodec<ByteBuf, Float>() {
        public Float decode(ByteBuf input) {
            return input.readFloat();
        }

        public void encode(ByteBuf output, Float value) {
            output.writeFloat(value);
        }
    };
    StreamCodec<ByteBuf, Double> DOUBLE = new StreamCodec<ByteBuf, Double>() {
        public Double decode(ByteBuf input) {
            return input.readDouble();
        }

        public void encode(ByteBuf output, Double value) {
            output.writeDouble(value);
        }
    };
    StreamCodec<ByteBuf, byte[]> BYTE_ARRAY = new StreamCodec<ByteBuf, byte[]>() {
        public byte[] decode(ByteBuf input) {
            return FriendlyByteBuf.readByteArray(input);
        }

        public void encode(ByteBuf output, byte[] value) {
            FriendlyByteBuf.writeByteArray(output, value);
        }
    };
    StreamCodec<ByteBuf, long[]> LONG_ARRAY = new StreamCodec<ByteBuf, long[]>() {
        public long[] decode(ByteBuf input) {
            return FriendlyByteBuf.readLongArray(input);
        }

        public void encode(ByteBuf output, long[] value) {
            FriendlyByteBuf.writeLongArray(output, value);
        }
    };
    StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    StreamCodec<ByteBuf, Tag> TAG = tagCodec(NbtAccounter::defaultQuota);
    StreamCodec<ByteBuf, Tag> TRUSTED_TAG = tagCodec(NbtAccounter::unlimitedHeap);
    StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = compoundTagCodec(NbtAccounter::defaultQuota);
    StreamCodec<ByteBuf, CompoundTag> TRUSTED_COMPOUND_TAG = compoundTagCodec(NbtAccounter::unlimitedHeap);
    StreamCodec<ByteBuf, Optional<CompoundTag>> OPTIONAL_COMPOUND_TAG = new StreamCodec<ByteBuf, Optional<CompoundTag>>() {
        public Optional<CompoundTag> decode(ByteBuf input) {
            return Optional.ofNullable(FriendlyByteBuf.readNbt(input));
        }

        public void encode(ByteBuf output, Optional<CompoundTag> value) {
            FriendlyByteBuf.writeNbt(output, (Tag) value.orElse((Object) null));
        }
    };
    StreamCodec<ByteBuf, Vector3fc> VECTOR3F = new StreamCodec<ByteBuf, Vector3fc>() {
        public Vector3fc decode(ByteBuf input) {
            return FriendlyByteBuf.readVector3f(input);
        }

        public void encode(ByteBuf output, Vector3fc value) {
            FriendlyByteBuf.writeVector3f(output, value);
        }
    };
    StreamCodec<ByteBuf, Quaternionfc> QUATERNIONF = new StreamCodec<ByteBuf, Quaternionfc>() {
        public Quaternionfc decode(ByteBuf input) {
            return FriendlyByteBuf.readQuaternion(input);
        }

        public void encode(ByteBuf output, Quaternionfc value) {
            FriendlyByteBuf.writeQuaternion(output, value);
        }
    };
    StreamCodec<ByteBuf, Integer> CONTAINER_ID = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf input) {
            return FriendlyByteBuf.readContainerId(input);
        }

        public void encode(ByteBuf output, Integer value) {
            FriendlyByteBuf.writeContainerId(output, value);
        }
    };
    StreamCodec<ByteBuf, PropertyMap> GAME_PROFILE_PROPERTIES = new StreamCodec<ByteBuf, PropertyMap>() {
        public PropertyMap decode(ByteBuf input) {
            int i = ByteBufCodecs.readCount(input, 16);
            ImmutableMultimap.Builder<String, Property> immutablemultimap_builder = ImmutableMultimap.builder();

            for (int j = 0; j < i; ++j) {
                String s = Utf8String.read(input, 64);
                String s1 = Utf8String.read(input, 32767);
                String s2 = (String) FriendlyByteBuf.readNullable(input, (bytebuf1) -> {
                    return Utf8String.read(bytebuf1, 1024);
                });
                Property property = new Property(s, s1, s2);

                immutablemultimap_builder.put(property.name(), property);
            }

            return new PropertyMap(immutablemultimap_builder.build());
        }

        public void encode(ByteBuf output, PropertyMap properties) {
            ByteBufCodecs.writeCount(output, properties.size(), 16);

            for (Property property : properties.values()) {
                Utf8String.write(output, property.name(), 64);
                Utf8String.write(output, property.value(), 32767);
                FriendlyByteBuf.writeNullable(output, property.signature(), (bytebuf1, s) -> {
                    Utf8String.write(bytebuf1, s, 1024);
                });
            }

        }
    };
    StreamCodec<ByteBuf, String> PLAYER_NAME = stringUtf8(16);
    StreamCodec<ByteBuf, GameProfile> GAME_PROFILE = StreamCodec.composite(UUIDUtil.STREAM_CODEC, GameProfile::id, ByteBufCodecs.PLAYER_NAME, GameProfile::name, ByteBufCodecs.GAME_PROFILE_PROPERTIES, GameProfile::properties, GameProfile::new);
    StreamCodec<ByteBuf, Integer> RGB_COLOR = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf input) {
            return ARGB.color(input.readByte() & 255, input.readByte() & 255, input.readByte() & 255);
        }

        public void encode(ByteBuf output, Integer value) {
            output.writeByte(ARGB.red(value));
            output.writeByte(ARGB.green(value));
            output.writeByte(ARGB.blue(value));
        }
    };

    static StreamCodec<ByteBuf, byte[]> byteArray(final int maxSize) {
        return new StreamCodec<ByteBuf, byte[]>() {
            public byte[] decode(ByteBuf input) {
                return FriendlyByteBuf.readByteArray(input, maxSize);
            }

            public void encode(ByteBuf output, byte[] value) {
                if (value.length > maxSize) {
                    throw new EncoderException("ByteArray with size " + value.length + " is bigger than allowed " + maxSize);
                } else {
                    FriendlyByteBuf.writeByteArray(output, value);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, String> stringUtf8(final int maxStringLength) {
        return new StreamCodec<ByteBuf, String>() {
            public String decode(ByteBuf input) {
                return Utf8String.read(input, maxStringLength);
            }

            public void encode(ByteBuf output, String value) {
                Utf8String.write(output, value, maxStringLength);
            }
        };
    }

    static StreamCodec<ByteBuf, Optional<Tag>> optionalTagCodec(final Supplier<NbtAccounter> accounter) {
        return new StreamCodec<ByteBuf, Optional<Tag>>() {
            public Optional<Tag> decode(ByteBuf input) {
                return Optional.ofNullable(FriendlyByteBuf.readNbt(input, (NbtAccounter) accounter.get()));
            }

            public void encode(ByteBuf output, Optional<Tag> value) {
                FriendlyByteBuf.writeNbt(output, (Tag) value.orElse((Object) null));
            }
        };
    }

    static StreamCodec<ByteBuf, Tag> tagCodec(final Supplier<NbtAccounter> accounter) {
        return new StreamCodec<ByteBuf, Tag>() {
            public Tag decode(ByteBuf input) {
                Tag tag = FriendlyByteBuf.readNbt(input, (NbtAccounter) accounter.get());

                if (tag == null) {
                    throw new DecoderException("Expected non-null compound tag");
                } else {
                    return tag;
                }
            }

            public void encode(ByteBuf output, Tag value) {
                if (value == EndTag.INSTANCE) {
                    throw new EncoderException("Expected non-null compound tag");
                } else {
                    FriendlyByteBuf.writeNbt(output, value);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, CompoundTag> compoundTagCodec(Supplier<NbtAccounter> accounter) {
        return tagCodec(accounter).map((tag) -> {
            if (tag instanceof CompoundTag compoundtag) {
                return compoundtag;
            } else {
                throw new DecoderException("Not a compound tag: " + String.valueOf(tag));
            }
        }, (compoundtag) -> {
            return compoundtag;
        });
    }

    static <T> StreamCodec<ByteBuf, T> fromCodecTrusted(Codec<T> codec) {
        return fromCodec(codec, NbtAccounter::unlimitedHeap);
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec) {
        return fromCodec(codec, NbtAccounter::defaultQuota);
    }

    static <T, B extends ByteBuf, V> StreamCodec.CodecOperation<B, T, V> fromCodec(DynamicOps<T> ops, Codec<V> codec) {
        return (streamcodec) -> {
            return new StreamCodec<B, V>() {
                public V decode(B input) {
                    T t0 = (T) streamcodec.decode(input);

                    return (V) codec.parse(ops, t0).getOrThrow((s) -> {
                        return new DecoderException("Failed to decode: " + s + " " + String.valueOf(t0));
                    });
                }

                public void encode(B output, V value) {
                    T t0 = (T) codec.encodeStart(ops, value).getOrThrow((s) -> {
                        return new EncoderException("Failed to encode: " + s + " " + String.valueOf(value));
                    });

                    streamcodec.encode(output, t0);
                }
            };
        };
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec, Supplier<NbtAccounter> accounter) {
        return tagCodec(accounter).apply(fromCodec(NbtOps.INSTANCE, codec));
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistriesTrusted(Codec<T> codec) {
        return fromCodecWithRegistries(codec, NbtAccounter::unlimitedHeap);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(Codec<T> codec) {
        return fromCodecWithRegistries(codec, NbtAccounter::defaultQuota);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(final Codec<T> codec, Supplier<NbtAccounter> accounter) {
        final StreamCodec<ByteBuf, Tag> streamcodec = tagCodec(accounter);

        return new StreamCodec<RegistryFriendlyByteBuf, T>() {
            public T decode(RegistryFriendlyByteBuf input) {
                Tag tag = (Tag) streamcodec.decode(input);
                RegistryOps<Tag> registryops = input.registryAccess().<Tag>createSerializationContext(NbtOps.INSTANCE);

                return (T) codec.parse(registryops, tag).getOrThrow((s) -> {
                    return new DecoderException("Failed to decode: " + s + " " + String.valueOf(tag));
                });
            }

            public void encode(RegistryFriendlyByteBuf output, T value) {
                RegistryOps<Tag> registryops = output.registryAccess().<Tag>createSerializationContext(NbtOps.INSTANCE);
                Tag tag = (Tag) codec.encodeStart(registryops, value).getOrThrow((s) -> {
                    return new EncoderException("Failed to encode: " + s + " " + String.valueOf(value));
                });

                streamcodec.encode(output, tag);
            }
        };
    }

    static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(final StreamCodec<? super B, V> original) {
        return new StreamCodec<B, Optional<V>>() {
            public Optional<V> decode(B input) {
                return input.readBoolean() ? Optional.of(original.decode(input)) : Optional.empty();
            }

            public void encode(B output, Optional<V> value) {
                if (value.isPresent()) {
                    output.writeBoolean(true);
                    original.encode(output, value.get());
                } else {
                    output.writeBoolean(false);
                }

            }
        };
    }

    static int readCount(ByteBuf input, int maxSize) {
        int j = VarInt.read(input);

        if (j > maxSize) {
            throw new DecoderException(j + " elements exceeded max size of: " + maxSize);
        } else {
            return j;
        }
    }

    static void writeCount(ByteBuf output, int count, int maxSize) {
        if (count > maxSize) {
            throw new EncoderException(count + " elements exceeded max size of: " + maxSize);
        } else {
            VarInt.write(output, count);
        }
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> constructor, StreamCodec<? super B, V> elementCodec) {
        return collection(constructor, elementCodec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(final IntFunction<C> constructor, final StreamCodec<? super B, V> elementCodec, final int maxSize) {
        return new StreamCodec<B, C>() {
            public C decode(B input) {
                int j = ByteBufCodecs.readCount(input, maxSize);
                C c0 = (C) ((Collection) constructor.apply(Math.min(j, 65536)));

                for (int k = 0; k < j; ++k) {
                    c0.add(elementCodec.decode(input));
                }

                return c0;
            }

            public void encode(B output, C value) {
                ByteBufCodecs.writeCount(output, value.size(), maxSize);

                for (V v0 : value) {
                    elementCodec.encode(output, v0);
                }

            }
        };
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec.CodecOperation<B, V, C> collection(IntFunction<C> constructor) {
        return (streamcodec) -> {
            return collection(constructor, streamcodec);
        };
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return (streamcodec) -> {
            return collection(ArrayList::new, streamcodec);
        };
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int maxSize) {
        return (streamcodec) -> {
            return collection(ArrayList::new, streamcodec, maxSize);
        };
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(IntFunction<? extends M> constructor, StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec) {
        return map(constructor, keyCodec, valueCodec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(final IntFunction<? extends M> constructor, final StreamCodec<? super B, K> keyCodec, final StreamCodec<? super B, V> valueCodec, final int maxSize) {
        return new StreamCodec<B, M>() {
            public void encode(B output, M map) {
                ByteBufCodecs.writeCount(output, map.size(), maxSize);
                map.forEach((object, object1) -> {
                    keyCodec.encode(output, object);
                    valueCodec.encode(output, object1);
                });
            }

            public M decode(B input) {
                int j = ByteBufCodecs.readCount(input, maxSize);
                M m0 = (M) ((Map) constructor.apply(Math.min(j, 65536)));

                for (int k = 0; k < j; ++k) {
                    K k0 = (K) keyCodec.decode(input);
                    V v0 = (V) valueCodec.decode(input);

                    m0.put(k0, v0);
                }

                return m0;
            }
        };
    }

    static <B extends ByteBuf, L, R> StreamCodec<B, Either<L, R>> either(final StreamCodec<? super B, L> leftCodec, final StreamCodec<? super B, R> rightCodec) {
        return new StreamCodec<B, Either<L, R>>() {
            public Either<L, R> decode(B input) {
                return input.readBoolean() ? Either.left(leftCodec.decode(input)) : Either.right(rightCodec.decode(input));
            }

            public void encode(B output, Either<L, R> value) {
                value.ifLeft((object) -> {
                    output.writeBoolean(true);
                    leftCodec.encode(output, object);
                }).ifRight((object) -> {
                    output.writeBoolean(false);
                    rightCodec.encode(output, object);
                });
            }
        };
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, V> lengthPrefixed(int maxSize, BiFunction<B, ByteBuf, B> decorator) {
        return (streamcodec) -> {
            return new StreamCodec<B, V>() {
                public V decode(B input) {
                    int j = VarInt.read(input);

                    if (j > maxSize) {
                        throw new DecoderException("Buffer size " + j + " is larger than allowed limit of " + maxSize);
                    } else {
                        int k = input.readerIndex();
                        B b1 = (B) ((ByteBuf) decorator.apply(input, input.slice(k, j)));

                        input.readerIndex(k + j);
                        return (V) streamcodec.decode(b1);
                    }
                }

                public void encode(B output, V value) {
                    B b1 = (B) ((ByteBuf) decorator.apply(output, output.alloc().buffer()));

                    try {
                        streamcodec.encode(b1, value);
                        int j = b1.readableBytes();

                        if (j > maxSize) {
                            throw new EncoderException("Buffer size " + j + " is  larger than allowed limit of " + maxSize);
                        }

                        VarInt.write(output, j);
                        output.writeBytes(b1);
                    } finally {
                        b1.release();
                    }

                }
            };
        };
    }

    static <V> StreamCodec.CodecOperation<ByteBuf, V, V> lengthPrefixed(int maxSize) {
        return lengthPrefixed(maxSize, (bytebuf, bytebuf1) -> {
            return bytebuf1;
        });
    }

    static <V> StreamCodec.CodecOperation<RegistryFriendlyByteBuf, V, V> registryFriendlyLengthPrefixed(int maxSize) {
        return lengthPrefixed(maxSize, (registryfriendlybytebuf, bytebuf) -> {
            return new RegistryFriendlyByteBuf(bytebuf, registryfriendlybytebuf.registryAccess());
        });
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(final IntFunction<T> byId, final ToIntFunction<T> toId) {
        return new StreamCodec<ByteBuf, T>() {
            public T decode(ByteBuf input) {
                int i = VarInt.read(input);

                return (T) byId.apply(i);
            }

            public void encode(ByteBuf output, T value) {
                int i = toId.applyAsInt(value);

                VarInt.write(output, i);
            }
        };
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(IdMap<T> mapper) {
        Objects.requireNonNull(mapper);
        IntFunction intfunction = mapper::byIdOrThrow;

        Objects.requireNonNull(mapper);
        return idMapper(intfunction, mapper::getIdOrThrow);
    }

    private static <T, R> StreamCodec<RegistryFriendlyByteBuf, R> registry(final ResourceKey<? extends Registry<T>> registryKey, final Function<Registry<T>, IdMap<R>> mapExtractor) {
        return new StreamCodec<RegistryFriendlyByteBuf, R>() {
            private IdMap<R> getRegistryOrThrow(RegistryFriendlyByteBuf input) {
                return (IdMap) mapExtractor.apply(input.registryAccess().lookupOrThrow(registryKey));
            }

            public R decode(RegistryFriendlyByteBuf input) {
                int i = VarInt.read(input);

                return (R) this.getRegistryOrThrow(input).byIdOrThrow(i);
            }

            public void encode(RegistryFriendlyByteBuf output, R value) {
                int i = this.getRegistryOrThrow(output).getIdOrThrow(value);

                VarInt.write(output, i);
            }
        };
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> registry(ResourceKey<? extends Registry<T>> registryKey) {
        return registry(registryKey, (registry) -> {
            return registry;
        });
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        return registry(registryKey, Registry::asHolderIdMap);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holder(final ResourceKey<? extends Registry<T>> registryKey, final StreamCodec<? super RegistryFriendlyByteBuf, T> directCodec) {
        return new StreamCodec<RegistryFriendlyByteBuf, Holder<T>>() {
            private static final int DIRECT_HOLDER_ID = 0;

            private IdMap<Holder<T>> getRegistryOrThrow(RegistryFriendlyByteBuf input) {
                return input.registryAccess().lookupOrThrow(registryKey).asHolderIdMap();
            }

            public Holder<T> decode(RegistryFriendlyByteBuf input) {
                int i = VarInt.read(input);

                return i == 0 ? Holder.direct(directCodec.decode(input)) : (Holder) this.getRegistryOrThrow(input).byIdOrThrow(i - 1);
            }

            public void encode(RegistryFriendlyByteBuf output, Holder<T> holder) {
                switch (holder.kind()) {
                    case REFERENCE:
                        int i = this.getRegistryOrThrow(output).getIdOrThrow(holder);

                        VarInt.write(output, i + 1);
                        break;
                    case DIRECT:
                        VarInt.write(output, 0);
                        directCodec.encode(output, holder.value());
                }

            }
        };
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>> holderSet(final ResourceKey<? extends Registry<T>> registryKey) {
        return new StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>>() {
            private static final int NAMED_SET = -1;
            private final StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderCodec = ByteBufCodecs.holderRegistry(registryKey);

            public HolderSet<T> decode(RegistryFriendlyByteBuf input) {
                int i = VarInt.read(input) - 1;

                if (i == -1) {
                    Registry<T> registry = input.registryAccess().lookupOrThrow(registryKey);

                    return (HolderSet) registry.get(TagKey.create(registryKey, (Identifier) Identifier.STREAM_CODEC.decode(input))).orElseThrow();
                } else {
                    List<Holder<T>> list = new ArrayList(Math.min(i, 65536));

                    for (int j = 0; j < i; ++j) {
                        list.add((Holder) this.holderCodec.decode(input));
                    }

                    return HolderSet.direct(list);
                }
            }

            public void encode(RegistryFriendlyByteBuf output, HolderSet<T> value) {
                Optional<TagKey<T>> optional = value.unwrapKey();

                if (optional.isPresent()) {
                    VarInt.write(output, 0);
                    Identifier.STREAM_CODEC.encode(output, ((TagKey) optional.get()).location());
                } else {
                    VarInt.write(output, value.size() + 1);

                    for (Holder<T> holder : value) {
                        this.holderCodec.encode(output, holder);
                    }
                }

            }
        };
    }

    static StreamCodec<ByteBuf, JsonElement> lenientJson(final int maxStringLength) {
        return new StreamCodec<ByteBuf, JsonElement>() {
            private static final Gson GSON = (new GsonBuilder()).disableHtmlEscaping().create();

            public JsonElement decode(ByteBuf input) {
                String s = Utf8String.read(input, maxStringLength);

                try {
                    return LenientJsonParser.parse(s);
                } catch (JsonSyntaxException jsonsyntaxexception) {
                    throw new DecoderException("Failed to parse JSON", jsonsyntaxexception);
                }
            }

            public void encode(ByteBuf output, JsonElement value) {
                String s = null.GSON.toJson(value);

                Utf8String.write(output, s, maxStringLength);
            }
        };
    }
}
