package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public class SwizzleArgument implements ArgumentType<EnumSet<Direction.Axis>> {

    private static final Collection<String> EXAMPLES = Arrays.asList("xyz", "x");
    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("arguments.swizzle.invalid"));

    public SwizzleArgument() {}

    public static SwizzleArgument swizzle() {
        return new SwizzleArgument();
    }

    public static EnumSet<Direction.Axis> getSwizzle(CommandContext<CommandSourceStack> context, String name) {
        return (EnumSet) context.getArgument(name, EnumSet.class);
    }

    public EnumSet<Direction.Axis> parse(StringReader reader) throws CommandSyntaxException {
        EnumSet<Direction.Axis> enumset = EnumSet.noneOf(Direction.Axis.class);

        while (reader.canRead() && reader.peek() != ' ') {
            char c0 = reader.read();
            Direction.Axis direction_axis;

            switch (c0) {
                case 'x':
                    direction_axis = Direction.Axis.X;
                    break;
                case 'y':
                    direction_axis = Direction.Axis.Y;
                    break;
                case 'z':
                    direction_axis = Direction.Axis.Z;
                    break;
                default:
                    throw SwizzleArgument.ERROR_INVALID.createWithContext(reader);
            }

            if (enumset.contains(direction_axis)) {
                throw SwizzleArgument.ERROR_INVALID.createWithContext(reader);
            }

            enumset.add(direction_axis);
        }

        return enumset;
    }

    public Collection<String> getExamples() {
        return SwizzleArgument.EXAMPLES;
    }
}
