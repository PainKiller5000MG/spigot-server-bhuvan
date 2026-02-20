package net.minecraft.world.level.block;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class TorchBlock extends BaseTorchBlock {

    protected static final MapCodec<SimpleParticleType> PARTICLE_OPTIONS_FIELD = BuiltInRegistries.PARTICLE_TYPE.byNameCodec().comapFlatMap((particletype) -> {
        DataResult dataresult;

        if (particletype instanceof SimpleParticleType simpleparticletype) {
            dataresult = DataResult.success(simpleparticletype);
        } else {
            dataresult = DataResult.error(() -> {
                return "Not a SimpleParticleType: " + String.valueOf(particletype);
            });
        }

        return dataresult;
    }, (simpleparticletype) -> {
        return simpleparticletype;
    }).fieldOf("particle_options");
    public static final MapCodec<TorchBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(TorchBlock.PARTICLE_OPTIONS_FIELD.forGetter((torchblock) -> {
            return torchblock.flameParticle;
        }), propertiesCodec()).apply(instance, TorchBlock::new);
    });
    protected final SimpleParticleType flameParticle;

    @Override
    public MapCodec<? extends TorchBlock> codec() {
        return TorchBlock.CODEC;
    }

    protected TorchBlock(SimpleParticleType flameParticle, BlockBehaviour.Properties properties) {
        super(properties);
        this.flameParticle = flameParticle;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double d0 = (double) pos.getX() + 0.5D;
        double d1 = (double) pos.getY() + 0.7D;
        double d2 = (double) pos.getZ() + 0.5D;

        level.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
        level.addParticle(this.flameParticle, d0, d1, d2, 0.0D, 0.0D, 0.0D);
    }
}
