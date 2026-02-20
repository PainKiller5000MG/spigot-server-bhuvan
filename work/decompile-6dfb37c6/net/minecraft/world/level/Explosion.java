package net.minecraft.world.level;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface Explosion {

    static DamageSource getDefaultDamageSource(Level level, @Nullable Entity source) {
        return level.damageSources().explosion(source, getIndirectSourceEntity(source));
    }

    static @Nullable LivingEntity getIndirectSourceEntity(@Nullable Entity source) {
        Entity entity1 = source;
        byte b0 = 0;

        while(true) {
            LivingEntity livingentity;

            //$FF: b0->value
            //0->net/minecraft/world/entity/item/PrimedTnt
            //1->net/minecraft/world/entity/LivingEntity
            //2->net/minecraft/world/entity/projectile/Projectile
            switch (entity1.typeSwitch<invokedynamic>(entity1, b0)) {
                case -1:
                default:
                    livingentity = null;
                    return livingentity;
                case 0:
                    PrimedTnt primedtnt = (PrimedTnt)entity1;

                    livingentity = primedtnt.getOwner();
                    return livingentity;
                case 1:
                    LivingEntity livingentity1 = (LivingEntity)entity1;

                    livingentity = livingentity1;
                    return livingentity;
                case 2:
                    Projectile projectile = (Projectile)entity1;
                    Entity entity2 = projectile.getOwner();

                    if (entity2 instanceof LivingEntity livingentity2) {
                        livingentity = livingentity2;
                        return livingentity;
                    }

                    b0 = 3;
            }
        }
    }

    ServerLevel level();

    Explosion.BlockInteraction getBlockInteraction();

    @Nullable
    LivingEntity getIndirectSourceEntity();

    @Nullable
    Entity getDirectSourceEntity();

    float radius();

    Vec3 center();

    boolean canTriggerBlocks();

    boolean shouldAffectBlocklikeEntities();

    public static enum BlockInteraction {

        KEEP(false), DESTROY(true), DESTROY_WITH_DECAY(true), TRIGGER_BLOCK(false);

        private final boolean shouldAffectBlocklikeEntities;

        private BlockInteraction(boolean shouldAffectBlocklikeEntities) {
            this.shouldAffectBlocklikeEntities = shouldAffectBlocklikeEntities;
        }

        public boolean shouldAffectBlocklikeEntities() {
            return this.shouldAffectBlocklikeEntities;
        }
    }
}
