package net.minecraft.core.component.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.criterion.CollectionPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.advancements.criterion.SingleComponentItemPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

public record WrittenBookPredicate(Optional<CollectionPredicate<Filterable<Component>, WrittenBookPredicate.PagePredicate>> pages, Optional<String> author, Optional<String> title, MinMaxBounds.Ints generation, Optional<Boolean> resolved) implements SingleComponentItemPredicate<WrittenBookContent> {

    public static final Codec<WrittenBookPredicate> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(CollectionPredicate.codec(WrittenBookPredicate.PagePredicate.CODEC).optionalFieldOf("pages").forGetter(WrittenBookPredicate::pages), Codec.STRING.optionalFieldOf("author").forGetter(WrittenBookPredicate::author), Codec.STRING.optionalFieldOf("title").forGetter(WrittenBookPredicate::title), MinMaxBounds.Ints.CODEC.optionalFieldOf("generation", MinMaxBounds.Ints.ANY).forGetter(WrittenBookPredicate::generation), Codec.BOOL.optionalFieldOf("resolved").forGetter(WrittenBookPredicate::resolved)).apply(instance, WrittenBookPredicate::new);
    });

    @Override
    public DataComponentType<WrittenBookContent> componentType() {
        return DataComponents.WRITTEN_BOOK_CONTENT;
    }

    public boolean matches(WrittenBookContent value) {
        return this.author.isPresent() && !((String) this.author.get()).equals(value.author()) ? false : (this.title.isPresent() && !((String) this.title.get()).equals(value.title().raw()) ? false : (!this.generation.matches(value.generation()) ? false : (this.resolved.isPresent() && (Boolean) this.resolved.get() != value.resolved() ? false : !this.pages.isPresent() || ((CollectionPredicate) this.pages.get()).test(value.pages()))));
    }

    public static record PagePredicate(Component contents) implements Predicate<Filterable<Component>> {

        public static final Codec<WrittenBookPredicate.PagePredicate> CODEC = ComponentSerialization.CODEC.xmap(WrittenBookPredicate.PagePredicate::new, WrittenBookPredicate.PagePredicate::contents);

        public boolean test(Filterable<Component> value) {
            return ((Component) value.raw()).equals(this.contents);
        }
    }
}
