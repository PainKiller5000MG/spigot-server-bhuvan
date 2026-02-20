package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SpawnEggItem extends Item {

    private static final Map<EntityType<?>, SpawnEggItem> BY_ID = Maps.newIdentityHashMap();

    public SpawnEggItem(Item.Properties properties) {
        super(properties);
        TypedEntityData<EntityType<?>> typedentitydata = (TypedEntityData) this.components().get(DataComponents.ENTITY_DATA);

        if (typedentitydata != null) {
            SpawnEggItem.BY_ID.put(typedentitydata.type(), this);
        }

    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (!(level instanceof ServerLevel serverlevel)) {
            return InteractionResult.SUCCESS;
        } else {
            ItemStack itemstack = context.getItemInHand();
            BlockPos blockpos = context.getClickedPos();
            Direction direction = context.getClickedFace();
            BlockState blockstate = level.getBlockState(blockpos);
            BlockEntity blockentity = level.getBlockEntity(blockpos);

            if (blockentity instanceof Spawner spawner) {
                EntityType<?> entitytype = this.getType(itemstack);

                if (entitytype == null) {
                    return InteractionResult.FAIL;
                } else if (!serverlevel.isSpawnerBlockEnabled()) {
                    Player player = context.getPlayer();

                    if (player instanceof ServerPlayer) {
                        ServerPlayer serverplayer = (ServerPlayer) player;

                        serverplayer.sendSystemMessage(Component.translatable("advMode.notEnabled.spawner"));
                    }

                    return InteractionResult.FAIL;
                } else {
                    spawner.setEntityId(entitytype, level.getRandom());
                    level.sendBlockUpdated(blockpos, blockstate, blockstate, 3);
                    level.gameEvent(context.getPlayer(), (Holder) GameEvent.BLOCK_CHANGE, blockpos);
                    itemstack.shrink(1);
                    return InteractionResult.SUCCESS;
                }
            } else {
                BlockPos blockpos1;

                if (blockstate.getCollisionShape(level, blockpos).isEmpty()) {
                    blockpos1 = blockpos;
                } else {
                    blockpos1 = blockpos.relative(direction);
                }

                return this.spawnMob(context.getPlayer(), itemstack, level, blockpos1, true, !Objects.equals(blockpos, blockpos1) && direction == Direction.UP);
            }
        }
    }

    private InteractionResult spawnMob(@Nullable LivingEntity user, ItemStack itemStack, Level level, BlockPos spawnPos, boolean tryMoveDown, boolean movedUp) {
        EntityType<?> entitytype = this.getType(itemStack);

        if (entitytype == null) {
            return InteractionResult.FAIL;
        } else if (!entitytype.isAllowedInPeaceful() && level.getDifficulty() == Difficulty.PEACEFUL) {
            return InteractionResult.FAIL;
        } else {
            if (entitytype.spawn((ServerLevel) level, itemStack, user, spawnPos, EntitySpawnReason.SPAWN_ITEM_USE, tryMoveDown, movedUp) != null) {
                itemStack.consume(1, user);
                level.gameEvent(user, (Holder) GameEvent.ENTITY_PLACE, spawnPos);
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        BlockHitResult blockhitresult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);

        if (blockhitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        } else if (level instanceof ServerLevel) {
            ServerLevel serverlevel = (ServerLevel) level;
            BlockPos blockpos = blockhitresult.getBlockPos();

            if (!(level.getBlockState(blockpos).getBlock() instanceof LiquidBlock)) {
                return InteractionResult.PASS;
            } else if (level.mayInteract(player, blockpos) && player.mayUseItemAt(blockpos, blockhitresult.getDirection(), itemstack)) {
                InteractionResult interactionresult = this.spawnMob(player, itemstack, level, blockpos, false, false);

                if (interactionresult == InteractionResult.SUCCESS) {
                    player.awardStat(Stats.ITEM_USED.get(this));
                }

                return interactionresult;
            } else {
                return InteractionResult.FAIL;
            }
        } else {
            return InteractionResult.SUCCESS;
        }
    }

    public boolean spawnsEntity(ItemStack itemStack, EntityType<?> type) {
        return Objects.equals(this.getType(itemStack), type);
    }

    public static @Nullable SpawnEggItem byId(@Nullable EntityType<?> type) {
        return (SpawnEggItem) SpawnEggItem.BY_ID.get(type);
    }

    public static Iterable<SpawnEggItem> eggs() {
        return Iterables.unmodifiableIterable(SpawnEggItem.BY_ID.values());
    }

    public @Nullable EntityType<?> getType(ItemStack itemStack) {
        TypedEntityData<EntityType<?>> typedentitydata = (TypedEntityData) itemStack.get(DataComponents.ENTITY_DATA);

        return typedentitydata != null ? (EntityType) typedentitydata.type() : null;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return (FeatureFlagSet) Optional.ofNullable((TypedEntityData) this.components().get(DataComponents.ENTITY_DATA)).map(TypedEntityData::type).map(EntityType::requiredFeatures).orElseGet(FeatureFlagSet::of);
    }

    public Optional<Mob> spawnOffspringFromSpawnEgg(Player player, Mob parent, EntityType<? extends Mob> type, ServerLevel level, Vec3 pos, ItemStack spawnEggStack) {
        if (!this.spawnsEntity(spawnEggStack, type)) {
            return Optional.empty();
        } else {
            Mob mob1;

            if (parent instanceof AgeableMob) {
                mob1 = ((AgeableMob) parent).getBreedOffspring(level, (AgeableMob) parent);
            } else {
                mob1 = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            }

            if (mob1 == null) {
                return Optional.empty();
            } else {
                mob1.setBaby(true);
                if (!mob1.isBaby()) {
                    return Optional.empty();
                } else {
                    mob1.snapTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);
                    mob1.applyComponentsFromItemStack(spawnEggStack);
                    level.addFreshEntityWithPassengers(mob1);
                    spawnEggStack.consume(1, player);
                    return Optional.of(mob1);
                }
            }
        }
    }

    @Override
    public boolean shouldPrintOpWarning(ItemStack stack, @Nullable Player player) {
        if (player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            TypedEntityData<EntityType<?>> typedentitydata = (TypedEntityData) stack.get(DataComponents.ENTITY_DATA);

            if (typedentitydata != null) {
                return ((EntityType) typedentitydata.type()).onlyOpCanSetNbt();
            }
        }

        return false;
    }
}
