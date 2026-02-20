package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;

public class CanyonCarverConfiguration extends CarverConfiguration {

    public static final Codec<CanyonCarverConfiguration> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(CarverConfiguration.CODEC.forGetter((canyoncarverconfiguration) -> {
            return canyoncarverconfiguration;
        }), FloatProvider.CODEC.fieldOf("vertical_rotation").forGetter((canyoncarverconfiguration) -> {
            return canyoncarverconfiguration.verticalRotation;
        }), CanyonCarverConfiguration.CanyonShapeConfiguration.CODEC.fieldOf("shape").forGetter((canyoncarverconfiguration) -> {
            return canyoncarverconfiguration.shape;
        })).apply(instance, CanyonCarverConfiguration::new);
    });
    public final FloatProvider verticalRotation;
    public final CanyonCarverConfiguration.CanyonShapeConfiguration shape;

    public CanyonCarverConfiguration(float probability, HeightProvider y, FloatProvider yScale, VerticalAnchor lavaLevel, CarverDebugSettings debugSettings, HolderSet<Block> replaceable, FloatProvider verticalRotation, CanyonCarverConfiguration.CanyonShapeConfiguration shape) {
        super(probability, y, yScale, lavaLevel, debugSettings, replaceable);
        this.verticalRotation = verticalRotation;
        this.shape = shape;
    }

    public CanyonCarverConfiguration(CarverConfiguration carver, FloatProvider distanceFactor, CanyonCarverConfiguration.CanyonShapeConfiguration shape) {
        this(carver.probability, carver.y, carver.yScale, carver.lavaLevel, carver.debugSettings, carver.replaceable, distanceFactor, shape);
    }

    public static class CanyonShapeConfiguration {

        public static final Codec<CanyonCarverConfiguration.CanyonShapeConfiguration> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(FloatProvider.CODEC.fieldOf("distance_factor").forGetter((canyoncarverconfiguration_canyonshapeconfiguration) -> {
                return canyoncarverconfiguration_canyonshapeconfiguration.distanceFactor;
            }), FloatProvider.CODEC.fieldOf("thickness").forGetter((canyoncarverconfiguration_canyonshapeconfiguration) -> {
                return canyoncarverconfiguration_canyonshapeconfiguration.thickness;
            }), ExtraCodecs.POSITIVE_INT.fieldOf("width_smoothness").forGetter((canyoncarverconfiguration_canyonshapeconfiguration) -> {
                return canyoncarverconfiguration_canyonshapeconfiguration.widthSmoothness;
            }), FloatProvider.CODEC.fieldOf("horizontal_radius_factor").forGetter((canyoncarverconfiguration_canyonshapeconfiguration) -> {
                return canyoncarverconfiguration_canyonshapeconfiguration.horizontalRadiusFactor;
            }), Codec.FLOAT.fieldOf("vertical_radius_default_factor").forGetter((canyoncarverconfiguration_canyonshapeconfiguration) -> {
                return canyoncarverconfiguration_canyonshapeconfiguration.verticalRadiusDefaultFactor;
            }), Codec.FLOAT.fieldOf("vertical_radius_center_factor").forGetter((canyoncarverconfiguration_canyonshapeconfiguration) -> {
                return canyoncarverconfiguration_canyonshapeconfiguration.verticalRadiusCenterFactor;
            })).apply(instance, CanyonCarverConfiguration.CanyonShapeConfiguration::new);
        });
        public final FloatProvider distanceFactor;
        public final FloatProvider thickness;
        public final int widthSmoothness;
        public final FloatProvider horizontalRadiusFactor;
        public final float verticalRadiusDefaultFactor;
        public final float verticalRadiusCenterFactor;

        public CanyonShapeConfiguration(FloatProvider distanceFactor, FloatProvider thickness, int widthSmoothness, FloatProvider horizontalRadiusFactor, float verticalRadiusDefaultFactor, float verticalRadiusCenterFactor) {
            this.widthSmoothness = widthSmoothness;
            this.horizontalRadiusFactor = horizontalRadiusFactor;
            this.verticalRadiusDefaultFactor = verticalRadiusDefaultFactor;
            this.verticalRadiusCenterFactor = verticalRadiusCenterFactor;
            this.distanceFactor = distanceFactor;
            this.thickness = thickness;
        }
    }
}
