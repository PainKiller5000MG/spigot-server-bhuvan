package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class IntegerArgumentInfo implements ArgumentTypeInfo<IntegerArgumentType, IntegerArgumentInfo.Template> {

    public IntegerArgumentInfo() {}

    public void serializeToNetwork(IntegerArgumentInfo.Template template, FriendlyByteBuf out) {
        boolean flag = template.min != Integer.MIN_VALUE;
        boolean flag1 = template.max != Integer.MAX_VALUE;

        out.writeByte(ArgumentUtils.createNumberFlags(flag, flag1));
        if (flag) {
            out.writeInt(template.min);
        }

        if (flag1) {
            out.writeInt(template.max);
        }

    }

    @Override
    public IntegerArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf in) {
        byte b0 = in.readByte();
        int i = ArgumentUtils.numberHasMin(b0) ? in.readInt() : Integer.MIN_VALUE;
        int j = ArgumentUtils.numberHasMax(b0) ? in.readInt() : Integer.MAX_VALUE;

        return new IntegerArgumentInfo.Template(i, j);
    }

    public void serializeToJson(IntegerArgumentInfo.Template template, JsonObject out) {
        if (template.min != Integer.MIN_VALUE) {
            out.addProperty("min", template.min);
        }

        if (template.max != Integer.MAX_VALUE) {
            out.addProperty("max", template.max);
        }

    }

    public IntegerArgumentInfo.Template unpack(IntegerArgumentType argument) {
        return new IntegerArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<IntegerArgumentType> {

        private final int min;
        private final int max;

        private Template(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public IntegerArgumentType instantiate(CommandBuildContext context) {
            return IntegerArgumentType.integer(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<IntegerArgumentType, ?> type() {
            return IntegerArgumentInfo.this;
        }
    }
}
