package net.minecraft.world.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

public class ShieldItem extends Item {

    public ShieldItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack itemStack) {
        DyeColor dyecolor = (DyeColor) itemStack.get(DataComponents.BASE_COLOR);

        return (Component) (dyecolor != null ? Component.translatable(this.descriptionId + "." + dyecolor.getName()) : super.getName(itemStack));
    }
}
