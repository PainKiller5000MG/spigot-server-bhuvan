package net.minecraft.core.dispenser;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

public class EquipmentDispenseItemBehavior extends DefaultDispenseItemBehavior {

    public static final EquipmentDispenseItemBehavior INSTANCE = new EquipmentDispenseItemBehavior();

    public EquipmentDispenseItemBehavior() {}

    @Override
    protected ItemStack execute(BlockSource source, ItemStack dispensed) {
        return dispenseEquipment(source, dispensed) ? dispensed : super.execute(source, dispensed);
    }

    public static boolean dispenseEquipment(BlockSource source, ItemStack dispensed) {
        BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));
        List<LivingEntity> list = source.level().<LivingEntity>getEntitiesOfClass(LivingEntity.class, new AABB(blockpos), (livingentity) -> {
            return livingentity.canEquipWithDispenser(dispensed);
        });

        if (list.isEmpty()) {
            return false;
        } else {
            LivingEntity livingentity = (LivingEntity) list.getFirst();
            EquipmentSlot equipmentslot = livingentity.getEquipmentSlotForItem(dispensed);
            ItemStack itemstack1 = dispensed.split(1);

            livingentity.setItemSlot(equipmentslot, itemstack1);
            if (livingentity instanceof Mob) {
                Mob mob = (Mob) livingentity;

                mob.setGuaranteedDrop(equipmentslot);
                mob.setPersistenceRequired();
            }

            return true;
        }
    }
}
