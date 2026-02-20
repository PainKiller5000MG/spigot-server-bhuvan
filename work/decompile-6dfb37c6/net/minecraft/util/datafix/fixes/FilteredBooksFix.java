package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import net.minecraft.util.Util;

public class FilteredBooksFix extends ItemStackTagFix {

    public FilteredBooksFix(Schema outputSchema) {
        super(outputSchema, "Remove filtered text from books", (s) -> {
            return s.equals("minecraft:writable_book") || s.equals("minecraft:written_book");
        });
    }

    @Override
    protected Typed<?> fixItemStackTag(Typed<?> tag) {
        return Util.writeAndReadTypedOrThrow(tag, tag.getType(), (dynamic) -> {
            return dynamic.remove("filtered_title").remove("filtered_pages");
        });
    }
}
