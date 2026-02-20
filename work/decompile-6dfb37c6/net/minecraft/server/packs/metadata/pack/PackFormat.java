package net.minecraft.server.packs.metadata.pack;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.InclusiveRange;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public record PackFormat(int major, int minor) implements Comparable<PackFormat> {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<PackFormat> BOTTOM_CODEC = fullCodec(0);
    public static final Codec<PackFormat> TOP_CODEC = fullCodec(Integer.MAX_VALUE);

    private static Codec<PackFormat> fullCodec(int defaultMinor) {
        return ExtraCodecs.compactListCodec(ExtraCodecs.NON_NEGATIVE_INT, ExtraCodecs.NON_NEGATIVE_INT.listOf(1, 256)).xmap((list) -> {
            return list.size() > 1 ? of((Integer) list.getFirst(), (Integer) list.get(1)) : of((Integer) list.getFirst(), defaultMinor);
        }, (packformat) -> {
            return packformat.minor != defaultMinor ? List.of(packformat.major(), packformat.minor()) : List.of(packformat.major());
        });
    }

    public static <ResultType, HolderType extends PackFormat.IntermediaryFormatHolder> DataResult<List<ResultType>> validateHolderList(List<HolderType> list, int lastPreMinorVersion, BiFunction<HolderType, InclusiveRange<PackFormat>, ResultType> constructor) {
        int j = list.stream().map(PackFormat.IntermediaryFormatHolder::format).mapToInt(PackFormat.IntermediaryFormat::effectiveMinMajorVersion).min().orElse(Integer.MAX_VALUE);
        List<ResultType> list1 = new ArrayList(list.size());

        for (HolderType holdertype : list) {
            PackFormat.IntermediaryFormat packformat_intermediaryformat = holdertype.format();

            if (packformat_intermediaryformat.min().isEmpty() && packformat_intermediaryformat.max().isEmpty() && packformat_intermediaryformat.supported().isEmpty()) {
                PackFormat.LOGGER.warn("Unknown or broken overlay entry {}", holdertype);
            } else {
                DataResult<InclusiveRange<PackFormat>> dataresult = packformat_intermediaryformat.validate(lastPreMinorVersion, false, j <= lastPreMinorVersion, "Overlay \"" + String.valueOf(holdertype) + "\"", "formats");

                if (!dataresult.isSuccess()) {
                    Error error = (Error) dataresult.error().get();

                    Objects.requireNonNull(error);
                    return DataResult.error(error::message);
                }

                list1.add(constructor.apply(holdertype, (InclusiveRange) dataresult.getOrThrow()));
            }
        }

        return DataResult.success(List.copyOf(list1));
    }

    @VisibleForTesting
    public static int lastPreMinorVersion(PackType type) {
        byte b0;

        switch (type) {
            case CLIENT_RESOURCES:
                b0 = 64;
                break;
            case SERVER_DATA:
                b0 = 81;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return b0;
    }

    public static MapCodec<InclusiveRange<PackFormat>> packCodec(PackType type) {
        int i = lastPreMinorVersion(type);

        return PackFormat.IntermediaryFormat.PACK_CODEC.flatXmap((packformat_intermediaryformat) -> {
            return packformat_intermediaryformat.validate(i, true, false, "Pack", "supported_formats");
        }, (inclusiverange) -> {
            return DataResult.success(PackFormat.IntermediaryFormat.fromRange(inclusiverange, i));
        });
    }

    public static PackFormat of(int major, int minor) {
        return new PackFormat(major, minor);
    }

    public static PackFormat of(int major) {
        return new PackFormat(major, 0);
    }

    public InclusiveRange<PackFormat> minorRange() {
        return new InclusiveRange<PackFormat>(this, of(this.major, Integer.MAX_VALUE));
    }

    public int compareTo(PackFormat other) {
        int i = Integer.compare(this.major(), other.major());

        return i != 0 ? i : Integer.compare(this.minor(), other.minor());
    }

    public String toString() {
        return this.minor == Integer.MAX_VALUE ? String.format(Locale.ROOT, "%d.*", this.major()) : String.format(Locale.ROOT, "%d.%d", this.major(), this.minor());
    }

    public static record IntermediaryFormat(Optional<PackFormat> min, Optional<PackFormat> max, Optional<Integer> format, Optional<InclusiveRange<Integer>> supported) {

        private static final MapCodec<PackFormat.IntermediaryFormat> PACK_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(PackFormat.BOTTOM_CODEC.optionalFieldOf("min_format").forGetter(PackFormat.IntermediaryFormat::min), PackFormat.TOP_CODEC.optionalFieldOf("max_format").forGetter(PackFormat.IntermediaryFormat::max), Codec.INT.optionalFieldOf("pack_format").forGetter(PackFormat.IntermediaryFormat::format), InclusiveRange.codec(Codec.INT).optionalFieldOf("supported_formats").forGetter(PackFormat.IntermediaryFormat::supported)).apply(instance, PackFormat.IntermediaryFormat::new);
        });
        public static final MapCodec<PackFormat.IntermediaryFormat> OVERLAY_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(PackFormat.BOTTOM_CODEC.optionalFieldOf("min_format").forGetter(PackFormat.IntermediaryFormat::min), PackFormat.TOP_CODEC.optionalFieldOf("max_format").forGetter(PackFormat.IntermediaryFormat::max), InclusiveRange.codec(Codec.INT).optionalFieldOf("formats").forGetter(PackFormat.IntermediaryFormat::supported)).apply(instance, (optional, optional1, optional2) -> {
                return new PackFormat.IntermediaryFormat(optional, optional1, optional.map(PackFormat::major), optional2);
            });
        });

        public static PackFormat.IntermediaryFormat fromRange(InclusiveRange<PackFormat> range, int lastPreMinorVersion) {
            InclusiveRange<Integer> inclusiverange1 = range.<Integer>map(PackFormat::major);

            return new PackFormat.IntermediaryFormat(Optional.of(range.minInclusive()), Optional.of(range.maxInclusive()), inclusiverange1.isValueInRange(lastPreMinorVersion) ? Optional.of(inclusiverange1.minInclusive()) : Optional.empty(), inclusiverange1.isValueInRange(lastPreMinorVersion) ? Optional.of(new InclusiveRange(inclusiverange1.minInclusive(), inclusiverange1.maxInclusive())) : Optional.empty());
        }

        public int effectiveMinMajorVersion() {
            return this.min.isPresent() ? (this.supported.isPresent() ? Math.min(((PackFormat) this.min.get()).major(), (Integer) ((InclusiveRange) this.supported.get()).minInclusive()) : ((PackFormat) this.min.get()).major()) : (this.supported.isPresent() ? (Integer) ((InclusiveRange) this.supported.get()).minInclusive() : Integer.MAX_VALUE);
        }

        public DataResult<InclusiveRange<PackFormat>> validate(int lastPreMinorVersion, boolean hasPackFormatField, boolean requireOldField, String context, String oldFieldName) {
            if (this.min.isPresent() != this.max.isPresent()) {
                return DataResult.error(() -> {
                    return context + " missing field, must declare both min_format and max_format";
                });
            } else if (requireOldField && this.supported.isEmpty()) {
                return DataResult.error(() -> {
                    return context + " missing required field " + oldFieldName + ", must be present in all overlays for any overlays to work across game versions";
                });
            } else if (this.min.isPresent()) {
                return this.validateNewFormat(lastPreMinorVersion, hasPackFormatField, requireOldField, context, oldFieldName);
            } else if (this.supported.isPresent()) {
                return this.validateOldFormat(lastPreMinorVersion, hasPackFormatField, context, oldFieldName);
            } else if (hasPackFormatField && this.format.isPresent()) {
                int j = (Integer) this.format.get();

                return j > lastPreMinorVersion ? DataResult.error(() -> {
                    return context + " declares support for version newer than " + lastPreMinorVersion + ", but is missing mandatory fields min_format and max_format";
                }) : DataResult.success(new InclusiveRange(PackFormat.of(j)));
            } else {
                return DataResult.error(() -> {
                    return context + " could not be parsed, missing format version information";
                });
            }
        }

        private DataResult<InclusiveRange<PackFormat>> validateNewFormat(int lastPreMinorVersion, boolean hasPackFormatField, boolean requireOldField, String context, String oldFieldName) {
            int j = ((PackFormat) this.min.get()).major();
            int k = ((PackFormat) this.max.get()).major();

            if (((PackFormat) this.min.get()).compareTo((PackFormat) this.max.get()) > 0) {
                return DataResult.error(() -> {
                    return context + " min_format (" + String.valueOf(this.min.get()) + ") is greater than max_format (" + String.valueOf(this.max.get()) + ")";
                });
            } else {
                if (j > lastPreMinorVersion && !requireOldField) {
                    if (this.supported.isPresent()) {
                        return DataResult.error(() -> {
                            return context + " key " + oldFieldName + " is deprecated starting from pack format " + (lastPreMinorVersion + 1) + ". Remove " + oldFieldName + " from your pack.mcmeta.";
                        });
                    }

                    if (hasPackFormatField && this.format.isPresent()) {
                        String s2 = this.validatePackFormatForRange(j, k);

                        if (s2 != null) {
                            return DataResult.error(() -> {
                                return s2;
                            });
                        }
                    }
                } else {
                    if (!this.supported.isPresent()) {
                        return DataResult.error(() -> {
                            return context + " declares support for format " + j + ", but game versions supporting formats 17 to " + lastPreMinorVersion + " require a " + oldFieldName + " field. Add \"" + oldFieldName + "\": [" + j + ", " + lastPreMinorVersion + "] or require a version greater or equal to " + (lastPreMinorVersion + 1) + ".0.";
                        });
                    }

                    InclusiveRange<Integer> inclusiverange = (InclusiveRange) this.supported.get();

                    if ((Integer) inclusiverange.minInclusive() != j) {
                        return DataResult.error(() -> {
                            return context + " version declaration mismatch between " + oldFieldName + " (from " + String.valueOf(inclusiverange.minInclusive()) + ") and min_format (" + String.valueOf(this.min.get()) + ")";
                        });
                    }

                    if ((Integer) inclusiverange.maxInclusive() != k && (Integer) inclusiverange.maxInclusive() != lastPreMinorVersion) {
                        return DataResult.error(() -> {
                            return context + " version declaration mismatch between " + oldFieldName + " (up to " + String.valueOf(inclusiverange.maxInclusive()) + ") and max_format (" + String.valueOf(this.max.get()) + ")";
                        });
                    }

                    if (hasPackFormatField) {
                        if (!this.format.isPresent()) {
                            return DataResult.error(() -> {
                                return context + " declares support for formats up to " + lastPreMinorVersion + ", but game versions supporting formats 17 to " + lastPreMinorVersion + " require a pack_format field. Add \"pack_format\": " + j + " or require a version greater or equal to " + (lastPreMinorVersion + 1) + ".0.";
                            });
                        }

                        String s3 = this.validatePackFormatForRange(j, k);

                        if (s3 != null) {
                            return DataResult.error(() -> {
                                return s3;
                            });
                        }
                    }
                }

                return DataResult.success(new InclusiveRange((PackFormat) this.min.get(), (PackFormat) this.max.get()));
            }
        }

        private DataResult<InclusiveRange<PackFormat>> validateOldFormat(int lastPreMinorVersion, boolean hasPackFormatField, String context, String oldFieldName) {
            InclusiveRange<Integer> inclusiverange = (InclusiveRange) this.supported.get();
            int j = (Integer) inclusiverange.minInclusive();
            int k = (Integer) inclusiverange.maxInclusive();

            if (k > lastPreMinorVersion) {
                return DataResult.error(() -> {
                    return context + " declares support for version newer than " + lastPreMinorVersion + ", but is missing mandatory fields min_format and max_format";
                });
            } else {
                if (hasPackFormatField) {
                    if (!this.format.isPresent()) {
                        return DataResult.error(() -> {
                            return context + " declares support for formats up to " + lastPreMinorVersion + ", but game versions supporting formats 17 to " + lastPreMinorVersion + " require a pack_format field. Add \"pack_format\": " + j + " or require a version greater or equal to " + (lastPreMinorVersion + 1) + ".0.";
                        });
                    }

                    String s2 = this.validatePackFormatForRange(j, k);

                    if (s2 != null) {
                        return DataResult.error(() -> {
                            return s2;
                        });
                    }
                }

                return DataResult.success((new InclusiveRange(j, k)).map(PackFormat::of));
            }
        }

        private @Nullable String validatePackFormatForRange(int min, int max) {
            int k = (Integer) this.format.get();

            return k >= min && k <= max ? (k < 15 ? "Multi-version packs cannot support minimum version of less than 15, since this will leave versions in range unable to load pack." : null) : "Pack declared support for versions " + min + " to " + max + " but declared main format is " + k;
        }
    }

    public interface IntermediaryFormatHolder {

        PackFormat.IntermediaryFormat format();
    }
}
