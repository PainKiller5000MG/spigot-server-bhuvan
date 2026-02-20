package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import org.jspecify.annotations.Nullable;

public class WitherSkullBlock extends SkullBlock {

    public static final MapCodec<WitherSkullBlock> CODEC = simpleCodec(WitherSkullBlock::new);
    private static @Nullable BlockPattern witherPatternFull;
    private static @Nullable BlockPattern witherPatternBase;

    @Override
    public MapCodec<WitherSkullBlock> codec() {
        return WitherSkullBlock.CODEC;
    }

    protected WitherSkullBlock(BlockBehaviour.Properties properties) {
        super(SkullBlock.Types.WITHER_SKELETON, properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        checkSpawn(level, pos);
    }

    public static void checkSpawn(Level level, BlockPos pos) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof SkullBlockEntity skullblockentity) {
            checkSpawn(level, pos, skullblockentity);
        }

    }

    public static void checkSpawn(Level level, BlockPos pos, SkullBlockEntity placedSkull) {
        if (!level.isClientSide()) {
            BlockState blockstate = placedSkull.getBlockState();
            boolean flag = blockstate.is(Blocks.WITHER_SKELETON_SKULL) || blockstate.is(Blocks.WITHER_SKELETON_WALL_SKULL);

            if (flag && pos.getY() >= level.getMinY() && level.getDifficulty() != Difficulty.PEACEFUL) {
                BlockPattern.BlockPatternMatch blockpattern_blockpatternmatch = getOrCreateWitherFull().find(level, pos);

                if (blockpattern_blockpatternmatch != null) {
                    WitherBoss witherboss = EntityType.WITHER.create(level, EntitySpawnReason.TRIGGERED);

                    if (witherboss != null) {
                        CarvedPumpkinBlock.clearPatternBlocks(level, blockpattern_blockpatternmatch);
                        BlockPos blockpos1 = blockpattern_blockpatternmatch.getBlock(1, 2, 0).getPos();

                        witherboss.snapTo((double) blockpos1.getX() + 0.5D, (double) blockpos1.getY() + 0.55D, (double) blockpos1.getZ() + 0.5D, blockpattern_blockpatternmatch.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F, 0.0F);
                        witherboss.yBodyRot = blockpattern_blockpatternmatch.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F;
                        witherboss.makeInvulnerable();

                        for (ServerPlayer serverplayer : level.getEntitiesOfClass(ServerPlayer.class, witherboss.getBoundingBox().inflate(50.0D))) {
                            CriteriaTriggers.SUMMONED_ENTITY.trigger(serverplayer, witherboss);
                        }

                        level.addFreshEntity(witherboss);
                        CarvedPumpkinBlock.updatePatternBlocks(level, blockpattern_blockpatternmatch);
                    }

                }
            }
        }
    }

    public static boolean canSpawnMob(Level level, BlockPos pos, ItemStack itemStack) {
        return itemStack.is(Items.WITHER_SKELETON_SKULL) && pos.getY() >= level.getMinY() + 2 && level.getDifficulty() != Difficulty.PEACEFUL && !level.isClientSide() ? getOrCreateWitherBase().find(level, pos) != null : false;
    }

    private static BlockPattern getOrCreateWitherFull() {
        if (WitherSkullBlock.witherPatternFull == null) {
            WitherSkullBlock.witherPatternFull = BlockPatternBuilder.start().aisle("^^^", "###", "~#~").where('#', (blockinworld) -> {
                return blockinworld.getState().is(BlockTags.WITHER_SUMMON_BASE_BLOCKS);
            }).where('^', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.WITHER_SKELETON_SKULL).or(BlockStatePredicate.forBlock(Blocks.WITHER_SKELETON_WALL_SKULL)))).where('~', (blockinworld) -> {
                return blockinworld.getState().isAir();
            }).build();
        }

        return WitherSkullBlock.witherPatternFull;
    }

    private static BlockPattern getOrCreateWitherBase() {
        if (WitherSkullBlock.witherPatternBase == null) {
            WitherSkullBlock.witherPatternBase = BlockPatternBuilder.start().aisle("   ", "###", "~#~").where('#', (blockinworld) -> {
                return blockinworld.getState().is(BlockTags.WITHER_SUMMON_BASE_BLOCKS);
            }).where('~', (blockinworld) -> {
                return blockinworld.getState().isAir();
            }).build();
        }

        return WitherSkullBlock.witherPatternBase;
    }
}
