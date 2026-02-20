package net.minecraft.world.level;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;

public interface Spawner {

    void setEntityId(EntityType<?> type, RandomSource random);

    static void appendHoverText(@Nullable TypedEntityData<BlockEntityType<?>> data, Consumer<Component> consumer, String nextSpawnDataTagKey) {
        Component component = getSpawnEntityDisplayName(data, nextSpawnDataTagKey);

        if (component != null) {
            consumer.accept(component);
        } else {
            consumer.accept(CommonComponents.EMPTY);
            consumer.accept(Component.translatable("block.minecraft.spawner.desc1").withStyle(ChatFormatting.GRAY));
            consumer.accept(CommonComponents.space().append((Component) Component.translatable("block.minecraft.spawner.desc2").withStyle(ChatFormatting.BLUE)));
        }

    }

    static @Nullable Component getSpawnEntityDisplayName(@Nullable TypedEntityData<BlockEntityType<?>> data, String nextSpawnDataTagKey) {
        return data == null ? null : (Component) data.getUnsafe().getCompound(nextSpawnDataTagKey).flatMap((compoundtag) -> {
            return compoundtag.getCompound("entity");
        }).flatMap((compoundtag) -> {
            return compoundtag.read("id", EntityType.CODEC);
        }).map((entitytype) -> {
            return Component.translatable(entitytype.getDescriptionId()).withStyle(ChatFormatting.GRAY);
        }).orElse((Object) null);
    }
}
