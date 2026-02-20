package net.minecraft.world.entity.decoration.painting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Painting extends HangingEntity {

    private static final EntityDataAccessor<Holder<PaintingVariant>> DATA_PAINTING_VARIANT_ID = SynchedEntityData.<Holder<PaintingVariant>>defineId(Painting.class, EntityDataSerializers.PAINTING_VARIANT);
    public static final float DEPTH = 0.0625F;

    public Painting(EntityType<? extends Painting> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Painting.DATA_PAINTING_VARIANT_ID, VariantUtils.getAny(this.registryAccess(), Registries.PAINTING_VARIANT));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (Painting.DATA_PAINTING_VARIANT_ID.equals(accessor)) {
            this.recalculateBoundingBox();
        }

    }

    public void setVariant(Holder<PaintingVariant> holder) {
        this.entityData.set(Painting.DATA_PAINTING_VARIANT_ID, holder);
    }

    public Holder<PaintingVariant> getVariant() {
        return (Holder) this.entityData.get(Painting.DATA_PAINTING_VARIANT_ID);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.PAINTING_VARIANT ? castComponentValue(type, this.getVariant()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.PAINTING_VARIANT);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.PAINTING_VARIANT) {
            this.setVariant((Holder) castComponentValue(DataComponents.PAINTING_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    public static Optional<Painting> create(Level level, BlockPos pos, Direction direction) {
        Painting painting = new Painting(level, pos);
        List<Holder<PaintingVariant>> list = new ArrayList();
        Iterable iterable = level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getTagOrEmpty(PaintingVariantTags.PLACEABLE);

        Objects.requireNonNull(list);
        iterable.forEach(list::add);
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            painting.setDirection(direction);
            list.removeIf((holder) -> {
                painting.setVariant(holder);
                return !painting.survives();
            });
            if (list.isEmpty()) {
                return Optional.empty();
            } else {
                int i = list.stream().mapToInt(Painting::variantArea).max().orElse(0);

                list.removeIf((holder) -> {
                    return variantArea(holder) < i;
                });
                Optional<Holder<PaintingVariant>> optional = Util.<Holder<PaintingVariant>>getRandomSafe(list, painting.random);

                if (optional.isEmpty()) {
                    return Optional.empty();
                } else {
                    painting.setVariant((Holder) optional.get());
                    painting.setDirection(direction);
                    return Optional.of(painting);
                }
            }
        }
    }

    private static int variantArea(Holder<PaintingVariant> holder) {
        return ((PaintingVariant) holder.value()).area();
    }

    private Painting(Level level, BlockPos blockPos) {
        super(EntityType.PAINTING, level, blockPos);
    }

    public Painting(Level level, BlockPos blockPos, Direction direction, Holder<PaintingVariant> holder) {
        this(level, blockPos);
        this.setVariant(holder);
        this.setDirection(direction);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("facing", Direction.LEGACY_ID_CODEC_2D, this.getDirection());
        super.addAdditionalSaveData(output);
        VariantUtils.writeVariant(output, this.getVariant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        Direction direction = (Direction) input.read("facing", Direction.LEGACY_ID_CODEC_2D).orElse(Direction.SOUTH);

        super.readAdditionalSaveData(input);
        this.setDirection(direction);
        VariantUtils.readVariant(input, Registries.PAINTING_VARIANT).ifPresent(this::setVariant);
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
        float f = 0.46875F;
        Vec3 vec3 = Vec3.atCenterOf(pos).relative(direction, -0.46875D);
        PaintingVariant paintingvariant = (PaintingVariant) this.getVariant().value();
        double d0 = this.offsetForPaintingSize(paintingvariant.width());
        double d1 = this.offsetForPaintingSize(paintingvariant.height());
        Direction direction1 = direction.getCounterClockWise();
        Vec3 vec31 = vec3.relative(direction1, d0).relative(Direction.UP, d1);
        Direction.Axis direction_axis = direction.getAxis();
        double d2 = direction_axis == Direction.Axis.X ? 0.0625D : (double) paintingvariant.width();
        double d3 = (double) paintingvariant.height();
        double d4 = direction_axis == Direction.Axis.Z ? 0.0625D : (double) paintingvariant.width();

        return AABB.ofSize(vec31, d2, d3, d4);
    }

    private double offsetForPaintingSize(int size) {
        return size % 2 == 0 ? 0.5D : 0.0D;
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity causedBy) {
        if ((Boolean) level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (causedBy instanceof Player) {
                Player player = (Player) causedBy;

                if (player.hasInfiniteMaterials()) {
                    return;
                }
            }

            this.spawnAtLocation(level, (ItemLike) Items.PAINTING);
        }
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void snapTo(double x, double y, double z, float yRot, float xRot) {
        this.setPos(x, y, z);
    }

    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(this.pos);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, this.getDirection().get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.PAINTING);
    }
}
