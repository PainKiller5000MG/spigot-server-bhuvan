package net.minecraft.world.item.slot;

import com.mojang.datafixers.Products;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;

public abstract class TransformedSlotSource implements SlotSource {

    protected final SlotSource slotSource;

    protected TransformedSlotSource(SlotSource slotSource) {
        this.slotSource = slotSource;
    }

    @Override
    public abstract MapCodec<? extends TransformedSlotSource> codec();

    protected static <T extends TransformedSlotSource> Products.P1<RecordCodecBuilder.Mu<T>, SlotSource> commonFields(RecordCodecBuilder.Instance<T> i) {
        return i.group(SlotSources.CODEC.fieldOf("slot_source").forGetter((transformedslotsource) -> {
            return transformedslotsource.slotSource;
        }));
    }

    protected abstract SlotCollection transform(SlotCollection slots);

    @Override
    public final SlotCollection provide(LootContext context) {
        return this.transform(this.slotSource.provide(context));
    }

    @Override
    public void validate(ValidationContext context) {
        SlotSource.super.validate(context);
        this.slotSource.validate(context.forChild(new ProblemReporter.FieldPathElement("slot_source")));
    }
}
