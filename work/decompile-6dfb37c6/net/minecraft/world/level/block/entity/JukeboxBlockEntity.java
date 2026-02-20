package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;

public class JukeboxBlockEntity extends BlockEntity implements ContainerSingleItem.BlockContainerSingleItem {

    public static final String SONG_ITEM_TAG_ID = "RecordItem";
    public static final String TICKS_SINCE_SONG_STARTED_TAG_ID = "ticks_since_song_started";
    private ItemStack item;
    private final JukeboxSongPlayer jukeboxSongPlayer;

    public JukeboxBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.JUKEBOX, worldPosition, blockState);
        this.item = ItemStack.EMPTY;
        this.jukeboxSongPlayer = new JukeboxSongPlayer(this::onSongChanged, this.getBlockPos());
    }

    public JukeboxSongPlayer getSongPlayer() {
        return this.jukeboxSongPlayer;
    }

    public void onSongChanged() {
        this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        this.setChanged();
    }

    private void notifyItemChangedInJukebox(boolean wasInserted) {
        if (this.level != null && this.level.getBlockState(this.getBlockPos()) == this.getBlockState()) {
            this.level.setBlock(this.getBlockPos(), (BlockState) this.getBlockState().setValue(JukeboxBlock.HAS_RECORD, wasInserted), 2);
            this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(this.getBlockState()));
        }
    }

    public void popOutTheItem() {
        if (this.level != null && !this.level.isClientSide()) {
            BlockPos blockpos = this.getBlockPos();
            ItemStack itemstack = this.getTheItem();

            if (!itemstack.isEmpty()) {
                this.removeTheItem();
                Vec3 vec3 = Vec3.atLowerCornerWithOffset(blockpos, 0.5D, 1.01D, 0.5D).offsetRandomXZ(this.level.random, 0.7F);
                ItemStack itemstack1 = itemstack.copy();
                ItemEntity itementity = new ItemEntity(this.level, vec3.x(), vec3.y(), vec3.z(), itemstack1);

                itementity.setDefaultPickUpDelay();
                this.level.addFreshEntity(itementity);
                this.onSongChanged();
            }
        }
    }

    public static void tick(Level level, BlockPos blockPos, BlockState blockState, JukeboxBlockEntity jukebox) {
        jukebox.jukeboxSongPlayer.tick(level, blockState);
    }

    public int getComparatorOutput() {
        return (Integer) JukeboxSong.fromStack(this.level.registryAccess(), this.item).map(Holder::value).map(JukeboxSong::comparatorOutput).orElse(0);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ItemStack itemstack = (ItemStack) input.read("RecordItem", ItemStack.CODEC).orElse(ItemStack.EMPTY);

        if (!this.item.isEmpty() && !ItemStack.isSameItemSameComponents(itemstack, this.item)) {
            this.jukeboxSongPlayer.stop(this.level, this.getBlockState());
        }

        this.item = itemstack;
        input.getLong("ticks_since_song_started").ifPresent((olong) -> {
            JukeboxSong.fromStack(input.lookup(), this.item).ifPresent((holder) -> {
                this.jukeboxSongPlayer.setSongWithoutPlaying(holder, olong);
            });
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.getTheItem().isEmpty()) {
            output.store("RecordItem", ItemStack.CODEC, this.getTheItem());
        }

        if (this.jukeboxSongPlayer.getSong() != null) {
            output.putLong("ticks_since_song_started", this.jukeboxSongPlayer.getTicksSinceSongStarted());
        }

    }

    @Override
    public ItemStack getTheItem() {
        return this.item;
    }

    @Override
    public ItemStack splitTheItem(int count) {
        ItemStack itemstack = this.item;

        this.setTheItem(ItemStack.EMPTY);
        return itemstack;
    }

    @Override
    public void setTheItem(ItemStack itemStack) {
        this.item = itemStack;
        boolean flag = !this.item.isEmpty();
        Optional<Holder<JukeboxSong>> optional = JukeboxSong.fromStack(this.level.registryAccess(), this.item);

        this.notifyItemChangedInJukebox(flag);
        if (flag && optional.isPresent()) {
            this.jukeboxSongPlayer.play(this.level, (Holder) optional.get());
        } else {
            this.jukeboxSongPlayer.stop(this.level, this.getBlockState());
        }

    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, this.getBlockPos(), GameEvent.Context.of(this.getBlockState()));
        this.level.levelEvent(1011, this.getBlockPos(), 0);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public BlockEntity getContainerBlockEntity() {
        return this;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack itemStack) {
        return itemStack.has(DataComponents.JUKEBOX_PLAYABLE) && this.getItem(slot).isEmpty();
    }

    @Override
    public boolean canTakeItem(Container into, int slot, ItemStack itemStack) {
        return into.hasAnyMatching(ItemStack::isEmpty);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        this.popOutTheItem();
    }

    @VisibleForTesting
    public void setSongItemWithoutPlaying(ItemStack itemStack) {
        this.item = itemStack;
        JukeboxSong.fromStack(this.level.registryAccess(), itemStack).ifPresent((holder) -> {
            this.jukeboxSongPlayer.setSongWithoutPlaying(holder, 0L);
        });
        this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        this.setChanged();
    }

    @VisibleForTesting
    public void tryForcePlaySong() {
        JukeboxSong.fromStack(this.level.registryAccess(), this.getTheItem()).ifPresent((holder) -> {
            this.jukeboxSongPlayer.play(this.level, holder);
        });
    }
}
