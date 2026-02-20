package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class FillPlayerHead extends LootItemConditionalFunction {

    public static final MapCodec<FillPlayerHead> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(LootContext.EntityTarget.CODEC.fieldOf("entity").forGetter((fillplayerhead) -> {
            return fillplayerhead.entityTarget;
        })).apply(instance, FillPlayerHead::new);
    });
    private final LootContext.EntityTarget entityTarget;

    public FillPlayerHead(List<LootItemCondition> predicates, LootContext.EntityTarget entityTarget) {
        super(predicates);
        this.entityTarget = entityTarget;
    }

    @Override
    public LootItemFunctionType<FillPlayerHead> getType() {
        return LootItemFunctions.FILL_PLAYER_HEAD;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(this.entityTarget.contextParam());
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        if (itemStack.is(Items.PLAYER_HEAD)) {
            Object object = context.getOptionalParameter(this.entityTarget.contextParam());

            if (object instanceof Player) {
                Player player = (Player) object;

                itemStack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
            }
        }

        return itemStack;
    }

    public static LootItemConditionalFunction.Builder<?> fillPlayerHead(LootContext.EntityTarget entityTarget) {
        return simpleBuilder((list) -> {
            return new FillPlayerHead(list, entityTarget);
        });
    }
}
