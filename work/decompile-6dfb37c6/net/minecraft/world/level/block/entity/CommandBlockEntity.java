package net.minecraft.world.level.block.entity;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class CommandBlockEntity extends BlockEntity {

    private static final boolean DEFAULT_POWERED = false;
    private static final boolean DEFAULT_CONDITION_MET = false;
    private static final boolean DEFAULT_AUTOMATIC = false;
    private boolean powered = false;
    private boolean auto = false;
    private boolean conditionMet = false;
    private final BaseCommandBlock commandBlock = new BaseCommandBlock() {
        @Override
        public void setCommand(String command) {
            super.setCommand(command);
            CommandBlockEntity.this.setChanged();
        }

        @Override
        public void onUpdated(ServerLevel level) {
            BlockState blockstate = level.getBlockState(CommandBlockEntity.this.worldPosition);

            level.sendBlockUpdated(CommandBlockEntity.this.worldPosition, blockstate, blockstate, 3);
        }

        @Override
        public CommandSourceStack createCommandSourceStack(ServerLevel level, CommandSource source) {
            Direction direction = (Direction) CommandBlockEntity.this.getBlockState().getValue(CommandBlock.FACING);

            return new CommandSourceStack(source, Vec3.atCenterOf(CommandBlockEntity.this.worldPosition), new Vec2(0.0F, direction.toYRot()), level, LevelBasedPermissionSet.GAMEMASTER, this.getName().getString(), this.getName(), level.getServer(), (Entity) null);
        }

        @Override
        public boolean isValid() {
            return !CommandBlockEntity.this.isRemoved();
        }
    };

    public CommandBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.COMMAND_BLOCK, worldPosition, blockState);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.commandBlock.save(output);
        output.putBoolean("powered", this.isPowered());
        output.putBoolean("conditionMet", this.wasConditionMet());
        output.putBoolean("auto", this.isAutomatic());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.commandBlock.load(input);
        this.powered = input.getBooleanOr("powered", false);
        this.conditionMet = input.getBooleanOr("conditionMet", false);
        this.setAutomatic(input.getBooleanOr("auto", false));
    }

    public BaseCommandBlock getCommandBlock() {
        return this.commandBlock;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public boolean isAutomatic() {
        return this.auto;
    }

    public void setAutomatic(boolean auto) {
        boolean flag1 = this.auto;

        this.auto = auto;
        if (!flag1 && auto && !this.powered && this.level != null && this.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
            this.scheduleTick();
        }

    }

    public void onModeSwitch() {
        CommandBlockEntity.Mode commandblockentity_mode = this.getMode();

        if (commandblockentity_mode == CommandBlockEntity.Mode.AUTO && (this.powered || this.auto) && this.level != null) {
            this.scheduleTick();
        }

    }

    private void scheduleTick() {
        Block block = this.getBlockState().getBlock();

        if (block instanceof CommandBlock) {
            this.markConditionMet();
            this.level.scheduleTick(this.worldPosition, block, 1);
        }

    }

    public boolean wasConditionMet() {
        return this.conditionMet;
    }

    public boolean markConditionMet() {
        this.conditionMet = true;
        if (this.isConditional()) {
            BlockPos blockpos = this.worldPosition.relative(((Direction) this.level.getBlockState(this.worldPosition).getValue(CommandBlock.FACING)).getOpposite());

            if (this.level.getBlockState(blockpos).getBlock() instanceof CommandBlock) {
                BlockEntity blockentity = this.level.getBlockEntity(blockpos);

                this.conditionMet = blockentity instanceof CommandBlockEntity && ((CommandBlockEntity) blockentity).getCommandBlock().getSuccessCount() > 0;
            } else {
                this.conditionMet = false;
            }
        }

        return this.conditionMet;
    }

    public CommandBlockEntity.Mode getMode() {
        BlockState blockstate = this.getBlockState();

        return blockstate.is(Blocks.COMMAND_BLOCK) ? CommandBlockEntity.Mode.REDSTONE : (blockstate.is(Blocks.REPEATING_COMMAND_BLOCK) ? CommandBlockEntity.Mode.AUTO : (blockstate.is(Blocks.CHAIN_COMMAND_BLOCK) ? CommandBlockEntity.Mode.SEQUENCE : CommandBlockEntity.Mode.REDSTONE));
    }

    public boolean isConditional() {
        BlockState blockstate = this.level.getBlockState(this.getBlockPos());

        return blockstate.getBlock() instanceof CommandBlock ? (Boolean) blockstate.getValue(CommandBlock.CONDITIONAL) : false;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.commandBlock.setCustomName((Component) components.get(DataComponents.CUSTOM_NAME));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.commandBlock.getCustomName());
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("CustomName");
        output.discard("conditionMet");
        output.discard("powered");
    }

    public static enum Mode {

        SEQUENCE, AUTO, REDSTONE;

        private Mode() {}
    }
}
