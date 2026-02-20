package net.minecraft.world.item;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.TooltipDisplay;

public class SmithingTemplateItem extends Item {

    private static final ChatFormatting TITLE_FORMAT = ChatFormatting.GRAY;
    private static final ChatFormatting DESCRIPTION_FORMAT = ChatFormatting.BLUE;
    private static final Component INGREDIENTS_TITLE = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template.ingredients"))).withStyle(SmithingTemplateItem.TITLE_FORMAT);
    private static final Component APPLIES_TO_TITLE = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template.applies_to"))).withStyle(SmithingTemplateItem.TITLE_FORMAT);
    private static final Component SMITHING_TEMPLATE_SUFFIX = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template"))).withStyle(SmithingTemplateItem.TITLE_FORMAT);
    private static final Component ARMOR_TRIM_APPLIES_TO = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template.armor_trim.applies_to"))).withStyle(SmithingTemplateItem.DESCRIPTION_FORMAT);
    private static final Component ARMOR_TRIM_INGREDIENTS = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template.armor_trim.ingredients"))).withStyle(SmithingTemplateItem.DESCRIPTION_FORMAT);
    private static final Component ARMOR_TRIM_BASE_SLOT_DESCRIPTION = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template.armor_trim.base_slot_description")));
    private static final Component ARMOR_TRIM_ADDITIONS_SLOT_DESCRIPTION = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template.armor_trim.additions_slot_description")));
    private static final Component NETHERITE_UPGRADE_APPLIES_TO = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template.netherite_upgrade.applies_to"))).withStyle(SmithingTemplateItem.DESCRIPTION_FORMAT);
    private static final Component NETHERITE_UPGRADE_INGREDIENTS = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template.netherite_upgrade.ingredients"))).withStyle(SmithingTemplateItem.DESCRIPTION_FORMAT);
    private static final Component NETHERITE_UPGRADE_BASE_SLOT_DESCRIPTION = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template.netherite_upgrade.base_slot_description")));
    private static final Component NETHERITE_UPGRADE_ADDITIONS_SLOT_DESCRIPTION = Component.translatable(Util.makeDescriptionId("item", Identifier.withDefaultNamespace("smithing_template.netherite_upgrade.additions_slot_description")));
    private static final Identifier EMPTY_SLOT_HELMET = Identifier.withDefaultNamespace("container/slot/helmet");
    private static final Identifier EMPTY_SLOT_CHESTPLATE = Identifier.withDefaultNamespace("container/slot/chestplate");
    private static final Identifier EMPTY_SLOT_LEGGINGS = Identifier.withDefaultNamespace("container/slot/leggings");
    private static final Identifier EMPTY_SLOT_BOOTS = Identifier.withDefaultNamespace("container/slot/boots");
    private static final Identifier EMPTY_SLOT_HOE = Identifier.withDefaultNamespace("container/slot/hoe");
    private static final Identifier EMPTY_SLOT_AXE = Identifier.withDefaultNamespace("container/slot/axe");
    private static final Identifier EMPTY_SLOT_SWORD = Identifier.withDefaultNamespace("container/slot/sword");
    private static final Identifier EMPTY_SLOT_SHOVEL = Identifier.withDefaultNamespace("container/slot/shovel");
    private static final Identifier EMPTY_SLOT_SPEAR = Identifier.withDefaultNamespace("container/slot/spear");
    private static final Identifier EMPTY_SLOT_PICKAXE = Identifier.withDefaultNamespace("container/slot/pickaxe");
    private static final Identifier EMPTY_SLOT_INGOT = Identifier.withDefaultNamespace("container/slot/ingot");
    private static final Identifier EMPTY_SLOT_REDSTONE_DUST = Identifier.withDefaultNamespace("container/slot/redstone_dust");
    private static final Identifier EMPTY_SLOT_QUARTZ = Identifier.withDefaultNamespace("container/slot/quartz");
    private static final Identifier EMPTY_SLOT_EMERALD = Identifier.withDefaultNamespace("container/slot/emerald");
    private static final Identifier EMPTY_SLOT_DIAMOND = Identifier.withDefaultNamespace("container/slot/diamond");
    private static final Identifier EMPTY_SLOT_LAPIS_LAZULI = Identifier.withDefaultNamespace("container/slot/lapis_lazuli");
    private static final Identifier EMPTY_SLOT_AMETHYST_SHARD = Identifier.withDefaultNamespace("container/slot/amethyst_shard");
    private static final Identifier EMPTY_SLOT_NAUTILUS_ARMOR = Identifier.withDefaultNamespace("container/slot/nautilus_armor");
    private final Component appliesTo;
    private final Component ingredients;
    private final Component baseSlotDescription;
    private final Component additionsSlotDescription;
    private final List<Identifier> baseSlotEmptyIcons;
    private final List<Identifier> additionalSlotEmptyIcons;

