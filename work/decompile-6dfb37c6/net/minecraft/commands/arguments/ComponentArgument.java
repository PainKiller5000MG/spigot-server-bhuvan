package net.minecraft.commands.arguments;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.DynamicOps;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.SnbtGrammar;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.parsing.packrat.commands.CommandArgumentParser;
import net.minecraft.util.parsing.packrat.commands.ParserBasedArgument;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public class ComponentArgument extends ParserBasedArgument<Component> {

    private static final Collection<String> EXAMPLES = Arrays.asList("\"hello world\"", "'hello world'", "\"\"", "{text:\"hello world\"}", "[\"\"]");
    public static final DynamicCommandExceptionType ERROR_INVALID_COMPONENT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.component.invalid", object);
    });
    private static final DynamicOps<Tag> OPS = NbtOps.INSTANCE;
    private static final CommandArgumentParser<Tag> TAG_PARSER = SnbtGrammar.<Tag>createParser(ComponentArgument.OPS);

    private ComponentArgument(HolderLookup.Provider registries) {
        super(ComponentArgument.TAG_PARSER.withCodec(registries.createSerializationContext(ComponentArgument.OPS), ComponentArgument.TAG_PARSER, ComponentSerialization.CODEC, ComponentArgument.ERROR_INVALID_COMPONENT));
    }

    public static Component getRawComponent(CommandContext<CommandSourceStack> context, String name) {
        return (Component) context.getArgument(name, Component.class);
    }

    public static Component getResolvedComponent(CommandContext<CommandSourceStack> context, String name, @Nullable Entity contentEntity) throws CommandSyntaxException {
        return ComponentUtils.updateForEntity((CommandSourceStack) context.getSource(), getRawComponent(context, name), contentEntity, 0);
    }

    public static Component getResolvedComponent(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResolvedComponent(context, name, ((CommandSourceStack) context.getSource()).getEntity());
    }

    public static ComponentArgument textComponent(CommandBuildContext context) {
        return new ComponentArgument(context);
    }

    public Collection<String> getExamples() {
        return ComponentArgument.EXAMPLES;
    }
}
