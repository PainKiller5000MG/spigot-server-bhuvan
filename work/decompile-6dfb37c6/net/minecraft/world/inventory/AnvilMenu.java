package net.minecraft.world.inventory;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class AnvilMenu extends ItemCombinerMenu {

    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_COST = false;
    public static final int MAX_NAME_LENGTH = 50;
    public int repairItemCountCost;
    public @Nullable String itemName;
    public final DataSlot cost;
    private boolean onlyRenaming;
    private static final int COST_FAIL = 0;
    private static final int COST_BASE = 1;
    private static final int COST_ADDED_BASE = 1;
    private static final int COST_REPAIR_MATERIAL = 1;
    private static final int COST_REPAIR_SACRIFICE = 2;
    private static final int COST_INCOMPATIBLE_PENALTY = 1;
    private static final int COST_RENAME = 1;
    private static final int INPUT_SLOT_X_PLACEMENT = 27;
    private static final int ADDITIONAL_SLOT_X_PLACEMENT = 76;
    private static final int RESULT_SLOT_X_PLACEMENT = 134;
    private static final int SLOT_Y_PLACEMENT = 47;

    public AnvilMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public AnvilMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(MenuType.ANVIL, containerId, inventory, access, createInputSlotDefinitions());
        this.cost = DataSlot.standalone();
        this.onlyRenaming = false;
        this.addDataSlot(this.cost);
    }

    private static ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create().withSlot(0, 27, 47, (itemstack) -> {
            return true;
        }).withSlot(1, 76, 47, (itemstack) -> {
            return true;
        }).withResultSlot(2, 134, 47).build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(BlockTags.ANVIL);
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasItem) {
        return (player.hasInfiniteMaterials() || player.experienceLevel >= this.cost.get()) && this.cost.get() > 0;
    }

    @Override
    protected void onTake(Player player, ItemStack carried) {
        if (!player.hasInfiniteMaterials()) {
            player.giveExperienceLevels(-this.cost.get());
        }

        if (this.repairItemCountCost > 0) {
            ItemStack itemstack1 = this.inputSlots.getItem(1);

            if (!itemstack1.isEmpty() && itemstack1.getCount() > this.repairItemCountCost) {
                itemstack1.shrink(this.repairItemCountCost);
                this.inputSlots.setItem(1, itemstack1);
            } else {
                this.inputSlots.setItem(1, ItemStack.EMPTY);
            }
        } else if (!this.onlyRenaming) {
            this.inputSlots.setItem(1, ItemStack.EMPTY);
        }

        this.cost.set(0);
        if (player instanceof ServerPlayer serverplayer) {
            if (!StringUtil.isBlank(this.itemName) && !this.inputSlots.getItem(0).getHoverName().getString().equals(this.itemName)) {
                serverplayer.getTextFilter().processStreamMessage(this.itemName);
            }
        }

        this.inputSlots.setItem(0, ItemStack.EMPTY);
        this.access.execute((level, blockpos) -> {
            BlockState blockstate = level.getBlockState(blockpos);

            if (!player.hasInfiniteMaterials() && blockstate.is(BlockTags.ANVIL) && player.getRandom().nextFloat() < 0.12F) {
                BlockState blockstate1 = AnvilBlock.damage(blockstate);

                if (blockstate1 == null) {
                    level.removeBlock(blockpos, false);
                    level.levelEvent(1029, blockpos, 0);
                } else {
                    level.setBlock(blockpos, blockstate1, 2);
                    level.levelEvent(1030, blockpos, 0);
                }
            } else {
                level.levelEvent(1030, blockpos, 0);
            }

        });
    }

    @Override
    public void createResult() {
        ItemStack itemstack = this.inputSlots.getItem(0);

        this.onlyRenaming = false;
        this.cost.set(1);
        int i = 0;
        long j = 0L;
        int k = 0;

        if (!itemstack.isEmpty() && EnchantmentHelper.canStoreEnchantments(itemstack)) {
            ItemStack itemstack1 = itemstack.copy();
            ItemStack itemstack2 = this.inputSlots.getItem(1);
            ItemEnchantments.Mutable itemenchantments_mutable = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(itemstack1));

            j += (long) (Integer) itemstack.getOrDefault(DataComponents.REPAIR_COST, 0) + (long) (Integer) itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0);
            this.repairItemCountCost = 0;
            if (!itemstack2.isEmpty()) {
                boolean flag = itemstack2.has(DataComponents.STORED_ENCHANTMENTS);

                if (itemstack1.isDamageableItem() && itemstack.isValidRepairItem(itemstack2)) {
                    int l = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);

                    if (l <= 0) {
                        this.resultSlots.setItem(0, ItemStack.EMPTY);
                        this.cost.set(0);
                        return;
                    }

                    int i1;

                    for (i1 = 0; l > 0 && i1 < itemstack2.getCount(); ++i1) {
                        int j1 = itemstack1.getDamageValue() - l;

                        itemstack1.setDamageValue(j1);
                        ++i;
                        l = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    }

                    this.repairItemCountCost = i1;
                } else {
                    if (!flag && (!itemstack1.is(itemstack2.getItem()) || !itemstack1.isDamageableItem())) {
                        this.resultSlots.setItem(0, ItemStack.EMPTY);
                        this.cost.set(0);
                        return;
                    }

                    if (itemstack1.isDamageableItem() && !flag) {
                        int k1 = itemstack.getMaxDamage() - itemstack.getDamageValue();
                        int l1 = itemstack2.getMaxDamage() - itemstack2.getDamageValue();
                        int i2 = l1 + itemstack1.getMaxDamage() * 12 / 100;
                        int j2 = k1 + i2;
                        int k2 = itemstack1.getMaxDamage() - j2;

                        if (k2 < 0) {
                            k2 = 0;
                        }

                        if (k2 < itemstack1.getDamageValue()) {
                            itemstack1.setDamageValue(k2);
                            i += 2;
                        }
                    }

                    ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(itemstack2);
                    boolean flag1 = false;
                    boolean flag2 = false;

                    for (Object2IntMap.Entry<Holder<Enchantment>> object2intmap_entry : itemenchantments.entrySet()) {
                        Holder<Enchantment> holder = (Holder) object2intmap_entry.getKey();
                        int l2 = itemenchantments_mutable.getLevel(holder);
                        int i3 = object2intmap_entry.getIntValue();

                        i3 = l2 == i3 ? i3 + 1 : Math.max(i3, l2);
                        Enchantment enchantment = holder.value();
                        boolean flag3 = enchantment.canEnchant(itemstack);

                        if (this.player.hasInfiniteMaterials() || itemstack.is(Items.ENCHANTED_BOOK)) {
                            flag3 = true;
                        }

                        for (Holder<Enchantment> holder1 : itemenchantments_mutable.keySet()) {
                            if (!holder1.equals(holder) && !Enchantment.areCompatible(holder, holder1)) {
                                flag3 = false;
                                ++i;
                            }
                        }

                        if (!flag3) {
                            flag2 = true;
                        } else {
                            flag1 = true;
                            if (i3 > enchantment.getMaxLevel()) {
                                i3 = enchantment.getMaxLevel();
                            }

                            itemenchantments_mutable.set(holder, i3);
                            int j3 = enchantment.getAnvilCost();

                            if (flag) {
                                j3 = Math.max(1, j3 / 2);
                            }

                            i += j3 * i3;
                            if (itemstack.getCount() > 1) {
                                i = 40;
                            }
                        }
                    }

                    if (flag2 && !flag1) {
                        this.resultSlots.setItem(0, ItemStack.EMPTY);
                        this.cost.set(0);
                        return;
                    }
                }
            }

            if (this.itemName != null && !StringUtil.isBlank(this.itemName)) {
                if (!this.itemName.equals(itemstack.getHoverName().getString())) {
                    k = 1;
                    i += k;
                    itemstack1.set(DataComponents.CUSTOM_NAME, Component.literal(this.itemName));
                }
            } else if (itemstack.has(DataComponents.CUSTOM_NAME)) {
                k = 1;
                i += k;
                itemstack1.remove(DataComponents.CUSTOM_NAME);
            }

            int k3 = i <= 0 ? 0 : (int) Mth.clamp(j + (long) i, 0L, 2147483647L);

            this.cost.set(k3);
            if (i <= 0) {
                itemstack1 = ItemStack.EMPTY;
            }

            if (k == i && k > 0) {
                if (this.cost.get() >= 40) {
                    this.cost.set(39);
                }

                this.onlyRenaming = true;
            }

            if (this.cost.get() >= 40 && !this.player.hasInfiniteMaterials()) {
                itemstack1 = ItemStack.EMPTY;
            }

            if (!itemstack1.isEmpty()) {
                int l3 = (Integer) itemstack1.getOrDefault(DataComponents.REPAIR_COST, 0);

                if (l3 < (Integer) itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0)) {
                    l3 = (Integer) itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0);
                }

                if (k != i || k == 0) {
                    l3 = calculateIncreasedRepairCost(l3);
                }

                itemstack1.set(DataComponents.REPAIR_COST, l3);
                EnchantmentHelper.setEnchantments(itemstack1, itemenchantments_mutable.toImmutable());
            }

            this.resultSlots.setItem(0, itemstack1);
            this.broadcastChanges();
        } else {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.cost.set(0);
        }
    }

    public static int calculateIncreasedRepairCost(int baseCost) {
        return (int) Math.min((long) baseCost * 2L + 1L, 2147483647L);
    }

    public boolean setItemName(String name) {
        String s1 = validateName(name);

        if (s1 != null && !s1.equals(this.itemName)) {
            this.itemName = s1;
            if (this.getSlot(2).hasItem()) {
                ItemStack itemstack = this.getSlot(2).getItem();

                if (StringUtil.isBlank(s1)) {
                    itemstack.remove(DataComponents.CUSTOM_NAME);
                } else {
                    itemstack.set(DataComponents.CUSTOM_NAME, Component.literal(s1));
                }
            }

            this.createResult();
            return true;
        } else {
            return false;
        }
    }

    private static @Nullable String validateName(String name) {
        String s1 = StringUtil.filterText(name);

        return s1.length() <= 50 ? s1 : null;
    }

    public int getCost() {
        return this.cost.get();
    }
}
