package net.minecraft.world.entity.decoration;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ItemFrame extends HangingEntity {

    public static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.<ItemStack>defineId(ItemFrame.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<Integer> DATA_ROTATION = SynchedEntityData.<Integer>defineId(ItemFrame.class, EntityDataSerializers.INT);
    public static final int NUM_ROTATIONS = 8;
    private static final float DEPTH = 0.0625F;
    private static final float WIDTH = 0.75F;
    private static final float HEIGHT = 0.75F;
    private static final byte DEFAULT_ROTATION = 0;
    private static final float DEFAULT_DROP_CHANCE = 1.0F;
    private static final boolean DEFAULT_INVISIBLE = false;
    private static final boolean DEFAULT_FIXED = false;
    public float dropChance;
    public boolean fixed;

    public ItemFrame(EntityType<? extends ItemFrame> type, Level level) {
        super(type, level);
        this.dropChance = 1.0F;
        this.fixed = false;
        this.setInvisible(false);
    }

    public ItemFrame(Level level, BlockPos pos, Direction direction) {
        this(EntityType.ITEM_FRAME, level, pos, direction);
    }

    public ItemFrame(EntityType<? extends ItemFrame> type, Level level, BlockPos pos, Direction direction) {
        super(type, level, pos);
        this.dropChance = 1.0F;
        this.fixed = false;
        this.setDirection(direction);
        this.setInvisible(false);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(ItemFrame.DATA_ITEM, ItemStack.EMPTY);
        entityData.define(ItemFrame.DATA_ROTATION, 0);
    }

    @Override
    public void setDirection(Direction direction) {
        Objects.requireNonNull(direction);
        super.setDirectionRaw(direction);
        if (direction.getAxis().isHorizontal()) {
            this.setXRot(0.0F);
            this.setYRot((float) (direction.get2DDataValue() * 90));
        } else {
            this.setXRot((float) (-90 * direction.getAxisDirection().getStep()));
            this.setYRot(0.0F);
        }

        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    @Override
    protected final void recalculateBoundingBox() {
        super.recalculateBoundingBox();
        this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos blockPos, Direction direction) {
        return this.createBoundingBox(blockPos, direction, this.hasFramedMap());
    }

    @Override
    protected AABB getPopBox() {
        return this.createBoundingBox(this.pos, this.getDirection(), false);
    }

    private AABB createBoundingBox(BlockPos blockPos, Direction direction, boolean hasFramedMap) {
        float f = 0.46875F;
        Vec3 vec3 = Vec3.atCenterOf(blockPos).relative(direction, -0.46875D);
        float f1 = hasFramedMap ? 1.0F : 0.75F;
        float f2 = hasFramedMap ? 1.0F : 0.75F;
        Direction.Axis direction_axis = direction.getAxis();
        double d0 = direction_axis == Direction.Axis.X ? 0.0625D : (double) f1;
        double d1 = direction_axis == Direction.Axis.Y ? 0.0625D : (double) f2;
        double d2 = direction_axis == Direction.Axis.Z ? 0.0625D : (double) f1;

        return AABB.ofSize(vec3, d0, d1, d2);
    }

    @Override
    public boolean survives() {
        if (this.fixed) {
            return true;
        } else if (this.hasLevelCollision(this.getPopBox())) {
            return false;
        } else {
            BlockState blockstate = this.level().getBlockState(this.pos.relative(this.getDirection().getOpposite()));

            return blockstate.isSolid() || this.getDirection().getAxis().isHorizontal() && DiodeBlock.isDiode(blockstate) ? this.canCoexist(true) : false;
        }
    }

    @Override
    public void move(MoverType moverType, Vec3 delta) {
        if (!this.fixed) {
            super.move(moverType, delta);
        }

    }

    @Override
    public void push(double xa, double ya, double za) {
        if (!this.fixed) {
            super.push(xa, ya, za);
        }

    }

    @Override
    public void kill(ServerLevel level) {
        this.removeFramedMap(this.getItem());
        super.kill(level);
    }

    private boolean shouldDamageDropItem(DamageSource source) {
        return !source.is(DamageTypeTags.IS_EXPLOSION) && !this.getItem().isEmpty();
    }

    private static boolean canHurtWhenFixed(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.isCreativePlayer();
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return this.fixed && !canHurtWhenFixed(source) ? false : !this.isInvulnerableToBase(source);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!this.fixed) {
            if (this.isInvulnerableToBase(source)) {
                return false;
            } else if (this.shouldDamageDropItem(source)) {
                this.dropItem(level, source.getEntity(), false);
                this.gameEvent(GameEvent.BLOCK_CHANGE, source.getEntity());
                this.playSound(this.getRemoveItemSound(), 1.0F, 1.0F);
                return true;
            } else {
                return super.hurtServer(level, source, damage);
            }
        } else {
            return canHurtWhenFixed(source) && super.hurtServer(level, source, damage);
        }
    }

    public SoundEvent getRemoveItemSound() {
        return SoundEvents.ITEM_FRAME_REMOVE_ITEM;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = 16.0D;

        d1 *= 64.0D * getViewScale();
        return distance < d1 * d1;
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity causedBy) {
        this.playSound(this.getBreakSound(), 1.0F, 1.0F);
        this.dropItem(level, causedBy, true);
        this.gameEvent(GameEvent.BLOCK_CHANGE, causedBy);
    }

    public SoundEvent getBreakSound() {
        return SoundEvents.ITEM_FRAME_BREAK;
    }

    @Override
    public void playPlacementSound() {
        this.playSound(this.getPlaceSound(), 1.0F, 1.0F);
    }

    public SoundEvent getPlaceSound() {
        return SoundEvents.ITEM_FRAME_PLACE;
    }

    private void dropItem(ServerLevel level, @Nullable Entity causedBy, boolean withFrame) {
        if (!this.fixed) {
            ItemStack itemstack = this.getItem();

            this.setItem(ItemStack.EMPTY);
            if (!(Boolean) level.getGameRules().get(GameRules.ENTITY_DROPS)) {
                if (causedBy == null) {
                    this.removeFramedMap(itemstack);
                }

            } else {
                if (causedBy instanceof Player) {
                    Player player = (Player) causedBy;

                    if (player.hasInfiniteMaterials()) {
                        this.removeFramedMap(itemstack);
                        return;
                    }
                }

                if (withFrame) {
                    this.spawnAtLocation(level, this.getFrameItemStack());
                }

                if (!itemstack.isEmpty()) {
                    itemstack = itemstack.copy();
                    this.removeFramedMap(itemstack);
                    if (this.random.nextFloat() < this.dropChance) {
                        this.spawnAtLocation(level, itemstack);
                    }
                }

            }
        }
    }

    private void removeFramedMap(ItemStack itemStack) {
        MapId mapid = this.getFramedMapId(itemStack);

        if (mapid != null) {
            MapItemSavedData mapitemsaveddata = MapItem.getSavedData(mapid, this.level());

            if (mapitemsaveddata != null) {
                mapitemsaveddata.removedFromFrame(this.pos, this.getId());
            }
        }

        itemStack.setEntityRepresentation((Entity) null);
    }

    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(ItemFrame.DATA_ITEM);
    }

    public @Nullable MapId getFramedMapId(ItemStack itemStack) {
        return (MapId) itemStack.get(DataComponents.MAP_ID);
    }

    public boolean hasFramedMap() {
        return this.getItem().has(DataComponents.MAP_ID);
    }

    public void setItem(ItemStack itemStack) {
        this.setItem(itemStack, true);
    }

    public void setItem(ItemStack itemStack, boolean updateNeighbours) {
        if (!itemStack.isEmpty()) {
            itemStack = itemStack.copyWithCount(1);
        }

        this.onItemChanged(itemStack);
        this.getEntityData().set(ItemFrame.DATA_ITEM, itemStack);
        if (!itemStack.isEmpty()) {
            this.playSound(this.getAddItemSound(), 1.0F, 1.0F);
        }

        if (updateNeighbours && this.pos != null) {
            this.level().updateNeighbourForOutputSignal(this.pos, Blocks.AIR);
        }

    }

    public SoundEvent getAddItemSound() {
        return SoundEvents.ITEM_FRAME_ADD_ITEM;
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        return slot == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(slot);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor.equals(ItemFrame.DATA_ITEM)) {
            this.onItemChanged(this.getItem());
        }

    }

    private void onItemChanged(ItemStack item) {
        if (!item.isEmpty() && item.getFrame() != this) {
            item.setEntityRepresentation(this);
        }

        this.recalculateBoundingBox();
    }

    public int getRotation() {
        return (Integer) this.getEntityData().get(ItemFrame.DATA_ROTATION);
    }

    public void setRotation(int rotation) {
        this.setRotation(rotation, true);
    }

    private void setRotation(int rotation, boolean updateNeighbours) {
        this.getEntityData().set(ItemFrame.DATA_ROTATION, rotation % 8);
        if (updateNeighbours && this.pos != null) {
            this.level().updateNeighbourForOutputSignal(this.pos, Blocks.AIR);
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        ItemStack itemstack = this.getItem();

        if (!itemstack.isEmpty()) {
            output.store("Item", ItemStack.CODEC, itemstack);
        }

        output.putByte("ItemRotation", (byte) this.getRotation());
        output.putFloat("ItemDropChance", this.dropChance);
        output.store("Facing", Direction.LEGACY_ID_CODEC, this.getDirection());
        output.putBoolean("Invisible", this.isInvisible());
        output.putBoolean("Fixed", this.fixed);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        ItemStack itemstack = (ItemStack) input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        ItemStack itemstack1 = this.getItem();

        if (!itemstack1.isEmpty() && !ItemStack.matches(itemstack, itemstack1)) {
            this.removeFramedMap(itemstack1);
        }

        this.setItem(itemstack, false);
        this.setRotation(input.getByteOr("ItemRotation", (byte) 0), false);
        this.dropChance = input.getFloatOr("ItemDropChance", 1.0F);
        this.setDirection((Direction) input.read("Facing", Direction.LEGACY_ID_CODEC).orElse(Direction.DOWN));
        this.setInvisible(input.getBooleanOr("Invisible", false));
        this.fixed = input.getBooleanOr("Fixed", false);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        boolean flag = !this.getItem().isEmpty();
        boolean flag1 = !itemstack.isEmpty();

        if (this.fixed) {
            return InteractionResult.PASS;
        } else if (!player.level().isClientSide()) {
            if (!flag) {
                if (flag1 && !this.isRemoved()) {
                    MapItemSavedData mapitemsaveddata = MapItem.getSavedData(itemstack, this.level());

                    if (mapitemsaveddata != null && mapitemsaveddata.isTrackedCountOverLimit(256)) {
                        return InteractionResult.FAIL;
                    } else {
                        this.setItem(itemstack);
                        this.gameEvent(GameEvent.BLOCK_CHANGE, player);
                        itemstack.consume(1, player);
                        return InteractionResult.SUCCESS;
                    }
                } else {
                    return InteractionResult.PASS;
                }
            } else {
                this.playSound(this.getRotateItemSound(), 1.0F, 1.0F);
                this.setRotation(this.getRotation() + 1);
                this.gameEvent(GameEvent.BLOCK_CHANGE, player);
                return InteractionResult.SUCCESS;
            }
        } else {
            return (InteractionResult) (!flag && !flag1 ? InteractionResult.PASS : InteractionResult.SUCCESS);
        }
    }

    public SoundEvent getRotateItemSound() {
        return SoundEvents.ITEM_FRAME_ROTATE_ITEM;
    }

    public int getAnalogOutput() {
        return this.getItem().isEmpty() ? 0 : this.getRotation() % 8 + 1;
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
        ItemStack itemstack = this.getItem();

        return itemstack.isEmpty() ? this.getFrameItemStack() : itemstack.copy();
    }

    protected ItemStack getFrameItemStack() {
        return new ItemStack(Items.ITEM_FRAME);
    }

    @Override
    public float getVisualRotationYInDegrees() {
        Direction direction = this.getDirection();
        int i = direction.getAxis().isVertical() ? 90 * direction.getAxisDirection().getStep() : 0;

        return (float) Mth.wrapDegrees(180 + direction.get2DDataValue() * 90 + this.getRotation() * 45 + i);
    }
}
