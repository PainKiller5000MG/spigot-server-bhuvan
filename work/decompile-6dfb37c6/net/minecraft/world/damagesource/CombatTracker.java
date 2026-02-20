package net.minecraft.world.damagesource;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.CommonLinks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class CombatTracker {

    public static final int RESET_DAMAGE_STATUS_TIME = 100;
    public static final int RESET_COMBAT_STATUS_TIME = 300;
    private static final Style INTENTIONAL_GAME_DESIGN_STYLE = Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(CommonLinks.INTENTIONAL_GAME_DESIGN_BUG)).withHoverEvent(new HoverEvent.ShowText(Component.literal("MCPE-28723")));
    private final List<CombatEntry> entries = Lists.newArrayList();
    private final LivingEntity mob;
    private int lastDamageTime;
    private int combatStartTime;
    private int combatEndTime;
    private boolean inCombat;
    private boolean takingDamage;

    public CombatTracker(LivingEntity mob) {
        this.mob = mob;
    }

    public void recordDamage(DamageSource source, float damage) {
        this.recheckStatus();
        FallLocation falllocation = FallLocation.getCurrentFallLocation(this.mob);
        CombatEntry combatentry = new CombatEntry(source, damage, falllocation, (float) this.mob.fallDistance);

        this.entries.add(combatentry);
        this.lastDamageTime = this.mob.tickCount;
        this.takingDamage = true;
        if (!this.inCombat && this.mob.isAlive() && shouldEnterCombat(source)) {
            this.inCombat = true;
            this.combatStartTime = this.mob.tickCount;
            this.combatEndTime = this.combatStartTime;
            this.mob.onEnterCombat();
        }

    }

    private static boolean shouldEnterCombat(DamageSource source) {
        return source.getEntity() instanceof LivingEntity;
    }

    private Component getMessageForAssistedFall(Entity attackerEntity, Component attackerName, String messageWithItem, String messageWithoutItem) {
        ItemStack itemstack;

        if (attackerEntity instanceof LivingEntity livingentity) {
            itemstack = livingentity.getMainHandItem();
        } else {
            itemstack = ItemStack.EMPTY;
        }

        ItemStack itemstack1 = itemstack;

        return !itemstack1.isEmpty() && itemstack1.has(DataComponents.CUSTOM_NAME) ? Component.translatable(messageWithItem, this.mob.getDisplayName(), attackerName, itemstack1.getDisplayName()) : Component.translatable(messageWithoutItem, this.mob.getDisplayName(), attackerName);
    }

    private Component getFallMessage(CombatEntry knockOffEntry, @Nullable Entity killingEntity) {
        DamageSource damagesource = knockOffEntry.source();

        if (!damagesource.is(DamageTypeTags.IS_FALL) && !damagesource.is(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL)) {
            Component component = getDisplayName(killingEntity);
            Entity entity1 = damagesource.getEntity();
            Component component1 = getDisplayName(entity1);

            return (Component) (component1 != null && !component1.equals(component) ? this.getMessageForAssistedFall(entity1, component1, "death.fell.assist.item", "death.fell.assist") : (component != null ? this.getMessageForAssistedFall(killingEntity, component, "death.fell.finish.item", "death.fell.finish") : Component.translatable("death.fell.killer", this.mob.getDisplayName())));
        } else {
            FallLocation falllocation = (FallLocation) Objects.requireNonNullElse(knockOffEntry.fallLocation(), FallLocation.GENERIC);

            return Component.translatable(falllocation.languageKey(), this.mob.getDisplayName());
        }
    }

    private static @Nullable Component getDisplayName(@Nullable Entity entity) {
        return entity == null ? null : entity.getDisplayName();
    }

    public Component getDeathMessage() {
        if (this.entries.isEmpty()) {
            return Component.translatable("death.attack.generic", this.mob.getDisplayName());
        } else {
            CombatEntry combatentry = (CombatEntry) this.entries.get(this.entries.size() - 1);
            DamageSource damagesource = combatentry.source();
            CombatEntry combatentry1 = this.getMostSignificantFall();
            DeathMessageType deathmessagetype = damagesource.type().deathMessageType();

            if (deathmessagetype == DeathMessageType.FALL_VARIANTS && combatentry1 != null) {
                return this.getFallMessage(combatentry1, damagesource.getEntity());
            } else if (deathmessagetype == DeathMessageType.INTENTIONAL_GAME_DESIGN) {
                String s = "death.attack." + damagesource.getMsgId();
                Component component = ComponentUtils.wrapInSquareBrackets(Component.translatable(s + ".link")).withStyle(CombatTracker.INTENTIONAL_GAME_DESIGN_STYLE);

                return Component.translatable(s + ".message", this.mob.getDisplayName(), component);
            } else {
                return damagesource.getLocalizedDeathMessage(this.mob);
            }
        }
    }

    private @Nullable CombatEntry getMostSignificantFall() {
        CombatEntry combatentry = null;
        CombatEntry combatentry1 = null;
        float f = 0.0F;
        float f1 = 0.0F;

        for (int i = 0; i < this.entries.size(); ++i) {
            CombatEntry combatentry2 = (CombatEntry) this.entries.get(i);
            CombatEntry combatentry3 = i > 0 ? (CombatEntry) this.entries.get(i - 1) : null;
            DamageSource damagesource = combatentry2.source();
            boolean flag = damagesource.is(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL);
            float f2 = flag ? Float.MAX_VALUE : combatentry2.fallDistance();

            if ((damagesource.is(DamageTypeTags.IS_FALL) || flag) && f2 > 0.0F && (combatentry == null || f2 > f1)) {
                if (i > 0) {
                    combatentry = combatentry3;
                } else {
                    combatentry = combatentry2;
                }

                f1 = f2;
            }

            if (combatentry2.fallLocation() != null && (combatentry1 == null || combatentry2.damage() > f)) {
                combatentry1 = combatentry2;
                f = combatentry2.damage();
            }
        }

        if (f1 > 5.0F && combatentry != null) {
            return combatentry;
        } else if (f > 5.0F && combatentry1 != null) {
            return combatentry1;
        } else {
            return null;
        }
    }

    public int getCombatDuration() {
        return this.inCombat ? this.mob.tickCount - this.combatStartTime : this.combatEndTime - this.combatStartTime;
    }

    public void recheckStatus() {
        int i = this.inCombat ? 300 : 100;

        if (this.takingDamage && (!this.mob.isAlive() || this.mob.tickCount - this.lastDamageTime > i)) {
            boolean flag = this.inCombat;

            this.takingDamage = false;
            this.inCombat = false;
            this.combatEndTime = this.mob.tickCount;
            if (flag) {
                this.mob.onLeaveCombat();
            }

            this.entries.clear();
        }

    }
}
