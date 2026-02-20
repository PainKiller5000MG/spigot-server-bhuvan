package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Sets;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;

public class EntityUUIDFix extends AbstractUUIDFix {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> ABSTRACT_HORSES = Sets.newHashSet();
    private static final Set<String> TAMEABLE_ANIMALS = Sets.newHashSet();
    private static final Set<String> ANIMALS = Sets.newHashSet();
    private static final Set<String> MOBS = Sets.newHashSet();
    private static final Set<String> LIVING_ENTITIES = Sets.newHashSet();
    private static final Set<String> PROJECTILES = Sets.newHashSet();

    public EntityUUIDFix(Schema outputSchema) {
        super(outputSchema, References.ENTITY);
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("EntityUUIDFixes", this.getInputSchema().getType(this.typeReference), (typed) -> {
            typed = typed.update(DSL.remainderFinder(), EntityUUIDFix::updateEntityUUID);

            for (String s : EntityUUIDFix.ABSTRACT_HORSES) {
                typed = this.updateNamedChoice(typed, s, EntityUUIDFix::updateAnimalOwner);
            }

            for (String s1 : EntityUUIDFix.TAMEABLE_ANIMALS) {
                typed = this.updateNamedChoice(typed, s1, EntityUUIDFix::updateAnimalOwner);
            }

            for (String s2 : EntityUUIDFix.ANIMALS) {
                typed = this.updateNamedChoice(typed, s2, EntityUUIDFix::updateAnimal);
            }

            for (String s3 : EntityUUIDFix.MOBS) {
                typed = this.updateNamedChoice(typed, s3, EntityUUIDFix::updateMob);
            }

            for (String s4 : EntityUUIDFix.LIVING_ENTITIES) {
                typed = this.updateNamedChoice(typed, s4, EntityUUIDFix::updateLivingEntity);
            }

            for (String s5 : EntityUUIDFix.PROJECTILES) {
                typed = this.updateNamedChoice(typed, s5, EntityUUIDFix::updateProjectile);
            }

            typed = this.updateNamedChoice(typed, "minecraft:bee", EntityUUIDFix::updateHurtBy);
            typed = this.updateNamedChoice(typed, "minecraft:zombified_piglin", EntityUUIDFix::updateHurtBy);
            typed = this.updateNamedChoice(typed, "minecraft:fox", EntityUUIDFix::updateFox);
            typed = this.updateNamedChoice(typed, "minecraft:item", EntityUUIDFix::updateItem);
            typed = this.updateNamedChoice(typed, "minecraft:shulker_bullet", EntityUUIDFix::updateShulkerBullet);
            typed = this.updateNamedChoice(typed, "minecraft:area_effect_cloud", EntityUUIDFix::updateAreaEffectCloud);
            typed = this.updateNamedChoice(typed, "minecraft:zombie_villager", EntityUUIDFix::updateZombieVillager);
            typed = this.updateNamedChoice(typed, "minecraft:evoker_fangs", EntityUUIDFix::updateEvokerFangs);
            typed = this.updateNamedChoice(typed, "minecraft:piglin", EntityUUIDFix::updatePiglin);
            return typed;
        });
    }

    private static Dynamic<?> updatePiglin(Dynamic<?> tag) {
        return tag.update("Brain", (dynamic1) -> {
            return dynamic1.update("memories", (dynamic2) -> {
                return dynamic2.update("minecraft:angry_at", (dynamic3) -> {
                    return (Dynamic) replaceUUIDString(dynamic3, "value", "value").orElseGet(() -> {
                        EntityUUIDFix.LOGGER.warn("angry_at has no value.");
                        return dynamic3;
                    });
                });
            });
        });
    }

    private static Dynamic<?> updateEvokerFangs(Dynamic<?> tag) {
        return (Dynamic) replaceUUIDLeastMost(tag, "OwnerUUID", "Owner").orElse(tag);
    }

    private static Dynamic<?> updateZombieVillager(Dynamic<?> tag) {
        return (Dynamic) replaceUUIDLeastMost(tag, "ConversionPlayer", "ConversionPlayer").orElse(tag);
    }

    private static Dynamic<?> updateAreaEffectCloud(Dynamic<?> tag) {
        return (Dynamic) replaceUUIDLeastMost(tag, "OwnerUUID", "Owner").orElse(tag);
    }

    private static Dynamic<?> updateShulkerBullet(Dynamic<?> tag) {
        tag = (Dynamic) replaceUUIDMLTag(tag, "Owner", "Owner").orElse(tag);
        return (Dynamic) replaceUUIDMLTag(tag, "Target", "Target").orElse(tag);
    }

