package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

public class BoneMealItem extends Item {

    public static final int GRASS_SPREAD_WIDTH = 3;
    public static final int GRASS_SPREAD_HEIGHT = 1;
    public static final int GRASS_COUNT_MULTIPLIER = 3;

    public BoneMealItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        BlockPos blockpos1 = blockpos.relative(context.getClickedFace());
        ItemStack itemstack = context.getItemInHand();

        if (growCrop(itemstack, level, blockpos)) {
            if (!level.isClientSide()) {
                itemstack.causeUseVibration(context.getPlayer(), GameEvent.ITEM_INTERACT_FINISH);
                level.levelEvent(1505, blockpos, 15);
            }

            return InteractionResult.SUCCESS;
        } else {
            BlockState blockstate = level.getBlockState(blockpos);
            boolean flag = blockstate.isFaceSturdy(level, blockpos, context.getClickedFace());

            if (flag && growWaterPlant(itemstack, level, blockpos1, context.getClickedFace())) {
                if (!level.isClientSide()) {
                    itemstack.causeUseVibration(context.getPlayer(), GameEvent.ITEM_INTERACT_FINISH);
                    level.levelEvent(1505, blockpos1, 15);
                }

                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    public static boolean growCrop(ItemStack itemStack, Level level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);
        Block block = blockstate.getBlock();

        if (block instanceof BonemealableBlock bonemealableblock) {
            if (bonemealableblock.isValidBonemealTarget(level, pos, blockstate)) {
                if (level instanceof ServerLevel) {
                    if (bonemealableblock.isBonemealSuccess(level, level.random, pos, blockstate)) {
                        bonemealableblock.performBonemeal((ServerLevel) level, level.random, pos, blockstate);
                    }

                    itemStack.shrink(1);
                }

                return true;
            }
        }

        return false;
    }

    public static boolean growWaterPlant(ItemStack itemStack, Level level, BlockPos pos, @Nullable Direction clickedFace) {
        if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).getAmount() == 8) {
            if (!(level instanceof ServerLevel)) {
                return true;
            } else {
                RandomSource randomsource = level.getRandom();

                label80:
                for (int i = 0; i < 128; ++i) {
                    BlockPos blockpos1 = pos;
                    BlockState blockstate = Blocks.SEAGRASS.defaultBlockState();

                    for (int j = 0; j < i / 16; ++j) {
                        blockpos1 = blockpos1.offset(randomsource.nextInt(3) - 1, (randomsource.nextInt(3) - 1) * randomsource.nextInt(3) / 2, randomsource.nextInt(3) - 1);
                        if (level.getBlockState(blockpos1).isCollisionShapeFullBlock(level, blockpos1)) {
                            continue label80;
                        }
                    }

                    Holder<Biome> holder = level.getBiome(blockpos1);

                    if (holder.is(BiomeTags.PRODUCES_CORALS_FROM_BONEMEAL)) {
                        if (i == 0 && clickedFace != null && clickedFace.getAxis().isHorizontal()) {
                            blockstate = (BlockState) BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.WALL_CORALS, level.random).map((holder1) -> {
                                return ((Block) holder1.value()).defaultBlockState();
                            }).orElse(blockstate);
                            if (blockstate.hasProperty(BaseCoralWallFanBlock.FACING)) {
                                blockstate = (BlockState) blockstate.setValue(BaseCoralWallFanBlock.FACING, clickedFace);
                            }
                        } else if (randomsource.nextInt(4) == 0) {
                            blockstate = (BlockState) BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.UNDERWATER_BONEMEALS, level.random).map((holder1) -> {
                                return ((Block) holder1.value()).defaultBlockState();
                            }).orElse(blockstate);
                        }
                    }

                    if (blockstate.is(BlockTags.WALL_CORALS, (blockbehaviour_blockstatebase) -> {
                        return blockbehaviour_blockstatebase.hasProperty(BaseCoralWallFanBlock.FACING);
                    })) {
                        for (int k = 0; !blockstate.canSurvive(level, blockpos1) && k < 4; ++k) {
                            blockstate = (BlockState) blockstate.setValue(BaseCoralWallFanBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(randomsource));
                        }
                    }

                    if (blockstate.canSurvive(level, blockpos1)) {
                        BlockState blockstate1 = level.getBlockState(blockpos1);

                        if (blockstate1.is(Blocks.WATER) && level.getFluidState(blockpos1).getAmount() == 8) {
                            level.setBlock(blockpos1, blockstate, 3);
                        } else if (blockstate1.is(Blocks.SEAGRASS) && ((BonemealableBlock) Blocks.SEAGRASS).isValidBonemealTarget(level, blockpos1, blockstate1) && randomsource.nextInt(10) == 0) {
                            ((BonemealableBlock) Blocks.SEAGRASS).performBonemeal((ServerLevel) level, randomsource, blockpos1, blockstate1);
                        }
                    }
                }

                itemStack.shrink(1);
                return true;
            }
        } else {
            return false;
        }
    }

    public static void addGrowthParticles(LevelAccessor level, BlockPos pos, int count) {
        BlockState blockstate = level.getBlockState(pos);
        Block block = blockstate.getBlock();

        if (block instanceof BonemealableBlock) {
            BonemealableBlock bonemealableblock = (BonemealableBlock) block;
            BlockPos blockpos1 = bonemealableblock.getParticlePos(pos);

            switch (bonemealableblock.getType()) {
                case NEIGHBOR_SPREADER:
                    ParticleUtils.spawnParticles(level, blockpos1, count * 3, 3.0D, 1.0D, false, ParticleTypes.HAPPY_VILLAGER);
                    break;
                case GROWER:
                    ParticleUtils.spawnParticleInBlock(level, blockpos1, count, ParticleTypes.HAPPY_VILLAGER);
            }
        } else if (blockstate.is(Blocks.WATER)) {
            ParticleUtils.spawnParticles(level, pos, count * 3, 3.0D, 1.0D, false, ParticleTypes.HAPPY_VILLAGER);
        }

    }
}
