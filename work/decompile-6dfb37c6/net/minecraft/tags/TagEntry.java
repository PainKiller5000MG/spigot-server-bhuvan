package net.minecraft.tags;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public class TagEntry {

    private static final Codec<TagEntry> FULL_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("id").forGetter(TagEntry::elementOrTag), Codec.BOOL.optionalFieldOf("required", true).forGetter((tagentry) -> {
            return tagentry.required;
        })).apply(instance, TagEntry::new);
    });
    public static final Codec<TagEntry> CODEC = Codec.either(ExtraCodecs.TAG_OR_ELEMENT_ID, TagEntry.FULL_CODEC).xmap((either) -> {
        return (TagEntry) either.map((extracodecs_tagorelementlocation) -> {
            return new TagEntry(extracodecs_tagorelementlocation, true);
        }, (tagentry) -> {
            return tagentry;
        });
    }, (tagentry) -> {
        return tagentry.required ? Either.left(tagentry.elementOrTag()) : Either.right(tagentry);
    });
    private final Identifier id;
    private final boolean tag;
    private final boolean required;

    private TagEntry(Identifier id, boolean tag, boolean required) {
        this.id = id;
        this.tag = tag;
        this.required = required;
    }

    private TagEntry(ExtraCodecs.TagOrElementLocation elementOrTag, boolean required) {
        this.id = elementOrTag.id();
        this.tag = elementOrTag.tag();
        this.required = required;
    }

    private ExtraCodecs.TagOrElementLocation elementOrTag() {
        return new ExtraCodecs.TagOrElementLocation(this.id, this.tag);
    }

    public static TagEntry element(Identifier id) {
        return new TagEntry(id, false, true);
    }

    public static TagEntry optionalElement(Identifier id) {
        return new TagEntry(id, false, false);
    }

    public static TagEntry tag(Identifier id) {
        return new TagEntry(id, true, true);
    }

    public static TagEntry optionalTag(Identifier id) {
        return new TagEntry(id, true, false);
    }

    public <T> boolean build(TagEntry.Lookup<T> lookup, Consumer<T> output) {
        if (this.tag) {
            Collection<T> collection = lookup.tag(this.id);

            if (collection == null) {
                return !this.required;
            }

            collection.forEach(output);
        } else {
            T t0 = lookup.element(this.id, this.required);

            if (t0 == null) {
                return !this.required;
            }

            output.accept(t0);
        }

        return true;
    }

    public void visitRequiredDependencies(Consumer<Identifier> output) {
        if (this.tag && this.required) {
            output.accept(this.id);
        }

    }

    public void visitOptionalDependencies(Consumer<Identifier> output) {
        if (this.tag && !this.required) {
            output.accept(this.id);
        }

    }

    public boolean verifyIfPresent(Predicate<Identifier> elementCheck, Predicate<Identifier> tagCheck) {
        return !this.required || (this.tag ? tagCheck : elementCheck).test(this.id);
    }

    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();

        if (this.tag) {
            stringbuilder.append('#');
        }

        stringbuilder.append(this.id);
        if (!this.required) {
            stringbuilder.append('?');
        }

        return stringbuilder.toString();
    }

    public interface Lookup<T> {

        @Nullable
        T element(Identifier key, boolean required);

        @Nullable
        Collection<T> tag(Identifier key);
    }
}
