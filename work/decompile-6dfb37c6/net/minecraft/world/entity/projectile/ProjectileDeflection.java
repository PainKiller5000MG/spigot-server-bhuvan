package net.minecraft.world.entity.projectile;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ProjectileDeflection {

    ProjectileDeflection NONE = (projectile, entity, randomsource) -> {
    };
    ProjectileDeflection REVERSE = (projectile, entity, randomsource) -> {
        float f = 170.0F + randomsource.nextFloat() * 20.0F;

        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-0.5D));
        projectile.setYRot(projectile.getYRot() + f);
        projectile.yRotO += f;
        projectile.needsSync = true;
    };
    ProjectileDeflection AIM_DEFLECT = (projectile, entity, randomsource) -> {
        if (entity != null) {
            Vec3 vec3 = entity.getLookAngle();

            projectile.setDeltaMovement(vec3);
            projectile.needsSync = true;
        }

    };
    ProjectileDeflection MOMENTUM_DEFLECT = (projectile, entity, randomsource) -> {
        if (entity != null) {
            Vec3 vec3 = entity.getDeltaMovement().normalize();

            projectile.setDeltaMovement(vec3);
            projectile.needsSync = true;
        }

    };

    void deflect(Projectile projectile, @Nullable Entity entity, RandomSource random);
}
