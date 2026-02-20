package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

public enum HumanoidArm implements StringRepresentable {

    LEFT(0, "left", "options.mainHand.left"), RIGHT(1, "right", "options.mainHand.right");

    public static final Codec<HumanoidArm> CODEC = StringRepresentable.<HumanoidArm>fromEnum(HumanoidArm::values);
    private static final IntFunction<HumanoidArm> BY_ID = ByIdMap.<HumanoidArm>continuous((humanoidarm) -> {
        return humanoidarm.id;
    }, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, HumanoidArm> STREAM_CODEC = ByteBufCodecs.idMapper(HumanoidArm.BY_ID, (humanoidarm) -> {
        return humanoidarm.id;
    });
    private final int id;
    private final String name;
    private final Component caption;

    private HumanoidArm(int id, String name, String translationKey) {
        this.id = id;
        this.name = name;
        this.caption = Component.translatable(translationKey);
    }

    public HumanoidArm getOpposite() {
        HumanoidArm humanoidarm;

        switch (this.ordinal()) {
            case 0:
                humanoidarm = HumanoidArm.RIGHT;
                break;
            case 1:
                humanoidarm = HumanoidArm.LEFT;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return humanoidarm;
    }

    public Component caption() {
        return this.caption;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
