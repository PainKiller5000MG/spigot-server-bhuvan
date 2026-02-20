package net.minecraft.world.item;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.NullOps;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ItemStack implements DataComponentHolder {

    private static final List<Component> OP_NBT_WARNING = List.of(Component.translatable("item.op_warning.line1").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.translatable("item.op_warning.line2").withStyle(ChatFormatting.RED), Component.translatable("item.op_warning.line3").withStyle(ChatFormatting.RED));
    private static final Component UNBREAKABLE_TOOLTIP = Component.translatable("item.unbreakable").withStyle(ChatFormatting.BLUE);
    private static final Component INTANGIBLE_TOOLTIP = Component.translatable("item.intangible").withStyle(ChatFormatting.GRAY);
    public static final MapCodec<ItemStack> MAP_CODEC = MapCodec.recursive("ItemStack", (codec) -> {
        return RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Item.CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder), ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1).forGetter(ItemStack::getCount), DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter((itemstack) -> {
                return itemstack.components.asPatch();
            })).apply(instance, ItemStack::new);
        });
    });
    public static final Codec<ItemStack> CODEC;
    public static final Codec<ItemStack> SINGLE_ITEM_CODEC;
    public static final Codec<ItemStack> STRICT_CODEC;
    public static final Codec<ItemStack> STRICT_SINGLE_ITEM_CODEC;
    public static final Codec<ItemStack> OPTIONAL_CODEC;
    public static final Codec<ItemStack> SIMPLE_ITEM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_UNTRUSTED_STREAM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC;
    private static final Logger LOGGER;
    public static final ItemStack EMPTY;
    private static final Component DISABLED_ITEM_TOOLTIP;
    private int count;
    private int popTime;
    /** @deprecated */
    @Deprecated
    private @Nullable Item item;
    private final PatchedDataComponentMap components;
    private @Nullable Entity entityRepresentation;

    public static DataResult<ItemStack> validateStrict(ItemStack itemStack) {
        DataResult<Unit> dataresult = validateComponents(itemStack.getComponents());

        return dataresult.isError() ? dataresult.map((unit) -> {
            return itemStack;
        }) : (itemStack.getCount() > itemStack.getMaxStackSize() ? DataResult.error(() -> {
            int i = itemStack.getCount();

            return "Item stack with stack size of " + i + " was larger than maximum: " + itemStack.getMaxStackSize();
        }) : DataResult.success(itemStack));
    }

    private static StreamCodec<RegistryFriendlyByteBuf, ItemStack> createOptionalStreamCodec(final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> patchCodec) {
        return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            public ItemStack decode(RegistryFriendlyByteBuf input) {
                int i = input.readVarInt();

                if (i <= 0) {
                    return ItemStack.EMPTY;
                } else {
                    Holder<Item> holder = (Holder) Item.STREAM_CODEC.decode(input);
                    DataComponentPatch datacomponentpatch = (DataComponentPatch) patchCodec.decode(input);

                    return new ItemStack(holder, i, datacomponentpatch);
                }
            }

            public void encode(RegistryFriendlyByteBuf output, ItemStack itemStack) {
                if (itemStack.isEmpty()) {
                    output.writeVarInt(0);
                } else {
                    output.writeVarInt(itemStack.getCount());
                    Item.STREAM_CODEC.encode(output, itemStack.getItemHolder());
                    patchCodec.encode(output, itemStack.components.asPatch());
                }
            }
        };
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> validatedStreamCodec(final StreamCodec<RegistryFriendlyByteBuf, ItemStack> codec) {
        return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            public ItemStack decode(RegistryFriendlyByteBuf input) {
                ItemStack itemstack = (ItemStack) codec.decode(input);

                if (!itemstack.isEmpty()) {
                    RegistryOps<Unit> registryops = input.registryAccess().<Unit>createSerializationContext(NullOps.INSTANCE);

                    ItemStack.CODEC.encodeStart(registryops, itemstack).getOrThrow(DecoderException::new);
                }

                return itemstack;
            }

            public void encode(RegistryFriendlyByteBuf output, ItemStack value) {
                codec.encode(output, value);
            }
        };
    }

    public Optional<TooltipComponent> getTooltipImage() {
        return this.getItem().getTooltipImage(this);
    }

    @Override
    public DataComponentMap getComponents() {
        return (DataComponentMap) (!this.isEmpty() ? this.components : DataComponentMap.EMPTY);
    }

    public DataComponentMap getPrototype() {
        return !this.isEmpty() ? this.getItem().components() : DataComponentMap.EMPTY;
    }

    public DataComponentPatch getComponentsPatch() {
        return !this.isEmpty() ? this.components.asPatch() : DataComponentPatch.EMPTY;
    }

    public DataComponentMap immutableComponents() {
        return !this.isEmpty() ? this.components.toImmutableMap() : DataComponentMap.EMPTY;
    }

    public boolean hasNonDefault(DataComponentType<?> type) {
        return !this.isEmpty() && this.components.hasNonDefault(type);
    }

    public ItemStack(ItemLike item) {
        this(item, 1);
    }

    public ItemStack(Holder<Item> item) {
        this(item.value(), 1);
    }

    public ItemStack(Holder<Item> item, int count, DataComponentPatch components) {
        this(item.value(), count, PatchedDataComponentMap.fromPatch(((Item) item.value()).components(), components));
    }

    public ItemStack(Holder<Item> item, int count) {
        this(item.value(), count);
    }

    public ItemStack(ItemLike item, int count) {
        this(item, count, new PatchedDataComponentMap(item.asItem().components()));
    }

    private ItemStack(ItemLike item, int count, PatchedDataComponentMap components) {
        this.item = item.asItem();
        this.count = count;
        this.components = components;
    }

    private ItemStack(@Nullable Void nullMarker) {
        this.item = null;
        this.components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
    }

    public static DataResult<Unit> validateComponents(DataComponentMap components) {
        if (components.has(DataComponents.MAX_DAMAGE) && (Integer) components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
            return DataResult.error(() -> {
                return "Item cannot be both damageable and stackable";
            });
        } else {
            ItemContainerContents itemcontainercontents = (ItemContainerContents) components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

            for (ItemStack itemstack : itemcontainercontents.nonEmptyItems()) {
                int i = itemstack.getCount();
                int j = itemstack.getMaxStackSize();

                if (i > j) {
                    return DataResult.error(() -> {
                        return "Item stack with count of " + i + " was larger than maximum: " + j;
                    });
                }
            }

            return DataResult.success(Unit.INSTANCE);
        }
    }

    public boolean isEmpty() {
        return this == ItemStack.EMPTY || this.item == Items.AIR || this.count <= 0;
    }

    public boolean isItemEnabled(FeatureFlagSet enabledFeatures) {
        return this.isEmpty() || this.getItem().isEnabled(enabledFeatures);
    }

    public ItemStack split(int amount) {
        int j = Math.min(amount, this.getCount());
        ItemStack itemstack = this.copyWithCount(j);

        this.shrink(j);
        return itemstack;
    }

    public ItemStack copyAndClear() {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = this.copy();

            this.setCount(0);
            return itemstack;
        }
    }

    public Item getItem() {
        return this.isEmpty() ? Items.AIR : this.item;
    }

    public Holder<Item> getItemHolder() {
        return this.getItem().builtInRegistryHolder();
    }

    public boolean is(TagKey<Item> tag) {
        return this.getItem().builtInRegistryHolder().is(tag);
    }

    public boolean is(Item item) {
        return this.getItem() == item;
    }

    public boolean is(Predicate<Holder<Item>> item) {
        return item.test(this.getItem().builtInRegistryHolder());
    }

    public boolean is(Holder<Item> item) {
        return this.getItem().builtInRegistryHolder() == item;
    }

    public boolean is(HolderSet<Item> set) {
        return set.contains(this.getItemHolder());
    }

    public Stream<TagKey<Item>> getTags() {
        return this.getItem().builtInRegistryHolder().tags();
    }

    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        BlockPos blockpos = context.getClickedPos();

        if (player != null && !player.getAbilities().mayBuild && !this.canPlaceOnBlockInAdventureMode(new BlockInWorld(context.getLevel(), blockpos, false))) {
            return InteractionResult.PASS;
        } else {
            Item item = this.getItem();
            InteractionResult interactionresult = item.useOn(context);

            if (player != null && interactionresult instanceof InteractionResult.Success) {
                InteractionResult.Success interactionresult_success = (InteractionResult.Success) interactionresult;

                if (interactionresult_success.wasItemInteraction()) {
                    player.awardStat(Stats.ITEM_USED.get(item));
                }
            }

            return interactionresult;
        }
    }

    public float getDestroySpeed(BlockState state) {
        return this.getItem().getDestroySpeed(this, state);
    }

    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = this.copy();
        boolean flag = this.getUseDuration(player) <= 0;
        InteractionResult interactionresult = this.getItem().use(level, player, hand);

        if (flag && interactionresult instanceof InteractionResult.Success interactionresult_success) {
            return interactionresult_success.heldItemTransformedTo(interactionresult_success.heldItemTransformedTo() == null ? this.applyAfterUseComponentSideEffects(player, itemstack) : interactionresult_success.heldItemTransformedTo().applyAfterUseComponentSideEffects(player, itemstack));
        } else {
            return interactionresult;
        }
    }

    public ItemStack finishUsingItem(Level level, LivingEntity livingEntity) {
        ItemStack itemstack = this.copy();
        ItemStack itemstack1 = this.getItem().finishUsingItem(this, level, livingEntity);

        return itemstack1.applyAfterUseComponentSideEffects(livingEntity, itemstack);
    }

    private ItemStack applyAfterUseComponentSideEffects(LivingEntity user, ItemStack stackBeforeUsing) {
        UseRemainder useremainder = (UseRemainder) stackBeforeUsing.get(DataComponents.USE_REMAINDER);
        UseCooldown usecooldown = (UseCooldown) stackBeforeUsing.get(DataComponents.USE_COOLDOWN);
        int i = stackBeforeUsing.getCount();
        ItemStack itemstack1 = this;

        if (useremainder != null) {
            boolean flag = user.hasInfiniteMaterials();

            Objects.requireNonNull(user);
            itemstack1 = useremainder.convertIntoRemainder(this, i, flag, user::handleExtraItemsCreatedOnUse);
        }

        if (usecooldown != null) {
            usecooldown.apply(stackBeforeUsing, user);
        }

        return itemstack1;
    }

    public int getMaxStackSize() {
        return (Integer) this.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
    }

    public boolean isDamageableItem() {
        return this.has(DataComponents.MAX_DAMAGE) && !this.has(DataComponents.UNBREAKABLE) && this.has(DataComponents.DAMAGE);
    }

    public boolean isDamaged() {
        return this.isDamageableItem() && this.getDamageValue() > 0;
    }

    public int getDamageValue() {
        return Mth.clamp((Integer) this.getOrDefault(DataComponents.DAMAGE, 0), 0, this.getMaxDamage());
    }

    public void setDamageValue(int value) {
        this.set(DataComponents.DAMAGE, Mth.clamp(value, 0, this.getMaxDamage()));
    }

    public int getMaxDamage() {
        return (Integer) this.getOrDefault(DataComponents.MAX_DAMAGE, 0);
    }

    public boolean isBroken() {
        return this.isDamageableItem() && this.getDamageValue() >= this.getMaxDamage();
    }

    public boolean nextDamageWillBreak() {
        return this.isDamageableItem() && this.getDamageValue() >= this.getMaxDamage() - 1;
    }

    public void hurtAndBreak(int amount, ServerLevel level, @Nullable ServerPlayer player, Consumer<Item> onBreak) {
        int j = this.processDurabilityChange(amount, level, player);

        if (j != 0) {
            this.applyDamage(this.getDamageValue() + j, player, onBreak);
        }

    }

    private int processDurabilityChange(int amount, ServerLevel level, @Nullable ServerPlayer player) {
        return !this.isDamageableItem() ? 0 : (player != null && player.hasInfiniteMaterials() ? 0 : (amount > 0 ? EnchantmentHelper.processDurabilityChange(level, this, amount) : amount));
    }

    private void applyDamage(int newDamage, @Nullable ServerPlayer player, Consumer<Item> onBreak) {
        if (player != null) {
            CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(player, this, newDamage);
        }

        this.setDamageValue(newDamage);
        if (this.isBroken()) {
            Item item = this.getItem();

            this.shrink(1);
            onBreak.accept(item);
        }

    }

    public void hurtWithoutBreaking(int amount, Player player) {
        if (player instanceof ServerPlayer serverplayer) {
            int j = this.processDurabilityChange(amount, serverplayer.level(), serverplayer);

            if (j == 0) {
                return;
            }

            int k = Math.min(this.getDamageValue() + j, this.getMaxDamage() - 1);

            this.applyDamage(k, serverplayer, (item) -> {
            });
        }

    }

    public void hurtAndBreak(int amount, LivingEntity owner, InteractionHand hand) {
        this.hurtAndBreak(amount, owner, hand.asEquipmentSlot());
    }

    public void hurtAndBreak(int amount, LivingEntity owner, EquipmentSlot slot) {
        Level level = owner.level();

        if (level instanceof ServerLevel serverlevel) {
            ServerPlayer serverplayer;

            if (owner instanceof ServerPlayer serverplayer1) {
                serverplayer = serverplayer1;
            } else {
                serverplayer = null;
            }

            this.hurtAndBreak(amount, serverlevel, serverplayer, (item) -> {
                owner.onEquippedItemBroken(item, slot);
            });
        }

    }

    public ItemStack hurtAndConvertOnBreak(int amount, ItemLike newItem, LivingEntity owner, EquipmentSlot slot) {
        this.hurtAndBreak(amount, owner, slot);
        if (this.isEmpty()) {
            ItemStack itemstack = this.transmuteCopyIgnoreEmpty(newItem, 1);

            if (itemstack.isDamageableItem()) {
                itemstack.setDamageValue(0);
            }

            return itemstack;
        } else {
            return this;
        }
    }

    public boolean isBarVisible() {
        return this.getItem().isBarVisible(this);
    }

    public int getBarWidth() {
        return this.getItem().getBarWidth(this);
    }

    public int getBarColor() {
        return this.getItem().getBarColor(this);
    }

    public boolean overrideStackedOnOther(Slot slot, ClickAction clickAction, Player player) {
        return this.getItem().overrideStackedOnOther(this, slot, clickAction, player);
    }

    public boolean overrideOtherStackedOnMe(ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem) {
        return this.getItem().overrideOtherStackedOnMe(this, other, slot, clickAction, player, carriedItem);
    }

    public boolean hurtEnemy(LivingEntity mob, LivingEntity attacker) {
        Item item = this.getItem();

        item.hurtEnemy(this, mob, attacker);
        if (this.has(DataComponents.WEAPON)) {
            if (attacker instanceof Player) {
                Player player = (Player) attacker;

                player.awardStat(Stats.ITEM_USED.get(item));
            }

            return true;
        } else {
            return false;
        }
    }

    public void postHurtEnemy(LivingEntity mob, LivingEntity attacker) {
        this.getItem().postHurtEnemy(this, mob, attacker);
        Weapon weapon = (Weapon) this.get(DataComponents.WEAPON);

        if (weapon != null) {
            this.hurtAndBreak(weapon.itemDamagePerAttack(), attacker, EquipmentSlot.MAINHAND);
        }

    }

    public void mineBlock(Level level, BlockState state, BlockPos pos, Player owner) {
        Item item = this.getItem();

        if (item.mineBlock(this, level, state, pos, owner)) {
            owner.awardStat(Stats.ITEM_USED.get(item));
        }

    }

    public boolean isCorrectToolForDrops(BlockState state) {
        return this.getItem().isCorrectToolForDrops(this, state);
    }

    public InteractionResult interactLivingEntity(Player player, LivingEntity target, InteractionHand hand) {
        Equippable equippable = (Equippable) this.get(DataComponents.EQUIPPABLE);

        if (equippable != null && equippable.equipOnInteract()) {
            InteractionResult interactionresult = equippable.equipOnTarget(player, target, this);

            if (interactionresult != InteractionResult.PASS) {
                return interactionresult;
            }
        }

        return this.getItem().interactLivingEntity(this, player, target, hand);
    }

    public ItemStack copy() {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = new ItemStack(this.getItem(), this.count, this.components.copy());

            itemstack.setPopTime(this.getPopTime());
            return itemstack;
        }
    }

    public ItemStack copyWithCount(int count) {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = this.copy();

            itemstack.setCount(count);
            return itemstack;
        }
    }

    public ItemStack transmuteCopy(ItemLike newItem) {
        return this.transmuteCopy(newItem, this.getCount());
    }

    public ItemStack transmuteCopy(ItemLike newItem, int newCount) {
        return this.isEmpty() ? ItemStack.EMPTY : this.transmuteCopyIgnoreEmpty(newItem, newCount);
    }

    private ItemStack transmuteCopyIgnoreEmpty(ItemLike newItem, int newCount) {
        return new ItemStack(newItem.asItem().builtInRegistryHolder(), newCount, this.components.asPatch());
    }

    public static boolean matches(ItemStack a, ItemStack b) {
        return a == b ? true : (a.getCount() != b.getCount() ? false : isSameItemSameComponents(a, b));
    }

    /** @deprecated */
    @Deprecated
    public static boolean listMatches(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        } else {
            for (int i = 0; i < left.size(); ++i) {
                if (!matches((ItemStack) left.get(i), (ItemStack) right.get(i))) {
                    return false;
                }
            }

            return true;
        }
    }

    public static boolean isSameItem(ItemStack a, ItemStack b) {
        return a.is(b.getItem());
    }

    public static boolean isSameItemSameComponents(ItemStack a, ItemStack b) {
        return !a.is(b.getItem()) ? false : (a.isEmpty() && b.isEmpty() ? true : Objects.equals(a.components, b.components));
    }

    public static boolean matchesIgnoringComponents(ItemStack a, ItemStack b, Predicate<DataComponentType<?>> ignoredPredicate) {
        if (a == b) {
            return true;
        } else if (a.getCount() != b.getCount()) {
            return false;
        } else if (!a.is(b.getItem())) {
            return false;
        } else if (a.isEmpty() && b.isEmpty()) {
            return true;
        } else if (a.components.size() != b.components.size()) {
            return false;
        } else {
            for (DataComponentType<?> datacomponenttype : a.components.keySet()) {
                Object object = a.components.get(datacomponenttype);
                Object object1 = b.components.get(datacomponenttype);

                if (object == null || object1 == null) {
                    return false;
                }

                if (!Objects.equals(object, object1) && !ignoredPredicate.test(datacomponenttype)) {
                    return false;
                }
            }

            return true;
        }
    }

    public static MapCodec<ItemStack> lenientOptionalFieldOf(String name) {
        return ItemStack.CODEC.lenientOptionalFieldOf(name).xmap((optional) -> {
            return (ItemStack) optional.orElse(ItemStack.EMPTY);
        }, (itemstack) -> {
            return itemstack.isEmpty() ? Optional.empty() : Optional.of(itemstack);
        });
    }

    public static int hashItemAndComponents(@Nullable ItemStack item) {
        if (item != null) {
            int i = 31 + item.getItem().hashCode();

            return 31 * i + item.getComponents().hashCode();
        } else {
            return 0;
        }
    }

    /** @deprecated */
    @Deprecated
    public static int hashStackList(List<ItemStack> items) {
        int i = 0;

        for (ItemStack itemstack : items) {
            i = i * 31 + hashItemAndComponents(itemstack);
        }

        return i;
    }

    public String toString() {
        int i = this.getCount();

        return i + " " + String.valueOf(this.getItem());
    }

    public void inventoryTick(Level level, Entity owner, @Nullable EquipmentSlot slot) {
        if (this.popTime > 0) {
            --this.popTime;
        }

        if (level instanceof ServerLevel serverlevel) {
            this.getItem().inventoryTick(this, serverlevel, owner, slot);
        }

    }

    public void onCraftedBy(Player player, int craftCount) {
        player.awardStat(Stats.ITEM_CRAFTED.get(this.getItem()), craftCount);
        this.getItem().onCraftedBy(this, player);
    }

    public void onCraftedBySystem(Level level) {
        this.getItem().onCraftedPostProcess(this, level);
    }

    public int getUseDuration(LivingEntity user) {
        return this.getItem().getUseDuration(this, user);
    }

    public ItemUseAnimation getUseAnimation() {
        return this.getItem().getUseAnimation(this);
    }

    public void releaseUsing(Level level, LivingEntity entity, int remainingTime) {
        ItemStack itemstack = this.copy();

        if (this.getItem().releaseUsing(this, level, entity, remainingTime)) {
            ItemStack itemstack1 = this.applyAfterUseComponentSideEffects(entity, itemstack);

            if (itemstack1 != this) {
                entity.setItemInHand(entity.getUsedItemHand(), itemstack1);
            }
        }

    }

    public void causeUseVibration(Entity causer, Holder.Reference<GameEvent> event) {
        UseEffects useeffects = (UseEffects) this.get(DataComponents.USE_EFFECTS);

        if (useeffects != null && useeffects.interactVibrations()) {
            causer.gameEvent(event);
        }

    }

    public boolean useOnRelease() {
        return this.getItem().useOnRelease(this);
    }

    public <T> @Nullable T set(DataComponentType<T> type, @Nullable T value) {
        return (T) this.components.set(type, value);
    }

    public <T> @Nullable T set(TypedDataComponent<T> value) {
        return (T) this.components.set(value);
    }

    public <T> void copyFrom(DataComponentType<T> type, DataComponentGetter source) {
        this.set(type, source.get(type));
    }

    public <T, U> @Nullable T update(DataComponentType<T> type, T defaultValue, U value, BiFunction<T, U, T> combiner) {
        return (T) this.set(type, combiner.apply(this.getOrDefault(type, defaultValue), value));
    }

    public <T> @Nullable T update(DataComponentType<T> type, T defaultValue, UnaryOperator<T> function) {
        T t1 = (T) this.getOrDefault(type, defaultValue);

        return (T) this.set(type, function.apply(t1));
    }

    public <T> @Nullable T remove(DataComponentType<? extends T> type) {
        return (T) this.components.remove(type);
    }

    public void applyComponentsAndValidate(DataComponentPatch patch) {
        DataComponentPatch datacomponentpatch1 = this.components.asPatch();

        this.components.applyPatch(patch);
        Optional<DataResult.Error<ItemStack>> optional = validateStrict(this).error();

        if (optional.isPresent()) {
            ItemStack.LOGGER.error("Failed to apply component patch '{}' to item: '{}'", patch, ((Error) optional.get()).message());
            this.components.restorePatch(datacomponentpatch1);
        }

    }

    public void applyComponents(DataComponentPatch patch) {
        this.components.applyPatch(patch);
    }

    public void applyComponents(DataComponentMap components) {
        this.components.setAll(components);
    }

    public Component getHoverName() {
        Component component = this.getCustomName();

        return component != null ? component : this.getItemName();
    }

    public @Nullable Component getCustomName() {
        Component component = (Component) this.get(DataComponents.CUSTOM_NAME);

        if (component != null) {
            return component;
        } else {
            WrittenBookContent writtenbookcontent = (WrittenBookContent) this.get(DataComponents.WRITTEN_BOOK_CONTENT);

            if (writtenbookcontent != null) {
                String s = (String) writtenbookcontent.title().raw();

                if (!StringUtil.isBlank(s)) {
                    return Component.literal(s);
                }
            }

            return null;
        }
    }

    public Component getItemName() {
        return this.getItem().getName(this);
    }

    public Component getStyledHoverName() {
        MutableComponent mutablecomponent = Component.empty().append(this.getHoverName()).withStyle(this.getRarity().color());

        if (this.has(DataComponents.CUSTOM_NAME)) {
            mutablecomponent.withStyle(ChatFormatting.ITALIC);
        }

        return mutablecomponent;
    }

    public <T extends TooltipProvider> void addToTooltip(DataComponentType<T> type, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> consumer, TooltipFlag flag) {
        T t0 = (T) (this.get(type));

        if (t0 != null && display.shows(type)) {
            t0.addToTooltip(context, consumer, flag, this.components);
        }

    }

    public List<Component> getTooltipLines(Item.TooltipContext context, @Nullable Player player, TooltipFlag tooltipFlag) {
        TooltipDisplay tooltipdisplay = (TooltipDisplay) this.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);

        if (!tooltipFlag.isCreative() && tooltipdisplay.hideTooltip()) {
            boolean flag = this.getItem().shouldPrintOpWarning(this, player);

            return flag ? ItemStack.OP_NBT_WARNING : List.of();
        } else {
            List<Component> list = Lists.newArrayList();

            list.add(this.getStyledHoverName());
            Objects.requireNonNull(list);
            this.addDetailsToTooltip(context, tooltipdisplay, player, tooltipFlag, list::add);
            return list;
        }
    }

    public void addDetailsToTooltip(Item.TooltipContext context, TooltipDisplay display, @Nullable Player player, TooltipFlag tooltipFlag, Consumer<Component> builder) {
        this.getItem().appendHoverText(this, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.TROPICAL_FISH_PATTERN, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.INSTRUMENT, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.MAP_ID, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.BEES, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.CONTAINER_LOOT, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.CONTAINER, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.BANNER_PATTERNS, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.POT_DECORATIONS, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.WRITTEN_BOOK_CONTENT, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.CHARGED_PROJECTILES, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.FIREWORKS, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.FIREWORK_EXPLOSION, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.POTION_CONTENTS, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.JUKEBOX_PLAYABLE, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.TRIM, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.STORED_ENCHANTMENTS, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.ENCHANTMENTS, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.DYED_COLOR, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.PROFILE, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.LORE, context, display, builder, tooltipFlag);
        this.addAttributeTooltips(builder, display, player);
        this.addUnitComponentToTooltip(DataComponents.INTANGIBLE_PROJECTILE, ItemStack.INTANGIBLE_TOOLTIP, display, builder);
        this.addUnitComponentToTooltip(DataComponents.UNBREAKABLE, ItemStack.UNBREAKABLE_TOOLTIP, display, builder);
        this.addToTooltip(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.SUSPICIOUS_STEW_EFFECTS, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.BLOCK_STATE, context, display, builder, tooltipFlag);
        this.addToTooltip(DataComponents.ENTITY_DATA, context, display, builder, tooltipFlag);
        if ((this.is(Items.SPAWNER) || this.is(Items.TRIAL_SPAWNER)) && display.shows(DataComponents.BLOCK_ENTITY_DATA)) {
            TypedEntityData<BlockEntityType<?>> typedentitydata = (TypedEntityData) this.get(DataComponents.BLOCK_ENTITY_DATA);

            Spawner.appendHoverText(typedentitydata, builder, "SpawnData");
        }

        AdventureModePredicate adventuremodepredicate = (AdventureModePredicate) this.get(DataComponents.CAN_BREAK);

        if (adventuremodepredicate != null && display.shows(DataComponents.CAN_BREAK)) {
            builder.accept(CommonComponents.EMPTY);
            builder.accept(AdventureModePredicate.CAN_BREAK_HEADER);
            adventuremodepredicate.addToTooltip(builder);
        }

        AdventureModePredicate adventuremodepredicate1 = (AdventureModePredicate) this.get(DataComponents.CAN_PLACE_ON);

        if (adventuremodepredicate1 != null && display.shows(DataComponents.CAN_PLACE_ON)) {
            builder.accept(CommonComponents.EMPTY);
            builder.accept(AdventureModePredicate.CAN_PLACE_HEADER);
            adventuremodepredicate1.addToTooltip(builder);
        }

        if (tooltipFlag.isAdvanced()) {
            if (this.isDamaged() && display.shows(DataComponents.DAMAGE)) {
                builder.accept(Component.translatable("item.durability", this.getMaxDamage() - this.getDamageValue(), this.getMaxDamage()));
            }

            builder.accept(Component.literal(BuiltInRegistries.ITEM.getKey(this.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            int i = this.components.size();

            if (i > 0) {
                builder.accept(Component.translatable("item.components", i).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (player != null && !this.getItem().isEnabled(player.level().enabledFeatures())) {
            builder.accept(ItemStack.DISABLED_ITEM_TOOLTIP);
        }

        boolean flag = this.getItem().shouldPrintOpWarning(this, player);

        if (flag) {
            ItemStack.OP_NBT_WARNING.forEach(builder);
        }

    }

    private void addUnitComponentToTooltip(DataComponentType<?> dataComponentType, Component component, TooltipDisplay display, Consumer<Component> builder) {
        if (this.has(dataComponentType) && display.shows(dataComponentType)) {
            builder.accept(component);
        }

    }

    private void addAttributeTooltips(Consumer<Component> consumer, TooltipDisplay display, @Nullable Player player) {
        if (display.shows(DataComponents.ATTRIBUTE_MODIFIERS)) {
            for (EquipmentSlotGroup equipmentslotgroup : EquipmentSlotGroup.values()) {
                MutableBoolean mutableboolean = new MutableBoolean(true);

                this.forEachModifier(equipmentslotgroup, (holder, attributemodifier, itemattributemodifiers_display) -> {
                    if (itemattributemodifiers_display != ItemAttributeModifiers.Display.hidden()) {
                        if (mutableboolean.isTrue()) {
                            consumer.accept(CommonComponents.EMPTY);
                            consumer.accept(Component.translatable("item.modifiers." + equipmentslotgroup.getSerializedName()).withStyle(ChatFormatting.GRAY));
                            mutableboolean.setFalse();
                        }

                        itemattributemodifiers_display.apply(consumer, player, holder, attributemodifier);
                    }
                });
            }

        }
    }

    public boolean hasFoil() {
        Boolean obool = (Boolean) this.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);

        return obool != null ? obool : this.getItem().isFoil(this);
    }

    public Rarity getRarity() {
        Rarity rarity = (Rarity) this.getOrDefault(DataComponents.RARITY, Rarity.COMMON);

        if (!this.isEnchanted()) {
            return rarity;
        } else {
            Rarity rarity1;

            switch (rarity) {
                case COMMON:
                case UNCOMMON:
                    rarity1 = Rarity.RARE;
                    break;
                case RARE:
                    rarity1 = Rarity.EPIC;
                    break;
                default:
                    rarity1 = rarity;
            }

            return rarity1;
        }
    }

    public boolean isEnchantable() {
        if (!this.has(DataComponents.ENCHANTABLE)) {
            return false;
        } else {
            ItemEnchantments itemenchantments = (ItemEnchantments) this.get(DataComponents.ENCHANTMENTS);

            return itemenchantments != null && itemenchantments.isEmpty();
        }
    }

    public void enchant(Holder<Enchantment> enchantment, int level) {
        EnchantmentHelper.updateEnchantments(this, (itemenchantments_mutable) -> {
            itemenchantments_mutable.upgrade(enchantment, level);
        });
    }

    public boolean isEnchanted() {
        return !((ItemEnchantments) this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)).isEmpty();
    }

    public ItemEnchantments getEnchantments() {
        return (ItemEnchantments) this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    public boolean isFramed() {
        return this.entityRepresentation instanceof ItemFrame;
    }

    public void setEntityRepresentation(@Nullable Entity entity) {
        if (!this.isEmpty()) {
            this.entityRepresentation = entity;
        }

    }

    public @Nullable ItemFrame getFrame() {
        return this.entityRepresentation instanceof ItemFrame ? (ItemFrame) this.getEntityRepresentation() : null;
    }

    public @Nullable Entity getEntityRepresentation() {
        return !this.isEmpty() ? this.entityRepresentation : null;
    }

    public void forEachModifier(EquipmentSlotGroup slot, TriConsumer<Holder<Attribute>, AttributeModifier, ItemAttributeModifiers.Display> consumer) {
        ItemAttributeModifiers itemattributemodifiers = (ItemAttributeModifiers) this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        itemattributemodifiers.forEach(slot, consumer);
        EnchantmentHelper.forEachModifier(this, slot, (holder, attributemodifier) -> {
            consumer.accept(holder, attributemodifier, ItemAttributeModifiers.Display.attributeModifiers());
        });
    }

    public void forEachModifier(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        ItemAttributeModifiers itemattributemodifiers = (ItemAttributeModifiers) this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        itemattributemodifiers.forEach(slot, consumer);
        EnchantmentHelper.forEachModifier(this, slot, consumer);
    }

    public Component getDisplayName() {
        MutableComponent mutablecomponent = Component.empty().append(this.getHoverName());

        if (this.has(DataComponents.CUSTOM_NAME)) {
            mutablecomponent.withStyle(ChatFormatting.ITALIC);
        }

        MutableComponent mutablecomponent1 = ComponentUtils.wrapInSquareBrackets(mutablecomponent);

        if (!this.isEmpty()) {
            mutablecomponent1.withStyle(this.getRarity().color()).withStyle((style) -> {
                return style.withHoverEvent(new HoverEvent.ShowItem(this));
            });
        }

        return mutablecomponent1;
    }

    public SwingAnimation getSwingAnimation() {
        return (SwingAnimation) this.getOrDefault(DataComponents.SWING_ANIMATION, SwingAnimation.DEFAULT);
    }

    public boolean canPlaceOnBlockInAdventureMode(BlockInWorld blockInWorld) {
        AdventureModePredicate adventuremodepredicate = (AdventureModePredicate) this.get(DataComponents.CAN_PLACE_ON);

        return adventuremodepredicate != null && adventuremodepredicate.test(blockInWorld);
    }

    public boolean canBreakBlockInAdventureMode(BlockInWorld blockInWorld) {
        AdventureModePredicate adventuremodepredicate = (AdventureModePredicate) this.get(DataComponents.CAN_BREAK);

        return adventuremodepredicate != null && adventuremodepredicate.test(blockInWorld);
    }

    public int getPopTime() {
        return this.popTime;
    }

    public void setPopTime(int popTime) {
        this.popTime = popTime;
    }

    public int getCount() {
        return this.isEmpty() ? 0 : this.count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void limitSize(int maxStackSize) {
        if (!this.isEmpty() && this.getCount() > maxStackSize) {
            this.setCount(maxStackSize);
        }

    }

    public void grow(int amount) {
        this.setCount(this.getCount() + amount);
    }

    public void shrink(int amount) {
        this.grow(-amount);
    }

    public void consume(int amount, @Nullable LivingEntity owner) {
        if (owner == null || !owner.hasInfiniteMaterials()) {
            this.shrink(amount);
        }

    }

    public ItemStack consumeAndReturn(int amount, @Nullable LivingEntity owner) {
        ItemStack itemstack = this.copyWithCount(amount);

        this.consume(amount, owner);
        return itemstack;
    }

    public void onUseTick(Level level, LivingEntity livingEntity, int ticksRemaining) {
        Consumable consumable = (Consumable) this.get(DataComponents.CONSUMABLE);

        if (consumable != null && consumable.shouldEmitParticlesAndSounds(ticksRemaining)) {
            consumable.emitParticlesAndSounds(livingEntity.getRandom(), livingEntity, this, 5);
        }

        KineticWeapon kineticweapon = (KineticWeapon) this.get(DataComponents.KINETIC_WEAPON);

        if (kineticweapon != null && !level.isClientSide()) {
            kineticweapon.damageEntities(this, ticksRemaining, livingEntity, livingEntity.getUsedItemHand().asEquipmentSlot());
        } else {
            this.getItem().onUseTick(level, livingEntity, this, ticksRemaining);
        }
    }

    public void onDestroyed(ItemEntity itemEntity) {
        this.getItem().onDestroyed(itemEntity);
    }

    public boolean canBeHurtBy(DamageSource source) {
        DamageResistant damageresistant = (DamageResistant) this.get(DataComponents.DAMAGE_RESISTANT);

        return damageresistant == null || !damageresistant.isResistantTo(source);
    }

    public boolean isValidRepairItem(ItemStack repairItem) {
        Repairable repairable = (Repairable) this.get(DataComponents.REPAIRABLE);

        return repairable != null && repairable.isValidRepairItem(repairItem);
    }

    public boolean canDestroyBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return this.getItem().canDestroyBlock(this, state, level, pos, player);
    }

    public DamageSource getDamageSource(LivingEntity attacker, Supplier<DamageSource> defaultSource) {
        return (DamageSource) Optional.ofNullable((EitherHolder) this.get(DataComponents.DAMAGE_TYPE)).flatMap((eitherholder) -> {
            return eitherholder.unwrap(attacker.registryAccess());
        }).map((holder) -> {
            return new DamageSource(holder, attacker);
        }).or(() -> {
            return Optional.ofNullable(this.getItem().getItemDamageSource(attacker));
        }).orElseGet(defaultSource);
    }

    static {
        MapCodec mapcodec = ItemStack.MAP_CODEC;

        Objects.requireNonNull(mapcodec);
        CODEC = Codec.lazyInitialized(mapcodec::codec);
        SINGLE_ITEM_CODEC = Codec.lazyInitialized(() -> {
            return RecordCodecBuilder.create((instance) -> {
                return instance.group(Item.CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder), DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter((itemstack) -> {
                    return itemstack.components.asPatch();
                })).apply(instance, (holder, datacomponentpatch) -> {
                    return new ItemStack(holder, 1, datacomponentpatch);
                });
            });
        });
        STRICT_CODEC = ItemStack.CODEC.validate(ItemStack::validateStrict);
        STRICT_SINGLE_ITEM_CODEC = ItemStack.SINGLE_ITEM_CODEC.validate(ItemStack::validateStrict);
        OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(ItemStack.CODEC).xmap((optional) -> {
            return (ItemStack) optional.orElse(ItemStack.EMPTY);
        }, (itemstack) -> {
            return itemstack.isEmpty() ? Optional.empty() : Optional.of(itemstack);
        });
        SIMPLE_ITEM_CODEC = Item.CODEC.xmap(ItemStack::new, ItemStack::getItemHolder);
        OPTIONAL_STREAM_CODEC = createOptionalStreamCodec(DataComponentPatch.STREAM_CODEC);
        OPTIONAL_UNTRUSTED_STREAM_CODEC = createOptionalStreamCodec(DataComponentPatch.DELIMITED_STREAM_CODEC);
        STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            public ItemStack decode(RegistryFriendlyByteBuf input) {
                ItemStack itemstack = (ItemStack) ItemStack.OPTIONAL_STREAM_CODEC.decode(input);

                if (itemstack.isEmpty()) {
                    throw new DecoderException("Empty ItemStack not allowed");
                } else {
                    return itemstack;
                }
            }

            public void encode(RegistryFriendlyByteBuf output, ItemStack itemStack) {
                if (itemStack.isEmpty()) {
                    throw new EncoderException("Empty ItemStack not allowed");
                } else {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(output, itemStack);
                }
            }
        };
        OPTIONAL_LIST_STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.collection(NonNullList::createWithCapacity));
        LOGGER = LogUtils.getLogger();
        EMPTY = new ItemStack((Void) null);
        DISABLED_ITEM_TOOLTIP = Component.translatable("item.disabled").withStyle(ChatFormatting.RED);
    }
}
