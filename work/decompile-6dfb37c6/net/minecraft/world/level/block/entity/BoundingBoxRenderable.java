package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

public interface BoundingBoxRenderable {

    BoundingBoxRenderable.Mode renderMode();

    BoundingBoxRenderable.RenderableBox getRenderableBox();

    public static record RenderableBox(BlockPos localPos, Vec3i size) {

        public static BoundingBoxRenderable.RenderableBox fromCorners(int x1, int y1, int z1, int x2, int y2, int z2) {
            int k1 = Math.min(x1, x2);
            int l1 = Math.min(y1, y2);
            int i2 = Math.min(z1, z2);

            return new BoundingBoxRenderable.RenderableBox(new BlockPos(k1, l1, i2), new Vec3i(Math.max(x1, x2) - k1, Math.max(y1, y2) - l1, Math.max(z1, z2) - i2));
        }
    }

    public static enum Mode {

        NONE, BOX, BOX_AND_INVISIBLE_BLOCKS;

        private Mode() {}
    }
}
