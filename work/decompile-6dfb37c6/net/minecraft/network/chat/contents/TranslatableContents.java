package net.minecraft.network.chat.contents;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public class TranslatableContents implements ComponentContents {

    public static final Object[] NO_ARGS = new Object[0];
    private static final Codec<Object> PRIMITIVE_ARG_CODEC = ExtraCodecs.JAVA.validate(TranslatableContents::filterAllowedArguments);
    private static final Codec<Object> ARG_CODEC = Codec.either(TranslatableContents.PRIMITIVE_ARG_CODEC, ComponentSerialization.CODEC).xmap((either) -> {
        return either.map((object) -> {
            return object;
        }, (component) -> {
            return Objects.requireNonNullElse(component.tryCollapseToString(), component);
        });
    }, (object) -> {
        Either either;

        if (object instanceof Component component) {
            either = Either.right(component);
        } else {
            either = Either.left(object);
        }

        return either;
    });
    public static final MapCodec<TranslatableContents> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.STRING.fieldOf("translate").forGetter((translatablecontents) -> {
            return translatablecontents.key;
        }), Codec.STRING.lenientOptionalFieldOf("fallback").forGetter((translatablecontents) -> {
            return Optional.ofNullable(translatablecontents.fallback);
        }), TranslatableContents.ARG_CODEC.listOf().optionalFieldOf("with").forGetter((translatablecontents) -> {
            return adjustArgs(translatablecontents.args);
        })).apply(instance, TranslatableContents::create);
    });
    private static final FormattedText TEXT_PERCENT = FormattedText.of("%");
    private static final FormattedText TEXT_NULL = FormattedText.of("null");
    private final String key;
    private final @Nullable String fallback;
    private final Object[] args;
    private @Nullable Language decomposedWith;
    private List<FormattedText> decomposedParts = ImmutableList.of();
    private static final Pattern FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private static DataResult<Object> filterAllowedArguments(@Nullable Object result) {
        return !isAllowedPrimitiveArgument(result) ? DataResult.error(() -> {
            return "This value needs to be parsed as component";
        }) : DataResult.success(result);
    }

    public static boolean isAllowedPrimitiveArgument(@Nullable Object object) {
        return object instanceof Number || object instanceof Boolean || object instanceof String;
    }

    private static Optional<List<Object>> adjustArgs(Object[] args) {
        return args.length == 0 ? Optional.empty() : Optional.of(Arrays.asList(args));
    }

    private static Object[] adjustArgs(Optional<List<Object>> args) {
        return args.map((list) -> {
            return list.isEmpty() ? TranslatableContents.NO_ARGS : list.toArray();
        }).orElse(TranslatableContents.NO_ARGS);
    }

    private static TranslatableContents create(String key, Optional<String> fallback, Optional<List<Object>> args) {
        return new TranslatableContents(key, (String) fallback.orElse((Object) null), adjustArgs(args));
    }

    public TranslatableContents(String key, @Nullable String fallback, Object[] args) {
        this.key = key;
        this.fallback = fallback;
        this.args = args;
    }

    @Override
    public MapCodec<TranslatableContents> codec() {
        return TranslatableContents.MAP_CODEC;
    }

    private void decompose() {
        Language language = Language.getInstance();

        if (language != this.decomposedWith) {
            this.decomposedWith = language;
            String s = this.fallback != null ? language.getOrDefault(this.key, this.fallback) : language.getOrDefault(this.key);

            try {
                ImmutableList.Builder<FormattedText> immutablelist_builder = ImmutableList.builder();

                Objects.requireNonNull(immutablelist_builder);
                this.decomposeTemplate(s, immutablelist_builder::add);
                this.decomposedParts = immutablelist_builder.build();
            } catch (TranslatableFormatException translatableformatexception) {
                this.decomposedParts = ImmutableList.of(FormattedText.of(s));
            }

        }
    }

    private void decomposeTemplate(String template, Consumer<FormattedText> decomposedParts) {
        Matcher matcher = TranslatableContents.FORMAT_PATTERN.matcher(template);

        try {
            int i = 0;

            int j;
            int k;

            for (j = 0; matcher.find(j); j = k) {
                int l = matcher.start();

                k = matcher.end();
                if (l > j) {
                    String s1 = template.substring(j, l);

                    if (s1.indexOf(37) != -1) {
                        throw new IllegalArgumentException();
                    }

                    decomposedParts.accept(FormattedText.of(s1));
                }

                String s2 = matcher.group(2);
                String s3 = template.substring(l, k);

                if ("%".equals(s2) && "%%".equals(s3)) {
                    decomposedParts.accept(TranslatableContents.TEXT_PERCENT);
                } else {
                    if (!"s".equals(s2)) {
                        throw new TranslatableFormatException(this, "Unsupported format: '" + s3 + "'");
                    }

                    String s4 = matcher.group(1);
                    int i1 = s4 != null ? Integer.parseInt(s4) - 1 : i++;

                    decomposedParts.accept(this.getArgument(i1));
                }
            }

            if (j < template.length()) {
                String s5 = template.substring(j);

                if (s5.indexOf(37) != -1) {
                    throw new IllegalArgumentException();
                }

                decomposedParts.accept(FormattedText.of(s5));
            }

        } catch (IllegalArgumentException illegalargumentexception) {
            throw new TranslatableFormatException(this, illegalargumentexception);
        }
    }

    private FormattedText getArgument(int index) {
        if (index >= 0 && index < this.args.length) {
            Object object = this.args[index];

            if (object instanceof Component) {
                Component component = (Component) object;

                return component;
            } else {
                return object == null ? TranslatableContents.TEXT_NULL : FormattedText.of(object.toString());
            }
        } else {
            throw new TranslatableFormatException(this, index);
        }
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style currentStyle) {
        this.decompose();

        for (FormattedText formattedtext : this.decomposedParts) {
            Optional<T> optional = formattedtext.<T>visit(output, currentStyle);

            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
        this.decompose();

        for (FormattedText formattedtext : this.decomposedParts) {
            Optional<T> optional = formattedtext.<T>visit(output);

            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack source, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        Object[] aobject = new Object[this.args.length];

        for (int j = 0; j < aobject.length; ++j) {
            Object object = this.args[j];

            if (object instanceof Component component) {
                aobject[j] = ComponentUtils.updateForEntity(source, component, entity, recursionDepth);
            } else {
                aobject[j] = object;
            }
        }

        return MutableComponent.create(new TranslatableContents(this.key, this.fallback, aobject));
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            boolean flag;

            if (o instanceof TranslatableContents) {
                TranslatableContents translatablecontents = (TranslatableContents) o;

                if (Objects.equals(this.key, translatablecontents.key) && Objects.equals(this.fallback, translatablecontents.fallback) && Arrays.equals(this.args, translatablecontents.args)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        int i = Objects.hashCode(this.key);

        i = 31 * i + Objects.hashCode(this.fallback);
        i = 31 * i + Arrays.hashCode(this.args);
        return i;
    }

    public String toString() {
        return "translation{key='" + this.key + "'" + (this.fallback != null ? ", fallback='" + this.fallback + "'" : "") + ", args=" + Arrays.toString(this.args) + "}";
    }

    public String getKey() {
        return this.key;
    }

    public @Nullable String getFallback() {
        return this.fallback;
    }

    public Object[] getArgs() {
        return this.args;
    }
}
