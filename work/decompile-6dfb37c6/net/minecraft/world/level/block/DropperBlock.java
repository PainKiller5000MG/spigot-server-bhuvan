package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class DropperBlock extends DispenserBlock {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<DropperBlock> CODEC = simpleCodec(DropperBlock::new);
    private static final DispenseItemBehavior DISPENSE_BEHAVIOUR = new DefaultDispenseItemBehavior();

    @Override
    public MapCodec<DropperBlock> codec() {
        return DropperBlock.CODEC;
    }

    public DropperBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected DispenseItemBehavior getDispenseMethod(Level level, ItemStack itemStack) {
        return DropperBlock.DISPENSE_BEHAVIOUR;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new DropperBlockEntity(worldPosition, blockState);
    }

    @Override
    public void dispenseFrom(ServerLevel level, BlockState state, BlockPos pos) {
        DispenserBlockEntity dispenserblockentity = (DispenserBlockEntity) level.getBlockEntity(pos, BlockEntityType.DROPPER).orElse((Object) null);

        if (dispenserblockentity == null) {
            DropperBlock.LOGGER.warn("Ignoring dispensing attempt for Dropper without matching block entity at {}", pos);
        } else {
            BlockSource blocksource = new BlockSource(level, pos, state, dispenserblockentity);
            int i = dispenserblockentity.getRandomSlot(level.random);

            if (i < 0) {
                level.levelEvent(1001, pos, 0);
            } else {
                ItemStack itemstack = dispenserblockentity.getItem(i);

                if (!itemstack.isEmpty()) {
                    Direction direction = (Direction) level.getBlockState(pos).getValue(DropperBlock.FACING);
                    Container container = HopperBlockEntity.getContainerAt(level, pos.relative(direction));
                    ItemStack itemstack1;

                    if (container == null) {
                        itemstack1 = DropperBlock.DISPENSE_BEHAVIOUR.dispense(blocksource, itemstack);
                    } else {
                        itemstack1 = HopperBlockEntity.addItem(dispenserblockentity, container, itemstack.copyWithCount(1), direction.getOpposite());
                        if (itemstack1.isEmpty()) {
                            itemstack1 = itemstack.copy();
                            itemstack1.shrink(1);
                        } else {
                            itemstack1 = itemstack.copy();
                        }
                    }

                    dispenserblockentity.setItem(i, itemstack1);
                }
            }
        }
    }
}
