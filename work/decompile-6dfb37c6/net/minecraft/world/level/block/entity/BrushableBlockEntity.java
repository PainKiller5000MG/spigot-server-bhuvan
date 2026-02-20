package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Objects;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class BrushableBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOOT_TABLE_TAG = "LootTable";
    private static final String LOOT_TABLE_SEED_TAG = "LootTableSeed";
    private static final String HIT_DIRECTION_TAG = "hit_direction";
    private static final String ITEM_TAG = "item";
    private static final int BRUSH_COOLDOWN_TICKS = 10;
    private static final int BRUSH_RESET_TICKS = 40;
    private static final int REQUIRED_BRUSHES_TO_BREAK = 10;
    private int brushCount;
    private long brushCountResetsAtTick;
    private long coolDownEndsAtTick;
    public ItemStack item;
    private @Nullable Direction hitDirection;
    public @Nullable ResourceKey<LootTable> lootTable;
    public long lootTableSeed;

    public BrushableBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.BRUSHABLE_BLOCK, worldPosition, blockState);
        this.item = ItemStack.EMPTY;
    }

    public boolean brush(long gameTime, ServerLevel level, LivingEntity user, Direction direction, ItemStack brush) {
        if (this.hitDirection == null) {
            this.hitDirection = direction;
        }

        this.brushCountResetsAtTick = gameTime + 40L;
        if (gameTime < this.coolDownEndsAtTick) {
            return false;
        } else {
            this.coolDownEndsAtTick = gameTime + 10L;
            this.unpackLootTable(level, user, brush);
            int j = this.getCompletionState();

            if (++this.brushCount >= 10) {
                this.brushingCompleted(level, user, brush);
                return true;
            } else {
                level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 2);
                int k = this.getCompletionState();

                if (j != k) {
                    BlockState blockstate = this.getBlockState();
                    BlockState blockstate1 = (BlockState) blockstate.setValue(BlockStateProperties.DUSTED, k);

                    level.setBlock(this.getBlockPos(), blockstate1, 3);
                }

                return false;
            }
        }
    }

    private void unpackLootTable(ServerLevel level, LivingEntity user, ItemStack brush) {
        if (this.lootTable != null) {
            LootTable loottable = level.getServer().reloadableRegistries().getLootTable(this.lootTable);

            if (user instanceof ServerPlayer) {
                ServerPlayer serverplayer = (ServerPlayer) user;

                CriteriaTriggers.GENERATE_LOOT.trigger(serverplayer, this.lootTable);
            }

            LootParams lootparams = (new LootParams.Builder(level)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.worldPosition)).withLuck(user.getLuck()).withParameter(LootContextParams.THIS_ENTITY, user).withParameter(LootContextParams.TOOL, brush).create(LootContextParamSets.ARCHAEOLOGY);
            ObjectArrayList<ItemStack> objectarraylist = loottable.getRandomItems(lootparams, this.lootTableSeed);
            ItemStack itemstack1;

            switch (objectarraylist.size()) {
                case 0:
                    itemstack1 = ItemStack.EMPTY;
                    break;
                case 1:
                    itemstack1 = (ItemStack) objectarraylist.getFirst();
                    break;
                default:
                    BrushableBlockEntity.LOGGER.warn("Expected max 1 loot from loot table {}, but got {}", this.lootTable.identifier(), objectarraylist.size());
                    itemstack1 = (ItemStack) objectarraylist.getFirst();
            }

            this.item = itemstack1;
            this.lootTable = null;
            this.setChanged();
        }
    }

    private void brushingCompleted(ServerLevel level, LivingEntity user, ItemStack brush) {
        this.dropContent(level, user, brush);
        BlockState blockstate = this.getBlockState();

        level.levelEvent(3008, this.getBlockPos(), Block.getId(blockstate));
        Block block = this.getBlockState().getBlock();
        Block block1;

        if (block instanceof BrushableBlock brushableblock) {
            block1 = brushableblock.getTurnsInto();
        } else {
            block1 = Blocks.AIR;
        }

        level.setBlock(this.worldPosition, block1.defaultBlockState(), 3);
    }

    private void dropContent(ServerLevel level, LivingEntity user, ItemStack brush) {
        this.unpackLootTable(level, user, brush);
        if (!this.item.isEmpty()) {
            double d0 = (double) EntityType.ITEM.getWidth();
            double d1 = 1.0D - d0;
            double d2 = d0 / 2.0D;
            Direction direction = (Direction) Objects.requireNonNullElse(this.hitDirection, Direction.UP);
            BlockPos blockpos = this.worldPosition.relative(direction, 1);
            double d3 = (double) blockpos.getX() + 0.5D * d1 + d2;
            double d4 = (double) blockpos.getY() + 0.5D + (double) (EntityType.ITEM.getHeight() / 2.0F);
            double d5 = (double) blockpos.getZ() + 0.5D * d1 + d2;
            ItemEntity itementity = new ItemEntity(level, d3, d4, d5, this.item.split(level.random.nextInt(21) + 10));

            itementity.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(itementity);
            this.item = ItemStack.EMPTY;
        }

    }

    public void checkReset(ServerLevel level) {
        if (this.brushCount != 0 && level.getGameTime() >= this.brushCountResetsAtTick) {
            int i = this.getCompletionState();

            this.brushCount = Math.max(0, this.brushCount - 2);
            int j = this.getCompletionState();

            if (i != j) {
                level.setBlock(this.getBlockPos(), (BlockState) this.getBlockState().setValue(BlockStateProperties.DUSTED, j), 3);
            }

            int k = 4;

            this.brushCountResetsAtTick = level.getGameTime() + 4L;
        }

        if (this.brushCount == 0) {
            this.hitDirection = null;
            this.brushCountResetsAtTick = 0L;
            this.coolDownEndsAtTick = 0L;
        } else {
            level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 2);
        }

    }

    private boolean tryLoadLootTable(ValueInput input) {
        this.lootTable = (ResourceKey) input.read("LootTable", LootTable.KEY_CODEC).orElse((Object) null);
        this.lootTableSeed = input.getLongOr("LootTableSeed", 0L);
        return this.lootTable != null;
    }

    private boolean trySaveLootTable(ValueOutput base) {
        if (this.lootTable == null) {
            return false;
        } else {
            base.store("LootTable", LootTable.KEY_CODEC, this.lootTable);
            if (this.lootTableSeed != 0L) {
                base.putLong("LootTableSeed", this.lootTableSeed);
            }

            return true;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag compoundtag = super.getUpdateTag(registries);

        compoundtag.storeNullable("hit_direction", Direction.LEGACY_ID_CODEC, this.hitDirection);
        if (!this.item.isEmpty()) {
            RegistryOps<Tag> registryops = registries.<Tag>createSerializationContext(NbtOps.INSTANCE);

            compoundtag.store("item", ItemStack.CODEC, registryops, this.item);
        }

        return compoundtag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (!this.tryLoadLootTable(input)) {
            this.item = (ItemStack) input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        } else {
            this.item = ItemStack.EMPTY;
        }

        this.hitDirection = (Direction) input.read("hit_direction", Direction.LEGACY_ID_CODEC).orElse((Object) null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output) && !this.item.isEmpty()) {
            output.store("item", ItemStack.CODEC, this.item);
        }

    }

    public void setLootTable(ResourceKey<LootTable> lootTable, long seed) {
        this.lootTable = lootTable;
        this.lootTableSeed = seed;
    }

    private int getCompletionState() {
        return this.brushCount == 0 ? 0 : (this.brushCount < 3 ? 1 : (this.brushCount < 6 ? 2 : 3));
    }

    public @Nullable Direction getHitDirection() {
        return this.hitDirection;
    }

    public ItemStack getItem() {
        return this.item;
    }
}
