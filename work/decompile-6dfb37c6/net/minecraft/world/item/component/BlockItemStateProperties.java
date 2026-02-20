package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public record BlockItemStateProperties(Map<String, String> properties) implements TooltipProvider {

    public static final BlockItemStateProperties EMPTY = new BlockItemStateProperties(Map.of());
    public static final Codec<BlockItemStateProperties> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(BlockItemStateProperties::new, BlockItemStateProperties::properties);
    private static final StreamCodec<ByteBuf, Map<String, String>> PROPERTIES_STREAM_CODEC = ByteBufCodecs.map(Object2ObjectOpenHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);
    public static final StreamCodec<ByteBuf, BlockItemStateProperties> STREAM_CODEC = BlockItemStateProperties.PROPERTIES_STREAM_CODEC.map(BlockItemStateProperties::new, BlockItemStateProperties::properties);

    public <T extends Comparable<T>> BlockItemStateProperties with(Property<T> property, T value) {
        return new BlockItemStateProperties(Util.copyAndPut(this.properties, property.getName(), property.getName(value)));
    }

    public <T extends Comparable<T>> BlockItemStateProperties with(Property<T> property, BlockState state) {
        return this.with(property, state.getValue(property));
    }

    public <T extends Comparable<T>> @Nullable T get(Property<T> property) {
        String s = (String) this.properties.get(property.getName());

        return (T) (s == null ? null : (Comparable) property.getValue(s).orElse((Object) null));
    }

    public BlockState apply(BlockState state) {
        StateDefinition<Block, BlockState> statedefinition = state.getBlock().getStateDefinition();

        for (Map.Entry<String, String> map_entry : this.properties.entrySet()) {
            Property<?> property = statedefinition.getProperty((String) map_entry.getKey());

            if (property != null) {
                state = updateState(state, property, (String) map_entry.getValue());
            }
        }

        return state;
    }

    private static <T extends Comparable<T>> BlockState updateState(BlockState state, Property<T> property, String value) {
        return (BlockState) property.getValue(value).map((comparable) -> {
            return (BlockState) state.setValue(property, comparable);
        }).orElse(state);
    }

    public boolean isEmpty() {
        return this.properties.isEmpty();
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        Integer integer = (Integer) this.get(BeehiveBlock.HONEY_LEVEL);

        if (integer != null) {
            consumer.accept(Component.translatable("container.beehive.honey", integer, 5).withStyle(ChatFormatting.GRAY));
        }

    }
}
