package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetBookCoverFunction extends LootItemConditionalFunction {

    public static final MapCodec<SetBookCoverFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(Filterable.codec(Codec.string(0, 32)).optionalFieldOf("title").forGetter((setbookcoverfunction) -> {
            return setbookcoverfunction.title;
        }), Codec.STRING.optionalFieldOf("author").forGetter((setbookcoverfunction) -> {
            return setbookcoverfunction.author;
        }), ExtraCodecs.intRange(0, 3).optionalFieldOf("generation").forGetter((setbookcoverfunction) -> {
            return setbookcoverfunction.generation;
        }))).apply(instance, SetBookCoverFunction::new);
    });
    private final Optional<String> author;
    private final Optional<Filterable<String>> title;
    private final Optional<Integer> generation;

    public SetBookCoverFunction(List<LootItemCondition> predicates, Optional<Filterable<String>> title, Optional<String> author, Optional<Integer> generation) {
        super(predicates);
        this.author = author;
        this.title = title;
        this.generation = generation;
    }

    @Override
    protected ItemStack run(ItemStack itemStack, LootContext context) {
        itemStack.update(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY, this::apply);
        return itemStack;
    }

    private WrittenBookContent apply(WrittenBookContent original) {
        Optional optional = this.title;

        Objects.requireNonNull(original);
        Filterable filterable = (Filterable) optional.orElseGet(original::title);
        Optional optional1 = this.author;

        Objects.requireNonNull(original);
        String s = (String) optional1.orElseGet(original::author);
        Optional optional2 = this.generation;

        Objects.requireNonNull(original);
        return new WrittenBookContent(filterable, s, (Integer) optional2.orElseGet(original::generation), original.pages(), original.resolved());
    }

    @Override
    public LootItemFunctionType<SetBookCoverFunction> getType() {
        return LootItemFunctions.SET_BOOK_COVER;
    }
}
