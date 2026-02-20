package net.minecraft.world.inventory;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class GrindstoneMenu extends AbstractContainerMenu {

    public static final int MAX_NAME_LENGTH = 35;
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private final Container resultSlots;
    private final Container repairSlots;
    private final ContainerLevelAccess access;

    public GrindstoneMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public GrindstoneMenu(int containerId, Inventory inventory, final ContainerLevelAccess access) {
        super(MenuType.GRINDSTONE, containerId);
        this.resultSlots = new ResultContainer();
        this.repairSlots = new SimpleContainer(2) {
            @Override
            public void setChanged() {
                super.setChanged();
                GrindstoneMenu.this.slotsChanged(this);
            }
        };
        this.access = access;
        this.addSlot(new Slot(this.repairSlots, 0, 49, 19) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(itemStack);
            }
        });
        this.addSlot(new Slot(this.repairSlots, 1, 49, 40) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(itemStack);
            }
        });
        this.addSlot(new Slot(this.resultSlots, 2, 129, 34) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack carried) {
                access.execute((level, blockpos) -> {
                    if (level instanceof ServerLevel) {
                        ExperienceOrb.award((ServerLevel) level, Vec3.atCenterOf(blockpos), this.getExperienceAmount(level));
                    }

                    level.levelEvent(1042, blockpos, 0);
                });
                GrindstoneMenu.this.repairSlots.setItem(0, ItemStack.EMPTY);
                GrindstoneMenu.this.repairSlots.setItem(1, ItemStack.EMPTY);
            }

            private int getExperienceAmount(Level level) {
                int j = 0;

                j += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(0));
                j += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(1));
                if (j > 0) {
                    int k = (int) Math.ceil((double) j / 2.0D);

                    return k + level.random.nextInt(k);
                } else {
                    return 0;
                }
            }

            private int getExperienceFromItem(ItemStack item) {
                int j = 0;
                ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(item);

                for (Object2IntMap.Entry<Holder<Enchantment>> object2intmap_entry : itemenchantments.entrySet()) {
                    Holder<Enchantment> holder = (Holder) object2intmap_entry.getKey();
                    int k = object2intmap_entry.getIntValue();

                    if (!holder.is(EnchantmentTags.CURSE)) {
                        j += ((Enchantment) holder.value()).getMinCost(k);
                    }
                }

                return j;
            }
        });
        this.addStandardInventorySlots(inventory, 8, 84);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == this.repairSlots) {
            this.createResult();
        }

    }

    private void createResult() {
        this.resultSlots.setItem(0, this.computeResult(this.repairSlots.getItem(0), this.repairSlots.getItem(1)));
        this.broadcastChanges();
    }

    private ItemStack computeResult(ItemStack input, ItemStack additional) {
        boolean flag = !input.isEmpty() || !additional.isEmpty();

        if (!flag) {
            return ItemStack.EMPTY;
        } else if (input.getCount() <= 1 && additional.getCount() <= 1) {
            boolean flag1 = !input.isEmpty() && !additional.isEmpty();

            if (!flag1) {
                ItemStack itemstack2 = !input.isEmpty() ? input : additional;

                return !EnchantmentHelper.hasAnyEnchantments(itemstack2) ? ItemStack.EMPTY : this.removeNonCursesFrom(itemstack2.copy());
            } else {
                return this.mergeItems(input, additional);
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    private ItemStack mergeItems(ItemStack input, ItemStack additional) {
        if (!input.is(additional.getItem())) {
            return ItemStack.EMPTY;
        } else {
            int i = Math.max(input.getMaxDamage(), additional.getMaxDamage());
            int j = input.getMaxDamage() - input.getDamageValue();
            int k = additional.getMaxDamage() - additional.getDamageValue();
            int l = j + k + i * 5 / 100;
            int i1 = 1;

            if (!input.isDamageableItem()) {
                if (input.getMaxStackSize() < 2 || !ItemStack.matches(input, additional)) {
                    return ItemStack.EMPTY;
                }

                i1 = 2;
            }

            ItemStack itemstack2 = input.copyWithCount(i1);

            if (itemstack2.isDamageableItem()) {
                itemstack2.set(DataComponents.MAX_DAMAGE, i);
                itemstack2.setDamageValue(Math.max(i - l, 0));
            }

            this.mergeEnchantsFrom(itemstack2, additional);
            return this.removeNonCursesFrom(itemstack2);
        }
    }

    private void mergeEnchantsFrom(ItemStack target, ItemStack source) {
        EnchantmentHelper.updateEnchantments(target, (itemenchantments_mutable) -> {
            ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(source);

            for (Object2IntMap.Entry<Holder<Enchantment>> object2intmap_entry : itemenchantments.entrySet()) {
                Holder<Enchantment> holder = (Holder) object2intmap_entry.getKey();

                if (!holder.is(EnchantmentTags.CURSE) || itemenchantments_mutable.getLevel(holder) == 0) {
                    itemenchantments_mutable.upgrade(holder, object2intmap_entry.getIntValue());
                }
            }

        });
    }

    private ItemStack removeNonCursesFrom(ItemStack item) {
        ItemEnchantments itemenchantments = EnchantmentHelper.updateEnchantments(item, (itemenchantments_mutable) -> {
            itemenchantments_mutable.removeIf((holder) -> {
                return !holder.is(EnchantmentTags.CURSE);
            });
        });

        if (item.is(Items.ENCHANTED_BOOK) && itemenchantments.isEmpty()) {
            item = item.transmuteCopy(Items.BOOK);
        }

        int i = 0;

        for (int j = 0; j < itemenchantments.size(); ++j) {
            i = AnvilMenu.calculateIncreasedRepairCost(i);
        }

        item.set(DataComponents.REPAIR_COST, i);
        return item;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockpos) -> {
            this.clearContainer(player, this.repairSlots);
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, Blocks.GRINDSTONE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            ItemStack itemstack2 = this.repairSlots.getItem(0);
            ItemStack itemstack3 = this.repairSlots.getItem(1);

            if (slotIndex == 2) {
                if (!this.moveItemStackTo(itemstack1, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (slotIndex != 0 && slotIndex != 1) {
                if (!itemstack2.isEmpty() && !itemstack3.isEmpty()) {
                    if (slotIndex >= 3 && slotIndex < 30) {
                        if (!this.moveItemStackTo(itemstack1, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (slotIndex >= 30 && slotIndex < 39 && !this.moveItemStackTo(itemstack1, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 0, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }

        return itemstack;
    }
}
