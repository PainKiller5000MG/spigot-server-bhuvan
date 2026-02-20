package net.minecraft.world.entity;

import com.mojang.logging.LogUtils;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ARGB;
import net.minecraft.util.Brightness;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class Display extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int NO_BRIGHTNESS_OVERRIDE = -1;
    private static final EntityDataAccessor<Integer> DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID = SynchedEntityData.<Integer>defineId(Display.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID = SynchedEntityData.<Integer>defineId(Display.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> DATA_POS_ROT_INTERPOLATION_DURATION_ID = SynchedEntityData.<Integer>defineId(Display.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_TRANSLATION_ID = SynchedEntityData.<Vector3fc>defineId(Display.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3fc> DATA_SCALE_ID = SynchedEntityData.<Vector3fc>defineId(Display.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Quaternionfc> DATA_LEFT_ROTATION_ID = SynchedEntityData.<Quaternionfc>defineId(Display.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Quaternionfc> DATA_RIGHT_ROTATION_ID = SynchedEntityData.<Quaternionfc>defineId(Display.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Byte> DATA_BILLBOARD_RENDER_CONSTRAINTS_ID = SynchedEntityData.<Byte>defineId(Display.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_BRIGHTNESS_OVERRIDE_ID = SynchedEntityData.<Integer>defineId(Display.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_VIEW_RANGE_ID = SynchedEntityData.<Float>defineId(Display.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SHADOW_RADIUS_ID = SynchedEntityData.<Float>defineId(Display.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SHADOW_STRENGTH_ID = SynchedEntityData.<Float>defineId(Display.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WIDTH_ID = SynchedEntityData.<Float>defineId(Display.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT_ID = SynchedEntityData.<Float>defineId(Display.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GLOW_COLOR_OVERRIDE_ID = SynchedEntityData.<Integer>defineId(Display.class, EntityDataSerializers.INT);
    private static final IntSet RENDER_STATE_IDS = IntSet.of(new int[]{Display.DATA_TRANSLATION_ID.id(), Display.DATA_SCALE_ID.id(), Display.DATA_LEFT_ROTATION_ID.id(), Display.DATA_RIGHT_ROTATION_ID.id(), Display.DATA_BILLBOARD_RENDER_CONSTRAINTS_ID.id(), Display.DATA_BRIGHTNESS_OVERRIDE_ID.id(), Display.DATA_SHADOW_RADIUS_ID.id(), Display.DATA_SHADOW_STRENGTH_ID.id()});
    private static final int INITIAL_TRANSFORMATION_INTERPOLATION_DURATION = 0;
    private static final int INITIAL_TRANSFORMATION_START_INTERPOLATION = 0;
    private static final int INITIAL_POS_ROT_INTERPOLATION_DURATION = 0;
    private static final float INITIAL_SHADOW_RADIUS = 0.0F;
    private static final float INITIAL_SHADOW_STRENGTH = 1.0F;
    private static final float INITIAL_VIEW_RANGE = 1.0F;
    private static final float INITIAL_WIDTH = 0.0F;
    private static final float INITIAL_HEIGHT = 0.0F;
    private static final int NO_GLOW_COLOR_OVERRIDE = -1;
    public static final String TAG_POS_ROT_INTERPOLATION_DURATION = "teleport_duration";
    public static final String TAG_TRANSFORMATION_INTERPOLATION_DURATION = "interpolation_duration";
    public static final String TAG_TRANSFORMATION_START_INTERPOLATION = "start_interpolation";
    public static final String TAG_TRANSFORMATION = "transformation";
    public static final String TAG_BILLBOARD = "billboard";
    public static final String TAG_BRIGHTNESS = "brightness";
    public static final String TAG_VIEW_RANGE = "view_range";
    public static final String TAG_SHADOW_RADIUS = "shadow_radius";
    public static final String TAG_SHADOW_STRENGTH = "shadow_strength";
    public static final String TAG_WIDTH = "width";
    public static final String TAG_HEIGHT = "height";
    public static final String TAG_GLOW_COLOR_OVERRIDE = "glow_color_override";
    private long interpolationStartClientTick = -2147483648L;
    private int interpolationDuration;
    private float lastProgress;
    private AABB cullingBoundingBox;
    private boolean noCulling = true;
    protected boolean updateRenderState;
    private boolean updateStartTick;
    private boolean updateInterpolationDuration;
    private Display.@Nullable RenderState renderState;
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 0);

    public Display(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.cullingBoundingBox = this.getBoundingBox();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (Display.DATA_HEIGHT_ID.equals(accessor) || Display.DATA_WIDTH_ID.equals(accessor)) {
            this.updateCulling();
        }

        if (Display.DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID.equals(accessor)) {
            this.updateStartTick = true;
        }

        if (Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID.equals(accessor)) {
            this.interpolation.setInterpolationLength(this.getPosRotInterpolationDuration());
        }

        if (Display.DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID.equals(accessor)) {
            this.updateInterpolationDuration = true;
        }

        if (Display.RENDER_STATE_IDS.contains(accessor.id())) {
            this.updateRenderState = true;
        }

    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    public static Transformation createTransformation(SynchedEntityData entityData) {
        Vector3fc vector3fc = (Vector3fc) entityData.get(Display.DATA_TRANSLATION_ID);
        Quaternionfc quaternionfc = (Quaternionfc) entityData.get(Display.DATA_LEFT_ROTATION_ID);
        Vector3fc vector3fc1 = (Vector3fc) entityData.get(Display.DATA_SCALE_ID);
        Quaternionfc quaternionfc1 = (Quaternionfc) entityData.get(Display.DATA_RIGHT_ROTATION_ID);

        return new Transformation(vector3fc, quaternionfc, vector3fc1, quaternionfc1);
    }

    @Override
    public void tick() {
        Entity entity = this.getVehicle();

        if (entity != null && entity.isRemoved()) {
            this.stopRiding();
        }

        if (this.level().isClientSide()) {
            if (this.updateStartTick) {
                this.updateStartTick = false;
                int i = this.getTransformationInterpolationDelay();

                this.interpolationStartClientTick = (long) (this.tickCount + i);
            }

            if (this.updateInterpolationDuration) {
                this.updateInterpolationDuration = false;
                this.interpolationDuration = this.getTransformationInterpolationDuration();
            }

            if (this.updateRenderState) {
                this.updateRenderState = false;
                boolean flag = this.interpolationDuration != 0;

                if (flag && this.renderState != null) {
                    this.renderState = this.createInterpolatedRenderState(this.renderState, this.lastProgress);
                } else {
                    this.renderState = this.createFreshRenderState();
                }

                this.updateRenderSubState(flag, this.lastProgress);
            }

            this.interpolation.interpolate();
        }

    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    protected abstract void updateRenderSubState(boolean shouldInterpolate, float progress);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID, 0);
        entityData.define(Display.DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID, 0);
        entityData.define(Display.DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID, 0);
        entityData.define(Display.DATA_TRANSLATION_ID, new Vector3f());
        entityData.define(Display.DATA_SCALE_ID, new Vector3f(1.0F, 1.0F, 1.0F));
        entityData.define(Display.DATA_RIGHT_ROTATION_ID, new Quaternionf());
        entityData.define(Display.DATA_LEFT_ROTATION_ID, new Quaternionf());
        entityData.define(Display.DATA_BILLBOARD_RENDER_CONSTRAINTS_ID, Display.BillboardConstraints.FIXED.getId());
        entityData.define(Display.DATA_BRIGHTNESS_OVERRIDE_ID, -1);
        entityData.define(Display.DATA_VIEW_RANGE_ID, 1.0F);
        entityData.define(Display.DATA_SHADOW_RADIUS_ID, 0.0F);
        entityData.define(Display.DATA_SHADOW_STRENGTH_ID, 1.0F);
        entityData.define(Display.DATA_WIDTH_ID, 0.0F);
        entityData.define(Display.DATA_HEIGHT_ID, 0.0F);
        entityData.define(Display.DATA_GLOW_COLOR_OVERRIDE_ID, -1);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setTransformation((Transformation) input.read("transformation", Transformation.EXTENDED_CODEC).orElse(Transformation.identity()));
        this.setTransformationInterpolationDuration(input.getIntOr("interpolation_duration", 0));
        this.setTransformationInterpolationDelay(input.getIntOr("start_interpolation", 0));
        int i = input.getIntOr("teleport_duration", 0);

        this.setPosRotInterpolationDuration(Mth.clamp(i, 0, 59));
        this.setBillboardConstraints((Display.BillboardConstraints) input.read("billboard", Display.BillboardConstraints.CODEC).orElse(Display.BillboardConstraints.FIXED));
        this.setViewRange(input.getFloatOr("view_range", 1.0F));
        this.setShadowRadius(input.getFloatOr("shadow_radius", 0.0F));
        this.setShadowStrength(input.getFloatOr("shadow_strength", 1.0F));
        this.setWidth(input.getFloatOr("width", 0.0F));
        this.setHeight(input.getFloatOr("height", 0.0F));
        this.setGlowColorOverride(input.getIntOr("glow_color_override", -1));
        this.setBrightnessOverride((Brightness) input.read("brightness", Brightness.CODEC).orElse((Object) null));
    }

    public void setTransformation(Transformation transformation) {
        this.entityData.set(Display.DATA_TRANSLATION_ID, transformation.getTranslation());
        this.entityData.set(Display.DATA_LEFT_ROTATION_ID, transformation.getLeftRotation());
        this.entityData.set(Display.DATA_SCALE_ID, transformation.getScale());
        this.entityData.set(Display.DATA_RIGHT_ROTATION_ID, transformation.getRightRotation());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("transformation", Transformation.EXTENDED_CODEC, createTransformation(this.entityData));
        output.store("billboard", Display.BillboardConstraints.CODEC, this.getBillboardConstraints());
        output.putInt("interpolation_duration", this.getTransformationInterpolationDuration());
        output.putInt("teleport_duration", this.getPosRotInterpolationDuration());
        output.putFloat("view_range", this.getViewRange());
        output.putFloat("shadow_radius", this.getShadowRadius());
        output.putFloat("shadow_strength", this.getShadowStrength());
        output.putFloat("width", this.getWidth());
        output.putFloat("height", this.getHeight());
        output.putInt("glow_color_override", this.getGlowColorOverride());
        output.storeNullable("brightness", Brightness.CODEC, this.getBrightnessOverride());
    }

    public AABB getBoundingBoxForCulling() {
        return this.cullingBoundingBox;
    }

    public boolean affectedByCulling() {
        return !this.noCulling;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    public Display.@Nullable RenderState renderState() {
        return this.renderState;
    }

    public void setTransformationInterpolationDuration(int duration) {
        this.entityData.set(Display.DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID, duration);
    }

    public int getTransformationInterpolationDuration() {
        return (Integer) this.entityData.get(Display.DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID);
    }

    public void setTransformationInterpolationDelay(int ticks) {
        this.entityData.set(Display.DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID, ticks, true);
    }

    public int getTransformationInterpolationDelay() {
        return (Integer) this.entityData.get(Display.DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID);
    }

    private void setPosRotInterpolationDuration(int duration) {
        this.entityData.set(Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID, duration);
    }

    private int getPosRotInterpolationDuration() {
        return (Integer) this.entityData.get(Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID);
    }

    public void setBillboardConstraints(Display.BillboardConstraints constraints) {
        this.entityData.set(Display.DATA_BILLBOARD_RENDER_CONSTRAINTS_ID, constraints.getId());
    }

    public Display.BillboardConstraints getBillboardConstraints() {
        return (Display.BillboardConstraints) Display.BillboardConstraints.BY_ID.apply((Byte) this.entityData.get(Display.DATA_BILLBOARD_RENDER_CONSTRAINTS_ID));
    }

    public void setBrightnessOverride(@Nullable Brightness brightness) {
        this.entityData.set(Display.DATA_BRIGHTNESS_OVERRIDE_ID, brightness != null ? brightness.pack() : -1);
    }

    public @Nullable Brightness getBrightnessOverride() {
        int i = (Integer) this.entityData.get(Display.DATA_BRIGHTNESS_OVERRIDE_ID);

        return i != -1 ? Brightness.unpack(i) : null;
    }

    private int getPackedBrightnessOverride() {
        return (Integer) this.entityData.get(Display.DATA_BRIGHTNESS_OVERRIDE_ID);
    }

    public void setViewRange(float range) {
        this.entityData.set(Display.DATA_VIEW_RANGE_ID, range);
    }

    public float getViewRange() {
        return (Float) this.entityData.get(Display.DATA_VIEW_RANGE_ID);
    }

    public void setShadowRadius(float size) {
        this.entityData.set(Display.DATA_SHADOW_RADIUS_ID, size);
    }

    public float getShadowRadius() {
        return (Float) this.entityData.get(Display.DATA_SHADOW_RADIUS_ID);
    }

    public void setShadowStrength(float strength) {
        this.entityData.set(Display.DATA_SHADOW_STRENGTH_ID, strength);
    }

    public float getShadowStrength() {
        return (Float) this.entityData.get(Display.DATA_SHADOW_STRENGTH_ID);
    }

    public void setWidth(float width) {
        this.entityData.set(Display.DATA_WIDTH_ID, width);
    }

    public float getWidth() {
        return (Float) this.entityData.get(Display.DATA_WIDTH_ID);
    }

    public void setHeight(float width) {
        this.entityData.set(Display.DATA_HEIGHT_ID, width);
    }

    public int getGlowColorOverride() {
        return (Integer) this.entityData.get(Display.DATA_GLOW_COLOR_OVERRIDE_ID);
    }

    public void setGlowColorOverride(int value) {
        this.entityData.set(Display.DATA_GLOW_COLOR_OVERRIDE_ID, value);
    }

    public float calculateInterpolationProgress(float partialTickTime) {
        int i = this.interpolationDuration;

        if (i <= 0) {
            return 1.0F;
        } else {
            float f1 = (float) ((long) this.tickCount - this.interpolationStartClientTick);
            float f2 = f1 + partialTickTime;
            float f3 = Mth.clamp(Mth.inverseLerp(f2, 0.0F, (float) i), 0.0F, 1.0F);

            this.lastProgress = f3;
            return f3;
        }
    }

    public float getHeight() {
        return (Float) this.entityData.get(Display.DATA_HEIGHT_ID);
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        this.updateCulling();
    }

    private void updateCulling() {
        float f = this.getWidth();
        float f1 = this.getHeight();

        this.noCulling = f == 0.0F || f1 == 0.0F;
        float f2 = f / 2.0F;
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        this.cullingBoundingBox = new AABB(d0 - (double) f2, d1, d2 - (double) f2, d0 + (double) f2, d1 + (double) f1, d2 + (double) f2);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < Mth.square((double) this.getViewRange() * 64.0D * getViewScale());
    }

    @Override
    public int getTeamColor() {
        int i = this.getGlowColorOverride();

        return i != -1 ? i : super.getTeamColor();
    }

    private Display.RenderState createFreshRenderState() {
        return new Display.RenderState(Display.GenericInterpolator.constant(createTransformation(this.entityData)), this.getBillboardConstraints(), this.getPackedBrightnessOverride(), Display.FloatInterpolator.constant(this.getShadowRadius()), Display.FloatInterpolator.constant(this.getShadowStrength()), this.getGlowColorOverride());
    }

    private Display.RenderState createInterpolatedRenderState(Display.RenderState previousState, float progress) {
        Transformation transformation = previousState.transformation.get(progress);
        float f1 = previousState.shadowRadius.get(progress);
        float f2 = previousState.shadowStrength.get(progress);

        return new Display.RenderState(new Display.TransformationInterpolator(transformation, createTransformation(this.entityData)), this.getBillboardConstraints(), this.getPackedBrightnessOverride(), new Display.LinearFloatInterpolator(f1, this.getShadowRadius()), new Display.LinearFloatInterpolator(f2, this.getShadowStrength()), this.getGlowColorOverride());
    }

    public static enum BillboardConstraints implements StringRepresentable {

        FIXED((byte) 0, "fixed"), VERTICAL((byte) 1, "vertical"), HORIZONTAL((byte) 2, "horizontal"), CENTER((byte) 3, "center");

        public static final Codec<Display.BillboardConstraints> CODEC = StringRepresentable.<Display.BillboardConstraints>fromEnum(Display.BillboardConstraints::values);
        public static final IntFunction<Display.BillboardConstraints> BY_ID = ByIdMap.<Display.BillboardConstraints>continuous(Display.BillboardConstraints::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        private final byte id;
        private final String name;

        private BillboardConstraints(byte id, String name) {
            this.name = name;
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        private byte getId() {
            return this.id;
        }
    }

    public static record RenderState(Display.GenericInterpolator<Transformation> transformation, Display.BillboardConstraints billboardConstraints, int brightnessOverride, Display.FloatInterpolator shadowRadius, Display.FloatInterpolator shadowStrength, int glowColorOverride) {

    }

    public static class ItemDisplay extends Display {

        private static final String TAG_ITEM = "item";
        private static final String TAG_ITEM_DISPLAY = "item_display";
        private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK_ID = SynchedEntityData.<ItemStack>defineId(Display.ItemDisplay.class, EntityDataSerializers.ITEM_STACK);
        private static final EntityDataAccessor<Byte> DATA_ITEM_DISPLAY_ID = SynchedEntityData.<Byte>defineId(Display.ItemDisplay.class, EntityDataSerializers.BYTE);
        private final SlotAccess slot = SlotAccess.of(this::getItemStack, this::setItemStack);
        private Display.ItemDisplay.@Nullable ItemRenderState itemRenderState;

        public ItemDisplay(EntityType<?> type, Level level) {
            super(type, level);
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder entityData) {
            super.defineSynchedData(entityData);
            entityData.define(Display.ItemDisplay.DATA_ITEM_STACK_ID, ItemStack.EMPTY);
            entityData.define(Display.ItemDisplay.DATA_ITEM_DISPLAY_ID, ItemDisplayContext.NONE.getId());
        }

        @Override
        public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
            super.onSyncedDataUpdated(accessor);
            if (Display.ItemDisplay.DATA_ITEM_STACK_ID.equals(accessor) || Display.ItemDisplay.DATA_ITEM_DISPLAY_ID.equals(accessor)) {
                this.updateRenderState = true;
            }

        }

        public ItemStack getItemStack() {
            return (ItemStack) this.entityData.get(Display.ItemDisplay.DATA_ITEM_STACK_ID);
        }

        public void setItemStack(ItemStack item) {
            this.entityData.set(Display.ItemDisplay.DATA_ITEM_STACK_ID, item);
        }

        public void setItemTransform(ItemDisplayContext transform) {
            this.entityData.set(Display.ItemDisplay.DATA_ITEM_DISPLAY_ID, transform.getId());
        }

        public ItemDisplayContext getItemTransform() {
            return (ItemDisplayContext) ItemDisplayContext.BY_ID.apply((Byte) this.entityData.get(Display.ItemDisplay.DATA_ITEM_DISPLAY_ID));
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            this.setItemStack((ItemStack) input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
            this.setItemTransform((ItemDisplayContext) input.read("item_display", ItemDisplayContext.CODEC).orElse(ItemDisplayContext.NONE));
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            ItemStack itemstack = this.getItemStack();

            if (!itemstack.isEmpty()) {
                output.store("item", ItemStack.CODEC, itemstack);
            }

            output.store("item_display", ItemDisplayContext.CODEC, this.getItemTransform());
        }

        @Override
        public @Nullable SlotAccess getSlot(int slot) {
            return slot == 0 ? this.slot : null;
        }

        public Display.ItemDisplay.@Nullable ItemRenderState itemRenderState() {
            return this.itemRenderState;
        }

        @Override
        protected void updateRenderSubState(boolean shouldInterpolate, float progress) {
            ItemStack itemstack = this.getItemStack();

            itemstack.setEntityRepresentation(this);
            this.itemRenderState = new Display.ItemDisplay.ItemRenderState(itemstack, this.getItemTransform());
        }

        public static record ItemRenderState(ItemStack itemStack, ItemDisplayContext itemTransform) {

        }
    }

    public static class BlockDisplay extends Display {

        public static final String TAG_BLOCK_STATE = "block_state";
        private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE_ID = SynchedEntityData.<BlockState>defineId(Display.BlockDisplay.class, EntityDataSerializers.BLOCK_STATE);
        private Display.BlockDisplay.@Nullable BlockRenderState blockRenderState;

        public BlockDisplay(EntityType<?> type, Level level) {
            super(type, level);
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder entityData) {
            super.defineSynchedData(entityData);
            entityData.define(Display.BlockDisplay.DATA_BLOCK_STATE_ID, Blocks.AIR.defaultBlockState());
        }

        @Override
        public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
            super.onSyncedDataUpdated(accessor);
            if (accessor.equals(Display.BlockDisplay.DATA_BLOCK_STATE_ID)) {
                this.updateRenderState = true;
            }

        }

        public BlockState getBlockState() {
            return (BlockState) this.entityData.get(Display.BlockDisplay.DATA_BLOCK_STATE_ID);
        }

        public void setBlockState(BlockState blockState) {
            this.entityData.set(Display.BlockDisplay.DATA_BLOCK_STATE_ID, blockState);
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            this.setBlockState((BlockState) input.read("block_state", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState()));
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            output.store("block_state", BlockState.CODEC, this.getBlockState());
        }

        public Display.BlockDisplay.@Nullable BlockRenderState blockRenderState() {
            return this.blockRenderState;
        }

        @Override
        protected void updateRenderSubState(boolean shouldInterpolate, float progress) {
            this.blockRenderState = new Display.BlockDisplay.BlockRenderState(this.getBlockState());
        }

        public static record BlockRenderState(BlockState blockState) {

        }
    }

    public static class TextDisplay extends Display {

        public static final String TAG_TEXT = "text";
        private static final String TAG_LINE_WIDTH = "line_width";
        private static final String TAG_TEXT_OPACITY = "text_opacity";
        private static final String TAG_BACKGROUND_COLOR = "background";
        private static final String TAG_SHADOW = "shadow";
        private static final String TAG_SEE_THROUGH = "see_through";
        private static final String TAG_USE_DEFAULT_BACKGROUND = "default_background";
        private static final String TAG_ALIGNMENT = "alignment";
        public static final byte FLAG_SHADOW = 1;
        public static final byte FLAG_SEE_THROUGH = 2;
        public static final byte FLAG_USE_DEFAULT_BACKGROUND = 4;
        public static final byte FLAG_ALIGN_LEFT = 8;
        public static final byte FLAG_ALIGN_RIGHT = 16;
        private static final byte INITIAL_TEXT_OPACITY = -1;
        public static final int INITIAL_BACKGROUND = 1073741824;
        private static final int INITIAL_LINE_WIDTH = 200;
        private static final EntityDataAccessor<Component> DATA_TEXT_ID = SynchedEntityData.<Component>defineId(Display.TextDisplay.class, EntityDataSerializers.COMPONENT);
        public static final EntityDataAccessor<Integer> DATA_LINE_WIDTH_ID = SynchedEntityData.<Integer>defineId(Display.TextDisplay.class, EntityDataSerializers.INT);
        public static final EntityDataAccessor<Integer> DATA_BACKGROUND_COLOR_ID = SynchedEntityData.<Integer>defineId(Display.TextDisplay.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<Byte> DATA_TEXT_OPACITY_ID = SynchedEntityData.<Byte>defineId(Display.TextDisplay.class, EntityDataSerializers.BYTE);
        private static final EntityDataAccessor<Byte> DATA_STYLE_FLAGS_ID = SynchedEntityData.<Byte>defineId(Display.TextDisplay.class, EntityDataSerializers.BYTE);
        private static final IntSet TEXT_RENDER_STATE_IDS = IntSet.of(new int[]{Display.TextDisplay.DATA_TEXT_ID.id(), Display.TextDisplay.DATA_LINE_WIDTH_ID.id(), Display.TextDisplay.DATA_BACKGROUND_COLOR_ID.id(), Display.TextDisplay.DATA_TEXT_OPACITY_ID.id(), Display.TextDisplay.DATA_STYLE_FLAGS_ID.id()});
        private Display.TextDisplay.@Nullable CachedInfo clientDisplayCache;
        private Display.TextDisplay.@Nullable TextRenderState textRenderState;

        public TextDisplay(EntityType<?> type, Level level) {
            super(type, level);
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder entityData) {
            super.defineSynchedData(entityData);
            entityData.define(Display.TextDisplay.DATA_TEXT_ID, Component.empty());
            entityData.define(Display.TextDisplay.DATA_LINE_WIDTH_ID, 200);
            entityData.define(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 1073741824);
            entityData.define(Display.TextDisplay.DATA_TEXT_OPACITY_ID, -1);
            entityData.define(Display.TextDisplay.DATA_STYLE_FLAGS_ID, (byte) 0);
        }

        @Override
        public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
            super.onSyncedDataUpdated(accessor);
            if (Display.TextDisplay.TEXT_RENDER_STATE_IDS.contains(accessor.id())) {
                this.updateRenderState = true;
            }

        }

        public Component getText() {
            return (Component) this.entityData.get(Display.TextDisplay.DATA_TEXT_ID);
        }

        public void setText(Component text) {
            this.entityData.set(Display.TextDisplay.DATA_TEXT_ID, text);
        }

        public int getLineWidth() {
            return (Integer) this.entityData.get(Display.TextDisplay.DATA_LINE_WIDTH_ID);
        }

        private void setLineWidth(int width) {
            this.entityData.set(Display.TextDisplay.DATA_LINE_WIDTH_ID, width);
        }

        public byte getTextOpacity() {
            return (Byte) this.entityData.get(Display.TextDisplay.DATA_TEXT_OPACITY_ID);
        }

        public void setTextOpacity(byte opacity) {
            this.entityData.set(Display.TextDisplay.DATA_TEXT_OPACITY_ID, opacity);
        }

        public int getBackgroundColor() {
            return (Integer) this.entityData.get(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID);
        }

        private void setBackgroundColor(int color) {
            this.entityData.set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, color);
        }

        public byte getFlags() {
            return (Byte) this.entityData.get(Display.TextDisplay.DATA_STYLE_FLAGS_ID);
        }

        public void setFlags(byte flags) {
            this.entityData.set(Display.TextDisplay.DATA_STYLE_FLAGS_ID, flags);
        }

        private static byte loadFlag(byte flags, ValueInput input, String id, byte mask) {
            return input.getBooleanOr(id, false) ? (byte) (flags | mask) : flags;
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            this.setLineWidth(input.getIntOr("line_width", 200));
            this.setTextOpacity(input.getByteOr("text_opacity", (byte) -1));
            this.setBackgroundColor(input.getIntOr("background", 1073741824));
            byte b0 = loadFlag((byte) 0, input, "shadow", (byte) 1);

            b0 = loadFlag(b0, input, "see_through", (byte) 2);
            b0 = loadFlag(b0, input, "default_background", (byte) 4);
            Optional<Display.TextDisplay.Align> optional = input.<Display.TextDisplay.Align>read("alignment", Display.TextDisplay.Align.CODEC);

            if (optional.isPresent()) {
                byte b1;

                switch (((Display.TextDisplay.Align) optional.get()).ordinal()) {
                    case 0:
                        b1 = b0;
                        break;
                    case 1:
                        b1 = (byte) (b0 | 8);
                        break;
                    case 2:
                        b1 = (byte) (b0 | 16);
                        break;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }

                b0 = b1;
            }

            this.setFlags(b0);
            Optional<Component> optional1 = input.<Component>read("text", ComponentSerialization.CODEC);

            if (optional1.isPresent()) {
                try {
                    Level level = this.level();

                    if (level instanceof ServerLevel) {
                        ServerLevel serverlevel = (ServerLevel) level;
                        CommandSourceStack commandsourcestack = this.createCommandSourceStackForNameResolution(serverlevel).withPermission(LevelBasedPermissionSet.GAMEMASTER);
                        Component component = ComponentUtils.updateForEntity(commandsourcestack, (Component) optional1.get(), this, 0);

                        this.setText(component);
                    } else {
                        this.setText(Component.empty());
                    }
                } catch (Exception exception) {
                    Display.LOGGER.warn("Failed to parse display entity text {}", optional1, exception);
                }
            }

        }

        private static void storeFlag(byte flags, ValueOutput output, String id, byte mask) {
            output.putBoolean(id, (flags & mask) != 0);
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            output.store("text", ComponentSerialization.CODEC, this.getText());
            output.putInt("line_width", this.getLineWidth());
            output.putInt("background", this.getBackgroundColor());
            output.putByte("text_opacity", this.getTextOpacity());
            byte b0 = this.getFlags();

            storeFlag(b0, output, "shadow", (byte) 1);
            storeFlag(b0, output, "see_through", (byte) 2);
            storeFlag(b0, output, "default_background", (byte) 4);
            output.store("alignment", Display.TextDisplay.Align.CODEC, getAlign(b0));
        }

        @Override
        protected void updateRenderSubState(boolean shouldInterpolate, float progress) {
            if (shouldInterpolate && this.textRenderState != null) {
                this.textRenderState = this.createInterpolatedTextRenderState(this.textRenderState, progress);
            } else {
                this.textRenderState = this.createFreshTextRenderState();
            }

            this.clientDisplayCache = null;
        }

        public Display.TextDisplay.@Nullable TextRenderState textRenderState() {
            return this.textRenderState;
        }

        private Display.TextDisplay.TextRenderState createFreshTextRenderState() {
            return new Display.TextDisplay.TextRenderState(this.getText(), this.getLineWidth(), Display.IntInterpolator.constant(this.getTextOpacity()), Display.IntInterpolator.constant(this.getBackgroundColor()), this.getFlags());
        }

        private Display.TextDisplay.TextRenderState createInterpolatedTextRenderState(Display.TextDisplay.TextRenderState previous, float progress) {
            int i = previous.backgroundColor.get(progress);
            int j = previous.textOpacity.get(progress);

            return new Display.TextDisplay.TextRenderState(this.getText(), this.getLineWidth(), new Display.LinearIntInterpolator(j, this.getTextOpacity()), new Display.ColorInterpolator(i, this.getBackgroundColor()), this.getFlags());
        }

        public Display.TextDisplay.CachedInfo cacheDisplay(Display.TextDisplay.LineSplitter splitter) {
            if (this.clientDisplayCache == null) {
                if (this.textRenderState != null) {
                    this.clientDisplayCache = splitter.split(this.textRenderState.text(), this.textRenderState.lineWidth());
                } else {
                    this.clientDisplayCache = new Display.TextDisplay.CachedInfo(List.of(), 0);
                }
            }

            return this.clientDisplayCache;
        }

        public static Display.TextDisplay.Align getAlign(byte flags) {
            return (flags & 8) != 0 ? Display.TextDisplay.Align.LEFT : ((flags & 16) != 0 ? Display.TextDisplay.Align.RIGHT : Display.TextDisplay.Align.CENTER);
        }

        public static enum Align implements StringRepresentable {

            CENTER("center"), LEFT("left"), RIGHT("right");

            public static final Codec<Display.TextDisplay.Align> CODEC = StringRepresentable.<Display.TextDisplay.Align>fromEnum(Display.TextDisplay.Align::values);
            private final String name;

            private Align(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }

        public static record CachedLine(FormattedCharSequence contents, int width) {

        }

        public static record CachedInfo(List<Display.TextDisplay.CachedLine> lines, int width) {

        }

        public static record TextRenderState(Component text, int lineWidth, Display.IntInterpolator textOpacity, Display.IntInterpolator backgroundColor, byte flags) {

        }

        @FunctionalInterface
        public interface LineSplitter {

            Display.TextDisplay.CachedInfo split(Component input, int width);
        }
    }

    @FunctionalInterface
    public interface GenericInterpolator<T> {

        static <T> Display.GenericInterpolator<T> constant(T value) {
            return (f) -> {
                return value;
            };
        }

        T get(float progress);
    }

    private static record TransformationInterpolator(Transformation previous, Transformation current) implements Display.GenericInterpolator<Transformation> {

        @Override
        public Transformation get(float progress) {
            return (double) progress >= 1.0D ? this.current : this.previous.slerp(this.current, progress);
        }
    }

    @FunctionalInterface
    public interface IntInterpolator {

        static Display.IntInterpolator constant(int value) {
            return (f) -> {
                return value;
            };
        }

        int get(float progress);
    }

    private static record LinearIntInterpolator(int previous, int current) implements Display.IntInterpolator {

        @Override
        public int get(float progress) {
            return Mth.lerpInt(progress, this.previous, this.current);
        }
    }

    private static record ColorInterpolator(int previous, int current) implements Display.IntInterpolator {

        @Override
        public int get(float progress) {
            return ARGB.srgbLerp(progress, this.previous, this.current);
        }
    }

    @FunctionalInterface
    public interface FloatInterpolator {

        static Display.FloatInterpolator constant(float value) {
            return (f1) -> {
                return value;
            };
        }

        float get(float progress);
    }

    private static record LinearFloatInterpolator(float previous, float current) implements Display.FloatInterpolator {

        @Override
        public float get(float progress) {
            return Mth.lerp(progress, this.previous, this.current);
        }
    }
}
