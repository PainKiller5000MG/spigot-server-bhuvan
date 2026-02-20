package net.minecraft.world.level.block;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public interface SuspiciousEffectHolder {

    SuspiciousStewEffects getSuspiciousEffects();

    static List<SuspiciousEffectHolder> getAllEffectHolders() {
        return (List) BuiltInRegistries.ITEM.stream().map(SuspiciousEffectHolder::tryGet).filter(Objects::nonNull).collect(Collectors.toList());
    }

    static @Nullable SuspiciousEffectHolder tryGet(ItemLike item) {
        Item item1 = item.asItem();

        if (item1 instanceof BlockItem blockitem) {
            Block block = blockitem.getBlock();

            if (block instanceof SuspiciousEffectHolder suspiciouseffectholder) {
                return suspiciouseffectholder;
            }
        }

        Item item2 = item.asItem();

        if (item2 instanceof SuspiciousEffectHolder suspiciouseffectholder1) {
            return suspiciouseffectholder1;
        } else {
            return null;
        }
    }
}
