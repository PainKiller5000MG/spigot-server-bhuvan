package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetWritableBookPagesFunction extends LootItemConditionalFunction {

    public static final MapCodec<SetWritableBookPagesFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(WritableBookContent.PAGES_CODEC.fieldOf("pages").forGetter((setwritablebookpagesfunction) -> {
            return setwritablebookpagesfunction.pages;
        }), ListOperation.codec(100).forGetter((setwritablebookpagesfunction) -> {
            return setwritablebookpagesfunction.pageOperation;
        }))).apply(instance, SetWritableBookPagesFunction::new);
    });
    private final List<Filterable<String>> pages;
    private final ListOperation pageOperation;

    protected SetWritableBookPagesFunction(List<LootItemCondition> predicates, List<Filterable<String>> pages, ListOperation pageOperation) {
        super(predicates);
        this.pages = pages;
        this.pageOperation = pageOperation;
    }

    @Override
    protected ItemStack run(ItemStack itemStack, LootContext context) {
        itemStack.update(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY, this::apply);
        return itemStack;
    }

    public WritableBookContent apply(WritableBookContent original) {
        List<Filterable<String>> list = this.pageOperation.<Filterable<String>>apply(original.pages(), this.pages, 100);

        return original.withReplacedPages(list);
    }

    @Override
    public LootItemFunctionType<SetWritableBookPagesFunction> getType() {
        return LootItemFunctions.SET_WRITABLE_BOOK_PAGES;
    }
}
