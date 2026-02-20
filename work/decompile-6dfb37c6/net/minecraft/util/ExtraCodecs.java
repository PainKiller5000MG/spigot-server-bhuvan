package net.minecraft.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.primitives.UnsignedBytes;
import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Codec.ResultFunction;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.BaseMapCodec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import net.minecraft.core.HolderSet;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

public class ExtraCodecs {

    public static final Codec<JsonElement> JSON = converter(JsonOps.INSTANCE);
    public static final Codec<Object> JAVA = converter(JavaOps.INSTANCE);
    public static final Codec<Tag> NBT = converter(NbtOps.INSTANCE);
    public static final Codec<Vector2fc> VECTOR2F = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 2).map((list1) -> {
            return new Vector2f((Float) list1.get(0), (Float) list1.get(1));
        });
    }, (vector2fc) -> {
        return List.of(vector2fc.x(), vector2fc.y());
    });
    public static final Codec<Vector3fc> VECTOR3F = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 3).map((list1) -> {
            return new Vector3f((Float) list1.get(0), (Float) list1.get(1), (Float) list1.get(2));
        });
    }, (vector3fc) -> {
        return List.of(vector3fc.x(), vector3fc.y(), vector3fc.z());
    });
    public static final Codec<Vector3ic> VECTOR3I = Codec.INT.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 3).map((list1) -> {
            return new Vector3i((Integer) list1.get(0), (Integer) list1.get(1), (Integer) list1.get(2));
        });
    }, (vector3ic) -> {
        return List.of(vector3ic.x(), vector3ic.y(), vector3ic.z());
    });
    public static final Codec<Vector4fc> VECTOR4F = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 4).map((list1) -> {
            return new Vector4f((Float) list1.get(0), (Float) list1.get(1), (Float) list1.get(2), (Float) list1.get(3));
        });
    }, (vector4fc) -> {
        return List.of(vector4fc.x(), vector4fc.y(), vector4fc.z(), vector4fc.w());
    });
    public static final Codec<Quaternionfc> QUATERNIONF_COMPONENTS = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 4).map((list1) -> {
            return (new Quaternionf((Float) list1.get(0), (Float) list1.get(1), (Float) list1.get(2), (Float) list1.get(3))).normalize();
        });
    }, (quaternionfc) -> {
        return List.of(quaternionfc.x(), quaternionfc.y(), quaternionfc.z(), quaternionfc.w());
    });
    public static final Codec<AxisAngle4f> AXISANGLE4F = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.FLOAT.fieldOf("angle").forGetter((axisangle4f) -> {
            return axisangle4f.angle;
        }), ExtraCodecs.VECTOR3F.fieldOf("axis").forGetter((axisangle4f) -> {
            return new Vector3f(axisangle4f.x, axisangle4f.y, axisangle4f.z);
        })).apply(instance, AxisAngle4f::new);
    });
    public static final Codec<Quaternionfc> QUATERNIONF = Codec.withAlternative(ExtraCodecs.QUATERNIONF_COMPONENTS, ExtraCodecs.AXISANGLE4F.xmap(Quaternionf::new, AxisAngle4f::new));
    public static final Codec<Matrix4fc> MATRIX4F = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 16).map((list1) -> {
            Matrix4f matrix4f = new Matrix4f();

            for (int i = 0; i < list1.size(); ++i) {
                matrix4f.setRowColumn(i >> 2, i & 3, (Float) list1.get(i));
            }

            return matrix4f.determineProperties();
        });
    }, (matrix4fc) -> {
        FloatList floatlist = new FloatArrayList(16);

        for (int i = 0; i < 16; ++i) {
            floatlist.add(matrix4fc.getRowColumn(i >> 2, i & 3));
        }

        return floatlist;
    });
    private static final String HEX_COLOR_PREFIX = "#";
    public static final Codec<Integer> RGB_COLOR_CODEC = Codec.withAlternative(Codec.INT, ExtraCodecs.VECTOR3F, (vector3fc) -> {
        return ARGB.colorFromFloat(1.0F, vector3fc.x(), vector3fc.y(), vector3fc.z());
    });
    public static final Codec<Integer> ARGB_COLOR_CODEC = Codec.withAlternative(Codec.INT, ExtraCodecs.VECTOR4F, (vector4fc) -> {
        return ARGB.colorFromFloat(vector4fc.w(), vector4fc.x(), vector4fc.y(), vector4fc.z());
    });
    public static final Codec<Integer> STRING_RGB_COLOR = Codec.withAlternative(hexColor(6).xmap(ARGB::opaque, ARGB::transparent), ExtraCodecs.RGB_COLOR_CODEC);
    public static final Codec<Integer> STRING_ARGB_COLOR = Codec.withAlternative(hexColor(8), ExtraCodecs.ARGB_COLOR_CODEC);
    public static final Codec<Integer> UNSIGNED_BYTE = Codec.BYTE.flatComapMap(UnsignedBytes::toInt, (integer) -> {
        return integer > 255 ? DataResult.error(() -> {
            return "Unsigned byte was too large: " + integer + " > 255";
        }) : DataResult.success(integer.byteValue());
    });
    public static final Codec<Integer> NON_NEGATIVE_INT = intRangeWithMessage(0, Integer.MAX_VALUE, (integer) -> {
        return "Value must be non-negative: " + integer;
    });
    public static final Codec<Integer> POSITIVE_INT = intRangeWithMessage(1, Integer.MAX_VALUE, (integer) -> {
        return "Value must be positive: " + integer;
    });
    public static final Codec<Long> NON_NEGATIVE_LONG = longRangeWithMessage(0L, Long.MAX_VALUE, (olong) -> {
        return "Value must be non-negative: " + olong;
    });
    public static final Codec<Long> POSITIVE_LONG = longRangeWithMessage(1L, Long.MAX_VALUE, (olong) -> {
        return "Value must be positive: " + olong;
    });
    public static final Codec<Float> NON_NEGATIVE_FLOAT = floatRangeMinInclusiveWithMessage(0.0F, Float.MAX_VALUE, (ofloat) -> {
        return "Value must be non-negative: " + ofloat;
    });
    public static final Codec<Float> POSITIVE_FLOAT = floatRangeMinExclusiveWithMessage(0.0F, Float.MAX_VALUE, (ofloat) -> {
        return "Value must be positive: " + ofloat;
    });
    public static final Codec<Pattern> PATTERN = Codec.STRING.comapFlatMap((s) -> {
        try {
            return DataResult.success(Pattern.compile(s));
        } catch (PatternSyntaxException patternsyntaxexception) {
            return DataResult.error(() -> {
                return "Invalid regex pattern '" + s + "': " + patternsyntaxexception.getMessage();
            });
        }
    }, Pattern::pattern);
    public static final Codec<Instant> INSTANT_ISO8601 = temporalCodec(DateTimeFormatter.ISO_INSTANT).xmap(Instant::from, Function.identity());
    public static final Codec<byte[]> BASE64_STRING = Codec.STRING.comapFlatMap((s) -> {
        try {
            return DataResult.success(Base64.getDecoder().decode(s));
        } catch (IllegalArgumentException illegalargumentexception) {
            return DataResult.error(() -> {
                return "Malformed base64 string";
            });
        }
    }, (abyte) -> {
        return Base64.getEncoder().encodeToString(abyte);
    });
    public static final Codec<String> ESCAPED_STRING = Codec.STRING.comapFlatMap((s) -> {
        return DataResult.success(StringEscapeUtils.unescapeJava(s));
    }, StringEscapeUtils::escapeJava);
    public static final Codec<ExtraCodecs.TagOrElementLocation> TAG_OR_ELEMENT_ID = Codec.STRING.comapFlatMap((s) -> {
        return s.startsWith("#") ? Identifier.read(s.substring(1)).map((identifier) -> {
            return new ExtraCodecs.TagOrElementLocation(identifier, true);
        }) : Identifier.read(s).map((identifier) -> {
            return new ExtraCodecs.TagOrElementLocation(identifier, false);
        });
    }, ExtraCodecs.TagOrElementLocation::decoratedId);
    public static final Function<Optional<Long>, OptionalLong> toOptionalLong = (optional) -> {
        return (OptionalLong) optional.map(OptionalLong::of).orElseGet(OptionalLong::empty);
    };
    public static final Function<OptionalLong, Optional<Long>> fromOptionalLong = (optionallong) -> {
        return optionallong.isPresent() ? Optional.of(optionallong.getAsLong()) : Optional.empty();
    };
    public static final Codec<BitSet> BIT_SET = Codec.LONG_STREAM.xmap((longstream) -> {
        return BitSet.valueOf(longstream.toArray());
    }, (bitset) -> {
        return Arrays.stream(bitset.toLongArray());
    });
    public static final int MAX_PROPERTY_NAME_LENGTH = 64;
    public static final int MAX_PROPERTY_VALUE_LENGTH = 32767;
    public static final int MAX_PROPERTY_SIGNATURE_LENGTH = 1024;
    public static final int MAX_PROPERTIES = 16;
    private static final Codec<Property> PROPERTY = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.sizeLimitedString(64).fieldOf("name").forGetter(Property::name), Codec.sizeLimitedString(32767).fieldOf("value").forGetter(Property::value), Codec.sizeLimitedString(1024).optionalFieldOf("signature").forGetter((property) -> {
            return Optional.ofNullable(property.signature());
        })).apply(instance, (s, s1, optional) -> {
            return new Property(s, s1, (String) optional.orElse((Object) null));
        });
    });
    public static final Codec<PropertyMap> PROPERTY_MAP = Codec.either(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).validate((map) -> {
        return map.size() > 16 ? DataResult.error(() -> {
            return "Cannot have more than 16 properties, but was " + map.size();
        }) : DataResult.success(map);
    }), ExtraCodecs.PROPERTY.sizeLimitedListOf(16)).xmap((either) -> {
        ImmutableMultimap.Builder<String, Property> immutablemultimap_builder = ImmutableMultimap.builder();

        either.ifLeft((map) -> {
            map.forEach((s, list) -> {
                for (String s1 : list) {
                    immutablemultimap_builder.put(s, new Property(s, s1));
                }

            });
        }).ifRight((list) -> {
            for (Property property : list) {
                immutablemultimap_builder.put(property.name(), property);
            }

        });
        return new PropertyMap(immutablemultimap_builder.build());
    }, (propertymap) -> {
        return Either.right(propertymap.values().stream().toList());
    });
    public static final Codec<String> PLAYER_NAME = Codec.string(0, 16).validate((s) -> {
        return StringUtil.isValidPlayerName(s) ? DataResult.success(s) : DataResult.error(() -> {
            return "Player name contained disallowed characters: '" + s + "'";
        });
    });
    public static final Codec<GameProfile> AUTHLIB_GAME_PROFILE = gameProfileCodec(UUIDUtil.AUTHLIB_CODEC).codec();
    public static final MapCodec<GameProfile> STORED_GAME_PROFILE = gameProfileCodec(UUIDUtil.CODEC);
    public static final Codec<String> NON_EMPTY_STRING = Codec.STRING.validate((s) -> {
        return s.isEmpty() ? DataResult.error(() -> {
            return "Expected non-empty string";
        }) : DataResult.success(s);
    });
    public static final Codec<Integer> CODEPOINT = Codec.STRING.comapFlatMap((s) -> {
        int[] aint = s.codePoints().toArray();

        return aint.length != 1 ? DataResult.error(() -> {
            return "Expected one codepoint, got: " + s;
        }) : DataResult.success(aint[0]);
    }, Character::toString);
    public static final Codec<String> RESOURCE_PATH_CODEC = Codec.STRING.validate((s) -> {
        return !Identifier.isValidPath(s) ? DataResult.error(() -> {
            return "Invalid string to use as a resource path element: " + s;
        }) : DataResult.success(s);
    });
    public static final Codec<URI> UNTRUSTED_URI = Codec.STRING.comapFlatMap((s) -> {
        try {
            return DataResult.success(Util.parseAndValidateUntrustedUri(s));
        } catch (URISyntaxException urisyntaxexception) {
            Objects.requireNonNull(urisyntaxexception);
            return DataResult.error(urisyntaxexception::getMessage);
        }
    }, URI::toString);
    public static final Codec<String> CHAT_STRING = Codec.STRING.validate((s) -> {
        for (int i = 0; i < s.length(); ++i) {
            char c0 = s.charAt(i);

            if (!StringUtil.isAllowedChatCharacter(c0)) {
                return DataResult.error(() -> {
                    return "Disallowed chat character: '" + c0 + "'";
                });
            }
        }

        return DataResult.success(s);
    });

    public ExtraCodecs() {}

    public static <T> Codec<T> converter(DynamicOps<T> ops) {
        return Codec.PASSTHROUGH.xmap((dynamic) -> {
            return dynamic.convert(ops).getValue();
        }, (object) -> {
            return new Dynamic(ops, object);
        });
    }

    private static Codec<Integer> hexColor(int expectedDigits) {
        long j = (1L << expectedDigits * 4) - 1L;

        return Codec.STRING.comapFlatMap((s) -> {
            if (!s.startsWith("#")) {
                return DataResult.error(() -> {
                    return "Hex color must begin with #";
                });
            } else {
                int k = s.length() - "#".length();

                if (k != expectedDigits) {
                    return DataResult.error(() -> {
                        return "Hex color is wrong size, expected " + expectedDigits + " digits but got " + k;
                    });
                } else {
                    try {
                        long l = HexFormat.fromHexDigitsToLong(s, "#".length(), s.length());

                        return l >= 0L && l <= j ? DataResult.success((int) l) : DataResult.error(() -> {
                            return "Color value out of range: " + s;
                        });
                    } catch (NumberFormatException numberformatexception) {
                        return DataResult.error(() -> {
                            return "Invalid color value: " + s;
                        });
                    }
                }
            }
        }, (integer) -> {
            HexFormat hexformat = HexFormat.of();

            return "#" + hexformat.toHexDigits((long) integer, expectedDigits);
        });
    }

    public static <P, I> Codec<I> intervalCodec(Codec<P> pointCodec, String lowerBoundName, String upperBoundName, BiFunction<P, P, DataResult<I>> makeInterval, Function<I, P> getMin, Function<I, P> getMax) {
        Codec<I> codec1 = Codec.list(pointCodec).comapFlatMap((list) -> {
            return Util.fixedSize(list, 2).flatMap((list1) -> {
                P p0 = (P) list1.get(0);
                P p1 = (P) list1.get(1);

                return (DataResult) makeInterval.apply(p0, p1);
            });
        }, (object) -> {
            return ImmutableList.of(getMin.apply(object), getMax.apply(object));
        });
        Codec<I> codec2 = RecordCodecBuilder.create((instance) -> {
            return instance.group(pointCodec.fieldOf(lowerBoundName).forGetter(Pair::getFirst), pointCodec.fieldOf(upperBoundName).forGetter(Pair::getSecond)).apply(instance, Pair::of);
        }).comapFlatMap((pair) -> {
            return (DataResult) makeInterval.apply(pair.getFirst(), pair.getSecond());
        }, (object) -> {
            return Pair.of(getMin.apply(object), getMax.apply(object));
        });
        Codec<I> codec3 = Codec.withAlternative(codec1, codec2);

        return Codec.either(pointCodec, codec3).comapFlatMap((either) -> {
            return (DataResult) either.map((object) -> {
                return (DataResult) makeInterval.apply(object, object);
            }, DataResult::success);
        }, (object) -> {
            P p0 = (P) getMin.apply(object);
            P p1 = (P) getMax.apply(object);

            return Objects.equals(p0, p1) ? Either.left(p0) : Either.right(object);
        });
    }

    public static <A> Codec.ResultFunction<A> orElsePartial(final A value) {
        return new Codec.ResultFunction<A>() {
            public <T> DataResult<Pair<A, T>> apply(DynamicOps<T> ops, T input, DataResult<Pair<A, T>> a) {
                MutableObject<String> mutableobject = new MutableObject();

                Objects.requireNonNull(mutableobject);
                Optional<Pair<A, T>> optional = a.resultOrPartial(mutableobject::setValue);

                return optional.isPresent() ? a : DataResult.error(() -> {
                    return "(" + (String) mutableobject.get() + " -> using default)";
                }, Pair.of(value, input));
            }

            public <T> DataResult<T> coApply(DynamicOps<T> ops, A input, DataResult<T> t) {
                return t;
            }

            public String toString() {
                return "OrElsePartial[" + String.valueOf(value) + "]";
            }
        };
    }

    public static <E> Codec<E> idResolverCodec(ToIntFunction<E> toInt, IntFunction<@Nullable E> fromInt, int unknownId) {
        return Codec.INT.flatXmap((integer) -> {
            return (DataResult) Optional.ofNullable(fromInt.apply(integer)).map(DataResult::success).orElseGet(() -> {
                return DataResult.error(() -> {
                    return "Unknown element id: " + integer;
                });
            });
        }, (object) -> {
            int j = toInt.applyAsInt(object);

            return j == unknownId ? DataResult.error(() -> {
                return "Element with unknown id: " + String.valueOf(object);
            }) : DataResult.success(j);
        });
    }

    public static <I, E> Codec<E> idResolverCodec(Codec<I> value, Function<I, @Nullable E> fromId, Function<E, @Nullable I> toId) {
        return value.flatXmap((object) -> {
            E e0 = (E) fromId.apply(object);

            return e0 == null ? DataResult.error(() -> {
                return "Unknown element id: " + String.valueOf(object);
            }) : DataResult.success(e0);
        }, (object) -> {
            I i0 = (I) toId.apply(object);

            return i0 == null ? DataResult.error(() -> {
                return "Element with unknown id: " + String.valueOf(object);
            }) : DataResult.success(i0);
        });
    }

    public static <E> Codec<E> orCompressed(final Codec<E> normal, final Codec<E> compressed) {
        return new Codec<E>() {
            public <T> DataResult<T> encode(E input, DynamicOps<T> ops, T prefix) {
                return ops.compressMaps() ? compressed.encode(input, ops, prefix) : normal.encode(input, ops, prefix);
            }

            public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> ops, T input) {
                return ops.compressMaps() ? compressed.decode(ops, input) : normal.decode(ops, input);
            }

            public String toString() {
                String s = String.valueOf(normal);

                return s + " orCompressed " + String.valueOf(compressed);
            }
        };
    }

    public static <E> MapCodec<E> orCompressed(final MapCodec<E> normal, final MapCodec<E> compressed) {
        return new MapCodec<E>() {
            public <T> RecordBuilder<T> encode(E input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return ops.compressMaps() ? compressed.encode(input, ops, prefix) : normal.encode(input, ops, prefix);
            }

            public <T> DataResult<E> decode(DynamicOps<T> ops, MapLike<T> input) {
                return ops.compressMaps() ? compressed.decode(ops, input) : normal.decode(ops, input);
            }

            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return compressed.keys(ops);
            }

            public String toString() {
                String s = String.valueOf(normal);

                return s + " orCompressed " + String.valueOf(compressed);
            }
        };
    }

    public static <E> Codec<E> overrideLifecycle(Codec<E> codec, final Function<E, Lifecycle> decodeLifecycle, final Function<E, Lifecycle> encodeLifecycle) {
        return codec.mapResult(new Codec.ResultFunction<E>() {
            public <T> DataResult<Pair<E, T>> apply(DynamicOps<T> ops, T input, DataResult<Pair<E, T>> a) {
                return (DataResult) a.result().map((pair) -> {
                    return a.setLifecycle((Lifecycle) decodeLifecycle.apply(pair.getFirst()));
                }).orElse(a);
            }

            public <T> DataResult<T> coApply(DynamicOps<T> ops, E input, DataResult<T> t) {
                return t.setLifecycle((Lifecycle) encodeLifecycle.apply(input));
            }

            public String toString() {
                String s = String.valueOf(decodeLifecycle);

                return "WithLifecycle[" + s + " " + String.valueOf(encodeLifecycle) + "]";
            }
        });
    }

    public static <E> Codec<E> overrideLifecycle(Codec<E> codec, Function<E, Lifecycle> lifecycleGetter) {
        return overrideLifecycle(codec, lifecycleGetter, lifecycleGetter);
    }

    public static <K, V> ExtraCodecs.StrictUnboundedMapCodec<K, V> strictUnboundedMap(Codec<K> keyCodec, Codec<V> elementCodec) {
        return new ExtraCodecs.StrictUnboundedMapCodec<K, V>(keyCodec, elementCodec);
    }

    public static <E> Codec<List<E>> compactListCodec(Codec<E> elementCodec) {
        return compactListCodec(elementCodec, elementCodec.listOf());
    }

    public static <E> Codec<List<E>> compactListCodec(Codec<E> elementCodec, Codec<List<E>> listCodec) {
        return Codec.either(listCodec, elementCodec).xmap((either) -> {
            return (List) either.map((list) -> {
                return list;
            }, List::of);
        }, (list) -> {
            return list.size() == 1 ? Either.right(list.getFirst()) : Either.left(list);
        });
    }

    private static Codec<Integer> intRangeWithMessage(int minInclusive, int maxInclusive, Function<Integer, String> error) {
        return Codec.INT.validate((integer) -> {
            return integer.compareTo(minInclusive) >= 0 && integer.compareTo(maxInclusive) <= 0 ? DataResult.success(integer) : DataResult.error(() -> {
                return (String) error.apply(integer);
            });
        });
    }

    public static Codec<Integer> intRange(int minInclusive, int maxInclusive) {
        return intRangeWithMessage(minInclusive, maxInclusive, (integer) -> {
            return "Value must be within range [" + minInclusive + ";" + maxInclusive + "]: " + integer;
        });
    }

    private static Codec<Long> longRangeWithMessage(long minInclusive, long maxInclusive, Function<Long, String> error) {
        return Codec.LONG.validate((olong) -> {
            return (long) olong.compareTo(minInclusive) >= 0L && (long) olong.compareTo(maxInclusive) <= 0L ? DataResult.success(olong) : DataResult.error(() -> {
                return (String) error.apply(olong);
            });
        });
    }

    public static Codec<Long> longRange(int minInclusive, int maxInclusive) {
        return longRangeWithMessage((long) minInclusive, (long) maxInclusive, (olong) -> {
            return "Value must be within range [" + minInclusive + ";" + maxInclusive + "]: " + olong;
        });
    }

    private static Codec<Float> floatRangeMinInclusiveWithMessage(float minInclusive, float maxInclusive, Function<Float, String> error) {
        return Codec.FLOAT.validate((ofloat) -> {
            return ofloat.compareTo(minInclusive) >= 0 && ofloat.compareTo(maxInclusive) <= 0 ? DataResult.success(ofloat) : DataResult.error(() -> {
                return (String) error.apply(ofloat);
            });
        });
    }

    private static Codec<Float> floatRangeMinExclusiveWithMessage(float minExclusive, float maxInclusive, Function<Float, String> error) {
        return Codec.FLOAT.validate((ofloat) -> {
            return ofloat.compareTo(minExclusive) > 0 && ofloat.compareTo(maxInclusive) <= 0 ? DataResult.success(ofloat) : DataResult.error(() -> {
                return (String) error.apply(ofloat);
            });
        });
    }

    public static Codec<Float> floatRange(float minInclusive, float maxInclusive) {
        return floatRangeMinInclusiveWithMessage(minInclusive, maxInclusive, (ofloat) -> {
            return "Value must be within range [" + minInclusive + ";" + maxInclusive + "]: " + ofloat;
        });
    }

    public static <T> Codec<List<T>> nonEmptyList(Codec<List<T>> listCodec) {
        return listCodec.validate((list) -> {
            return list.isEmpty() ? DataResult.error(() -> {
                return "List must have contents";
            }) : DataResult.success(list);
        });
    }

    public static <T> Codec<HolderSet<T>> nonEmptyHolderSet(Codec<HolderSet<T>> listCodec) {
        return listCodec.validate((holderset) -> {
            return holderset.unwrap().right().filter(List::isEmpty).isPresent() ? DataResult.error(() -> {
                return "List must have contents";
            }) : DataResult.success(holderset);
        });
    }

    public static <M extends Map<?, ?>> Codec<M> nonEmptyMap(Codec<M> mapCodec) {
        return mapCodec.validate((map) -> {
            return map.isEmpty() ? DataResult.error(() -> {
                return "Map must have contents";
            }) : DataResult.success(map);
        });
    }

    public static <E> MapCodec<E> retrieveContext(final Function<DynamicOps<?>, DataResult<E>> getter) {
        class 1ContextRetrievalCodec extends MapCodec<E> {

            _ContextRetrievalCodec/* $FF was: 1ContextRetrievalCodec*/() {
}

            public <T> RecordBuilder<T> encode(E input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return prefix;
            }

            public <T> DataResult<E> decode(DynamicOps<T> ops, MapLike<T> input) {
                return (DataResult)getter.apply(ops);
            }

            public String toString() {
                return "ContextRetrievalCodec[" + String.valueOf(getter) + "]";
            }

            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.empty();
            }
        }


        return new 1ContextRetrievalCodec();
    }

    public static <E, L extends Collection<E>, T> Function<L, DataResult<L>> ensureHomogenous(Function<E, T> typeGetter) {
        return (collection) -> {
            Iterator<E> iterator = collection.iterator();

            if (iterator.hasNext()) {
                T t0 = (T) typeGetter.apply(iterator.next());

                while (iterator.hasNext()) {
                    E e0 = (E) iterator.next();
                    T t1 = (T) typeGetter.apply(e0);

                    if (t1 != t0) {
                        return DataResult.error(() -> {
                            String s = String.valueOf(e0);

                            return "Mixed type list: element " + s + " had type " + String.valueOf(t1) + ", but list is of type " + String.valueOf(t0);
                        });
                    }
                }
            }

            return DataResult.success(collection, Lifecycle.stable());
        };
    }

    public static <A> Codec<A> catchDecoderException(final Codec<A> codec) {
        return Codec.of(codec, new Decoder<A>() {
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    return codec.decode(ops, input);
                } catch (Exception exception) {
                    return DataResult.error(() -> {
                        String s = String.valueOf(input);

                        return "Caught exception decoding " + s + ": " + exception.getMessage();
                    });
                }
            }
        });
    }

    public static Codec<TemporalAccessor> temporalCodec(DateTimeFormatter formatter) {
        PrimitiveCodec primitivecodec = Codec.STRING;
        Function function = (s) -> {
            try {
                return DataResult.success(formatter.parse(s));
            } catch (Exception exception) {
                Objects.requireNonNull(exception);
                return DataResult.error(exception::getMessage);
            }
        };

        Objects.requireNonNull(formatter);
        return primitivecodec.comapFlatMap(function, formatter::format);
    }

    public static MapCodec<OptionalLong> asOptionalLong(MapCodec<Optional<Long>> fieldCodec) {
        return fieldCodec.xmap(ExtraCodecs.toOptionalLong, ExtraCodecs.fromOptionalLong);
    }

    private static MapCodec<GameProfile> gameProfileCodec(Codec<UUID> uuidCodec) {
        return RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(uuidCodec.fieldOf("id").forGetter(GameProfile::id), ExtraCodecs.PLAYER_NAME.fieldOf("name").forGetter(GameProfile::name), ExtraCodecs.PROPERTY_MAP.optionalFieldOf("properties", PropertyMap.EMPTY).forGetter(GameProfile::properties)).apply(instance, GameProfile::new);
        });
    }

    public static <K, V> Codec<Map<K, V>> sizeLimitedMap(Codec<Map<K, V>> codec, int maxSizeInclusive) {
        return codec.validate((map) -> {
            return map.size() > maxSizeInclusive ? DataResult.error(() -> {
                int j = map.size();

                return "Map is too long: " + j + ", expected range [0-" + maxSizeInclusive + "]";
            }) : DataResult.success(map);
        });
    }

    public static <T> Codec<Object2BooleanMap<T>> object2BooleanMap(Codec<T> keyCodec) {
        return Codec.unboundedMap(keyCodec, Codec.BOOL).xmap(Object2BooleanOpenHashMap::new, Object2ObjectOpenHashMap::new);
    }

    /** @deprecated */
    @Deprecated
    public static <K, V> MapCodec<V> dispatchOptionalValue(final String typeKey, final String valueKey, final Codec<K> typeCodec, final Function<? super V, ? extends K> typeGetter, final Function<? super K, ? extends Codec<? extends V>> valueCodec) {
        return new MapCodec<V>() {
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(typeKey), ops.createString(valueKey));
            }

            public <T> DataResult<V> decode(DynamicOps<T> ops, MapLike<T> input) {
                T t0 = (T) input.get(typeKey);

                return t0 == null ? DataResult.error(() -> {
                    return "Missing \"" + typeKey + "\" in: " + String.valueOf(input);
                }) : typeCodec.decode(ops, t0).flatMap((pair) -> {
                    Object object = input.get(valueKey);

                    Objects.requireNonNull(ops);
                    T t1 = (T) Objects.requireNonNullElseGet(object, ops::emptyMap);

                    return ((Codec) valueCodec.apply(pair.getFirst())).decode(ops, t1).map(Pair::getFirst);
                });
            }

            public <T> RecordBuilder<T> encode(V input, DynamicOps<T> ops, RecordBuilder<T> builder) {
                K k0 = (K) typeGetter.apply(input);

                builder.add(typeKey, typeCodec.encodeStart(ops, k0));
                DataResult<T> dataresult = this.encode((Codec) valueCodec.apply(k0), input, ops);

                if (dataresult.result().isEmpty() || !Objects.equals(dataresult.result().get(), ops.emptyMap())) {
                    builder.add(valueKey, dataresult);
                }

                return builder;
            }

            private <T, V2 extends V> DataResult<T> encode(Codec<V2> codec, V input, DynamicOps<T> ops) {
                return codec.encodeStart(ops, input);
            }
        };
    }

    public static <A> Codec<Optional<A>> optionalEmptyMap(final Codec<A> codec) {
        return new Codec<Optional<A>>() {
            public <T> DataResult<Pair<Optional<A>, T>> decode(DynamicOps<T> ops, T input) {
                return isEmptyMap(ops, input) ? DataResult.success(Pair.of(Optional.empty(), input)) : codec.decode(ops, input).map((pair) -> {
                    return pair.mapFirst(Optional::of);
                });
            }

            private static <T> boolean isEmptyMap(DynamicOps<T> ops, T input) {
                Optional<MapLike<T>> optional = ops.getMap(input).result();

                return optional.isPresent() && ((MapLike) optional.get()).entries().findAny().isEmpty();
            }

            public <T> DataResult<T> encode(Optional<A> input, DynamicOps<T> ops, T prefix) {
                return input.isEmpty() ? DataResult.success(ops.emptyMap()) : codec.encode(input.get(), ops, prefix);
            }
        };
    }

    /** @deprecated */
    @Deprecated
    public static <E extends Enum<E>> Codec<E> legacyEnum(Function<String, E> valueOf) {
        return Codec.STRING.comapFlatMap((s) -> {
            try {
                return DataResult.success((Enum) valueOf.apply(s));
            } catch (IllegalArgumentException illegalargumentexception) {
                return DataResult.error(() -> {
                    return "No value with id: " + s;
                });
            }
        }, Enum::toString);
    }

    public static record StrictUnboundedMapCodec<K, V>(Codec<K> keyCodec, Codec<V> elementCodec) implements BaseMapCodec<K, V>, Codec<Map<K, V>> {

        public <T> DataResult<Map<K, V>> decode(DynamicOps<T> ops, MapLike<T> input) {
            ImmutableMap.Builder<K, V> immutablemap_builder = ImmutableMap.builder();

            for (Pair<T, T> pair : input.entries().toList()) {
                DataResult<K> dataresult = this.keyCodec().parse(ops, pair.getFirst());
                DataResult<V> dataresult1 = this.elementCodec().parse(ops, pair.getSecond());
                DataResult<Pair<K, V>> dataresult2 = dataresult.apply2stable(Pair::of, dataresult1);
                Optional<DataResult.Error<Pair<K, V>>> optional = dataresult2.error();

                if (optional.isPresent()) {
                    String s = ((Error) optional.get()).message();

                    return DataResult.error(() -> {
                        if (dataresult.result().isPresent()) {
                            String s1 = String.valueOf(dataresult.result().get());

                            return "Map entry '" + s1 + "' : " + s;
                        } else {
                            return s;
                        }
                    });
                }

                if (!dataresult2.result().isPresent()) {
                    return DataResult.error(() -> {
                        return "Empty or invalid map contents are not allowed";
                    });
                }

                Pair<K, V> pair1 = (Pair) dataresult2.result().get();

                immutablemap_builder.put(pair1.getFirst(), pair1.getSecond());
            }

            Map<K, V> map = immutablemap_builder.build();

            return DataResult.success(map);
        }

        public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getMap(input).setLifecycle(Lifecycle.stable()).flatMap((maplike) -> {
                return this.decode(ops, maplike);
            }).map((map) -> {
                return Pair.of(map, input);
            });
        }

        public <T> DataResult<T> encode(Map<K, V> input, DynamicOps<T> ops, T prefix) {
            return this.encode(input, ops, ops.mapBuilder()).build(prefix);
        }

        public String toString() {
            String s = String.valueOf(this.keyCodec);

            return "StrictUnboundedMapCodec[" + s + " -> " + String.valueOf(this.elementCodec) + "]";
        }
    }

    public static record TagOrElementLocation(Identifier id, boolean tag) {

        public String toString() {
            return this.decoratedId();
        }

        private String decoratedId() {
            return this.tag ? "#" + String.valueOf(this.id) : this.id.toString();
        }
    }

    public static class LateBoundIdMapper<I, V> {

        private final BiMap<I, V> idToValue = HashBiMap.create();

        public LateBoundIdMapper() {}

        public Codec<V> codec(Codec<I> idCodec) {
            BiMap<V, I> bimap = this.idToValue.inverse();
            BiMap bimap1 = this.idToValue;

            Objects.requireNonNull(this.idToValue);
            Function function = bimap1::get;

            Objects.requireNonNull(bimap);
            return ExtraCodecs.idResolverCodec(idCodec, function, bimap::get);
        }

        public ExtraCodecs.LateBoundIdMapper<I, V> put(I id, V value) {
            Objects.requireNonNull(value, () -> {
                return "Value for " + String.valueOf(id) + " is null";
            });
            this.idToValue.put(id, value);
            return this;
        }

        public Set<V> values() {
            return Collections.unmodifiableSet(this.idToValue.values());
        }
    }
}
