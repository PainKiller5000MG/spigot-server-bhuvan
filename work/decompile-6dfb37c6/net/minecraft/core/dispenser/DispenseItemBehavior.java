package net.minecraft.core.dispenser;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

public interface DispenseItemBehavior {

    Logger LOGGER = LogUtils.getLogger();
    DispenseItemBehavior NOOP = (blocksource, itemstack) -> {
        return itemstack;
    };

    ItemStack dispense(BlockSource source, ItemStack dispensed);

    static void bootStrap() {
        DispenserBlock.registerProjectileBehavior(Items.ARROW);
        DispenserBlock.registerProjectileBehavior(Items.TIPPED_ARROW);
        DispenserBlock.registerProjectileBehavior(Items.SPECTRAL_ARROW);
        DispenserBlock.registerProjectileBehavior(Items.EGG);
        DispenserBlock.registerProjectileBehavior(Items.BLUE_EGG);
        DispenserBlock.registerProjectileBehavior(Items.BROWN_EGG);
        DispenserBlock.registerProjectileBehavior(Items.SNOWBALL);
        DispenserBlock.registerProjectileBehavior(Items.EXPERIENCE_BOTTLE);
        DispenserBlock.registerProjectileBehavior(Items.SPLASH_POTION);
        DispenserBlock.registerProjectileBehavior(Items.LINGERING_POTION);
        DispenserBlock.registerProjectileBehavior(Items.FIREWORK_ROCKET);
        DispenserBlock.registerProjectileBehavior(Items.FIRE_CHARGE);
        DispenserBlock.registerProjectileBehavior(Items.WIND_CHARGE);
        DefaultDispenseItemBehavior defaultdispenseitembehavior = new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource source, ItemStack dispensed) {
                Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);
                EntityType<?> entitytype = ((SpawnEggItem) dispensed.getItem()).getType(dispensed);

