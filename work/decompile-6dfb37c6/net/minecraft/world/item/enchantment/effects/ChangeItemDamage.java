package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

public record ChangeItemDamage(LevelBasedValue amount) implements EnchantmentEntityEffect {

    public static final MapCodec<ChangeItemDamage> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(LevelBasedValue.CODEC.fieldOf("amount").forGetter((changeitemdamage) -> {
            return changeitemdamage.amount;
        })).apply(instance, ChangeItemDamage::new);
    });

    @Override
    public void apply(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
        ItemStack itemstack = item.itemStack();

        if (itemstack.has(DataComponents.MAX_DAMAGE) && itemstack.has(DataComponents.DAMAGE)) {
            LivingEntity livingentity = item.owner();
            ServerPlayer serverplayer;

            if (livingentity instanceof ServerPlayer) {
                ServerPlayer serverplayer1 = (ServerPlayer) livingentity;

                serverplayer = serverplayer1;
            } else {
                serverplayer = null;
            }

            ServerPlayer serverplayer2 = serverplayer;
            int j = (int) this.amount.calculate(enchantmentLevel);

            itemstack.hurtAndBreak(j, serverLevel, serverplayer2, item.onBreak());
        }

    }

    @Override
    public MapCodec<ChangeItemDamage> codec() {
        return ChangeItemDamage.CODEC;
    }
}
