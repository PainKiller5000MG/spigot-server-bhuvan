package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class WebBlock extends Block {

    public static final MapCodec<WebBlock> CODEC = simpleCodec(WebBlock::new);

    @Override
    public MapCodec<WebBlock> codec() {
        return WebBlock.CODEC;
    }

    public WebBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        Vec3 vec3 = new Vec3(0.25D, (double) 0.05F, 0.25D);

        if (entity instanceof LivingEntity livingentity) {
            if (livingentity.hasEffect(MobEffects.WEAVING)) {
                vec3 = new Vec3(0.5D, 0.25D, 0.5D);
            }
        }

        entity.makeStuckInBlock(state, vec3);
    }
}
