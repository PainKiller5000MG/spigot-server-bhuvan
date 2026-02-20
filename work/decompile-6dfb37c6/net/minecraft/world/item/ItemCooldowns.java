package net.minecraft.world.item;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.UseCooldown;

public class ItemCooldowns {

    public final Map<Identifier, ItemCooldowns.CooldownInstance> cooldowns = Maps.newHashMap();
    public int tickCount;

    public ItemCooldowns() {}

    public boolean isOnCooldown(ItemStack item) {
        return this.getCooldownPercent(item, 0.0F) > 0.0F;
    }

    public float getCooldownPercent(ItemStack item, float a) {
        Identifier identifier = this.getCooldownGroup(item);
        ItemCooldowns.CooldownInstance itemcooldowns_cooldowninstance = (ItemCooldowns.CooldownInstance) this.cooldowns.get(identifier);

        if (itemcooldowns_cooldowninstance != null) {
            float f1 = (float) (itemcooldowns_cooldowninstance.endTime - itemcooldowns_cooldowninstance.startTime);
            float f2 = (float) itemcooldowns_cooldowninstance.endTime - ((float) this.tickCount + a);

            return Mth.clamp(f2 / f1, 0.0F, 1.0F);
        } else {
            return 0.0F;
        }
    }

    public void tick() {
        ++this.tickCount;
        if (!this.cooldowns.isEmpty()) {
            Iterator<Map.Entry<Identifier, ItemCooldowns.CooldownInstance>> iterator = this.cooldowns.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Identifier, ItemCooldowns.CooldownInstance> map_entry = (Entry) iterator.next();

                if (((ItemCooldowns.CooldownInstance) map_entry.getValue()).endTime <= this.tickCount) {
                    iterator.remove();
                    this.onCooldownEnded((Identifier) map_entry.getKey());
                }
            }
        }

    }

    public Identifier getCooldownGroup(ItemStack item) {
        UseCooldown usecooldown = (UseCooldown) item.get(DataComponents.USE_COOLDOWN);
        Identifier identifier = BuiltInRegistries.ITEM.getKey(item.getItem());

        return usecooldown == null ? identifier : (Identifier) usecooldown.cooldownGroup().orElse(identifier);
    }

    public void addCooldown(ItemStack item, int time) {
        this.addCooldown(this.getCooldownGroup(item), time);
    }

    public void addCooldown(Identifier cooldownGroup, int time) {
        this.cooldowns.put(cooldownGroup, new ItemCooldowns.CooldownInstance(this.tickCount, this.tickCount + time));
        this.onCooldownStarted(cooldownGroup, time);
    }

    public void removeCooldown(Identifier cooldownGroup) {
        this.cooldowns.remove(cooldownGroup);
        this.onCooldownEnded(cooldownGroup);
    }

    protected void onCooldownStarted(Identifier cooldownGroup, int duration) {}

    protected void onCooldownEnded(Identifier cooldownGroup) {}

    public static record CooldownInstance(int startTime, int endTime) {

    }
}
