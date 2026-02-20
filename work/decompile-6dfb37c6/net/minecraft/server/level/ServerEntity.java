package net.minecraft.server.level;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TOLERANCE_LEVEL_ROTATION = 1;
    private static final double TOLERANCE_LEVEL_POSITION = (double) 7.6293945E-6F;
    public static final int FORCED_POS_UPDATE_PERIOD = 60;
    private static final int FORCED_TELEPORT_PERIOD = 400;
    private final ServerLevel level;
    private final Entity entity;
    private final int updateInterval;
    private final boolean trackDelta;
    private final ServerEntity.Synchronizer synchronizer;
    private final VecDeltaCodec positionCodec = new VecDeltaCodec();
    private byte lastSentYRot;
    private byte lastSentXRot;
    private byte lastSentYHeadRot;
    private Vec3 lastSentMovement;
    private int tickCount;
    private int teleportDelay;
    private List<Entity> lastPassengers = Collections.emptyList();
    private boolean wasRiding;
    private boolean wasOnGround;
    private @Nullable List<SynchedEntityData.DataValue<?>> trackedDataValues;

    public ServerEntity(ServerLevel level, Entity entity, int updateInterval, boolean trackDelta, ServerEntity.Synchronizer synchronizer) {
        this.level = level;
        this.synchronizer = synchronizer;
        this.entity = entity;
        this.updateInterval = updateInterval;
        this.trackDelta = trackDelta;
        this.positionCodec.setBase(entity.trackingPosition());
        this.lastSentMovement = entity.getDeltaMovement();
        this.lastSentYRot = Mth.packDegrees(entity.getYRot());
        this.lastSentXRot = Mth.packDegrees(entity.getXRot());
        this.lastSentYHeadRot = Mth.packDegrees(entity.getYHeadRot());
        this.wasOnGround = entity.onGround();
        this.trackedDataValues = entity.getEntityData().getNonDefaultValues();
    }

    public void sendChanges() {
        this.entity.updateDataBeforeSync();
        List<Entity> list = this.entity.getPassengers();

        if (!list.equals(this.lastPassengers)) {
            this.synchronizer.sendToTrackingPlayersFiltered(new ClientboundSetPassengersPacket(this.entity), (serverplayer) -> {
                return list.contains(serverplayer) == this.lastPassengers.contains(serverplayer);
            });
            this.lastPassengers = list;
        }

        Entity entity = this.entity;

        if (entity instanceof ItemFrame itemframe) {
            if (this.tickCount % 10 == 0) {
                ItemStack itemstack = itemframe.getItem();

                if (itemstack.getItem() instanceof MapItem) {
                    MapId mapid = (MapId) itemstack.get(DataComponents.MAP_ID);
                    MapItemSavedData mapitemsaveddata = MapItem.getSavedData(mapid, this.level);

                    if (mapitemsaveddata != null) {
                        for (ServerPlayer serverplayer : this.level.players()) {
                            mapitemsaveddata.tickCarriedBy(serverplayer, itemstack);
                            Packet<?> packet = mapitemsaveddata.getUpdatePacket(mapid, serverplayer);

                            if (packet != null) {
                                serverplayer.connection.send(packet);
                            }
                        }
                    }
                }

                this.sendDirtyEntityData();
            }
        }

        if (this.tickCount % this.updateInterval == 0 || this.entity.needsSync || this.entity.getEntityData().isDirty()) {
            byte b0 = Mth.packDegrees(this.entity.getYRot());
            byte b1 = Mth.packDegrees(this.entity.getXRot());
            boolean flag = Math.abs(b0 - this.lastSentYRot) >= 1 || Math.abs(b1 - this.lastSentXRot) >= 1;

            if (this.entity.isPassenger()) {
                if (flag) {
                    this.synchronizer.sendToTrackingPlayers(new ClientboundMoveEntityPacket.Rot(this.entity.getId(), b0, b1, this.entity.onGround()));
                    this.lastSentYRot = b0;
                    this.lastSentXRot = b1;
                }

                this.positionCodec.setBase(this.entity.trackingPosition());
                this.sendDirtyEntityData();
                this.wasRiding = true;
            } else {
                label197:
                {
                    Entity entity1 = this.entity;

                    if (entity1 instanceof AbstractMinecart) {
                        AbstractMinecart abstractminecart = (AbstractMinecart) entity1;
                        MinecartBehavior minecartbehavior = abstractminecart.getBehavior();

                        if (minecartbehavior instanceof NewMinecartBehavior) {
                            NewMinecartBehavior newminecartbehavior = (NewMinecartBehavior) minecartbehavior;

                            this.handleMinecartPosRot(newminecartbehavior, b0, b1, flag);
                            break label197;
                        }
                    }

                    ++this.teleportDelay;
                    Vec3 vec3 = this.entity.trackingPosition();
                    boolean flag1 = this.positionCodec.delta(vec3).lengthSqr() >= (double) 7.6293945E-6F;
                    Packet<ClientGamePacketListener> packet1 = null;
                    boolean flag2 = flag1 || this.tickCount % 60 == 0;
                    boolean flag3 = false;
                    boolean flag4 = false;
                    long i = this.positionCodec.encodeX(vec3);
                    long j = this.positionCodec.encodeY(vec3);
                    long k = this.positionCodec.encodeZ(vec3);
                    boolean flag5 = i < -32768L || i > 32767L || j < -32768L || j > 32767L || k < -32768L || k > 32767L;

                    if (!this.entity.getRequiresPrecisePosition() && !flag5 && this.teleportDelay <= 400 && !this.wasRiding && this.wasOnGround == this.entity.onGround()) {
                        if ((!flag2 || !flag) && !(this.entity instanceof AbstractArrow)) {
                            if (flag2) {
                                packet1 = new ClientboundMoveEntityPacket.Pos(this.entity.getId(), (short) ((int) i), (short) ((int) j), (short) ((int) k), this.entity.onGround());
                                flag3 = true;
                            } else if (flag) {
                                packet1 = new ClientboundMoveEntityPacket.Rot(this.entity.getId(), b0, b1, this.entity.onGround());
                                flag4 = true;
                            }
                        } else {
                            packet1 = new ClientboundMoveEntityPacket.PosRot(this.entity.getId(), (short) ((int) i), (short) ((int) j), (short) ((int) k), b0, b1, this.entity.onGround());
                            flag3 = true;
                            flag4 = true;
                        }
                    } else {
                        this.wasOnGround = this.entity.onGround();
                        this.teleportDelay = 0;
                        packet1 = ClientboundEntityPositionSyncPacket.of(this.entity);
                        flag3 = true;
                        flag4 = true;
                    }

                    if (this.entity.needsSync || this.trackDelta || this.entity instanceof LivingEntity && ((LivingEntity) this.entity).isFallFlying()) {
                        Vec3 vec31 = this.entity.getDeltaMovement();
                        double d0 = vec31.distanceToSqr(this.lastSentMovement);

                        if (d0 > 1.0E-7D || d0 > 0.0D && vec31.lengthSqr() == 0.0D) {
                            this.lastSentMovement = vec31;
                            Entity entity2 = this.entity;

                            if (entity2 instanceof AbstractHurtingProjectile) {
                                AbstractHurtingProjectile abstracthurtingprojectile = (AbstractHurtingProjectile) entity2;

                                this.synchronizer.sendToTrackingPlayers(new ClientboundBundlePacket(List.of(new ClientboundSetEntityMotionPacket(this.entity.getId(), this.lastSentMovement), new ClientboundProjectilePowerPacket(abstracthurtingprojectile.getId(), abstracthurtingprojectile.accelerationPower))));
                            } else {
                                this.synchronizer.sendToTrackingPlayers(new ClientboundSetEntityMotionPacket(this.entity.getId(), this.lastSentMovement));
                            }
                        }
                    }

                    if (packet1 != null) {
                        this.synchronizer.sendToTrackingPlayers(packet1);
                    }

                    this.sendDirtyEntityData();
                    if (flag3) {
                        this.positionCodec.setBase(vec3);
                    }

                    if (flag4) {
                        this.lastSentYRot = b0;
                        this.lastSentXRot = b1;
                    }

                    this.wasRiding = false;
                }
            }

            byte b2 = Mth.packDegrees(this.entity.getYHeadRot());

            if (Math.abs(b2 - this.lastSentYHeadRot) >= 1) {
                this.synchronizer.sendToTrackingPlayers(new ClientboundRotateHeadPacket(this.entity, b2));
                this.lastSentYHeadRot = b2;
            }

            this.entity.needsSync = false;
        }

        ++this.tickCount;
        if (this.entity.hurtMarked) {
            this.entity.hurtMarked = false;
            this.synchronizer.sendToTrackingPlayersAndSelf(new ClientboundSetEntityMotionPacket(this.entity));
        }

    }

    private void handleMinecartPosRot(NewMinecartBehavior newMinecartBehavior, byte yRotn, byte xRotn, boolean shouldSendRotation) {
        this.sendDirtyEntityData();
        if (newMinecartBehavior.lerpSteps.isEmpty()) {
            Vec3 vec3 = this.entity.getDeltaMovement();
            double d0 = vec3.distanceToSqr(this.lastSentMovement);
            Vec3 vec31 = this.entity.trackingPosition();
            boolean flag1 = this.positionCodec.delta(vec31).lengthSqr() >= (double) 7.6293945E-6F;
            boolean flag2 = flag1 || this.tickCount % 60 == 0;

            if (flag2 || shouldSendRotation || d0 > 1.0E-7D) {
                this.synchronizer.sendToTrackingPlayers(new ClientboundMoveMinecartPacket(this.entity.getId(), List.of(new NewMinecartBehavior.MinecartStep(this.entity.position(), this.entity.getDeltaMovement(), this.entity.getYRot(), this.entity.getXRot(), 1.0F))));
            }
        } else {
            this.synchronizer.sendToTrackingPlayers(new ClientboundMoveMinecartPacket(this.entity.getId(), List.copyOf(newMinecartBehavior.lerpSteps)));
            newMinecartBehavior.lerpSteps.clear();
        }

        this.lastSentYRot = yRotn;
        this.lastSentXRot = xRotn;
        this.positionCodec.setBase(this.entity.position());
    }

    public void removePairing(ServerPlayer player) {
        this.entity.stopSeenByPlayer(player);
        player.connection.send(new ClientboundRemoveEntitiesPacket(new int[]{this.entity.getId()}));
    }

    public void addPairing(ServerPlayer player) {
        List<Packet<? super ClientGamePacketListener>> list = new ArrayList();

        Objects.requireNonNull(list);
        this.sendPairingData(player, list::add);
        player.connection.send(new ClientboundBundlePacket(list));
        this.entity.startSeenByPlayer(player);
    }

    public void sendPairingData(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> broadcast) {
        this.entity.updateDataBeforeSync();
        if (this.entity.isRemoved()) {
            ServerEntity.LOGGER.warn("Fetching packet for removed entity {}", this.entity);
        }

        Packet<ClientGamePacketListener> packet = this.entity.getAddEntityPacket(this);

        broadcast.accept(packet);
        if (this.trackedDataValues != null) {
            broadcast.accept(new ClientboundSetEntityDataPacket(this.entity.getId(), this.trackedDataValues));
        }

        Entity entity = this.entity;

        if (entity instanceof LivingEntity livingentity) {
            Collection<AttributeInstance> collection = livingentity.getAttributes().getSyncableAttributes();

            if (!collection.isEmpty()) {
                broadcast.accept(new ClientboundUpdateAttributesPacket(this.entity.getId(), collection));
            }
        }

        entity = this.entity;
        if (entity instanceof LivingEntity livingentity1) {
            List<Pair<EquipmentSlot, ItemStack>> list = Lists.newArrayList();

            for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
                ItemStack itemstack = livingentity1.getItemBySlot(equipmentslot);

                if (!itemstack.isEmpty()) {
                    list.add(Pair.of(equipmentslot, itemstack.copy()));
                }
            }

            if (!list.isEmpty()) {
                broadcast.accept(new ClientboundSetEquipmentPacket(this.entity.getId(), list));
            }
        }

        if (!this.entity.getPassengers().isEmpty()) {
            broadcast.accept(new ClientboundSetPassengersPacket(this.entity));
        }

        if (this.entity.isPassenger()) {
            broadcast.accept(new ClientboundSetPassengersPacket(this.entity.getVehicle()));
        }

        entity = this.entity;
        if (entity instanceof Leashable leashable) {
            if (leashable.isLeashed()) {
                broadcast.accept(new ClientboundSetEntityLinkPacket(this.entity, leashable.getLeashHolder()));
            }
        }

    }

    public Vec3 getPositionBase() {
        return this.positionCodec.getBase();
    }

    public Vec3 getLastSentMovement() {
        return this.lastSentMovement;
    }

    public float getLastSentXRot() {
        return Mth.unpackDegrees(this.lastSentXRot);
    }

    public float getLastSentYRot() {
        return Mth.unpackDegrees(this.lastSentYRot);
    }

    public float getLastSentYHeadRot() {
        return Mth.unpackDegrees(this.lastSentYHeadRot);
    }

    private void sendDirtyEntityData() {
        SynchedEntityData synchedentitydata = this.entity.getEntityData();
        List<SynchedEntityData.DataValue<?>> list = synchedentitydata.packDirty();

        if (list != null) {
            this.trackedDataValues = synchedentitydata.getNonDefaultValues();
            this.synchronizer.sendToTrackingPlayersAndSelf(new ClientboundSetEntityDataPacket(this.entity.getId(), list));
        }

        if (this.entity instanceof LivingEntity) {
            Set<AttributeInstance> set = ((LivingEntity) this.entity).getAttributes().getAttributesToSync();

            if (!set.isEmpty()) {
                this.synchronizer.sendToTrackingPlayersAndSelf(new ClientboundUpdateAttributesPacket(this.entity.getId(), set));
            }

            set.clear();
        }

    }

    public interface Synchronizer {

        void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet);

        void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet);

        void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, Predicate<ServerPlayer> predicate);
    }
}
