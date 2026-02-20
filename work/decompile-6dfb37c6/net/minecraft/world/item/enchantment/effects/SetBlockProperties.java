package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public record SetBlockProperties(BlockItemStateProperties properties, Vec3i offset, Optional<Holder<GameEvent>> triggerGameEvent) implements EnchantmentEntityEffect {

    public static final MapCodec<SetBlockProperties> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockItemStateProperties.CODEC.fieldOf("properties").forGetter(SetBlockProperties::properties), Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(SetBlockProperties::offset), GameEvent.CODEC.optionalFieldOf("trigger_game_event").forGetter(SetBlockProperties::triggerGameEvent)).apply(instance, SetBlockProperties::new);
    });

    public SetBlockProperties(BlockItemStateProperties properties) {
        this(properties, Vec3i.ZERO, Optional.of(GameEvent.BLOCK_CHANGE));
    }

    @Override
    public void apply(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
        BlockPos blockpos = BlockPos.containing(position).offset(this.offset);
        BlockState blockstate = entity.level().getBlockState(blockpos);
        BlockState blockstate1 = this.properties.apply(blockstate);

        if (blockstate != blockstate1 && entity.level().setBlock(blockpos, blockstate1, 3)) {
            this.triggerGameEvent.ifPresent((holder) -> {
                serverLevel.gameEvent(entity, holder, blockpos);
            });
        }

    }

    @Override
    public MapCodec<SetBlockProperties> codec() {
        return SetBlockProperties.CODEC;
    }
}
