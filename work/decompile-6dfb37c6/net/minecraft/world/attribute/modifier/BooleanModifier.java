package net.minecraft.world.attribute.modifier;

import com.mojang.serialization.Codec;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;

public enum BooleanModifier implements AttributeModifier<Boolean, Boolean> {

    AND, NAND, OR, NOR, XOR, XNOR;

    private BooleanModifier() {}

    public Boolean apply(Boolean subject, Boolean argument) {
        Boolean obool2;

        switch (this.ordinal()) {
            case 0:
                obool2 = argument && subject;
                break;
            case 1:
                obool2 = !argument || !subject;
                break;
            case 2:
                obool2 = argument || subject;
                break;
            case 3:
                obool2 = !argument && !subject;
                break;
            case 4:
                obool2 = argument ^ subject;
                break;
            case 5:
                obool2 = argument == subject;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return obool2;
    }

    @Override
    public Codec<Boolean> argumentCodec(EnvironmentAttribute<Boolean> type) {
        return Codec.BOOL;
    }

    @Override
    public LerpFunction<Boolean> argumentKeyframeLerp(EnvironmentAttribute<Boolean> type) {
        return LerpFunction.<Boolean>ofConstant();
    }
}
