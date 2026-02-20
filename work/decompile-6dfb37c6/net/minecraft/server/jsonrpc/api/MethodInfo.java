package net.minecraft.server.jsonrpc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record MethodInfo<Params, Result>(String description, Optional<ParamInfo<Params>> params, Optional<ResultInfo<Result>> result) {

    public MethodInfo(String description, @Nullable ParamInfo<Params> paramInfo, @Nullable ResultInfo<Result> resultInfo) {
        this(description, Optional.ofNullable(paramInfo), Optional.ofNullable(resultInfo));
    }

    private static <Params> Optional<ParamInfo<Params>> toOptional(List<ParamInfo<Params>> list) {
        return list.isEmpty() ? Optional.empty() : Optional.of((ParamInfo) list.getFirst());
    }

    private static <Params> List<ParamInfo<Params>> toList(Optional<ParamInfo<Params>> opt) {
        return opt.isPresent() ? List.of((ParamInfo) opt.get()) : List.of();
    }

    private static <Params> Codec<Optional<ParamInfo<Params>>> paramsTypedCodec() {
        return ParamInfo.typedCodec().codec().listOf().xmap(MethodInfo::toOptional, MethodInfo::toList);
    }

    private static <Params, Result> MapCodec<MethodInfo<Params, Result>> typedCodec() {
        return RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.STRING.fieldOf("description").forGetter(MethodInfo::description), paramsTypedCodec().fieldOf("params").forGetter(MethodInfo::params), ResultInfo.typedCodec().optionalFieldOf("result").forGetter(MethodInfo::result)).apply(instance, MethodInfo::new);
        });
    }

    public MethodInfo.Named<Params, Result> named(Identifier name) {
        return new MethodInfo.Named<Params, Result>(name, this);
    }

    public static record Named<Params, Result>(Identifier name, MethodInfo<Params, Result> contents) {

        public static final Codec<MethodInfo.Named<?, ?>> CODEC = typedCodec();

        public static <Params, Result> Codec<MethodInfo.Named<Params, Result>> typedCodec() {
            return RecordCodecBuilder.create((instance) -> {
                return instance.group(Identifier.CODEC.fieldOf("name").forGetter(MethodInfo.Named::name), MethodInfo.typedCodec().forGetter(MethodInfo.Named::contents)).apply(instance, MethodInfo.Named::new);
            });
        }
    }
}
