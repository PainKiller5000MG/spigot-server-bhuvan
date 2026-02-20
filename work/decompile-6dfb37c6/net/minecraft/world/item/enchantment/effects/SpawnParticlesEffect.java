package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public record SpawnParticlesEffect(ParticleOptions particle, SpawnParticlesEffect.PositionSource horizontalPosition, SpawnParticlesEffect.PositionSource verticalPosition, SpawnParticlesEffect.VelocitySource horizontalVelocity, SpawnParticlesEffect.VelocitySource verticalVelocity, FloatProvider speed) implements EnchantmentEntityEffect {

    public static final MapCodec<SpawnParticlesEffect> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(ParticleTypes.CODEC.fieldOf("particle").forGetter(SpawnParticlesEffect::particle), SpawnParticlesEffect.PositionSource.CODEC.fieldOf("horizontal_position").forGetter(SpawnParticlesEffect::horizontalPosition), SpawnParticlesEffect.PositionSource.CODEC.fieldOf("vertical_position").forGetter(SpawnParticlesEffect::verticalPosition), SpawnParticlesEffect.VelocitySource.CODEC.fieldOf("horizontal_velocity").forGetter(SpawnParticlesEffect::horizontalVelocity), SpawnParticlesEffect.VelocitySource.CODEC.fieldOf("vertical_velocity").forGetter(SpawnParticlesEffect::verticalVelocity), FloatProvider.CODEC.optionalFieldOf("speed", ConstantFloat.ZERO).forGetter(SpawnParticlesEffect::speed)).apply(instance, SpawnParticlesEffect::new);
    });

    public static SpawnParticlesEffect.PositionSource offsetFromEntityPosition(float offset) {
        return new SpawnParticlesEffect.PositionSource(SpawnParticlesEffect.PositionSourceType.ENTITY_POSITION, offset, 1.0F);
    }

    public static SpawnParticlesEffect.PositionSource inBoundingBox() {
        return new SpawnParticlesEffect.PositionSource(SpawnParticlesEffect.PositionSourceType.BOUNDING_BOX, 0.0F, 1.0F);
    }

    public static SpawnParticlesEffect.VelocitySource movementScaled(float scale) {
        return new SpawnParticlesEffect.VelocitySource(scale, ConstantFloat.ZERO);
    }

    public static SpawnParticlesEffect.VelocitySource fixedVelocity(FloatProvider provider) {
        return new SpawnParticlesEffect.VelocitySource(0.0F, provider);
    }

    @Override
    public void apply(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
        RandomSource randomsource = entity.getRandom();
        Vec3 vec31 = entity.getKnownMovement();
        float f = entity.getBbWidth();
        float f1 = entity.getBbHeight();

        serverLevel.sendParticles(this.particle, this.horizontalPosition.getCoordinate(position.x(), position.x(), f, randomsource), this.verticalPosition.getCoordinate(position.y(), position.y() + (double) (f1 / 2.0F), f1, randomsource), this.horizontalPosition.getCoordinate(position.z(), position.z(), f, randomsource), 0, this.horizontalVelocity.getVelocity(vec31.x(), randomsource), this.verticalVelocity.getVelocity(vec31.y(), randomsource), this.horizontalVelocity.getVelocity(vec31.z(), randomsource), (double) this.speed.sample(randomsource));
    }

    @Override
    public MapCodec<SpawnParticlesEffect> codec() {
        return SpawnParticlesEffect.CODEC;
    }

    public static enum PositionSourceType implements StringRepresentable {

        ENTITY_POSITION("entity_position", (d0, d1, f, randomsource) -> {
            return d0;
        }), BOUNDING_BOX("in_bounding_box", (d0, d1, f, randomsource) -> {
            return d1 + (randomsource.nextDouble() - 0.5D) * (double) f;
        });

        public static final Codec<SpawnParticlesEffect.PositionSourceType> CODEC = StringRepresentable.<SpawnParticlesEffect.PositionSourceType>fromEnum(SpawnParticlesEffect.PositionSourceType::values);
        private final String id;
        private final SpawnParticlesEffect.PositionSourceType.CoordinateSource source;

        private PositionSourceType(String id, SpawnParticlesEffect.PositionSourceType.CoordinateSource source) {
            this.id = id;
            this.source = source;
        }

        public double getCoordinate(double position, double center, float boundingBoxSpan, RandomSource random) {
            return this.source.getCoordinate(position, center, boundingBoxSpan, random);
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }

        @FunctionalInterface
        private interface CoordinateSource {

            double getCoordinate(double pos, double center, float boundingBoxSpan, RandomSource random);
        }
    }

    public static record PositionSource(SpawnParticlesEffect.PositionSourceType type, float offset, float scale) {

        public static final MapCodec<SpawnParticlesEffect.PositionSource> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(SpawnParticlesEffect.PositionSourceType.CODEC.fieldOf("type").forGetter(SpawnParticlesEffect.PositionSource::type), Codec.FLOAT.optionalFieldOf("offset", 0.0F).forGetter(SpawnParticlesEffect.PositionSource::offset), ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("scale", 1.0F).forGetter(SpawnParticlesEffect.PositionSource::scale)).apply(instance, SpawnParticlesEffect.PositionSource::new);
        }).validate((spawnparticleseffect_positionsource) -> {
            return spawnparticleseffect_positionsource.type() == SpawnParticlesEffect.PositionSourceType.ENTITY_POSITION && spawnparticleseffect_positionsource.scale() != 1.0F ? DataResult.error(() -> {
                return "Cannot scale an entity position coordinate source";
            }) : DataResult.success(spawnparticleseffect_positionsource);
        });

        public double getCoordinate(double position, double center, float boundingBoxSpan, RandomSource random) {
            return this.type.getCoordinate(position, center, boundingBoxSpan * this.scale, random) + (double) this.offset;
        }
    }

    public static record VelocitySource(float movementScale, FloatProvider base) {

        public static final MapCodec<SpawnParticlesEffect.VelocitySource> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.FLOAT.optionalFieldOf("movement_scale", 0.0F).forGetter(SpawnParticlesEffect.VelocitySource::movementScale), FloatProvider.CODEC.optionalFieldOf("base", ConstantFloat.ZERO).forGetter(SpawnParticlesEffect.VelocitySource::base)).apply(instance, SpawnParticlesEffect.VelocitySource::new);
        });

        public double getVelocity(double movement, RandomSource random) {
            return movement * (double) this.movementScale + (double) this.base.sample(random);
        }
    }
}
