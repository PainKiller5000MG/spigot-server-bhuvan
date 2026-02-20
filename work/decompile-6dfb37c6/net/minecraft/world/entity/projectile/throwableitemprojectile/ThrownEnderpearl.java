package net.minecraft.world.entity.projectile.throwableitemprojectile;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ThrownEnderpearl extends ThrowableItemProjectile {

    private long ticketTimer = 0L;

    public ThrownEnderpearl(EntityType<? extends ThrownEnderpearl> type, Level level) {
        super(type, level);
    }

    public ThrownEnderpearl(Level level, LivingEntity mob, ItemStack itemStack) {
        super(EntityType.ENDER_PEARL, mob, level, itemStack);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void setOwner(@Nullable EntityReference<Entity> owner) {
        this.deregisterFromCurrentOwner();
        super.setOwner(owner);
        this.registerToCurrentOwner();
    }

    private void deregisterFromCurrentOwner() {
        Entity entity = this.getOwner();

        if (entity instanceof ServerPlayer serverplayer) {
            serverplayer.deregisterEnderPearl(this);
        }

    }

    private void registerToCurrentOwner() {
        Entity entity = this.getOwner();

        if (entity instanceof ServerPlayer serverplayer) {
            serverplayer.registerEnderPearl(this);
        }

    }

    @Override
    public @Nullable Entity getOwner() {
        if (this.owner != null) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                return (Entity) this.owner.getEntity(serverlevel, Entity.class);
            }
        }

        return super.getOwner();
    }

    private static @Nullable Entity findOwnerIncludingDeadPlayer(ServerLevel serverLevel, UUID uuid) {
        Entity entity = serverLevel.getEntityInAnyDimension(uuid);

        return (Entity) (entity != null ? entity : serverLevel.getServer().getPlayerList().getPlayer(uuid));
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        hitResult.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);

        for (int i = 0; i < 32; ++i) {
            this.level().addParticle(ParticleTypes.PORTAL, this.getX(), this.getY() + this.random.nextDouble() * 2.0D, this.getZ(), this.random.nextGaussian(), 0.0D, this.random.nextGaussian());
        }

        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (!this.isRemoved()) {
                Entity entity = this.getOwner();

                if (entity != null && isAllowedToTeleportOwner(entity, serverlevel)) {
                    Vec3 vec3 = this.oldPosition();

                    if (entity instanceof ServerPlayer) {
                        ServerPlayer serverplayer = (ServerPlayer) entity;

                        if (serverplayer.connection.isAcceptingMessages()) {
                            if (this.random.nextFloat() < 0.05F && serverlevel.isSpawningMonsters()) {
                                Endermite endermite = EntityType.ENDERMITE.create(serverlevel, EntitySpawnReason.TRIGGERED);

                                if (endermite != null) {
                                    endermite.snapTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                                    serverlevel.addFreshEntity(endermite);
                                }
                            }

                            if (this.isOnPortalCooldown()) {
                                entity.setPortalCooldown();
                            }

                            ServerPlayer serverplayer1 = serverplayer.teleport(new TeleportTransition(serverlevel, vec3, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.ROTATION, Relative.DELTA), TeleportTransition.DO_NOTHING));

                            if (serverplayer1 != null) {
                                serverplayer1.resetFallDistance();
                                serverplayer1.resetCurrentImpulseContext();
                                serverplayer1.hurtServer(serverplayer.level(), this.damageSources().enderPearl(), 5.0F);
                            }

                            this.playSound(serverlevel, vec3);
                        }
                    } else {
                        Entity entity1 = entity.teleport(new TeleportTransition(serverlevel, vec3, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), TeleportTransition.DO_NOTHING));

                        if (entity1 != null) {
                            entity1.resetFallDistance();
                        }

                        this.playSound(serverlevel, vec3);
                    }

                    this.discard();
                    return;
                }

                this.discard();
                return;
            }
        }

    }

    private static boolean isAllowedToTeleportOwner(Entity owner, Level newLevel) {
        if (owner.level().dimension() == newLevel.dimension()) {
            if (!(owner instanceof LivingEntity)) {
                return owner.isAlive();
            } else {
                LivingEntity livingentity = (LivingEntity) owner;

                return livingentity.isAlive() && !livingentity.isSleeping();
            }
        } else {
            return owner.canUsePortal(true);
        }
    }

    @Override
    public void tick() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            int i;
            Entity entity;
            label39:
            {
                j = SectionPos.blockToSectionCoord(this.position().x());
                i = SectionPos.blockToSectionCoord(this.position().z());
                entity = this.owner != null ? findOwnerIncludingDeadPlayer(serverlevel, this.owner.getUUID()) : null;
                if (entity instanceof ServerPlayer serverplayer) {
                    if (!entity.isAlive() && !serverplayer.wonGame && (Boolean) serverplayer.level().getGameRules().get(GameRules.ENDER_PEARLS_VANISH_ON_DEATH)) {
                        this.discard();
                        break label39;
                    }
                }

                super.tick();
            }

            if (this.isAlive()) {
                BlockPos blockpos = BlockPos.containing(this.position());

                if ((--this.ticketTimer <= 0L || j != SectionPos.blockToSectionCoord(blockpos.getX()) || i != SectionPos.blockToSectionCoord(blockpos.getZ())) && entity instanceof ServerPlayer) {
                    ServerPlayer serverplayer1 = (ServerPlayer) entity;

                    this.ticketTimer = serverplayer1.registerAndUpdateEnderPearlTicket(this);
                }

            }
        } else {
            super.tick();
        }
    }

    private void playSound(Level level, Vec3 position) {
        level.playSound((Entity) null, position.x, position.y, position.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS);
    }

    @Override
    public @Nullable Entity teleport(TeleportTransition transition) {
        Entity entity = super.teleport(transition);

        if (entity != null) {
            entity.placePortalTicket(BlockPos.containing(entity.position()));
        }

        return entity;
    }

    @Override
    public boolean canTeleport(Level from, Level to) {
        if (from.dimension() == Level.END && to.dimension() == Level.OVERWORLD) {
            Entity entity = this.getOwner();

            if (entity instanceof ServerPlayer) {
                ServerPlayer serverplayer = (ServerPlayer) entity;

                return super.canTeleport(from, to) && serverplayer.seenCredits;
            }
        }

        return super.canTeleport(from, to);
    }

    @Override
    protected void onInsideBlock(BlockState state) {
        super.onInsideBlock(state);
        if (state.is(Blocks.END_GATEWAY)) {
            Entity entity = this.getOwner();

            if (entity instanceof ServerPlayer) {
                ServerPlayer serverplayer = (ServerPlayer) entity;

                serverplayer.onInsideBlock(state);
            }
        }

    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        if (reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            this.deregisterFromCurrentOwner();
        }

        super.onRemoval(reason);
    }

    @Override
    public void onAboveBubbleColumn(boolean dragDown, BlockPos pos) {
        Entity.handleOnAboveBubbleColumn(this, dragDown, pos);
    }

    @Override
    public void onInsideBubbleColumn(boolean dragDown) {
        Entity.handleOnInsideBubbleColumn(this, dragDown);
    }
}
