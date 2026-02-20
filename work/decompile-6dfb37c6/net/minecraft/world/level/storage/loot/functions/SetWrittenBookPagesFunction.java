package net.minecraft.world.level.storage.loot.functions;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetWrittenBookPagesFunction extends LootItemConditionalFunction {

    public static final MapCodec<SetWrittenBookPagesFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(WrittenBookContent.PAGES_CODEC.fieldOf("pages").forGetter((setwrittenbookpagesfunction) -> {
            return setwrittenbookpagesfunction.pages;
        }), ListOperation.UNLIMITED_CODEC.forGetter((setwrittenbookpagesfunction) -> {
            return setwrittenbookpagesfunction.pageOperation;
        }))).apply(instance, SetWrittenBookPagesFunction::new);
    });
    private final List<Filterable<Component>> pages;
    private final ListOperation pageOperation;

    protected SetWrittenBookPagesFunction(List<LootItemCondition> predicates, List<Filterable<Component>> pages, ListOperation pageOperation) {
        super(predicates);
        this.pages = pages;
        this.pageOperation = pageOperation;
    }

    @Override
    protected ItemStack run(ItemStack itemStack, LootContext context) {
        itemStack.update(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY, this::apply);
        return itemStack;
    }

    @VisibleForTesting
    public WrittenBookContent apply(WrittenBookContent original) {
        List<Filterable<Component>> list = this.pageOperation.<Filterable<Component>>apply(original.pages(), this.pages);

        return original.withReplacedPages(list);
    }

    @Override
    public LootItemFunctionType<SetWrittenBookPagesFunction> getType() {
        return LootItemFunctions.SET_WRITTEN_BOOK_PAGES;
    }
}
