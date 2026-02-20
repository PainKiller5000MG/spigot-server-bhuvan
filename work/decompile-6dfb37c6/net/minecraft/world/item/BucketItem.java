package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

public class BucketItem extends Item implements DispensibleContainerItem {

    public final Fluid content;

    public BucketItem(Fluid content, Item.Properties properties) {
        super(properties);
        this.content = content;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        BlockHitResult blockhitresult = getPlayerPOVHitResult(level, player, this.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);

        if (blockhitresult.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        } else if (blockhitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        } else {
            BlockPos blockpos = blockhitresult.getBlockPos();
            Direction direction = blockhitresult.getDirection();
            BlockPos blockpos1 = blockpos.relative(direction);

            if (level.mayInteract(player, blockpos) && player.mayUseItemAt(blockpos1, direction, itemstack)) {
                if (this.content == Fluids.EMPTY) {
                    BlockState blockstate = level.getBlockState(blockpos);
                    Block block = blockstate.getBlock();

                    if (block instanceof BucketPickup) {
                        BucketPickup bucketpickup = (BucketPickup) block;
                        ItemStack itemstack1 = bucketpickup.pickupBlock(player, level, blockpos, blockstate);

                        if (!itemstack1.isEmpty()) {
                            player.awardStat(Stats.ITEM_USED.get(this));
                            bucketpickup.getPickupSound().ifPresent((soundevent) -> {
                                player.playSound(soundevent, 1.0F, 1.0F);
                            });
                            level.gameEvent(player, (Holder) GameEvent.FLUID_PICKUP, blockpos);
                            ItemStack itemstack2 = ItemUtils.createFilledResult(itemstack, player, itemstack1);

                            if (!level.isClientSide()) {
                                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer) player, itemstack1);
                            }

                            return InteractionResult.SUCCESS.heldItemTransformedTo(itemstack2);
                        }
                    }

                    return InteractionResult.FAIL;
                } else {
                    BlockState blockstate1 = level.getBlockState(blockpos);
                    BlockPos blockpos2 = blockstate1.getBlock() instanceof LiquidBlockContainer && this.content == Fluids.WATER ? blockpos : blockpos1;

                    if (this.emptyContents(player, level, blockpos2, blockhitresult)) {
                        this.checkExtraContent(player, level, itemstack, blockpos2);
                        if (player instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) player, blockpos2, itemstack);
                        }

                        player.awardStat(Stats.ITEM_USED.get(this));
                        ItemStack itemstack3 = ItemUtils.createFilledResult(itemstack, player, getEmptySuccessItem(itemstack, player));

                        return InteractionResult.SUCCESS.heldItemTransformedTo(itemstack3);
                    } else {
                        return InteractionResult.FAIL;
                    }
                }
            } else {
                return InteractionResult.FAIL;
            }
        }
    }

    public static ItemStack getEmptySuccessItem(ItemStack itemStack, Player player) {
        return !player.hasInfiniteMaterials() ? new ItemStack(Items.BUCKET) : itemStack;
    }

    @Override
    public void checkExtraContent(@Nullable LivingEntity user, Level level, ItemStack itemStack, BlockPos pos) {}

    @Override
    public boolean emptyContents(@Nullable LivingEntity user, Level level, BlockPos pos, @Nullable BlockHitResult hitResult) {
        Fluid fluid = this.content;

        if (!(fluid instanceof FlowingFluid flowingfluid)) {
            return false;
        } else {
            Block block;
            boolean flag;
            boolean flag1;
            boolean flag2;
            label106:
            {
                blockstate = level.getBlockState(pos);
                block = blockstate.getBlock();
                flag = blockstate.canBeReplaced(this.content);
                flag1 = user != null && user.isShiftKeyDown();
                if (!flag) {
                    label103:
                    {
                        if (block instanceof LiquidBlockContainer) {
                            LiquidBlockContainer liquidblockcontainer = (LiquidBlockContainer) block;

                            if (liquidblockcontainer.canPlaceLiquid(user, level, pos, blockstate, this.content)) {
                                break label103;
                            }
                        }

                        flag2 = false;
                        break label106;
                    }
                }

                flag2 = true;
            }

            boolean flag3 = flag2;
            boolean flag4 = blockstate.isAir() || flag3 && (!flag1 || hitResult == null);

            if (!flag4) {
                return hitResult != null && this.emptyContents(user, level, hitResult.getBlockPos().relative(hitResult.getDirection()), (BlockHitResult) null);
            } else if ((Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos) && this.content.is(FluidTags.WATER)) {
                int i = pos.getX();
                int j = pos.getY();
                int k = pos.getZ();

                level.playSound(user, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);

                for (int l = 0; l < 8; ++l) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE, (double) ((float) i + level.random.nextFloat()), (double) ((float) j + level.random.nextFloat()), (double) ((float) k + level.random.nextFloat()), 0.0D, 0.0D, 0.0D);
                }

                return true;
            } else {
                if (block instanceof LiquidBlockContainer) {
                    LiquidBlockContainer liquidblockcontainer1 = (LiquidBlockContainer) block;

                    if (this.content == Fluids.WATER) {
                        liquidblockcontainer1.placeLiquid(level, pos, blockstate, flowingfluid.getSource(false));
                        this.playEmptySound(user, level, pos);
                        return true;
                    }
                }

                if (!level.isClientSide() && flag && !blockstate.liquid()) {
                    level.destroyBlock(pos, true);
                }

                if (!level.setBlock(pos, this.content.defaultFluidState().createLegacyBlock(), 11) && !blockstate.getFluidState().isSource()) {
                    return false;
                } else {
                    this.playEmptySound(user, level, pos);
                    return true;
                }
            }
        }
    }

    protected void playEmptySound(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos) {
        SoundEvent soundevent = this.content.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;

        level.playSound(user, pos, soundevent, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(user, (Holder) GameEvent.FLUID_PLACE, pos);
    }

    public Fluid getContent() {
        return this.content;
    }
}