    public SmithingTemplateItem(Component appliesTo, Component ingredients, Component baseSlotDescription, Component additionsSlotDescription, List<Identifier> baseSlotEmptyIcons, List<Identifier> additionalSlotEmptyIcons, Item.Properties properties) {
        super(properties);
        this.appliesTo = appliesTo;
        this.ingredients = ingredients;
        this.baseSlotDescription = baseSlotDescription;
        this.additionsSlotDescription = additionsSlotDescription;
        this.baseSlotEmptyIcons = baseSlotEmptyIcons;
        this.additionalSlotEmptyIcons = additionalSlotEmptyIcons;
    }

    public static SmithingTemplateItem createArmorTrimTemplate(Item.Properties properties) {
        return new SmithingTemplateItem(SmithingTemplateItem.ARMOR_TRIM_APPLIES_TO, SmithingTemplateItem.ARMOR_TRIM_INGREDIENTS, SmithingTemplateItem.ARMOR_TRIM_BASE_SLOT_DESCRIPTION, SmithingTemplateItem.ARMOR_TRIM_ADDITIONS_SLOT_DESCRIPTION, createTrimmableArmorIconList(), createTrimmableMaterialIconList(), properties);
    }

    public static SmithingTemplateItem createNetheriteUpgradeTemplate(Item.Properties properties) {
        return new SmithingTemplateItem(SmithingTemplateItem.NETHERITE_UPGRADE_APPLIES_TO, SmithingTemplateItem.NETHERITE_UPGRADE_INGREDIENTS, SmithingTemplateItem.NETHERITE_UPGRADE_BASE_SLOT_DESCRIPTION, SmithingTemplateItem.NETHERITE_UPGRADE_ADDITIONS_SLOT_DESCRIPTION, createNetheriteUpgradeIconList(), createNetheriteUpgradeMaterialList(), properties);
    }

    private static List<Identifier> createTrimmableArmorIconList() {
        return List.of(SmithingTemplateItem.EMPTY_SLOT_HELMET, SmithingTemplateItem.EMPTY_SLOT_CHESTPLATE, SmithingTemplateItem.EMPTY_SLOT_LEGGINGS, SmithingTemplateItem.EMPTY_SLOT_BOOTS);
    }

    private static List<Identifier> createTrimmableMaterialIconList() {
        return List.of(SmithingTemplateItem.EMPTY_SLOT_INGOT, SmithingTemplateItem.EMPTY_SLOT_REDSTONE_DUST, SmithingTemplateItem.EMPTY_SLOT_LAPIS_LAZULI, SmithingTemplateItem.EMPTY_SLOT_QUARTZ, SmithingTemplateItem.EMPTY_SLOT_DIAMOND, SmithingTemplateItem.EMPTY_SLOT_EMERALD, SmithingTemplateItem.EMPTY_SLOT_AMETHYST_SHARD);
    }

    private static List<Identifier> createNetheriteUpgradeIconList() {
        return List.of(SmithingTemplateItem.EMPTY_SLOT_HELMET, SmithingTemplateItem.EMPTY_SLOT_SWORD, SmithingTemplateItem.EMPTY_SLOT_CHESTPLATE, SmithingTemplateItem.EMPTY_SLOT_PICKAXE, SmithingTemplateItem.EMPTY_SLOT_LEGGINGS, SmithingTemplateItem.EMPTY_SLOT_AXE, SmithingTemplateItem.EMPTY_SLOT_BOOTS, SmithingTemplateItem.EMPTY_SLOT_HOE, SmithingTemplateItem.EMPTY_SLOT_SHOVEL, SmithingTemplateItem.EMPTY_SLOT_NAUTILUS_ARMOR, SmithingTemplateItem.EMPTY_SLOT_SPEAR);
    }

    private static List<Identifier> createNetheriteUpgradeMaterialList() {
        return List.of(SmithingTemplateItem.EMPTY_SLOT_INGOT);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        builder.accept(SmithingTemplateItem.SMITHING_TEMPLATE_SUFFIX);
        builder.accept(CommonComponents.EMPTY);
        builder.accept(SmithingTemplateItem.APPLIES_TO_TITLE);
        builder.accept(CommonComponents.space().append(this.appliesTo));
        builder.accept(SmithingTemplateItem.INGREDIENTS_TITLE);
        builder.accept(CommonComponents.space().append(this.ingredients));
    }

    public Component getBaseSlotDescription() {
        return this.baseSlotDescription;
    }

    public Component getAdditionSlotDescription() {
        return this.additionsSlotDescription;
    }

    public List<Identifier> getBaseSlotEmptyIcons() {
        return this.baseSlotEmptyIcons;
    }

    public List<Identifier> getAdditionalSlotEmptyIcons() {
        return this.additionalSlotEmptyIcons;
    }
}