                if (entitytype == null) {
                    return dispensed;
                } else {
                    try {
                        entitytype.spawn(source.level(), dispensed, (LivingEntity) null, source.pos().relative(direction), EntitySpawnReason.DISPENSER, direction != Direction.UP, false);
                    } catch (Exception exception) {
                        null.LOGGER.error("Error while dispensing spawn egg from dispenser at {}", source.pos(), exception);
                        return ItemStack.EMPTY;
                    }

                    dispensed.shrink(1);
                    source.level().gameEvent((Entity) null, (Holder) GameEvent.ENTITY_PLACE, source.pos());
                    return dispensed;
                }
            }
        };

        for (SpawnEggItem spawneggitem : SpawnEggItem.eggs()) {
            DispenserBlock.registerBehavior(spawneggitem, defaultdispenseitembehavior);
        }

        DispenserBlock.registerBehavior(Items.ARMOR_STAND, new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource source, ItemStack dispensed) {
                Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);
                BlockPos blockpos = source.pos().relative(direction);
                ServerLevel serverlevel = source.level();
                Consumer<ArmorStand> consumer = EntityType.<ArmorStand>appendDefaultStackConfig((armorstand) -> {
                    armorstand.setYRot(direction.toYRot());
                }, serverlevel, dispensed, (LivingEntity) null);
                ArmorStand armorstand = EntityType.ARMOR_STAND.spawn(serverlevel, consumer, blockpos, EntitySpawnReason.DISPENSER, false, false);

                if (armorstand != null) {
                    dispensed.shrink(1);
                }

                return dispensed;
            }
        });
        DispenserBlock.registerBehavior(Items.CHEST, new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource source, ItemStack dispensed) {
                BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));

                for (AbstractChestedHorse abstractchestedhorse : source.level().getEntitiesOfClass(AbstractChestedHorse.class, new AABB(blockpos), (abstractchestedhorse1) -> {
                    return abstractchestedhorse1.isAlive() && !abstractchestedhorse1.hasChest();
                })) {
                    if (abstractchestedhorse.isTamed()) {
                        SlotAccess slotaccess = abstractchestedhorse.getSlot(499);

                        if (slotaccess != null && slotaccess.set(dispensed)) {
                            dispensed.shrink(1);
                            this.setSuccess(true);
                            return dispensed;
                        }
                    }
                }

                return super.execute(source, dispensed);
            }
        });
        DispenserBlock.registerBehavior(Items.OAK_BOAT, new BoatDispenseItemBehavior(EntityType.OAK_BOAT));
        DispenserBlock.registerBehavior(Items.SPRUCE_BOAT, new BoatDispenseItemBehavior(EntityType.SPRUCE_BOAT));
        DispenserBlock.registerBehavior(Items.BIRCH_BOAT, new BoatDispenseItemBehavior(EntityType.BIRCH_BOAT));
        DispenserBlock.registerBehavior(Items.JUNGLE_BOAT, new BoatDispenseItemBehavior(EntityType.JUNGLE_BOAT));
        DispenserBlock.registerBehavior(Items.DARK_OAK_BOAT, new BoatDispenseItemBehavior(EntityType.DARK_OAK_BOAT));
        DispenserBlock.registerBehavior(Items.ACACIA_BOAT, new BoatDispenseItemBehavior(EntityType.ACACIA_BOAT));
        DispenserBlock.registerBehavior(Items.CHERRY_BOAT, new BoatDispenseItemBehavior(EntityType.CHERRY_BOAT));
        DispenserBlock.registerBehavior(Items.MANGROVE_BOAT, new BoatDispenseItemBehavior(EntityType.MANGROVE_BOAT));
        DispenserBlock.registerBehavior(Items.PALE_OAK_BOAT, new BoatDispenseItemBehavior(EntityType.PALE_OAK_BOAT));
        DispenserBlock.registerBehavior(Items.BAMBOO_RAFT, new BoatDispenseItemBehavior(EntityType.BAMBOO_RAFT));
        DispenserBlock.registerBehavior(Items.OAK_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.OAK_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.SPRUCE_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.SPRUCE_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.BIRCH_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.BIRCH_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.JUNGLE_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.JUNGLE_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.DARK_OAK_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.DARK_OAK_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.ACACIA_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.ACACIA_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.CHERRY_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.CHERRY_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.MANGROVE_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.MANGROVE_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.PALE_OAK_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.PALE_OAK_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.BAMBOO_CHEST_RAFT, new BoatDispenseItemBehavior(EntityType.BAMBOO_CHEST_RAFT));
        DispenseItemBehavior dispenseitembehavior = new DefaultDispenseItemBehavior() {
            private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

            @Override
            public ItemStack execute(BlockSource source, ItemStack dispensed) {
                DispensibleContainerItem dispensiblecontaineritem = (DispensibleContainerItem) dispensed.getItem();
                BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));
                Level level = source.level();

                if (dispensiblecontaineritem.emptyContents((LivingEntity) null, level, blockpos, (BlockHitResult) null)) {
                    dispensiblecontaineritem.checkExtraContent((LivingEntity) null, level, dispensed, blockpos);
                    return this.consumeWithRemainder(source, dispensed, new ItemStack(Items.BUCKET));
                } else {
                    return this.defaultDispenseItemBehavior.dispense(source, dispensed);
                }
            }
        };

        DispenserBlock.registerBehavior(Items.LAVA_BUCKET, dispenseitembehavior);
        DispenserBlock.registerBehavior(Items.WATER_BUCKET, dispenseitembehavior);
        DispenserBlock.registerBehavior(Items.POWDER_SNOW_BUCKET, dispenseitembehavior);
        DispenserBlock.registerBehavior(Items.SALMON_BUCKET, dispenseitembehavior);
        DispenserBlock.registerBehavior(Items.COD_BUCKET, dispenseitembehavior);
        DispenserBlock.registerBehavior(Items.PUFFERFISH_BUCKET, dispenseitembehavior);
        DispenserBlock.registerBehavior(Items.TROPICAL_FISH_BUCKET, dispenseitembehavior);
        DispenserBlock.registerBehavior(Items.AXOLOTL_BUCKET, dispenseitembehavior);
        DispenserBlock.registerBehavior(Items.TADPOLE_BUCKET, dispenseitembehavior);
        DispenserBlock.registerBehavior(Items.BUCKET, new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource source, ItemStack dispensed) {
                LevelAccessor levelaccessor = source.level();
                BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));
                BlockState blockstate = levelaccessor.getBlockState(blockpos);
                Block block = blockstate.getBlock();

                if (block instanceof BucketPickup bucketpickup) {
                    ItemStack itemstack1 = bucketpickup.pickupBlock((LivingEntity) null, levelaccessor, blockpos, blockstate);

                    if (itemstack1.isEmpty()) {
                        return super.execute(source, dispensed);
                    } else {
                        levelaccessor.gameEvent((Entity) null, (Holder) GameEvent.FLUID_PICKUP, blockpos);
                        Item item = itemstack1.getItem();

                        return this.consumeWithRemainder(source, dispensed, new ItemStack(item));
                    }
                } else {
                    return super.execute(source, dispensed);
                }
            }
        });
        DispenserBlock.registerBehavior(Items.FLINT_AND_STEEL, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack dispensed) {
                ServerLevel serverlevel = source.level();

                this.setSuccess(true);
                Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);
                BlockPos blockpos = source.pos().relative(direction);
                BlockState blockstate = serverlevel.getBlockState(blockpos);

                if (BaseFireBlock.canBePlacedAt(serverlevel, blockpos, direction)) {
                    serverlevel.setBlockAndUpdate(blockpos, BaseFireBlock.getState(serverlevel, blockpos));
                    serverlevel.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_PLACE, blockpos);
                } else if (!CampfireBlock.canLight(blockstate) && !CandleBlock.canLight(blockstate) && !CandleCakeBlock.canLight(blockstate)) {
                    if (blockstate.getBlock() instanceof TntBlock) {
                        if (TntBlock.prime(serverlevel, blockpos)) {
                            serverlevel.removeBlock(blockpos, false);
                        } else {
                            this.setSuccess(false);
                        }
                    } else {
                        this.setSuccess(false);
                    }
                } else {
                    serverlevel.setBlockAndUpdate(blockpos, (BlockState) blockstate.setValue(BlockStateProperties.LIT, true));
                    serverlevel.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_CHANGE, blockpos);
                }

                if (this.isSuccess()) {
                    dispensed.hurtAndBreak(1, serverlevel, (ServerPlayer) null, (item) -> {
                    });
                }

                return dispensed;
            }
        });
        DispenserBlock.registerBehavior(Items.BONE_MEAL, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack dispensed) {
                this.setSuccess(true);
                Level level = source.level();
                BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));

                if (!BoneMealItem.growCrop(dispensed, level, blockpos) && !BoneMealItem.growWaterPlant(dispensed, level, blockpos, (Direction) null)) {
                    this.setSuccess(false);
                } else if (!level.isClientSide()) {
                    level.levelEvent(1505, blockpos, 15);
                }

                return dispensed;
            }
        });
        DispenserBlock.registerBehavior(Blocks.TNT, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack dispensed) {
                ServerLevel serverlevel = source.level();

                if (!(Boolean) serverlevel.getGameRules().get(GameRules.TNT_EXPLODES)) {
                    this.setSuccess(false);
                    return dispensed;
                } else {
                    BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));
                    PrimedTnt primedtnt = new PrimedTnt(serverlevel, (double) blockpos.getX() + 0.5D, (double) blockpos.getY(), (double) blockpos.getZ() + 0.5D, (LivingEntity) null);

                    serverlevel.addFreshEntity(primedtnt);
                    serverlevel.playSound((Entity) null, primedtnt.getX(), primedtnt.getY(), primedtnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
                    serverlevel.gameEvent((Entity) null, (Holder) GameEvent.ENTITY_PLACE, blockpos);
                    dispensed.shrink(1);
                    this.setSuccess(true);
                    return dispensed;
                }
            }
        });
        DispenserBlock.registerBehavior(Items.WITHER_SKELETON_SKULL, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack dispensed) {
                Level level = source.level();
                Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);
                BlockPos blockpos = source.pos().relative(direction);

                if (level.isEmptyBlock(blockpos) && WitherSkullBlock.canSpawnMob(level, blockpos, dispensed)) {
                    level.setBlock(blockpos, (BlockState) Blocks.WITHER_SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(direction)), 3);
                    level.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_PLACE, blockpos);
                    BlockEntity blockentity = level.getBlockEntity(blockpos);

                    if (blockentity instanceof SkullBlockEntity) {
                        WitherSkullBlock.checkSpawn(level, blockpos, (SkullBlockEntity) blockentity);
                    }

                    dispensed.shrink(1);
                    this.setSuccess(true);
                } else {
                    this.setSuccess(EquipmentDispenseItemBehavior.dispenseEquipment(source, dispensed));
                }

                return dispensed;
            }
        });
        DispenserBlock.registerBehavior(Blocks.CARVED_PUMPKIN, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack dispensed) {
                Level level = source.level();
                BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));
                CarvedPumpkinBlock carvedpumpkinblock = (CarvedPumpkinBlock) Blocks.CARVED_PUMPKIN;

                if (level.isEmptyBlock(blockpos) && carvedpumpkinblock.canSpawnGolem(level, blockpos)) {
                    if (!level.isClientSide()) {
                        level.setBlock(blockpos, carvedpumpkinblock.defaultBlockState(), 3);
                        level.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_PLACE, blockpos);
                    }

                    dispensed.shrink(1);
                    this.setSuccess(true);
                } else {
                    this.setSuccess(EquipmentDispenseItemBehavior.dispenseEquipment(source, dispensed));
                }

                return dispensed;
            }
        });
        DispenserBlock.registerBehavior(Blocks.SHULKER_BOX.asItem(), new ShulkerBoxDispenseBehavior());

        for (DyeColor dyecolor : DyeColor.values()) {
            DispenserBlock.registerBehavior(ShulkerBoxBlock.getBlockByColor(dyecolor).asItem(), new ShulkerBoxDispenseBehavior());
        }

        DispenserBlock.registerBehavior(Items.GLASS_BOTTLE.asItem(), new OptionalDispenseItemBehavior() {
            private ItemStack takeLiquid(BlockSource source, ItemStack dispensed, ItemStack filledItemStack) {
                source.level().gameEvent((Entity) null, (Holder) GameEvent.FLUID_PICKUP, source.pos());
                return this.consumeWithRemainder(source, dispensed, filledItemStack);
            }

            @Override
            public ItemStack execute(BlockSource source, ItemStack dispensed) {
                this.setSuccess(false);
                ServerLevel serverlevel = source.level();
                BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));
                BlockState blockstate = serverlevel.getBlockState(blockpos);

                if (blockstate.is(BlockTags.BEEHIVES, (blockbehaviour_blockstatebase) -> {
                    return blockbehaviour_blockstatebase.hasProperty(BeehiveBlock.HONEY_LEVEL) && blockbehaviour_blockstatebase.getBlock() instanceof BeehiveBlock;
                }) && (Integer) blockstate.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
                    ((BeehiveBlock) blockstate.getBlock()).releaseBeesAndResetHoneyLevel(serverlevel, blockstate, blockpos, (Player) null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                    this.setSuccess(true);
                    return this.takeLiquid(source, dispensed, new ItemStack(Items.HONEY_BOTTLE));
                } else if (serverlevel.getFluidState(blockpos).is(FluidTags.WATER)) {
                    this.setSuccess(true);
                    return this.takeLiquid(source, dispensed, PotionContents.createItemStack(Items.POTION, Potions.WATER));
                } else {
                    return super.execute(source, dispensed);
                }
            }
        });
        DispenserBlock.registerBehavior(Items.GLOWSTONE, new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource source, ItemStack dispensed) {
                Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);
                BlockPos blockpos = source.pos().relative(direction);
                Level level = source.level();
                BlockState blockstate = level.getBlockState(blockpos);

                this.setSuccess(true);
                if (blockstate.is(Blocks.RESPAWN_ANCHOR)) {
                    if ((Integer) blockstate.getValue(RespawnAnchorBlock.CHARGE) != 4) {
                        RespawnAnchorBlock.charge((Entity) null, level, blockpos, blockstate);
                        dispensed.shrink(1);
                    } else {
                        this.setSuccess(false);
                    }

                    return dispensed;
                } else {
                    return super.execute(source, dispensed);
                }
            }
        });
        DispenserBlock.registerBehavior(Items.SHEARS.asItem(), new ShearsDispenseItemBehavior());
        DispenserBlock.registerBehavior(Items.BRUSH.asItem(), new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack dispensed) {
                ServerLevel serverlevel = source.level();
                BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));
                List<Armadillo> list = serverlevel.<Armadillo>getEntitiesOfClass(Armadillo.class, new AABB(blockpos), EntitySelector.NO_SPECTATORS);

                if (list.isEmpty()) {
                    this.setSuccess(false);
                    return dispensed;
                } else {
                    for (Armadillo armadillo : list) {
                        if (armadillo.brushOffScute((Entity) null, dispensed)) {
                            dispensed.hurtAndBreak(16, serverlevel, (ServerPlayer) null, (item) -> {
                            });
                            return dispensed;
                        }
                    }

                    this.setSuccess(false);
                    return dispensed;
                }
            }
        });
        DispenserBlock.registerBehavior(Items.HONEYCOMB, new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource source, ItemStack dispensed) {
                BlockPos blockpos = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));
                Level level = source.level();
                BlockState blockstate = level.getBlockState(blockpos);
                Optional<BlockState> optional = HoneycombItem.getWaxed(blockstate);

                if (optional.isPresent()) {
                    level.setBlockAndUpdate(blockpos, (BlockState) optional.get());
                    level.levelEvent(3003, blockpos, 0);
                    dispensed.shrink(1);
                    this.setSuccess(true);
                    return dispensed;
                } else {
                    return super.execute(source, dispensed);
                }
            }
        });
        DispenserBlock.registerBehavior(Items.POTION, new DefaultDispenseItemBehavior() {
            private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

            @Override
            public ItemStack execute(BlockSource source, ItemStack dispensed) {
                PotionContents potioncontents = (PotionContents) dispensed.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);

                if (!potioncontents.is(Potions.WATER)) {
                    return this.defaultDispenseItemBehavior.dispense(source, dispensed);
                } else {
                    ServerLevel serverlevel = source.level();
                    BlockPos blockpos = source.pos();
                    BlockPos blockpos1 = source.pos().relative((Direction) source.state().getValue(DispenserBlock.FACING));

                    if (!serverlevel.getBlockState(blockpos1).is(BlockTags.CONVERTABLE_TO_MUD)) {
                        return this.defaultDispenseItemBehavior.dispense(source, dispensed);
                    } else {
                        if (!serverlevel.isClientSide()) {
                            for (int i = 0; i < 5; ++i) {
                                serverlevel.sendParticles(ParticleTypes.SPLASH, (double) blockpos.getX() + serverlevel.random.nextDouble(), (double) (blockpos.getY() + 1), (double) blockpos.getZ() + serverlevel.random.nextDouble(), 1, 0.0D, 0.0D, 0.0D, 1.0D);
                            }
                        }

                        serverlevel.playSound((Entity) null, blockpos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        serverlevel.gameEvent((Entity) null, (Holder) GameEvent.FLUID_PLACE, blockpos);
                        serverlevel.setBlockAndUpdate(blockpos1, Blocks.MUD.defaultBlockState());
                        return this.consumeWithRemainder(source, dispensed, new ItemStack(Items.GLASS_BOTTLE));
                    }
                }
            }
        });
        DispenserBlock.registerBehavior(Items.MINECART, new MinecartDispenseItemBehavior(EntityType.MINECART));
        DispenserBlock.registerBehavior(Items.CHEST_MINECART, new MinecartDispenseItemBehavior(EntityType.CHEST_MINECART));
        DispenserBlock.registerBehavior(Items.FURNACE_MINECART, new MinecartDispenseItemBehavior(EntityType.FURNACE_MINECART));
        DispenserBlock.registerBehavior(Items.TNT_MINECART, new MinecartDispenseItemBehavior(EntityType.TNT_MINECART));
        DispenserBlock.registerBehavior(Items.HOPPER_MINECART, new MinecartDispenseItemBehavior(EntityType.HOPPER_MINECART));
        DispenserBlock.registerBehavior(Items.COMMAND_BLOCK_MINECART, new MinecartDispenseItemBehavior(EntityType.COMMAND_BLOCK_MINECART));
    }
}
