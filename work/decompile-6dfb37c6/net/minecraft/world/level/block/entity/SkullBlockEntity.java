package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class SkullBlockEntity extends BlockEntity {

    private static final String TAG_PROFILE = "profile";
    private static final String TAG_NOTE_BLOCK_SOUND = "note_block_sound";
    private static final String TAG_CUSTOM_NAME = "custom_name";
    public @Nullable ResolvableProfile owner;
    public @Nullable Identifier noteBlockSound;
    private int animationTickCount;
    private boolean isAnimating;
    private @Nullable Component customName;

    public SkullBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.SKULL, worldPosition, blockState);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("profile", ResolvableProfile.CODEC, this.owner);
        output.storeNullable("note_block_sound", Identifier.CODEC, this.noteBlockSound);
        output.storeNullable("custom_name", ComponentSerialization.CODEC, this.customName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = (ResolvableProfile) input.read("profile", ResolvableProfile.CODEC).orElse((Object) null);
        this.noteBlockSound = (Identifier) input.read("note_block_sound", Identifier.CODEC).orElse((Object) null);
        this.customName = parseCustomNameSafe(input, "custom_name");
    }

    public static void animation(Level level, BlockPos pos, BlockState state, SkullBlockEntity entity) {
        if (state.hasProperty(SkullBlock.POWERED) && (Boolean) state.getValue(SkullBlock.POWERED)) {
            entity.isAnimating = true;
            ++entity.animationTickCount;
        } else {
            entity.isAnimating = false;
        }

    }

    public float getAnimation(float a) {
        return this.isAnimating ? (float) this.animationTickCount + a : (float) this.animationTickCount;
    }

    public @Nullable ResolvableProfile getOwnerProfile() {
        return this.owner;
    }

    public @Nullable Identifier getNoteBlockSound() {
        return this.noteBlockSound;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.owner = (ResolvableProfile) components.get(DataComponents.PROFILE);
        this.noteBlockSound = (Identifier) components.get(DataComponents.NOTE_BLOCK_SOUND);
        this.customName = (Component) components.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.PROFILE, this.owner);
        components.set(DataComponents.NOTE_BLOCK_SOUND, this.noteBlockSound);
        components.set(DataComponents.CUSTOM_NAME, this.customName);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("profile");
        output.discard("note_block_sound");
        output.discard("custom_name");
    }
}
