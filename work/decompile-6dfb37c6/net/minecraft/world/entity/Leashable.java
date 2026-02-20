package net.minecraft.world.entity;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface Leashable {

    String LEASH_TAG = "leash";
    double LEASH_TOO_FAR_DIST = 12.0D;
    double LEASH_ELASTIC_DIST = 6.0D;
    double MAXIMUM_ALLOWED_LEASHED_DIST = 16.0D;
    Vec3 AXIS_SPECIFIC_ELASTICITY = new Vec3(0.8D, 0.2D, 0.8D);
    float SPRING_DAMPENING = 0.7F;
    double TORSIONAL_ELASTICITY = 10.0D;
    double STIFFNESS = 0.11D;
    List<Vec3> ENTITY_ATTACHMENT_POINT = ImmutableList.of(new Vec3(0.0D, 0.5D, 0.5D));
    List<Vec3> LEASHER_ATTACHMENT_POINT = ImmutableList.of(new Vec3(0.0D, 0.5D, 0.0D));
    List<Vec3> SHARED_QUAD_ATTACHMENT_POINTS = ImmutableList.of(new Vec3(-0.5D, 0.5D, 0.5D), new Vec3(-0.5D, 0.5D, -0.5D), new Vec3(0.5D, 0.5D, -0.5D), new Vec3(0.5D, 0.5D, 0.5D));

    Leashable.@Nullable LeashData getLeashData();

    void setLeashData(Leashable.@Nullable LeashData leashData);

    default boolean isLeashed() {
        return this.getLeashData() != null && this.getLeashData().leashHolder != null;
    }

    default boolean mayBeLeashed() {
        return this.getLeashData() != null;
    }

    default boolean canHaveALeashAttachedTo(Entity entity) {
        return this == entity ? false : (this.leashDistanceTo(entity) > this.leashSnapDistance() ? false : this.canBeLeashed());
    }

    default double leashDistanceTo(Entity entity) {
        return entity.getBoundingBox().getCenter().distanceTo(((Entity) this).getBoundingBox().getCenter());
    }

    default boolean canBeLeashed() {
        return true;
    }

    default void setDelayedLeashHolderId(int entityId) {
        this.setLeashData(new Leashable.LeashData(entityId));
        dropLeash((Entity) this, false, false);
    }

    default void readLeashData(ValueInput input) {
        Leashable.LeashData leashable_leashdata = (Leashable.LeashData) input.read("leash", Leashable.LeashData.CODEC).orElse((Object) null);

        if (this.getLeashData() != null && leashable_leashdata == null) {
            this.removeLeash();
        }

        this.setLeashData(leashable_leashdata);
    }

    default void writeLeashData(ValueOutput output, Leashable.@Nullable LeashData leashData) {
        output.storeNullable("leash", Leashable.LeashData.CODEC, leashData);
    }

    private static <E extends Entity & Leashable> void restoreLeashFromSave(E entity, Leashable.LeashData leashData) {
        if (leashData.delayedLeashInfo != null) {
            Level level = entity.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;
                Optional<UUID> optional = leashData.delayedLeashInfo.left();
                Optional<BlockPos> optional1 = leashData.delayedLeashInfo.right();

                if (optional.isPresent()) {
                    Entity entity1 = serverlevel.getEntity((UUID) optional.get());

                    if (entity1 != null) {
                        setLeashedTo(entity, entity1, true);
                        return;
                    }
                } else if (optional1.isPresent()) {
                    setLeashedTo(entity, LeashFenceKnotEntity.getOrCreateKnot(serverlevel, (BlockPos) optional1.get()), true);
                    return;
                }

                if (entity.tickCount > 100) {
                    entity.spawnAtLocation(serverlevel, (ItemLike) Items.LEAD);
                    ((Leashable) entity).setLeashData((Leashable.LeashData) null);
                }
            }
        }

    }

    default void dropLeash() {
        dropLeash((Entity) this, true, true);
    }

    default void removeLeash() {
        dropLeash((Entity) this, true, false);
    }

    default void onLeashRemoved() {}

    private static <E extends Entity & Leashable> void dropLeash(E entity, boolean sendPacket, boolean dropLead) {
        Leashable.LeashData leashable_leashdata = ((Leashable) entity).getLeashData();

        if (leashable_leashdata != null && leashable_leashdata.leashHolder != null) {
            ((Leashable) entity).setLeashData((Leashable.LeashData) null);
            ((Leashable) entity).onLeashRemoved();
            Level level = entity.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                if (dropLead) {
                    entity.spawnAtLocation(serverlevel, (ItemLike) Items.LEAD);
                }

                if (sendPacket) {
                    serverlevel.getChunkSource().sendToTrackingPlayers(entity, new ClientboundSetEntityLinkPacket(entity, (Entity) null));
                }

                leashable_leashdata.leashHolder.notifyLeasheeRemoved(entity);
            }
        }

    }

    static <E extends Entity & Leashable> void tickLeash(ServerLevel level, E entity) {
        Leashable.LeashData leashable_leashdata = ((Leashable) entity).getLeashData();

        if (leashable_leashdata != null && leashable_leashdata.delayedLeashInfo != null) {
            restoreLeashFromSave(entity, leashable_leashdata);
        }

        if (leashable_leashdata != null && leashable_leashdata.leashHolder != null) {
            if (!entity.canInteractWithLevel() || !leashable_leashdata.leashHolder.canInteractWithLevel()) {
                if ((Boolean) level.getGameRules().get(GameRules.ENTITY_DROPS)) {
                    ((Leashable) entity).dropLeash();
                } else {
                    ((Leashable) entity).removeLeash();
                }
            }

            Entity entity1 = ((Leashable) entity).getLeashHolder();

            if (entity1 != null && entity1.level() == entity.level()) {
                double d0 = ((Leashable) entity).leashDistanceTo(entity1);

                ((Leashable) entity).whenLeashedTo(entity1);
                if (d0 > ((Leashable) entity).leashSnapDistance()) {
                    level.playSound((Entity) null, entity1.getX(), entity1.getY(), entity1.getZ(), SoundEvents.LEAD_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    ((Leashable) entity).leashTooFarBehaviour();
                } else if (d0 > ((Leashable) entity).leashElasticDistance() - (double) entity1.getBbWidth() - (double) entity.getBbWidth() && ((Leashable) entity).checkElasticInteractions(entity1, leashable_leashdata)) {
                    ((Leashable) entity).onElasticLeashPull();
                } else {
                    ((Leashable) entity).closeRangeLeashBehaviour(entity1);
                }

                entity.setYRot((float) ((double) entity.getYRot() - leashable_leashdata.angularMomentum));
                leashable_leashdata.angularMomentum *= (double) angularFriction(entity);
            }

        }
    }

    default void onElasticLeashPull() {
        Entity entity = (Entity) this;

        entity.checkFallDistanceAccumulation();
    }

    default double leashSnapDistance() {
        return 12.0D;
    }

    default double leashElasticDistance() {
        return 6.0D;
    }

    static <E extends Entity & Leashable> float angularFriction(E entity) {
        return entity.onGround() ? entity.level().getBlockState(entity.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91F : (entity.isInLiquid() ? 0.8F : 0.91F);
    }

    default void whenLeashedTo(Entity leashHolder) {
        leashHolder.notifyLeashHolder(this);
    }

    default void leashTooFarBehaviour() {
        this.dropLeash();
    }

    default void closeRangeLeashBehaviour(Entity leashHolder) {}

    default boolean checkElasticInteractions(Entity leashHolder, Leashable.LeashData leashData) {
        boolean flag = leashHolder.supportQuadLeashAsHolder() && this.supportQuadLeash();
        List<Leashable.Wrench> list = computeElasticInteraction((Entity) this, leashHolder, flag ? Leashable.SHARED_QUAD_ATTACHMENT_POINTS : Leashable.ENTITY_ATTACHMENT_POINT, flag ? Leashable.SHARED_QUAD_ATTACHMENT_POINTS : Leashable.LEASHER_ATTACHMENT_POINT);

        if (list.isEmpty()) {
            return false;
        } else {
            Leashable.Wrench leashable_wrench = Leashable.Wrench.accumulate(list).scale(flag ? 0.25D : 1.0D);

            leashData.angularMomentum += 10.0D * leashable_wrench.torque();
            Vec3 vec3 = getHolderMovement(leashHolder).subtract(((Entity) this).getKnownMovement());

            ((Entity) this).addDeltaMovement(leashable_wrench.force().multiply(Leashable.AXIS_SPECIFIC_ELASTICITY).add(vec3.scale(0.11D)));
            return true;
        }
    }

    private static Vec3 getHolderMovement(Entity leashHolder) {
        if (leashHolder instanceof Mob mob) {
            if (mob.isNoAi()) {
                return Vec3.ZERO;
            }
        }

        return leashHolder.getKnownMovement();
    }

    private static <E extends Entity & Leashable> List<Leashable.Wrench> computeElasticInteraction(E entity, Entity leashHolder, List<Vec3> entityAttachmentPoints, List<Vec3> leasherAttachmentPoints) {
        double d0 = ((Leashable) entity).leashElasticDistance();
        Vec3 vec3 = getHolderMovement(entity);
        float f = entity.getYRot() * ((float) Math.PI / 180F);
        Vec3 vec31 = new Vec3((double) entity.getBbWidth(), (double) entity.getBbHeight(), (double) entity.getBbWidth());
        float f1 = leashHolder.getYRot() * ((float) Math.PI / 180F);
        Vec3 vec32 = new Vec3((double) leashHolder.getBbWidth(), (double) leashHolder.getBbHeight(), (double) leashHolder.getBbWidth());
        List<Leashable.Wrench> list2 = new ArrayList();

        for (int i = 0; i < entityAttachmentPoints.size(); ++i) {
            Vec3 vec33 = ((Vec3) entityAttachmentPoints.get(i)).multiply(vec31).yRot(-f);
            Vec3 vec34 = entity.position().add(vec33);
            Vec3 vec35 = ((Vec3) leasherAttachmentPoints.get(i)).multiply(vec32).yRot(-f1);
            Vec3 vec36 = leashHolder.position().add(vec35);
            Optional optional = computeDampenedSpringInteraction(vec36, vec34, d0, vec3, vec33);

            Objects.requireNonNull(list2);
            optional.ifPresent(list2::add);
        }

        return list2;
    }

    private static Optional<Leashable.Wrench> computeDampenedSpringInteraction(Vec3 pivotPoint, Vec3 objectPosition, double springSlack, Vec3 objectMotion, Vec3 leverArm) {
        double d1 = objectPosition.distanceTo(pivotPoint);

        if (d1 < springSlack) {
            return Optional.empty();
        } else {
            Vec3 vec34 = pivotPoint.subtract(objectPosition).normalize().scale(d1 - springSlack);
            double d2 = Leashable.Wrench.torqueFromForce(leverArm, vec34);
            boolean flag = objectMotion.dot(vec34) >= 0.0D;

            if (flag) {
                vec34 = vec34.scale((double) 0.3F);
            }

            return Optional.of(new Leashable.Wrench(vec34, d2));
        }
    }

    default boolean supportQuadLeash() {
        return false;
    }

    default Vec3[] getQuadLeashOffsets() {
        return createQuadLeashOffsets((Entity) this, 0.0D, 0.5D, 0.5D, 0.5D);
    }

    static Vec3[] createQuadLeashOffsets(Entity entity, double frontOffset, double frontBack, double leftRight, double height) {
        float f = entity.getBbWidth();
        double d4 = frontOffset * (double) f;
        double d5 = frontBack * (double) f;
        double d6 = leftRight * (double) f;
        double d7 = height * (double) entity.getBbHeight();

        return new Vec3[]{new Vec3(-d6, d7, d5 + d4), new Vec3(-d6, d7, -d5 + d4), new Vec3(d6, d7, -d5 + d4), new Vec3(d6, d7, d5 + d4)};
    }

    default Vec3 getLeashOffset(float partialTicks) {
        return this.getLeashOffset();
    }

    default Vec3 getLeashOffset() {
        Entity entity = (Entity) this;

        return new Vec3(0.0D, (double) entity.getEyeHeight(), (double) (entity.getBbWidth() * 0.4F));
    }

    default void setLeashedTo(Entity holder, boolean synch) {
        if (this != holder) {
            setLeashedTo((Entity) this, holder, synch);
        }
    }

    private static <E extends Entity & Leashable> void setLeashedTo(E entity, Entity holder, boolean synch) {
        Leashable.LeashData leashable_leashdata = ((Leashable) entity).getLeashData();

        if (leashable_leashdata == null) {
            leashable_leashdata = new Leashable.LeashData(holder);
            ((Leashable) entity).setLeashData(leashable_leashdata);
        } else {
            Entity entity2 = leashable_leashdata.leashHolder;

            leashable_leashdata.setLeashHolder(holder);
            if (entity2 != null && entity2 != holder) {
                entity2.notifyLeasheeRemoved(entity);
            }
        }

        if (synch) {
            Level level = entity.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                serverlevel.getChunkSource().sendToTrackingPlayers(entity, new ClientboundSetEntityLinkPacket(entity, holder));
            }
        }

        if (entity.isPassenger()) {
            entity.stopRiding();
        }

    }

    default @Nullable Entity getLeashHolder() {
        return getLeashHolder((Entity) this);
    }

    private static <E extends Entity & Leashable> @Nullable Entity getLeashHolder(E entity) {
        Leashable.LeashData leashable_leashdata = ((Leashable) entity).getLeashData();

        if (leashable_leashdata == null) {
            return null;
        } else {
            if (leashable_leashdata.delayedLeashHolderId != 0 && entity.level().isClientSide()) {
                Entity entity1 = entity.level().getEntity(leashable_leashdata.delayedLeashHolderId);

                if (entity1 instanceof Entity) {
                    leashable_leashdata.setLeashHolder(entity1);
                }
            }

            return leashable_leashdata.leashHolder;
        }
    }

    static List<Leashable> leashableLeashedTo(Entity entity) {
        return leashableInArea(entity, (leashable) -> {
            return leashable.getLeashHolder() == entity;
        });
    }

    static List<Leashable> leashableInArea(Entity entity, Predicate<Leashable> test) {
        return leashableInArea(entity.level(), entity.getBoundingBox().getCenter(), test);
    }

    static List<Leashable> leashableInArea(Level level, Vec3 pos, Predicate<Leashable> test) {
        double d0 = 32.0D;
        AABB aabb = AABB.ofSize(pos, 32.0D, 32.0D, 32.0D);
        Stream stream = level.getEntitiesOfClass(Entity.class, aabb, (entity) -> {
            boolean flag;

            if (entity instanceof Leashable leashable) {
                if (test.test(leashable)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }).stream();

        Objects.requireNonNull(Leashable.class);
        return stream.map(Leashable.class::cast).toList();
    }

    public static final class LeashData {

        public static final Codec<Leashable.LeashData> CODEC = Codec.xor(UUIDUtil.CODEC.fieldOf("UUID").codec(), BlockPos.CODEC).xmap(Leashable.LeashData::new, (leashable_leashdata) -> {
            Entity entity = leashable_leashdata.leashHolder;

            if (entity instanceof LeashFenceKnotEntity leashfenceknotentity) {
                return Either.right(leashfenceknotentity.getPos());
            } else {
                return leashable_leashdata.leashHolder != null ? Either.left(leashable_leashdata.leashHolder.getUUID()) : (Either) Objects.requireNonNull(leashable_leashdata.delayedLeashInfo, "Invalid LeashData had no attachment");
            }
        });
        private int delayedLeashHolderId;
        public @Nullable Entity leashHolder;
        public @Nullable Either<UUID, BlockPos> delayedLeashInfo;
        public double angularMomentum;

        private LeashData(Either<UUID, BlockPos> delayedLeashInfo) {
            this.delayedLeashInfo = delayedLeashInfo;
        }

        private LeashData(Entity entity) {
            this.leashHolder = entity;
        }

        private LeashData(int entityId) {
            this.delayedLeashHolderId = entityId;
        }

        public void setLeashHolder(Entity leashHolder) {
            this.leashHolder = leashHolder;
            this.delayedLeashInfo = null;
            this.delayedLeashHolderId = 0;
        }
    }

    public static record Wrench(Vec3 force, double torque) {

        static Leashable.Wrench ZERO = new Leashable.Wrench(Vec3.ZERO, 0.0D);

        static double torqueFromForce(Vec3 leverArm, Vec3 force) {
            return leverArm.z * force.x - leverArm.x * force.z;
        }

        static Leashable.Wrench accumulate(List<Leashable.Wrench> wrenches) {
            if (wrenches.isEmpty()) {
                return Leashable.Wrench.ZERO;
            } else {
                double d0 = 0.0D;
                double d1 = 0.0D;
                double d2 = 0.0D;
                double d3 = 0.0D;

                for (Leashable.Wrench leashable_wrench : wrenches) {
                    Vec3 vec3 = leashable_wrench.force;

                    d0 += vec3.x;
                    d1 += vec3.y;
                    d2 += vec3.z;
                    d3 += leashable_wrench.torque;
                }

                return new Leashable.Wrench(new Vec3(d0, d1, d2), d3);
            }
        }

        public Leashable.Wrench scale(double scale) {
            return new Leashable.Wrench(this.force.scale(scale), this.torque * scale);
        }
    }
}
