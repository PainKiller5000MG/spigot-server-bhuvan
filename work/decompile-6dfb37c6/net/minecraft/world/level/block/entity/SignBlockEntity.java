package net.minecraft.world.level.block.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SignBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TEXT_LINE_WIDTH = 90;
    private static final int TEXT_LINE_HEIGHT = 10;
    private static final boolean DEFAULT_IS_WAXED = false;
    public @Nullable UUID playerWhoMayEdit;
    private SignText frontText;
    private SignText backText;
    private boolean isWaxed;

    public SignBlockEntity(BlockPos worldPosition, BlockState blockState) {
        this(BlockEntityType.SIGN, worldPosition, blockState);
    }

    public SignBlockEntity(BlockEntityType type, BlockPos worldPosition, BlockState blockState) {
        super(type, worldPosition, blockState);
        this.isWaxed = false;
        this.frontText = this.createDefaultSignText();
        this.backText = this.createDefaultSignText();
    }

    protected SignText createDefaultSignText() {
        return new SignText();
    }

    public boolean isFacingFrontText(Player player) {
        Block block = this.getBlockState().getBlock();

        if (block instanceof SignBlock signblock) {
            Vec3 vec3 = signblock.getSignHitboxCenterPosition(this.getBlockState());
            double d0 = player.getX() - ((double) this.getBlockPos().getX() + vec3.x);
            double d1 = player.getZ() - ((double) this.getBlockPos().getZ() + vec3.z);
            float f = signblock.getYRotationDegrees(this.getBlockState());
            float f1 = (float) (Mth.atan2(d1, d0) * (double) (180F / (float) Math.PI)) - 90.0F;

            return Mth.degreesDifferenceAbs(f, f1) <= 90.0F;
        } else {
            return false;
        }
    }

    public SignText getText(boolean isFrontText) {
        return isFrontText ? this.frontText : this.backText;
    }

    public SignText getFrontText() {
        return this.frontText;
    }

    public SignText getBackText() {
        return this.backText;
    }

    public int getTextLineHeight() {
        return 10;
    }

    public int getMaxTextLineWidth() {
        return 90;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("front_text", SignText.DIRECT_CODEC, this.frontText);
        output.store("back_text", SignText.DIRECT_CODEC, this.backText);
        output.putBoolean("is_waxed", this.isWaxed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.frontText = (SignText) input.read("front_text", SignText.DIRECT_CODEC).map(this::loadLines).orElseGet(SignText::new);
        this.backText = (SignText) input.read("back_text", SignText.DIRECT_CODEC).map(this::loadLines).orElseGet(SignText::new);
        this.isWaxed = input.getBooleanOr("is_waxed", false);
    }

    private SignText loadLines(SignText data) {
        for (int i = 0; i < 4; ++i) {
            Component component = this.loadLine(data.getMessage(i, false));
            Component component1 = this.loadLine(data.getMessage(i, true));

            data = data.setMessage(i, component, component1);
        }

        return data;
    }

    private Component loadLine(Component component) {
        Level level = this.level;

        if (level instanceof ServerLevel serverlevel) {
            try {
                return ComponentUtils.updateForEntity(createCommandSourceStack((Player) null, serverlevel, this.worldPosition), component, (Entity) null, 0);
            } catch (CommandSyntaxException commandsyntaxexception) {
                ;
            }
        }

        return component;
    }

    public void updateSignText(Player player, boolean frontText, List<FilteredText> lines) {
        if (!this.isWaxed() && player.getUUID().equals(this.getPlayerWhoMayEdit()) && this.level != null) {
            this.updateText((signtext) -> {
                return this.setMessages(player, lines, signtext);
            }, frontText);
            this.setAllowedPlayerEditor((UUID) null);
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        } else {
            SignBlockEntity.LOGGER.warn("Player {} just tried to change non-editable sign", player.getPlainTextName());
        }
    }

    public boolean updateText(UnaryOperator<SignText> function, boolean isFrontText) {
        SignText signtext = this.getText(isFrontText);

        return this.setText((SignText) function.apply(signtext), isFrontText);
    }

    private SignText setMessages(Player player, List<FilteredText> lines, SignText text) {
        for (int i = 0; i < lines.size(); ++i) {
            FilteredText filteredtext = (FilteredText) lines.get(i);
            Style style = text.getMessage(i, player.isTextFilteringEnabled()).getStyle();

            if (player.isTextFilteringEnabled()) {
                text = text.setMessage(i, Component.literal(filteredtext.filteredOrEmpty()).setStyle(style));
            } else {
                text = text.setMessage(i, Component.literal(filteredtext.raw()).setStyle(style), Component.literal(filteredtext.filteredOrEmpty()).setStyle(style));
            }
        }

        return text;
    }

    public boolean setText(SignText text, boolean isFrontText) {
        return isFrontText ? this.setFrontText(text) : this.setBackText(text);
    }

    private boolean setBackText(SignText text) {
        if (text != this.backText) {
            this.backText = text;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    private boolean setFrontText(SignText text) {
        if (text != this.frontText) {
            this.frontText = text;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean canExecuteClickCommands(boolean isFrontText, Player player) {
        return this.isWaxed() && this.getText(isFrontText).hasAnyClickCommands(player);
    }

    public boolean executeClickCommandsIfPresent(ServerLevel level, Player player, BlockPos pos, boolean isFrontText) {
        boolean flag1 = false;

        for(Component component : this.getText(isFrontText).getMessages(player.isTextFilteringEnabled())) {
            Style style = component.getStyle();
            ClickEvent clickevent = style.getClickEvent();
            byte b0 = 0;

            //$FF: b0->value
            //0->net/minecraft/network/chat/ClickEvent$RunCommand
            //1->net/minecraft/network/chat/ClickEvent$ShowDialog
            //2->net/minecraft/network/chat/ClickEvent$Custom
            switch (clickevent.typeSwitch<invokedynamic>(clickevent, b0)) {
                case -1:
                default:
                    break;
                case 0:
                    ClickEvent.RunCommand clickevent_runcommand = (ClickEvent.RunCommand)clickevent;

                    level.getServer().getCommands().performPrefixedCommand(createCommandSourceStack(player, level, pos), clickevent_runcommand.command());
                    flag1 = true;
                    break;
                case 1:
                    ClickEvent.ShowDialog clickevent_showdialog = (ClickEvent.ShowDialog)clickevent;

                    player.openDialog(clickevent_showdialog.dialog());
                    flag1 = true;
                    break;
                case 2:
                    ClickEvent.Custom clickevent_custom = (ClickEvent.Custom)clickevent;

                    level.getServer().handleCustomClickAction(clickevent_custom.id(), clickevent_custom.payload());
                    flag1 = true;
            }
        }

        return flag1;
    }

    private static CommandSourceStack createCommandSourceStack(@Nullable Player player, ServerLevel level, BlockPos pos) {
        String s = player == null ? "Sign" : player.getPlainTextName();
        Component component = (Component) (player == null ? Component.literal("Sign") : player.getDisplayName());

        return new CommandSourceStack(CommandSource.NULL, Vec3.atCenterOf(pos), Vec2.ZERO, level, LevelBasedPermissionSet.GAMEMASTER, s, component, level.getServer(), player);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public void setAllowedPlayerEditor(@Nullable UUID playerUUID) {
        this.playerWhoMayEdit = playerUUID;
    }

    public @Nullable UUID getPlayerWhoMayEdit() {
        return this.playerWhoMayEdit;
    }

    private void markUpdated() {
        this.setChanged();
        this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    public boolean isWaxed() {
        return this.isWaxed;
    }

    public boolean setWaxed(boolean isWaxed) {
        if (this.isWaxed != isWaxed) {
            this.isWaxed = isWaxed;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean playerIsTooFarAwayToEdit(UUID player) {
        Player player1 = this.level.getPlayerByUUID(player);

        return player1 == null || !player1.isWithinBlockInteractionRange(this.getBlockPos(), 4.0D);
    }

    public static void tick(Level level, BlockPos blockPos, BlockState blockState, SignBlockEntity signBlockEntity) {
        UUID uuid = signBlockEntity.getPlayerWhoMayEdit();

        if (uuid != null) {
            signBlockEntity.clearInvalidPlayerWhoMayEdit(signBlockEntity, level, uuid);
        }

    }

    private void clearInvalidPlayerWhoMayEdit(SignBlockEntity signBlockEntity, Level level, UUID playerWhoMayEdit) {
        if (signBlockEntity.playerIsTooFarAwayToEdit(playerWhoMayEdit)) {
            signBlockEntity.setAllowedPlayerEditor((UUID) null);
        }

    }

    public SoundEvent getSignInteractionFailedSoundEvent() {
        return SoundEvents.WAXED_SIGN_INTERACT_FAIL;
    }
}
