package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

public class WindChargeItem extends Item implements ProjectileItem {

    public static float PROJECTILE_SHOOT_POWER = 1.5F;

    public WindChargeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverlevel) {
            Projectile.spawnProjectileFromRotation((serverlevel1, livingentity, itemstack1) -> {
                return new WindCharge(player, level, player.position().x(), player.getEyePosition().y(), player.position().z());
            }, serverlevel, itemstack, player, 0.0F, WindChargeItem.PROJECTILE_SHOOT_POWER, 1.0F);
        }

        level.playSound((Entity) null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        player.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
        RandomSource randomsource = level.getRandom();
        double d0 = randomsource.triangle((double) direction.getStepX(), 0.11485000000000001D);
        double d1 = randomsource.triangle((double) direction.getStepY(), 0.11485000000000001D);
        double d2 = randomsource.triangle((double) direction.getStepZ(), 0.11485000000000001D);
        Vec3 vec3 = new Vec3(d0, d1, d2);
        WindCharge windcharge = new WindCharge(level, position.x(), position.y(), position.z(), vec3);

        windcharge.setDeltaMovement(vec3);
        return windcharge;
    }

    @Override
    public void shoot(Projectile projectile, double xd, double yd, double zd, float pow, float uncertainty) {}

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder().positionFunction((blocksource, direction) -> {
            return DispenserBlock.getDispensePosition(blocksource, 1.0D, Vec3.ZERO);
        }).uncertainty(6.6666665F).power(1.0F).overrideDispenseEvent(1051).build();
    }
}
