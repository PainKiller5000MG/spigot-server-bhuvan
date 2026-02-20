package net.minecraft.world.level.levelgen.structure.placement;

import com.mojang.serialization.Codec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

public enum RandomSpreadType implements StringRepresentable {

    LINEAR("linear"), TRIANGULAR("triangular");

    public static final Codec<RandomSpreadType> CODEC = StringRepresentable.<RandomSpreadType>fromEnum(RandomSpreadType::values);
    private final String id;

    private RandomSpreadType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    public int evaluate(RandomSource random, int limit) {
        int j;

        switch (this.ordinal()) {
            case 0:
                j = random.nextInt(limit);
                break;
            case 1:
                j = (random.nextInt(limit) + random.nextInt(limit)) / 2;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return j;
    }
}