    private static Dynamic<?> updateItem(Dynamic<?> tag) {
        tag = (Dynamic) replaceUUIDMLTag(tag, "Owner", "Owner").orElse(tag);
        return (Dynamic) replaceUUIDMLTag(tag, "Thrower", "Thrower").orElse(tag);
    }

    private static Dynamic<?> updateFox(Dynamic<?> tag) {
        Optional<Dynamic<?>> optional = tag.get("TrustedUUIDs").result().map((dynamic1) -> {
            return tag.createList(dynamic1.asStream().map((dynamic2) -> {
                return (Dynamic) createUUIDFromML(dynamic2).orElseGet(() -> {
                    EntityUUIDFix.LOGGER.warn("Trusted contained invalid data.");
                    return dynamic2;
                });
            }));
        });

        return (Dynamic) DataFixUtils.orElse(optional.map((dynamic1) -> {
            return tag.remove("TrustedUUIDs").set("Trusted", dynamic1);
        }), tag);
    }

    private static Dynamic<?> updateHurtBy(Dynamic<?> tag) {
        return (Dynamic) replaceUUIDString(tag, "HurtBy", "HurtBy").orElse(tag);
    }

    private static Dynamic<?> updateAnimalOwner(Dynamic<?> tag) {
        Dynamic<?> dynamic1 = updateAnimal(tag);

        return (Dynamic) replaceUUIDString(dynamic1, "OwnerUUID", "Owner").orElse(dynamic1);
    }

    private static Dynamic<?> updateAnimal(Dynamic<?> tag) {
        Dynamic<?> dynamic1 = updateMob(tag);

        return (Dynamic) replaceUUIDLeastMost(dynamic1, "LoveCause", "LoveCause").orElse(dynamic1);
    }

    private static Dynamic<?> updateMob(Dynamic<?> tag) {
        return updateLivingEntity(tag).update("Leash", (dynamic1) -> {
            return (Dynamic) replaceUUIDLeastMost(dynamic1, "UUID", "UUID").orElse(dynamic1);
        });
    }

    public static Dynamic<?> updateLivingEntity(Dynamic<?> tag) {
        return tag.update("Attributes", (dynamic1) -> {
            return tag.createList(dynamic1.asStream().map((dynamic2) -> {
                return dynamic2.update("Modifiers", (dynamic3) -> {
                    return dynamic2.createList(dynamic3.asStream().map((dynamic4) -> {
                        return (Dynamic) replaceUUIDLeastMost(dynamic4, "UUID", "UUID").orElse(dynamic4);
                    }));
                });
            }));
        });
    }

    private static Dynamic<?> updateProjectile(Dynamic<?> tag) {
        return (Dynamic) DataFixUtils.orElse(tag.get("OwnerUUID").result().map((dynamic1) -> {
            return tag.remove("OwnerUUID").set("Owner", dynamic1);
        }), tag);
    }

    public static Dynamic<?> updateEntityUUID(Dynamic<?> tag) {
        return (Dynamic) replaceUUIDLeastMost(tag, "UUID", "UUID").orElse(tag);
    }

