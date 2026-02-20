package net.minecraft.world;

import javax.annotation.concurrent.Immutable;
import net.minecraft.util.Mth;

@Immutable
public class DifficultyInstance {

    private static final float DIFFICULTY_TIME_GLOBAL_OFFSET = -72000.0F;
    private static final float MAX_DIFFICULTY_TIME_GLOBAL = 1440000.0F;
    private static final float MAX_DIFFICULTY_TIME_LOCAL = 3600000.0F;
    private final Difficulty base;
    private final float effectiveDifficulty;

    public DifficultyInstance(Difficulty base, long totalGameTime, long localGameTime, float moonBrightness) {
        this.base = base;
        this.effectiveDifficulty = this.calculateDifficulty(base, totalGameTime, localGameTime, moonBrightness);
    }

    public Difficulty getDifficulty() {
        return this.base;
    }

    public float getEffectiveDifficulty() {
        return this.effectiveDifficulty;
    }

    public boolean isHard() {
        return this.effectiveDifficulty >= (float) Difficulty.HARD.ordinal();
    }

    public boolean isHarderThan(float requiredDifficulty) {
        return this.effectiveDifficulty > requiredDifficulty;
    }

    public float getSpecialMultiplier() {
        return this.effectiveDifficulty < 2.0F ? 0.0F : (this.effectiveDifficulty > 4.0F ? 1.0F : (this.effectiveDifficulty - 2.0F) / 2.0F);
    }

    private float calculateDifficulty(Difficulty base, long totalGameTime, long localGameTime, float moonBrightness) {
        if (base == Difficulty.PEACEFUL) {
            return 0.0F;
        } else {
            boolean flag = base == Difficulty.HARD;
            float f1 = 0.75F;
            float f2 = Mth.clamp(((float) totalGameTime + -72000.0F) / 1440000.0F, 0.0F, 1.0F) * 0.25F;

            f1 += f2;
            float f3 = 0.0F;

            f3 += Mth.clamp((float) localGameTime / 3600000.0F, 0.0F, 1.0F) * (flag ? 1.0F : 0.75F);
            f3 += Mth.clamp(moonBrightness * 0.25F, 0.0F, f2);
            if (base == Difficulty.EASY) {
                f3 *= 0.5F;
            }

            f1 += f3;
            return (float) base.getId() * f1;
        }
    }
}
