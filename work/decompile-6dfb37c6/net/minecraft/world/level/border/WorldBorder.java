package net.minecraft.world.level.border;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WorldBorder extends SavedData {

    public static final double MAX_SIZE = (double) 5.999997E7F;
    public static final double MAX_CENTER_COORDINATE = 2.9999984E7D;
    public static final Codec<WorldBorder> CODEC = WorldBorder.Settings.CODEC.xmap(WorldBorder::new, WorldBorder.Settings::new);
    public static final SavedDataType<WorldBorder> TYPE = new SavedDataType<WorldBorder>("world_border", WorldBorder::new, WorldBorder.CODEC, DataFixTypes.SAVED_DATA_WORLD_BORDER);
    private final WorldBorder.Settings settings;
    private boolean initialized;
    private final List<BorderChangeListener> listeners;
    private double damagePerBlock;
    private double safeZone;
    private int warningTime;
    private int warningBlocks;
    private double centerX;
    private double centerZ;
    private int absoluteMaxSize;
    private WorldBorder.BorderExtent extent;

    public WorldBorder() {
        this(WorldBorder.Settings.DEFAULT);
    }

    public WorldBorder(WorldBorder.Settings settings) {
        this.listeners = Lists.newArrayList();
        this.damagePerBlock = 0.2D;
        this.safeZone = 5.0D;
        this.warningTime = 15;
        this.warningBlocks = 5;
        this.absoluteMaxSize = 29999984;
        this.extent = new WorldBorder.StaticBorderExtent((double) 5.999997E7F);
        this.settings = settings;
    }

    public boolean isWithinBounds(BlockPos pos) {
        return this.isWithinBounds((double) pos.getX(), (double) pos.getZ());
    }

    public boolean isWithinBounds(Vec3 pos) {
        return this.isWithinBounds(pos.x, pos.z);
    }

    public boolean isWithinBounds(ChunkPos pos) {
        return this.isWithinBounds((double) pos.getMinBlockX(), (double) pos.getMinBlockZ()) && this.isWithinBounds((double) pos.getMaxBlockX(), (double) pos.getMaxBlockZ());
    }

    public boolean isWithinBounds(AABB aabb) {
        return this.isWithinBounds(aabb.minX, aabb.minZ, aabb.maxX - (double) 1.0E-5F, aabb.maxZ - (double) 1.0E-5F);
    }

    private boolean isWithinBounds(double minX, double minZ, double maxX, double maxZ) {
        return this.isWithinBounds(minX, minZ) && this.isWithinBounds(maxX, maxZ);
    }

    public boolean isWithinBounds(double x, double z) {
        return this.isWithinBounds(x, z, 0.0D);
    }

    public boolean isWithinBounds(double x, double z, double margin) {
        return x >= this.getMinX() - margin && x < this.getMaxX() + margin && z >= this.getMinZ() - margin && z < this.getMaxZ() + margin;
    }

    public BlockPos clampToBounds(BlockPos position) {
        return this.clampToBounds((double) position.getX(), (double) position.getY(), (double) position.getZ());
    }

    public BlockPos clampToBounds(Vec3 position) {
        return this.clampToBounds(position.x(), position.y(), position.z());
    }

    public BlockPos clampToBounds(double x, double y, double z) {
        return BlockPos.containing(this.clampVec3ToBound(x, y, z));
    }

    public Vec3 clampVec3ToBound(Vec3 position) {
        return this.clampVec3ToBound(position.x, position.y, position.z);
    }

    public Vec3 clampVec3ToBound(double x, double y, double z) {
        return new Vec3(Mth.clamp(x, this.getMinX(), this.getMaxX() - (double) 1.0E-5F), y, Mth.clamp(z, this.getMinZ(), this.getMaxZ() - (double) 1.0E-5F));
    }

    public double getDistanceToBorder(Entity entity) {
        return this.getDistanceToBorder(entity.getX(), entity.getZ());
    }

    public VoxelShape getCollisionShape() {
        return this.extent.getCollisionShape();
    }

    public double getDistanceToBorder(double x, double z) {
        double d2 = z - this.getMinZ();
        double d3 = this.getMaxZ() - z;
        double d4 = x - this.getMinX();
        double d5 = this.getMaxX() - x;
        double d6 = Math.min(d4, d5);

        d6 = Math.min(d6, d2);
        return Math.min(d6, d3);
    }

    public boolean isInsideCloseToBorder(Entity source, AABB boundingBox) {
        double d0 = Math.max(Mth.absMax(boundingBox.getXsize(), boundingBox.getZsize()), 1.0D);

        return this.getDistanceToBorder(source) < d0 * 2.0D && this.isWithinBounds(source.getX(), source.getZ(), d0);
    }

    public BorderStatus getStatus() {
        return this.extent.getStatus();
    }

    public double getMinX() {
        return this.getMinX(0.0F);
    }

    public double getMinX(float deltaPartialTick) {
        return this.extent.getMinX(deltaPartialTick);
    }

    public double getMinZ() {
        return this.getMinZ(0.0F);
    }

    public double getMinZ(float deltaPartialTick) {
        return this.extent.getMinZ(deltaPartialTick);
    }

    public double getMaxX() {
        return this.getMaxX(0.0F);
    }

    public double getMaxX(float deltaPartialTick) {
        return this.extent.getMaxX(deltaPartialTick);
    }

    public double getMaxZ() {
        return this.getMaxZ(0.0F);
    }

    public double getMaxZ(float deltaPartialTick) {
        return this.extent.getMaxZ(deltaPartialTick);
    }

    public double getCenterX() {
        return this.centerX;
    }

    public double getCenterZ() {
        return this.centerZ;
    }

    public void setCenter(double x, double z) {
        this.centerX = x;
        this.centerZ = z;
        this.extent.onCenterChange();
        this.setDirty();

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onSetCenter(this, x, z);
        }

    }

    public double getSize() {
        return this.extent.getSize();
    }

    public long getLerpTime() {
        return this.extent.getLerpTime();
    }

    public double getLerpTarget() {
        return this.extent.getLerpTarget();
    }

    public void setSize(double size) {
        this.extent = new WorldBorder.StaticBorderExtent(size);
        this.setDirty();

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onSetSize(this, size);
        }

    }

    public void lerpSizeBetween(double from, double to, long ticks, long gameTime) {
        this.extent = (WorldBorder.BorderExtent) (from == to ? new WorldBorder.StaticBorderExtent(to) : new WorldBorder.MovingBorderExtent(from, to, ticks, gameTime));
        this.setDirty();

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onLerpSize(this, from, to, ticks, gameTime);
        }

    }

    protected List<BorderChangeListener> getListeners() {
        return Lists.newArrayList(this.listeners);
    }

    public void addListener(BorderChangeListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(BorderChangeListener listener) {
        this.listeners.remove(listener);
    }

    public void setAbsoluteMaxSize(int absoluteMaxSize) {
        this.absoluteMaxSize = absoluteMaxSize;
        this.extent.onAbsoluteMaxSizeChange();
    }

    public int getAbsoluteMaxSize() {
        return this.absoluteMaxSize;
    }

    public double getSafeZone() {
        return this.safeZone;
    }

    public void setSafeZone(double safeZone) {
        this.safeZone = safeZone;
        this.setDirty();

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onSetSafeZone(this, safeZone);
        }

    }

    public double getDamagePerBlock() {
        return this.damagePerBlock;
    }

    public void setDamagePerBlock(double damagePerBlock) {
        this.damagePerBlock = damagePerBlock;
        this.setDirty();

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onSetDamagePerBlock(this, damagePerBlock);
        }

    }

    public double getLerpSpeed() {
        return this.extent.getLerpSpeed();
    }

    public int getWarningTime() {
        return this.warningTime;
    }

    public void setWarningTime(int warningTime) {
        this.warningTime = warningTime;
        this.setDirty();

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onSetWarningTime(this, warningTime);
        }

    }

    public int getWarningBlocks() {
        return this.warningBlocks;
    }

    public void setWarningBlocks(int warningBlocks) {
        this.warningBlocks = warningBlocks;
        this.setDirty();

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onSetWarningBlocks(this, warningBlocks);
        }

    }

    public void tick() {
        this.extent = this.extent.update();
    }

    public void applyInitialSettings(long gameTime) {
        if (!this.initialized) {
            this.setCenter(this.settings.centerX(), this.settings.centerZ());
            this.setDamagePerBlock(this.settings.damagePerBlock());
            this.setSafeZone(this.settings.safeZone());
            this.setWarningBlocks(this.settings.warningBlocks());
            this.setWarningTime(this.settings.warningTime());
            if (this.settings.lerpTime() > 0L) {
                this.lerpSizeBetween(this.settings.size(), this.settings.lerpTarget(), this.settings.lerpTime(), gameTime);
            } else {
                this.setSize(this.settings.size());
            }

            this.initialized = true;
        }

    }

    private class MovingBorderExtent implements WorldBorder.BorderExtent {

        private final double from;
        private final double to;
        private final long lerpEnd;
        private final long lerpBegin;
        private final double lerpDuration;
        private long lerpProgress;
        private double size;
        private double previousSize;

        private MovingBorderExtent(double from, double to, long duration, long gameTime) {
            this.from = from;
            this.to = to;
            this.lerpDuration = (double) duration;
            this.lerpProgress = duration;
            this.lerpBegin = gameTime;
            this.lerpEnd = this.lerpBegin + duration;
            double d2 = this.calculateSize();

            this.size = d2;
            this.previousSize = d2;
        }

        @Override
        public double getMinX(float deltaPartialTick) {
            return Mth.clamp(WorldBorder.this.getCenterX() - Mth.lerp((double) deltaPartialTick, this.getPreviousSize(), this.getSize()) / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getMinZ(float deltaPartialTick) {
            return Mth.clamp(WorldBorder.this.getCenterZ() - Mth.lerp((double) deltaPartialTick, this.getPreviousSize(), this.getSize()) / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getMaxX(float deltaPartialTick) {
            return Mth.clamp(WorldBorder.this.getCenterX() + Mth.lerp((double) deltaPartialTick, this.getPreviousSize(), this.getSize()) / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getMaxZ(float deltaPartialTick) {
            return Mth.clamp(WorldBorder.this.getCenterZ() + Mth.lerp((double) deltaPartialTick, this.getPreviousSize(), this.getSize()) / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getSize() {
            return this.size;
        }

        public double getPreviousSize() {
            return this.previousSize;
        }

        private double calculateSize() {
            double d0 = (this.lerpDuration - (double) this.lerpProgress) / this.lerpDuration;

            return d0 < 1.0D ? Mth.lerp(d0, this.from, this.to) : this.to;
        }

        @Override
        public double getLerpSpeed() {
            return Math.abs(this.from - this.to) / (double) (this.lerpEnd - this.lerpBegin);
        }

        @Override
        public long getLerpTime() {
            return this.lerpProgress;
        }

        @Override
        public double getLerpTarget() {
            return this.to;
        }

        @Override
        public BorderStatus getStatus() {
            return this.to < this.from ? BorderStatus.SHRINKING : BorderStatus.GROWING;
        }

        @Override
        public void onCenterChange() {}

        @Override
        public void onAbsoluteMaxSizeChange() {}

        @Override
        public WorldBorder.BorderExtent update() {
            --this.lerpProgress;
            this.previousSize = this.size;
            this.size = this.calculateSize();
            if (this.lerpProgress <= 0L) {
                WorldBorder.this.setDirty();
                return WorldBorder.this.new StaticBorderExtent(this.to);
            } else {
                return this;
            }
        }

        @Override
        public VoxelShape getCollisionShape() {
            return Shapes.join(Shapes.INFINITY, Shapes.box(Math.floor(this.getMinX(0.0F)), Double.NEGATIVE_INFINITY, Math.floor(this.getMinZ(0.0F)), Math.ceil(this.getMaxX(0.0F)), Double.POSITIVE_INFINITY, Math.ceil(this.getMaxZ(0.0F))), BooleanOp.ONLY_FIRST);
        }
    }

    private class StaticBorderExtent implements WorldBorder.BorderExtent {

        private final double size;
        private double minX;
        private double minZ;
        private double maxX;
        private double maxZ;
        private VoxelShape shape;

        public StaticBorderExtent(double size) {
            this.size = size;
            this.updateBox();
        }

        @Override
        public double getMinX(float deltaPartialTick) {
            return this.minX;
        }

        @Override
        public double getMaxX(float deltaPartialTick) {
            return this.maxX;
        }

        @Override
        public double getMinZ(float deltaPartialTick) {
            return this.minZ;
        }

        @Override
        public double getMaxZ(float deltaPartialTick) {
            return this.maxZ;
        }

        @Override
        public double getSize() {
            return this.size;
        }

        @Override
        public BorderStatus getStatus() {
            return BorderStatus.STATIONARY;
        }

        @Override
        public double getLerpSpeed() {
            return 0.0D;
        }

        @Override
        public long getLerpTime() {
            return 0L;
        }

        @Override
        public double getLerpTarget() {
            return this.size;
        }

        private void updateBox() {
            this.minX = Mth.clamp(WorldBorder.this.getCenterX() - this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.minZ = Mth.clamp(WorldBorder.this.getCenterZ() - this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.maxX = Mth.clamp(WorldBorder.this.getCenterX() + this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.maxZ = Mth.clamp(WorldBorder.this.getCenterZ() + this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.shape = Shapes.join(Shapes.INFINITY, Shapes.box(Math.floor(this.getMinX(0.0F)), Double.NEGATIVE_INFINITY, Math.floor(this.getMinZ(0.0F)), Math.ceil(this.getMaxX(0.0F)), Double.POSITIVE_INFINITY, Math.ceil(this.getMaxZ(0.0F))), BooleanOp.ONLY_FIRST);
        }

        @Override
        public void onAbsoluteMaxSizeChange() {
            this.updateBox();
        }

        @Override
        public void onCenterChange() {
            this.updateBox();
        }

        @Override
        public WorldBorder.BorderExtent update() {
            return this;
        }

        @Override
        public VoxelShape getCollisionShape() {
            return this.shape;
        }
    }

    public static record Settings(double centerX, double centerZ, double damagePerBlock, double safeZone, int warningBlocks, int warningTime, double size, long lerpTime, double lerpTarget) {

        public static final WorldBorder.Settings DEFAULT = new WorldBorder.Settings(0.0D, 0.0D, 0.2D, 5.0D, 5, 300, (double) 5.999997E7F, 0L, 0.0D);
        public static final Codec<WorldBorder.Settings> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.doubleRange(-2.9999984E7D, 2.9999984E7D).fieldOf("center_x").forGetter(WorldBorder.Settings::centerX), Codec.doubleRange(-2.9999984E7D, 2.9999984E7D).fieldOf("center_z").forGetter(WorldBorder.Settings::centerZ), Codec.DOUBLE.fieldOf("damage_per_block").forGetter(WorldBorder.Settings::damagePerBlock), Codec.DOUBLE.fieldOf("safe_zone").forGetter(WorldBorder.Settings::safeZone), Codec.INT.fieldOf("warning_blocks").forGetter(WorldBorder.Settings::warningBlocks), Codec.INT.fieldOf("warning_time").forGetter(WorldBorder.Settings::warningTime), Codec.DOUBLE.fieldOf("size").forGetter(WorldBorder.Settings::size), Codec.LONG.fieldOf("lerp_time").forGetter(WorldBorder.Settings::lerpTime), Codec.DOUBLE.fieldOf("lerp_target").forGetter(WorldBorder.Settings::lerpTarget)).apply(instance, WorldBorder.Settings::new);
        });

        public Settings(WorldBorder worldBorder) {
            this(worldBorder.centerX, worldBorder.centerZ, worldBorder.damagePerBlock, worldBorder.safeZone, worldBorder.warningBlocks, worldBorder.warningTime, worldBorder.extent.getSize(), worldBorder.extent.getLerpTime(), worldBorder.extent.getLerpTarget());
        }
    }

    private interface BorderExtent {

        double getMinX(float deltaPartialTick);

        double getMaxX(float deltaPartialTick);

        double getMinZ(float deltaPartialTick);

        double getMaxZ(float deltaPartialTick);

        double getSize();

        double getLerpSpeed();

        long getLerpTime();

        double getLerpTarget();

        BorderStatus getStatus();

        void onAbsoluteMaxSizeChange();

        void onCenterChange();

        WorldBorder.BorderExtent update();

        VoxelShape getCollisionShape();
    }
}
