package net.minecraft.network.chat.contents;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.data.DataSource;
import net.minecraft.network.chat.contents.data.DataSources;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class NbtContents implements ComponentContents {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<NbtContents> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.STRING.fieldOf("nbt").forGetter(NbtContents::getNbtPath), Codec.BOOL.lenientOptionalFieldOf("interpret", false).forGetter(NbtContents::isInterpreting), ComponentSerialization.CODEC.lenientOptionalFieldOf("separator").forGetter(NbtContents::getSeparator), DataSources.CODEC.forGetter(NbtContents::getDataSource)).apply(instance, NbtContents::new);
    });
    private final boolean interpreting;
    private final Optional<Component> separator;
    private final String nbtPathPattern;
    private final DataSource dataSource;
    protected final NbtPathArgument.@Nullable NbtPath compiledNbtPath;

    public NbtContents(String nbtPath, boolean interpreting, Optional<Component> separator, DataSource dataSource) {
        this(nbtPath, compileNbtPath(nbtPath), interpreting, separator, dataSource);
    }

    private NbtContents(String nbtPathPattern, NbtPathArgument.@Nullable NbtPath compiledNbtPath, boolean interpreting, Optional<Component> separator, DataSource dataSource) {
        this.nbtPathPattern = nbtPathPattern;
        this.compiledNbtPath = compiledNbtPath;
        this.interpreting = interpreting;
        this.separator = separator;
        this.dataSource = dataSource;
    }

    private static NbtPathArgument.@Nullable NbtPath compileNbtPath(String path) {
        try {
            return (new NbtPathArgument()).parse(new StringReader(path));
        } catch (CommandSyntaxException commandsyntaxexception) {
            return null;
        }
    }

    public String getNbtPath() {
        return this.nbtPathPattern;
    }

    public boolean isInterpreting() {
        return this.interpreting;
    }

    public Optional<Component> getSeparator() {
        return this.separator;
    }

    public DataSource getDataSource() {
        return this.dataSource;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            boolean flag;

            if (o instanceof NbtContents) {
                NbtContents nbtcontents = (NbtContents) o;

                if (this.dataSource.equals(nbtcontents.dataSource) && this.separator.equals(nbtcontents.separator) && this.interpreting == nbtcontents.interpreting && this.nbtPathPattern.equals(nbtcontents.nbtPathPattern)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        int i = this.interpreting ? 1 : 0;

        i = 31 * i + this.separator.hashCode();
        i = 31 * i + this.nbtPathPattern.hashCode();
        i = 31 * i + this.dataSource.hashCode();
        return i;
    }

    public String toString() {
        String s = String.valueOf(this.dataSource);

        return "nbt{" + s + ", interpreting=" + this.interpreting + ", separator=" + String.valueOf(this.separator) + "}";
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack source, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        if (source != null && this.compiledNbtPath != null) {
            Stream<Tag> stream = this.dataSource.getData(source).flatMap((compoundtag) -> {
                try {
                    return this.compiledNbtPath.get(compoundtag).stream();
                } catch (CommandSyntaxException commandsyntaxexception) {
                    return Stream.empty();
                }
            });

            if (this.interpreting) {
                RegistryOps<Tag> registryops = source.registryAccess().<Tag>createSerializationContext(NbtOps.INSTANCE);
                Component component = (Component) DataFixUtils.orElse(ComponentUtils.updateForEntity(source, this.separator, entity, recursionDepth), ComponentUtils.DEFAULT_NO_STYLE_SEPARATOR);

                return (MutableComponent) stream.flatMap((tag) -> {
                    try {
                        Component component1 = (Component) ComponentSerialization.CODEC.parse(registryops, tag).getOrThrow();

                        return Stream.of(ComponentUtils.updateForEntity(source, component1, entity, recursionDepth));
                    } catch (Exception exception) {
                        NbtContents.LOGGER.warn("Failed to parse component: {}", tag, exception);
                        return Stream.of();
                    }
                }).reduce((mutablecomponent, mutablecomponent1) -> {
                    return mutablecomponent.append(component).append((Component) mutablecomponent1);
                }).orElseGet(Component::empty);
            } else {
                Stream<String> stream1 = stream.map(NbtContents::asString);

                return (MutableComponent) ComponentUtils.updateForEntity(source, this.separator, entity, recursionDepth).map((mutablecomponent) -> {
                    return (MutableComponent) stream1.map(Component::literal).reduce((mutablecomponent1, mutablecomponent2) -> {
                        return mutablecomponent1.append((Component) mutablecomponent).append((Component) mutablecomponent2);
                    }).orElseGet(Component::empty);
                }).orElseGet(() -> {
                    return Component.literal((String) stream1.collect(Collectors.joining(", ")));
                });
            }
        } else {
            return Component.empty();
        }
    }

    private static String asString(Tag tag) {
        if (tag instanceof StringTag stringtag) {
            StringTag stringtag1 = stringtag;

            try {
                s = stringtag1.value();
            } catch (Throwable throwable) {
                throw new MatchException(throwable.toString(), throwable);
            }

            String s1 = s;

            return s1;
        } else {
            return tag.toString();
        }
    }

    @Override
    public MapCodec<NbtContents> codec() {
        return NbtContents.MAP_CODEC;
    }
}
