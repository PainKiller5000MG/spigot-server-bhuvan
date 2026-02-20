package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

public class JigsawJunction {

    private final int sourceX;
    private final int sourceGroundY;
    private final int sourceZ;
    private final int deltaY;
    private final StructureTemplatePool.Projection destProjection;

    public JigsawJunction(int sourceX, int sourceGroundY, int sourceZ, int deltaY, StructureTemplatePool.Projection destProjection) {
        this.sourceX = sourceX;
        this.sourceGroundY = sourceGroundY;
        this.sourceZ = sourceZ;
        this.deltaY = deltaY;
        this.destProjection = destProjection;
    }

    public int getSourceX() {
        return this.sourceX;
    }

    public int getSourceGroundY() {
        return this.sourceGroundY;
    }

    public int getSourceZ() {
        return this.sourceZ;
    }

    public int getDeltaY() {
        return this.deltaY;
    }

    public StructureTemplatePool.Projection getDestProjection() {
        return this.destProjection;
    }

    public <T> Dynamic<T> serialize(DynamicOps<T> ops) {
        ImmutableMap.Builder<T, T> immutablemap_builder = ImmutableMap.builder();

        immutablemap_builder.put(ops.createString("source_x"), ops.createInt(this.sourceX)).put(ops.createString("source_ground_y"), ops.createInt(this.sourceGroundY)).put(ops.createString("source_z"), ops.createInt(this.sourceZ)).put(ops.createString("delta_y"), ops.createInt(this.deltaY)).put(ops.createString("dest_proj"), ops.createString(this.destProjection.getName()));
        return new Dynamic(ops, ops.createMap(immutablemap_builder.build()));
    }

    public static <T> JigsawJunction deserialize(Dynamic<T> input) {
        return new JigsawJunction(input.get("source_x").asInt(0), input.get("source_ground_y").asInt(0), input.get("source_z").asInt(0), input.get("delta_y").asInt(0), StructureTemplatePool.Projection.byName(input.get("dest_proj").asString("")));
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            JigsawJunction jigsawjunction = (JigsawJunction) o;

            return this.sourceX != jigsawjunction.sourceX ? false : (this.sourceZ != jigsawjunction.sourceZ ? false : (this.deltaY != jigsawjunction.deltaY ? false : this.destProjection == jigsawjunction.destProjection));
        } else {
            return false;
        }
    }

    public int hashCode() {
        int i = this.sourceX;

        i = 31 * i + this.sourceGroundY;
        i = 31 * i + this.sourceZ;
        i = 31 * i + this.deltaY;
        i = 31 * i + this.destProjection.hashCode();
        return i;
    }

    public String toString() {
        return "JigsawJunction{sourceX=" + this.sourceX + ", sourceGroundY=" + this.sourceGroundY + ", sourceZ=" + this.sourceZ + ", deltaY=" + this.deltaY + ", destProjection=" + String.valueOf(this.destProjection) + "}";
    }
}
