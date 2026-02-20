package net.minecraft.world.item;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public class CreativeModeTab {

    private static final Identifier DEFAULT_BACKGROUND = createTextureLocation("items");
    private final Component displayName;
    private Identifier backgroundTexture;
    private boolean canScroll;
    private boolean showTitle;
    private boolean alignedRight;
    private final CreativeModeTab.Row row;
    private final int column;
    private final CreativeModeTab.Type type;
    private @Nullable ItemStack iconItemStack;
    private Collection<ItemStack> displayItems;
    private Set<ItemStack> displayItemsSearchTab;
    private final Supplier<ItemStack> iconGenerator;
    private final CreativeModeTab.DisplayItemsGenerator displayItemsGenerator;

    private CreativeModeTab(CreativeModeTab.Row row, int column, CreativeModeTab.Type type, Component displayName, Supplier<ItemStack> iconGenerator, CreativeModeTab.DisplayItemsGenerator displayItemsGenerator) {
        this.backgroundTexture = CreativeModeTab.DEFAULT_BACKGROUND;
        this.canScroll = true;
        this.showTitle = true;
        this.alignedRight = false;
        this.displayItems = ItemStackLinkedSet.createTypeAndComponentsSet();
        this.displayItemsSearchTab = ItemStackLinkedSet.createTypeAndComponentsSet();
        this.row = row;
        this.column = column;
        this.displayName = displayName;
        this.iconGenerator = iconGenerator;
        this.displayItemsGenerator = displayItemsGenerator;
        this.type = type;
    }

    public static Identifier createTextureLocation(String name) {
        return Identifier.withDefaultNamespace("textures/gui/container/creative_inventory/tab_" + name + ".png");
    }

    public static CreativeModeTab.Builder builder(CreativeModeTab.Row row, int column) {
        return new CreativeModeTab.Builder(row, column);
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public ItemStack getIconItem() {
        if (this.iconItemStack == null) {
            this.iconItemStack = (ItemStack) this.iconGenerator.get();
        }

        return this.iconItemStack;
    }

    public Identifier getBackgroundTexture() {
        return this.backgroundTexture;
    }

    public boolean showTitle() {
        return this.showTitle;
    }

    public boolean canScroll() {
        return this.canScroll;
    }

    public int column() {
        return this.column;
    }

    public CreativeModeTab.Row row() {
        return this.row;
    }

    public boolean hasAnyItems() {
        return !this.displayItems.isEmpty();
    }

    public boolean shouldDisplay() {
        return this.type != CreativeModeTab.Type.CATEGORY || this.hasAnyItems();
    }

    public boolean isAlignedRight() {
        return this.alignedRight;
    }

    public CreativeModeTab.Type getType() {
        return this.type;
    }

    public void buildContents(CreativeModeTab.ItemDisplayParameters parameters) {
        CreativeModeTab.ItemDisplayBuilder creativemodetab_itemdisplaybuilder = new CreativeModeTab.ItemDisplayBuilder(this, parameters.enabledFeatures);
        ResourceKey resourcekey = (ResourceKey) BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(this).orElseThrow(() -> {
            return new IllegalStateException("Unregistered creative tab: " + String.valueOf(this));
        });

        this.displayItemsGenerator.accept(parameters, creativemodetab_itemdisplaybuilder);
        this.displayItems = creativemodetab_itemdisplaybuilder.tabContents;
        this.displayItemsSearchTab = creativemodetab_itemdisplaybuilder.searchTabContents;
    }

    public Collection<ItemStack> getDisplayItems() {
        return this.displayItems;
    }

    public Collection<ItemStack> getSearchTabDisplayItems() {
        return this.displayItemsSearchTab;
    }

    public boolean contains(ItemStack stack) {
        return this.displayItemsSearchTab.contains(stack);
    }

    public static record ItemDisplayParameters(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider holders) {

        public boolean needsUpdate(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider holders) {
            return !this.enabledFeatures.equals(enabledFeatures) || this.hasPermissions != hasPermissions || this.holders != holders;
        }
    }

    public static enum Type {

        CATEGORY, INVENTORY, HOTBAR, SEARCH;

        private Type() {}
    }

    public static enum Row {

        TOP, BOTTOM;

        private Row() {}
    }

    public static class Builder {

        private static final CreativeModeTab.DisplayItemsGenerator EMPTY_GENERATOR = (creativemodetab_itemdisplayparameters, creativemodetab_output) -> {
        };
        private final CreativeModeTab.Row row;
        private final int column;
        private Component displayName = Component.empty();
        private Supplier<ItemStack> iconGenerator = () -> {
            return ItemStack.EMPTY;
        };
        private CreativeModeTab.DisplayItemsGenerator displayItemsGenerator;
        private boolean canScroll;
        private boolean showTitle;
        private boolean alignedRight;
        private CreativeModeTab.Type type;
        private Identifier backgroundTexture;

        public Builder(CreativeModeTab.Row row, int column) {
            this.displayItemsGenerator = CreativeModeTab.Builder.EMPTY_GENERATOR;
            this.canScroll = true;
            this.showTitle = true;
            this.alignedRight = false;
            this.type = CreativeModeTab.Type.CATEGORY;
            this.backgroundTexture = CreativeModeTab.DEFAULT_BACKGROUND;
            this.row = row;
            this.column = column;
        }

        public CreativeModeTab.Builder title(Component displayName) {
            this.displayName = displayName;
            return this;
        }

        public CreativeModeTab.Builder icon(Supplier<ItemStack> iconGenerator) {
            this.iconGenerator = iconGenerator;
            return this;
        }

        public CreativeModeTab.Builder displayItems(CreativeModeTab.DisplayItemsGenerator displayItemsGenerator) {
            this.displayItemsGenerator = displayItemsGenerator;
            return this;
        }

        public CreativeModeTab.Builder alignedRight() {
            this.alignedRight = true;
            return this;
        }

        public CreativeModeTab.Builder hideTitle() {
            this.showTitle = false;
            return this;
        }

        public CreativeModeTab.Builder noScrollBar() {
            this.canScroll = false;
            return this;
        }

        protected CreativeModeTab.Builder type(CreativeModeTab.Type type) {
            this.type = type;
            return this;
        }

        public CreativeModeTab.Builder backgroundTexture(Identifier backgroundTexture) {
            this.backgroundTexture = backgroundTexture;
            return this;
        }

        public CreativeModeTab build() {
            if ((this.type == CreativeModeTab.Type.HOTBAR || this.type == CreativeModeTab.Type.INVENTORY) && this.displayItemsGenerator != CreativeModeTab.Builder.EMPTY_GENERATOR) {
                throw new IllegalStateException("Special tabs can't have display items");
            } else {
                CreativeModeTab creativemodetab = new CreativeModeTab(this.row, this.column, this.type, this.displayName, this.iconGenerator, this.displayItemsGenerator);

                creativemodetab.alignedRight = this.alignedRight;
                creativemodetab.showTitle = this.showTitle;
                creativemodetab.canScroll = this.canScroll;
                creativemodetab.backgroundTexture = this.backgroundTexture;
                return creativemodetab;
            }
        }
    }

    private static class ItemDisplayBuilder implements CreativeModeTab.Output {

        public final Collection<ItemStack> tabContents = ItemStackLinkedSet.createTypeAndComponentsSet();
        public final Set<ItemStack> searchTabContents = ItemStackLinkedSet.createTypeAndComponentsSet();
        private final CreativeModeTab tab;
        private final FeatureFlagSet featureFlagSet;

        public ItemDisplayBuilder(CreativeModeTab tab, FeatureFlagSet featureFlagSet) {
            this.tab = tab;
            this.featureFlagSet = featureFlagSet;
        }

        @Override
        public void accept(ItemStack stack, CreativeModeTab.TabVisibility tabVisibility) {
            if (stack.getCount() != 1) {
                throw new IllegalArgumentException("Stack size must be exactly 1");
            } else {
                boolean flag = this.tabContents.contains(stack) && tabVisibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY;

                if (flag) {
                    String s = stack.getDisplayName().getString();

                    throw new IllegalStateException("Accidentally adding the same item stack twice " + s + " to a Creative Mode Tab: " + this.tab.getDisplayName().getString());
                } else {
                    if (stack.getItem().isEnabled(this.featureFlagSet)) {
                        switch (tabVisibility.ordinal()) {
                            case 0:
                                this.tabContents.add(stack);
                                this.searchTabContents.add(stack);
                                break;
                            case 1:
                                this.tabContents.add(stack);
                                break;
                            case 2:
                                this.searchTabContents.add(stack);
                        }
                    }

                }
            }
        }
    }

    protected static enum TabVisibility {

        PARENT_AND_SEARCH_TABS, PARENT_TAB_ONLY, SEARCH_TAB_ONLY;

        private TabVisibility() {}
    }

    public interface Output {

        void accept(ItemStack stack, CreativeModeTab.TabVisibility tabVisibility);

        default void accept(ItemStack stack) {
            this.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }

        default void accept(ItemLike item, CreativeModeTab.TabVisibility tabVisibility) {
            this.accept(new ItemStack(item), tabVisibility);
        }

        default void accept(ItemLike item) {
            this.accept(new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }

        default void acceptAll(Collection<ItemStack> stacks, CreativeModeTab.TabVisibility tabVisibility) {
            stacks.forEach((itemstack) -> {
                this.accept(itemstack, tabVisibility);
            });
        }

        default void acceptAll(Collection<ItemStack> stacks) {
            this.acceptAll(stacks, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    @FunctionalInterface
    public interface DisplayItemsGenerator {

        void accept(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output);
    }
}
