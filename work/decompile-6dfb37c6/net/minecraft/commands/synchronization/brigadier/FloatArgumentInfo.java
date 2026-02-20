package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class FloatArgumentInfo implements ArgumentTypeInfo<FloatArgumentType, FloatArgumentInfo.Template> {

    public FloatArgumentInfo() {}

    public void serializeToNetwork(FloatArgumentInfo.Template template, FriendlyByteBuf out) {
        boolean flag = template.min != -Float.MAX_VALUE;
        boolean flag1 = template.max != Float.MAX_VALUE;

        out.writeByte(ArgumentUtils.createNumberFlags(flag, flag1));
        if (flag) {
            out.writeFloat(template.min);
        }

        if (flag1) {
            out.writeFloat(template.max);
        }

    }

    @Override
    public FloatArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf in) {
        byte b0 = in.readByte();
        float f = ArgumentUtils.numberHasMin(b0) ? in.readFloat() : -Float.MAX_VALUE;
        float f1 = ArgumentUtils.numberHasMax(b0) ? in.readFloat() : Float.MAX_VALUE;

        return new FloatArgumentInfo.Template(f, f1);
    }

    public void serializeToJson(FloatArgumentInfo.Template template, JsonObject out) {
        if (template.min != -Float.MAX_VALUE) {
            out.addProperty("min", template.min);
        }

        if (template.max != Float.MAX_VALUE) {
            out.addProperty("max", template.max);
        }

    }

    public FloatArgumentInfo.Template unpack(FloatArgumentType argument) {
        return new FloatArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<FloatArgumentType> {

        private final float min;
        private final float max;

        private Template(float min, float max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public FloatArgumentType instantiate(CommandBuildContext context) {
            return FloatArgumentType.floatArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<FloatArgumentType, ?> type() {
            return FloatArgumentInfo.this;
        }
    }
}
