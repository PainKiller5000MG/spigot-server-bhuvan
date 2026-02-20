package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

public record WorldCoordinate(boolean relative, double value) {

    private static final char PREFIX_RELATIVE = '~';
    public static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.double"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_INT = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.int"));

    public double get(double original) {
        return this.relative ? this.value + original : this.value;
    }

    public static WorldCoordinate parseDouble(StringReader reader, boolean center) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else if (!reader.canRead()) {
            throw WorldCoordinate.ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        } else {
            boolean flag1 = isRelative(reader);
            int i = reader.getCursor();
            double d0 = reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0D;
            String s = reader.getString().substring(i, reader.getCursor());

            if (flag1 && s.isEmpty()) {
                return new WorldCoordinate(true, 0.0D);
            } else {
                if (!s.contains(".") && !flag1 && center) {
                    d0 += 0.5D;
                }

                return new WorldCoordinate(flag1, d0);
            }
        }
    }

    public static WorldCoordinate parseInt(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else if (!reader.canRead()) {
            throw WorldCoordinate.ERROR_EXPECTED_INT.createWithContext(reader);
        } else {
            boolean flag = isRelative(reader);
            double d0;

            if (reader.canRead() && reader.peek() != ' ') {
                d0 = flag ? reader.readDouble() : (double) reader.readInt();
            } else {
                d0 = 0.0D;
            }

            return new WorldCoordinate(flag, d0);
        }
    }

    public static boolean isRelative(StringReader reader) {
        boolean flag;

        if (reader.peek() == '~') {
            flag = true;
            reader.skip();
        } else {
            flag = false;
        }

        return flag;
    }

    public boolean isRelative() {
        return this.relative;
    }
}