    static {
        EntityUUIDFix.ABSTRACT_HORSES.add("minecraft:donkey");
        EntityUUIDFix.ABSTRACT_HORSES.add("minecraft:horse");
        EntityUUIDFix.ABSTRACT_HORSES.add("minecraft:llama");
        EntityUUIDFix.ABSTRACT_HORSES.add("minecraft:mule");
        EntityUUIDFix.ABSTRACT_HORSES.add("minecraft:skeleton_horse");
        EntityUUIDFix.ABSTRACT_HORSES.add("minecraft:trader_llama");
        EntityUUIDFix.ABSTRACT_HORSES.add("minecraft:zombie_horse");
        EntityUUIDFix.TAMEABLE_ANIMALS.add("minecraft:cat");
        EntityUUIDFix.TAMEABLE_ANIMALS.add("minecraft:parrot");
        EntityUUIDFix.TAMEABLE_ANIMALS.add("minecraft:wolf");
        EntityUUIDFix.ANIMALS.add("minecraft:bee");
        EntityUUIDFix.ANIMALS.add("minecraft:chicken");
        EntityUUIDFix.ANIMALS.add("minecraft:cow");
        EntityUUIDFix.ANIMALS.add("minecraft:fox");
        EntityUUIDFix.ANIMALS.add("minecraft:mooshroom");
        EntityUUIDFix.ANIMALS.add("minecraft:ocelot");
        EntityUUIDFix.ANIMALS.add("minecraft:panda");
        EntityUUIDFix.ANIMALS.add("minecraft:pig");
        EntityUUIDFix.ANIMALS.add("minecraft:polar_bear");
        EntityUUIDFix.ANIMALS.add("minecraft:rabbit");
        EntityUUIDFix.ANIMALS.add("minecraft:sheep");
        EntityUUIDFix.ANIMALS.add("minecraft:turtle");
        EntityUUIDFix.ANIMALS.add("minecraft:hoglin");
        EntityUUIDFix.MOBS.add("minecraft:bat");
        EntityUUIDFix.MOBS.add("minecraft:blaze");
        EntityUUIDFix.MOBS.add("minecraft:cave_spider");
        EntityUUIDFix.MOBS.add("minecraft:cod");
        EntityUUIDFix.MOBS.add("minecraft:creeper");
        EntityUUIDFix.MOBS.add("minecraft:dolphin");
        EntityUUIDFix.MOBS.add("minecraft:drowned");
        EntityUUIDFix.MOBS.add("minecraft:elder_guardian");
        EntityUUIDFix.MOBS.add("minecraft:ender_dragon");
        EntityUUIDFix.MOBS.add("minecraft:enderman");
        EntityUUIDFix.MOBS.add("minecraft:endermite");
        EntityUUIDFix.MOBS.add("minecraft:evoker");
        EntityUUIDFix.MOBS.add("minecraft:ghast");
        EntityUUIDFix.MOBS.add("minecraft:giant");
        EntityUUIDFix.MOBS.add("minecraft:guardian");
        EntityUUIDFix.MOBS.add("minecraft:husk");
        EntityUUIDFix.MOBS.add("minecraft:illusioner");
        EntityUUIDFix.MOBS.add("minecraft:magma_cube");
        EntityUUIDFix.MOBS.add("minecraft:pufferfish");
        EntityUUIDFix.MOBS.add("minecraft:zombified_piglin");
        EntityUUIDFix.MOBS.add("minecraft:salmon");
        EntityUUIDFix.MOBS.add("minecraft:shulker");
        EntityUUIDFix.MOBS.add("minecraft:silverfish");
        EntityUUIDFix.MOBS.add("minecraft:skeleton");
        EntityUUIDFix.MOBS.add("minecraft:slime");
        EntityUUIDFix.MOBS.add("minecraft:snow_golem");
        EntityUUIDFix.MOBS.add("minecraft:spider");
        EntityUUIDFix.MOBS.add("minecraft:squid");
        EntityUUIDFix.MOBS.add("minecraft:stray");
        EntityUUIDFix.MOBS.add("minecraft:tropical_fish");
        EntityUUIDFix.MOBS.add("minecraft:vex");
        EntityUUIDFix.MOBS.add("minecraft:villager");
        EntityUUIDFix.MOBS.add("minecraft:iron_golem");
        EntityUUIDFix.MOBS.add("minecraft:vindicator");
        EntityUUIDFix.MOBS.add("minecraft:pillager");
        EntityUUIDFix.MOBS.add("minecraft:wandering_trader");
        EntityUUIDFix.MOBS.add("minecraft:witch");
        EntityUUIDFix.MOBS.add("minecraft:wither");
        EntityUUIDFix.MOBS.add("minecraft:wither_skeleton");
        EntityUUIDFix.MOBS.add("minecraft:zombie");
        EntityUUIDFix.MOBS.add("minecraft:zombie_villager");
        EntityUUIDFix.MOBS.add("minecraft:phantom");
        EntityUUIDFix.MOBS.add("minecraft:ravager");
        EntityUUIDFix.MOBS.add("minecraft:piglin");
        EntityUUIDFix.LIVING_ENTITIES.add("minecraft:armor_stand");
        EntityUUIDFix.PROJECTILES.add("minecraft:arrow");
        EntityUUIDFix.PROJECTILES.add("minecraft:dragon_fireball");
        EntityUUIDFix.PROJECTILES.add("minecraft:firework_rocket");
        EntityUUIDFix.PROJECTILES.add("minecraft:fireball");
        EntityUUIDFix.PROJECTILES.add("minecraft:llama_spit");
        EntityUUIDFix.PROJECTILES.add("minecraft:small_fireball");
        EntityUUIDFix.PROJECTILES.add("minecraft:snowball");
        EntityUUIDFix.PROJECTILES.add("minecraft:spectral_arrow");
        EntityUUIDFix.PROJECTILES.add("minecraft:egg");
        EntityUUIDFix.PROJECTILES.add("minecraft:ender_pearl");
        EntityUUIDFix.PROJECTILES.add("minecraft:experience_bottle");
        EntityUUIDFix.PROJECTILES.add("minecraft:potion");
        EntityUUIDFix.PROJECTILES.add("minecraft:trident");
        EntityUUIDFix.PROJECTILES.add("minecraft:wither_skull");
    }
}
