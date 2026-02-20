package net.minecraft.world.entity.animal.equine;

import java.util.Objects;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractChestedHorse extends AbstractHorse {

    private static final EntityDataAccessor<Boolean> DATA_ID_CHEST = SynchedEntityData.<Boolean>defineId(AbstractChestedHorse.class, EntityDataSerializers.BOOLEAN);
    private static final boolean DEFAULT_HAS_CHEST = false;
    private final EntityDimensions babyDimensions;

    protected AbstractChestedHorse(EntityType<? extends AbstractChestedHorse> type, Level level) {
        super(type, level);
        this.canGallop = false;
        this.babyDimensions = type.getDimensions().withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, type.getHeight() - 0.15625F, 0.0F)).scale(0.5F);
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.MAX_HEALTH);

        Objects.requireNonNull(random);
        attributeinstance.setBaseValue((double) generateMaxHealth(random::nextInt));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(AbstractChestedHorse.DATA_ID_CHEST, false);
    }

    public static AttributeSupplier.Builder createBaseChestedHorseAttributes() {
        return createBaseHorseAttributes().add(Attributes.MOVEMENT_SPEED, (double) 0.175F).add(Attributes.JUMP_STRENGTH, 0.5D);
    }

    public boolean hasChest() {
        return (Boolean) this.entityData.get(AbstractChestedHorse.DATA_ID_CHEST);
    }

    public void setChest(boolean flag) {
        this.entityData.set(AbstractChestedHorse.DATA_ID_CHEST, flag);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? this.babyDimensions : super.getDefaultDimensions(pose);
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        if (this.hasChest()) {
            this.spawnAtLocation(level, (ItemLike) Blocks.CHEST);
            this.setChest(false);
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("ChestedHorse", this.hasChest());
        if (this.hasChest()) {
            ValueOutput.TypedOutputList<ItemStackWithSlot> valueoutput_typedoutputlist = output.<ItemStackWithSlot>list("Items", ItemStackWithSlot.CODEC);

            for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
                ItemStack itemstack = this.inventory.getItem(i);

                if (!itemstack.isEmpty()) {
                    valueoutput_typedoutputlist.add(new ItemStackWithSlot(i, itemstack));
                }
            }
        }

    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setChest(input.getBooleanOr("ChestedHorse", false));
        this.createInventory();
        if (this.hasChest()) {
            for (ItemStackWithSlot itemstackwithslot : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
                if (itemstackwithslot.isValidInContainer(this.inventory.getContainerSize())) {
                    this.inventory.setItem(itemstackwithslot.slot(), itemstackwithslot.stack());
                }
            }
        }

    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        return slot == 499 ? new SlotAccess() {
            @Override
            public ItemStack get() {
                return AbstractChestedHorse.this.hasChest() ? new ItemStack(Items.CHEST) : ItemStack.EMPTY;
            }

            @Override
            public boolean set(ItemStack itemStack) {
                if (itemStack.isEmpty()) {
                    if (AbstractChestedHorse.this.hasChest()) {
                        AbstractChestedHorse.this.setChest(false);
                        AbstractChestedHorse.this.createInventory();
                    }

                    return true;
                } else if (itemStack.is(Items.CHEST)) {
                    if (!AbstractChestedHorse.this.hasChest()) {
                        AbstractChestedHorse.this.setChest(true);
                        AbstractChestedHorse.this.createInventory();
                    }

                    return true;
                } else {
                    return false;
                }
            }
        } : super.getSlot(slot);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean flag = !this.isBaby() && this.isTamed() && player.isSecondaryUseActive();

        if (!this.isVehicle() && !flag) {
            ItemStack itemstack = player.getItemInHand(hand);

            if (!itemstack.isEmpty()) {
                if (this.isFood(itemstack)) {
                    return this.fedFood(player, itemstack);
                }

                if (!this.isTamed()) {
                    this.makeMad();
                    return InteractionResult.SUCCESS;
                }

                if (!this.hasChest() && itemstack.is(Items.CHEST)) {
                    this.equipChest(player, itemstack);
                    return InteractionResult.SUCCESS;
                }
            }

            return super.mobInteract(player, hand);
        } else {
            return super.mobInteract(player, hand);
        }
    }

    private void equipChest(Player player, ItemStack itemStack) {
        this.setChest(true);
        this.playChestEquipsSound();
        itemStack.consume(1, player);
        this.createInventory();
    }

    @Override
    public Vec3[] getQuadLeashOffsets() {
        return Leashable.createQuadLeashOffsets(this, 0.04D, 0.41D, 0.18D, 0.73D);
    }

    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.DONKEY_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public int getInventoryColumns() {
        return this.hasChest() ? 5 : 0;
    }
}
