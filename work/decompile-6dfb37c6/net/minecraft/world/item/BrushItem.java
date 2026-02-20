package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BrushItem extends Item {

    public static final int ANIMATION_DURATION = 10;
    private static final int USE_DURATION = 200;

    public BrushItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();

        if (player != null && this.calculateHitResult(player).getType() == HitResult.Type.BLOCK) {
            player.startUsingItem(context.getHand());
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
        return ItemUseAnimation.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return 200;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int ticksRemaining) {
        if (ticksRemaining >= 0 && livingEntity instanceof Player player) {
            HitResult hitresult = this.calculateHitResult(player);

            if (hitresult instanceof BlockHitResult blockhitresult) {
                if (hitresult.getType() == HitResult.Type.BLOCK) {
                    int j = this.getUseDuration(itemStack, livingEntity) - ticksRemaining + 1;
                    boolean flag = j % 10 == 5;

                    if (flag) {
                        BlockPos blockpos = blockhitresult.getBlockPos();
                        BlockState blockstate = level.getBlockState(blockpos);
                        HumanoidArm humanoidarm = livingEntity.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();

                        if (blockstate.shouldSpawnTerrainParticles() && blockstate.getRenderShape() != RenderShape.INVISIBLE) {
                            this.spawnDustParticles(level, blockhitresult, blockstate, livingEntity.getViewVector(0.0F), humanoidarm);
                        }

                        Block block = blockstate.getBlock();
                        SoundEvent soundevent;

                        if (block instanceof BrushableBlock) {
                            BrushableBlock brushableblock = (BrushableBlock) block;

                            soundevent = brushableblock.getBrushSound();
                        } else {
                            soundevent = SoundEvents.BRUSH_GENERIC;
                        }

                        level.playSound(player, blockpos, soundevent, SoundSource.BLOCKS);
                        if (level instanceof ServerLevel) {
                            ServerLevel serverlevel = (ServerLevel) level;
                            BlockEntity blockentity = level.getBlockEntity(blockpos);

                            if (blockentity instanceof BrushableBlockEntity) {
                                BrushableBlockEntity brushableblockentity = (BrushableBlockEntity) blockentity;
                                boolean flag1 = brushableblockentity.brush(level.getGameTime(), serverlevel, player, blockhitresult.getDirection(), itemStack);

                                if (flag1) {
                                    EquipmentSlot equipmentslot = itemStack.equals(player.getItemBySlot(EquipmentSlot.OFFHAND)) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;

                                    itemStack.hurtAndBreak(1, player, equipmentslot);
                                }
                            }
                        }
                    }

                    return;
                }
            }

            livingEntity.releaseUsingItem();
        } else {
            livingEntity.releaseUsingItem();
        }
    }

    private HitResult calculateHitResult(Player player) {
        return ProjectileUtil.getHitResultOnViewVector(player, EntitySelector.CAN_BE_PICKED, player.blockInteractionRange());
    }

    private void spawnDustParticles(Level level, BlockHitResult hitResult, BlockState state, Vec3 viewVector, HumanoidArm brushingArm) {
        double d0 = 3.0D;
        int i = brushingArm == HumanoidArm.RIGHT ? 1 : -1;
        int j = level.getRandom().nextInt(7, 12);
        BlockParticleOption blockparticleoption = new BlockParticleOption(ParticleTypes.BLOCK, state);
        Direction direction = hitResult.getDirection();
        BrushItem.DustParticlesDelta brushitem_dustparticlesdelta = BrushItem.DustParticlesDelta.fromDirection(viewVector, direction);
        Vec3 vec31 = hitResult.getLocation();

        for (int k = 0; k < j; ++k) {
            level.addParticle(blockparticleoption, vec31.x - (double) (direction == Direction.WEST ? 1.0E-6F : 0.0F), vec31.y, vec31.z - (double) (direction == Direction.NORTH ? 1.0E-6F : 0.0F), brushitem_dustparticlesdelta.xd() * (double) i * 3.0D * level.getRandom().nextDouble(), 0.0D, brushitem_dustparticlesdelta.zd() * (double) i * 3.0D * level.getRandom().nextDouble());
        }

    }

    private static record DustParticlesDelta(double xd, double yd, double zd) {

        private static final double ALONG_SIDE_DELTA = 1.0D;
        private static final double OUT_FROM_SIDE_DELTA = 0.1D;

        public static BrushItem.DustParticlesDelta fromDirection(Vec3 viewVector, Direction hitDirection) {
            double d0 = 0.0D;
            BrushItem.DustParticlesDelta brushitem_dustparticlesdelta;

            switch (hitDirection) {
                case DOWN:
                case UP:
                    brushitem_dustparticlesdelta = new BrushItem.DustParticlesDelta(viewVector.z(), 0.0D, -viewVector.x());
                    break;
                case NORTH:
                    brushitem_dustparticlesdelta = new BrushItem.DustParticlesDelta(1.0D, 0.0D, -0.1D);
                    break;
                case SOUTH:
                    brushitem_dustparticlesdelta = new BrushItem.DustParticlesDelta(-1.0D, 0.0D, 0.1D);
                    break;
                case WEST:
                    brushitem_dustparticlesdelta = new BrushItem.DustParticlesDelta(-0.1D, 0.0D, -1.0D);
                    break;
                case EAST:
                    brushitem_dustparticlesdelta = new BrushItem.DustParticlesDelta(0.1D, 0.0D, 1.0D);
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return brushitem_dustparticlesdelta;
        }
    }
}
