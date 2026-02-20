package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SkullBlock extends AbstractSkullBlock {

    public static final MapCodec<SkullBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(SkullBlock.Type.CODEC.fieldOf("kind").forGetter(AbstractSkullBlock::getType), propertiesCodec()).apply(instance, SkullBlock::new);
    });
    public static final int MAX = RotationSegment.getMaxSegmentIndex();
    private static final int ROTATIONS = SkullBlock.MAX + 1;
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    private static final VoxelShape SHAPE = Block.column(8.0D, 0.0D, 8.0D);
    private static final VoxelShape SHAPE_PIGLIN = Block.column(10.0D, 0.0D, 8.0D);

    @Override
    public MapCodec<? extends SkullBlock> codec() {
        return SkullBlock.CODEC;
    }

    protected SkullBlock(SkullBlock.Type type, BlockBehaviour.Properties properties) {
        super(type, properties);
        this.registerDefaultState((BlockState) this.defaultBlockState().setValue(SkullBlock.ROTATION, 0));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getType() == SkullBlock.Types.PIGLIN ? SkullBlock.SHAPE_PIGLIN : SkullBlock.SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) super.getStateForPlacement(context).setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(context.getRotation()));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(SkullBlock.ROTATION, rotation.rotate((Integer) state.getValue(SkullBlock.ROTATION), SkullBlock.ROTATIONS));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return (BlockState) state.setValue(SkullBlock.ROTATION, mirror.mirror((Integer) state.getValue(SkullBlock.ROTATION), SkullBlock.ROTATIONS));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SkullBlock.ROTATION);
    }

    public interface Type extends StringRepresentable {

        Map<String, SkullBlock.Type> TYPES = new Object2ObjectArrayMap();
        Codec<SkullBlock.Type> CODEC;

        static {
            Function function = StringRepresentable::getSerializedName;
            Map map = SkullBlock.Type.TYPES;

            Objects.requireNonNull(map);
            CODEC = Codec.stringResolver(function, map::get);
        }
    }

    public static enum Types implements SkullBlock.Type {

        SKELETON("skeleton"), WITHER_SKELETON("wither_skeleton"), PLAYER("player"), ZOMBIE("zombie"), CREEPER("creeper"), PIGLIN("piglin"), DRAGON("dragon");

        private final String name;

        private Types(String name) {
            this.name = name;
            SkullBlock.Types.TYPES.put(name, this);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
