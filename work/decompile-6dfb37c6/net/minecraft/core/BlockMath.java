package net.minecraft.core;

import com.google.common.collect.Maps;
import com.mojang.math.MatrixUtil;
import com.mojang.math.Transformation;
import java.util.Map;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class BlockMath {

    private static final Map<Direction, Transformation> VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL = Maps.newEnumMap(Map.of(Direction.SOUTH, Transformation.identity(), Direction.EAST, new Transformation((Vector3fc) null, (new Quaternionf()).rotateY(((float) Math.PI / 2F)), (Vector3fc) null, (Quaternionfc) null), Direction.WEST, new Transformation((Vector3fc) null, (new Quaternionf()).rotateY((-(float) Math.PI / 2F)), (Vector3fc) null, (Quaternionfc) null), Direction.NORTH, new Transformation((Vector3fc) null, (new Quaternionf()).rotateY((float) Math.PI), (Vector3fc) null, (Quaternionfc) null), Direction.UP, new Transformation((Vector3fc) null, (new Quaternionf()).rotateX((-(float) Math.PI / 2F)), (Vector3fc) null, (Quaternionfc) null), Direction.DOWN, new Transformation((Vector3fc) null, (new Quaternionf()).rotateX(((float) Math.PI / 2F)), (Vector3fc) null, (Quaternionfc) null)));
    private static final Map<Direction, Transformation> VANILLA_UV_TRANSFORM_GLOBAL_TO_LOCAL = Maps.newEnumMap(Util.mapValues(BlockMath.VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL, Transformation::inverse));

    public BlockMath() {}

    public static Transformation blockCenterToCorner(Transformation transform) {
        Matrix4f matrix4f = (new Matrix4f()).translation(0.5F, 0.5F, 0.5F);

        matrix4f.mul(transform.getMatrix());
        matrix4f.translate(-0.5F, -0.5F, -0.5F);
        return new Transformation(matrix4f);
    }

    public static Transformation blockCornerToCenter(Transformation transform) {
        Matrix4f matrix4f = (new Matrix4f()).translation(-0.5F, -0.5F, -0.5F);

        matrix4f.mul(transform.getMatrix());
        matrix4f.translate(0.5F, 0.5F, 0.5F);
        return new Transformation(matrix4f);
    }

    public static Transformation getFaceTransformation(Transformation transformation, Direction originalSide) {
        if (MatrixUtil.isIdentity(transformation.getMatrix())) {
            return transformation;
        } else {
            Transformation transformation1 = (Transformation) BlockMath.VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL.get(originalSide);

            transformation1 = transformation.compose(transformation1);
            Vector3f vector3f = transformation1.getMatrix().transformDirection(new Vector3f(0.0F, 0.0F, 1.0F));
            Direction direction1 = Direction.getApproximateNearest(vector3f.x, vector3f.y, vector3f.z);

            return ((Transformation) BlockMath.VANILLA_UV_TRANSFORM_GLOBAL_TO_LOCAL.get(direction1)).compose(transformation1);
        }
    }
}
