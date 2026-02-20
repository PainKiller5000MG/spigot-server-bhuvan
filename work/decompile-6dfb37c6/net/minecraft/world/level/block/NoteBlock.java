package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class NoteBlock extends Block {

    public static final MapCodec<NoteBlock> CODEC = simpleCodec(NoteBlock::new);
    public static final EnumProperty<NoteBlockInstrument> INSTRUMENT = BlockStateProperties.NOTEBLOCK_INSTRUMENT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty NOTE = BlockStateProperties.NOTE;
    public static final int NOTE_VOLUME = 3;

    @Override
    public MapCodec<NoteBlock> codec() {
        return NoteBlock.CODEC;
    }

    public NoteBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.HARP)).setValue(NoteBlock.NOTE, 0)).setValue(NoteBlock.POWERED, false));
    }

    private BlockState setInstrument(LevelReader level, BlockPos position, BlockState state) {
        NoteBlockInstrument noteblockinstrument = level.getBlockState(position.above()).instrument();

        if (noteblockinstrument.worksAboveNoteBlock()) {
            return (BlockState) state.setValue(NoteBlock.INSTRUMENT, noteblockinstrument);
        } else {
            NoteBlockInstrument noteblockinstrument1 = level.getBlockState(position.below()).instrument();
            NoteBlockInstrument noteblockinstrument2 = noteblockinstrument1.worksAboveNoteBlock() ? NoteBlockInstrument.HARP : noteblockinstrument1;

            return (BlockState) state.setValue(NoteBlock.INSTRUMENT, noteblockinstrument2);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.setInstrument(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        boolean flag = directionToNeighbour.getAxis() == Direction.Axis.Y;

        return flag ? this.setInstrument(level, pos, state) : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean flag1 = level.hasNeighborSignal(pos);

        if (flag1 != (Boolean) state.getValue(NoteBlock.POWERED)) {
            if (flag1) {
                this.playNote((Entity) null, state, level, pos);
            }

            level.setBlock(pos, (BlockState) state.setValue(NoteBlock.POWERED, flag1), 3);
        }

    }

    private void playNote(@Nullable Entity source, BlockState state, Level level, BlockPos pos) {
        if (((NoteBlockInstrument) state.getValue(NoteBlock.INSTRUMENT)).worksAboveNoteBlock() || level.getBlockState(pos.above()).isAir()) {
            level.blockEvent(pos, this, 0, 0);
            level.gameEvent(source, (Holder) GameEvent.NOTE_BLOCK_PLAY, pos);
        }

    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return (InteractionResult) (itemStack.is(ItemTags.NOTE_BLOCK_TOP_INSTRUMENTS) && hitResult.getDirection() == Direction.UP ? InteractionResult.PASS : super.useItemOn(itemStack, state, level, pos, player, hand, hitResult));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            state = (BlockState) state.cycle(NoteBlock.NOTE);
            level.setBlock(pos, state, 3);
            this.playNote(player, state, level, pos);
            player.awardStat(Stats.TUNE_NOTEBLOCK);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            this.playNote(player, state, level, pos);
            player.awardStat(Stats.PLAY_NOTEBLOCK);
        }
    }

    public static float getPitchFromNote(int twoOctaveRangeNote) {
        return (float) Math.pow(2.0D, (double) (twoOctaveRangeNote - 12) / 12.0D);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
        NoteBlockInstrument noteblockinstrument = (NoteBlockInstrument) state.getValue(NoteBlock.INSTRUMENT);
        float f;

        if (noteblockinstrument.isTunable()) {
            int k = (Integer) state.getValue(NoteBlock.NOTE);

            f = getPitchFromNote(k);
            level.addParticle(ParticleTypes.NOTE, (double) pos.getX() + 0.5D, (double) pos.getY() + 1.2D, (double) pos.getZ() + 0.5D, (double) k / 24.0D, 0.0D, 0.0D);
        } else {
            f = 1.0F;
        }

        Holder<SoundEvent> holder;

        if (noteblockinstrument.hasCustomSound()) {
            Identifier identifier = this.getCustomSoundId(level, pos);

            if (identifier == null) {
                return false;
            }

            holder = Holder.<SoundEvent>direct(SoundEvent.createVariableRangeEvent(identifier));
        } else {
            holder = noteblockinstrument.getSoundEvent();
        }

        level.playSeededSound((Entity) null, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, holder, SoundSource.RECORDS, 3.0F, f, level.random.nextLong());
        return true;
    }

    private @Nullable Identifier getCustomSoundId(Level level, BlockPos pos) {
        BlockEntity blockentity = level.getBlockEntity(pos.above());

        if (blockentity instanceof SkullBlockEntity skullblockentity) {
            return skullblockentity.getNoteBlockSound();
        } else {
            return null;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NoteBlock.INSTRUMENT, NoteBlock.POWERED, NoteBlock.NOTE);
    }
}
