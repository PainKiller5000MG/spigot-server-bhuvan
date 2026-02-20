package net.minecraft.world.level.block.state.properties;

import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.util.SegmentedAnglePrecision;

public class RotationSegment {

    private static final SegmentedAnglePrecision SEGMENTED_ANGLE16 = new SegmentedAnglePrecision(4);
    private static final int MAX_SEGMENT_INDEX = RotationSegment.SEGMENTED_ANGLE16.getMask();
    private static final int NORTH_0 = 0;
    private static final int EAST_90 = 4;
    private static final int SOUTH_180 = 8;
    private static final int WEST_270 = 12;

    public RotationSegment() {}

    public static int getMaxSegmentIndex() {
        return RotationSegment.MAX_SEGMENT_INDEX;
    }

    public static int convertToSegment(Direction direction) {
        return RotationSegment.SEGMENTED_ANGLE16.fromDirection(direction);
    }

    public static int convertToSegment(float rotDegrees) {
        return RotationSegment.SEGMENTED_ANGLE16.fromDegrees(rotDegrees);
    }

    public static Optional<Direction> convertToDirection(int segment) {
        Direction direction;

        switch (segment) {
            case 0:
                direction = Direction.NORTH;
                break;
            case 4:
                direction = Direction.EAST;
                break;
            case 8:
                direction = Direction.SOUTH;
                break;
            case 12:
                direction = Direction.WEST;
                break;
            default:
                direction = null;
        }

        Direction direction1 = direction;

        return Optional.ofNullable(direction1);
    }

    public static float convertToDegrees(int segment) {
        return RotationSegment.SEGMENTED_ANGLE16.toDegrees(segment);
    }
}
