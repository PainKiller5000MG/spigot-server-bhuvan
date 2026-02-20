package net.minecraft.world.level.block.entity;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LecternBlockEntity extends BlockEntity implements Clearable, MenuProvider {

    public static final int DATA_PAGE = 0;
    public static final int NUM_DATA = 1;
    public static final int SLOT_BOOK = 0;
    public static final int NUM_SLOTS = 1;
    public final Container bookAccess = new Container() {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return LecternBlockEntity.this.book.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? LecternBlockEntity.this.book : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            if (slot == 0) {
                ItemStack itemstack = LecternBlockEntity.this.book.split(count);

                if (LecternBlockEntity.this.book.isEmpty()) {
                    LecternBlockEntity.this.onBookItemRemove();
                }

                return itemstack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot == 0) {
                ItemStack itemstack = LecternBlockEntity.this.book;

                LecternBlockEntity.this.book = ItemStack.EMPTY;
                LecternBlockEntity.this.onBookItemRemove();
                return itemstack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public void setItem(int slot, ItemStack itemStack) {}

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            LecternBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(LecternBlockEntity.this, player) && LecternBlockEntity.this.hasBook();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack itemStack) {
            return false;
        }

        @Override
        public void clearContent() {}
    };
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int dataId) {
            return dataId == 0 ? LecternBlockEntity.this.page : 0;
        }

        @Override
        public void set(int dataId, int value) {
            if (dataId == 0) {
                LecternBlockEntity.this.setPage(value);
            }

        }

        @Override
        public int getCount() {
            return 1;
        }
    };
    private ItemStack book;
    private int page;
    private int pageCount;

    public LecternBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.LECTERN, worldPosition, blockState);
        this.book = ItemStack.EMPTY;
    }

    public ItemStack getBook() {
        return this.book;
    }

    public boolean hasBook() {
        return this.book.has(DataComponents.WRITABLE_BOOK_CONTENT) || this.book.has(DataComponents.WRITTEN_BOOK_CONTENT);
    }

    public void setBook(ItemStack book) {
        this.setBook(book, (Player) null);
    }

    private void onBookItemRemove() {
        this.page = 0;
        this.pageCount = 0;
        LecternBlock.resetBookState((Entity) null, this.getLevel(), this.getBlockPos(), this.getBlockState(), false);
    }

    public void setBook(ItemStack book, @Nullable Player resolutionContext) {
        this.book = this.resolveBook(book, resolutionContext);
        this.page = 0;
        this.pageCount = getPageCount(this.book);
        this.setChanged();
    }

    public void setPage(int page) {
        int j = Mth.clamp(page, 0, this.pageCount - 1);

        if (j != this.page) {
            this.page = j;
            this.setChanged();
            LecternBlock.signalPageChange(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }

    }

    public int getPage() {
        return this.page;
    }

    public int getRedstoneSignal() {
        float f = this.pageCount > 1 ? (float) this.getPage() / ((float) this.pageCount - 1.0F) : 1.0F;

        return Mth.floor(f * 14.0F) + (this.hasBook() ? 1 : 0);
    }

    private ItemStack resolveBook(ItemStack book, @Nullable Player player) {
        Level level = this.level;

        if (level instanceof ServerLevel serverlevel) {
            WrittenBookContent.resolveForItem(book, this.createCommandSourceStack(player, serverlevel), player);
        }

        return book;
    }

    private CommandSourceStack createCommandSourceStack(@Nullable Player player, ServerLevel level) {
        String s;
        Component component;

        if (player == null) {
            s = "Lectern";
            component = Component.literal("Lectern");
        } else {
            s = player.getPlainTextName();
            component = player.getDisplayName();
        }

        Vec3 vec3 = Vec3.atCenterOf(this.worldPosition);

        return new CommandSourceStack(CommandSource.NULL, vec3, Vec2.ZERO, level, LevelBasedPermissionSet.GAMEMASTER, s, component, level.getServer(), player);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.book = (ItemStack) input.read("Book", ItemStack.CODEC).map((itemstack) -> {
            return this.resolveBook(itemstack, (Player) null);
        }).orElse(ItemStack.EMPTY);
        this.pageCount = getPageCount(this.book);
        this.page = Mth.clamp(input.getIntOr("Page", 0), 0, this.pageCount - 1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.getBook().isEmpty()) {
            output.store("Book", ItemStack.CODEC, this.getBook());
            output.putInt("Page", this.page);
        }

    }

    @Override
    public void clearContent() {
        this.setBook(ItemStack.EMPTY);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if ((Boolean) state.getValue(LecternBlock.HAS_BOOK) && this.level != null) {
            Direction direction = (Direction) state.getValue(LecternBlock.FACING);
            ItemStack itemstack = this.getBook().copy();
            float f = 0.25F * (float) direction.getStepX();
            float f1 = 0.25F * (float) direction.getStepZ();
            ItemEntity itementity = new ItemEntity(this.level, (double) pos.getX() + 0.5D + (double) f, (double) (pos.getY() + 1), (double) pos.getZ() + 0.5D + (double) f1, itemstack);

            itementity.setDefaultPickUpDelay();
            this.level.addFreshEntity(itementity);
        }

    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LecternMenu(containerId, this.bookAccess, this.dataAccess);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.lectern");
    }

    private static int getPageCount(ItemStack book) {
        WrittenBookContent writtenbookcontent = (WrittenBookContent) book.get(DataComponents.WRITTEN_BOOK_CONTENT);

        if (writtenbookcontent != null) {
            return writtenbookcontent.pages().size();
        } else {
            WritableBookContent writablebookcontent = (WritableBookContent) book.get(DataComponents.WRITABLE_BOOK_CONTENT);

            return writablebookcontent != null ? writablebookcontent.pages().size() : 0;
        }
    }
}
