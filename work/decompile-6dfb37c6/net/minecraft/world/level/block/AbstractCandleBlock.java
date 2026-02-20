package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractCandleBlock extends Block {

    public static final int LIGHT_PER_CANDLE = 3;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    @Override
    protected abstract MapCodec<? extends AbstractCandleBlock> codec();

    protected AbstractCandleBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    protected abstract Iterable<Vec3> getParticleOffsets(BlockState state);

    public static boolean isLit(BlockState state) {
        return state.hasProperty(AbstractCandleBlock.LIT) && (state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES)) && (Boolean) state.getValue(AbstractCandleBlock.LIT);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult blockHit, Projectile projectile) {
        if (!level.isClientSide() && projectile.isOnFire() && this.canBeLit(state)) {
            setLit(level, state, blockHit.getBlockPos(), true);
        }

    }

    protected boolean canBeLit(BlockState state) {
        return !(Boolean) state.getValue(AbstractCandleBlock.LIT);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(AbstractCandleBlock.LIT)) {
            this.getParticleOffsets(state).forEach((vec3) -> {
                addParticlesAndSound(level, vec3.add((double) pos.getX(), (double) pos.getY(), (double) pos.getZ()), random);
            });
        }
    }

    private static void addParticlesAndSound(Level level, Vec3 pos, RandomSource random) {
        float f = random.nextFloat();

        if (f < 0.3F) {
            level.addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D);
            if (f < 0.17F) {
                level.playLocalSound(pos.x + 0.5D, pos.y + 0.5D, pos.z + 0.5D, SoundEvents.CANDLE_AMBIENT, SoundSource.BLOCKS, 1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
            }
        }

        level.addParticle(ParticleTypes.SMALL_FLAME, pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D);
    }

    public static void extinguish(@Nullable Player player, BlockState state, LevelAccessor level, BlockPos pos) {
        setLit(level, state, pos, false);
        if (state.getBlock() instanceof AbstractCandleBlock) {
            ((AbstractCandleBlock) state.getBlock()).getParticleOffsets(state).forEach((vec3) -> {
                level.addParticle(ParticleTypes.SMOKE, (double) pos.getX() + vec3.x(), (double) pos.getY() + vec3.y(), (double) pos.getZ() + vec3.z(), 0.0D, (double) 0.1F, 0.0D);
            });
        }

        level.playSound((Entity) null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, (Holder) GameEvent.BLOCK_CHANGE, pos);
    }

    private static void setLit(LevelAccessor level, BlockState state, BlockPos pos, boolean lit) {
        level.setBlock(pos, (BlockState) state.setValue(AbstractCandleBlock.LIT, lit), 11);
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (explosion.canTriggerBlocks() && (Boolean) state.getValue(AbstractCandleBlock.LIT)) {
            extinguish((Player) null, state, level, pos);
        }

        super.onExplosionHit(state, level, pos, explosion, onHit);
    }
}
