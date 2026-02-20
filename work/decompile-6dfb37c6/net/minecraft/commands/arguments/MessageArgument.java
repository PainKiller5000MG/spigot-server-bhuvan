package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

public class MessageArgument implements SignedArgument<MessageArgument.Message> {

    private static final Collection<String> EXAMPLES = Arrays.asList("Hello world!", "foo", "@e", "Hello @p :)");
    private static final Dynamic2CommandExceptionType TOO_LONG = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.message.too_long", object, object1);
    });

    public MessageArgument() {}

    public static MessageArgument message() {
        return new MessageArgument();
    }

    public static Component getMessage(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        MessageArgument.Message messageargument_message = (MessageArgument.Message) context.getArgument(name, MessageArgument.Message.class);

        return messageargument_message.resolveComponent((CommandSourceStack) context.getSource());
    }

    public static void resolveChatMessage(CommandContext<CommandSourceStack> context, String name, Consumer<PlayerChatMessage> task) throws CommandSyntaxException {
        MessageArgument.Message messageargument_message = (MessageArgument.Message) context.getArgument(name, MessageArgument.Message.class);
        CommandSourceStack commandsourcestack = (CommandSourceStack) context.getSource();
        Component component = messageargument_message.resolveComponent(commandsourcestack);
        CommandSigningContext commandsigningcontext = commandsourcestack.getSigningContext();
        PlayerChatMessage playerchatmessage = commandsigningcontext.getArgument(name);

        if (playerchatmessage != null) {
            resolveSignedMessage(task, commandsourcestack, playerchatmessage.withUnsignedContent(component));
        } else {
            resolveDisguisedMessage(task, commandsourcestack, PlayerChatMessage.system(messageargument_message.text).withUnsignedContent(component));
        }

    }

    private static void resolveSignedMessage(Consumer<PlayerChatMessage> task, CommandSourceStack sender, PlayerChatMessage signedArgument) {
        MinecraftServer minecraftserver = sender.getServer();
        CompletableFuture<FilteredText> completablefuture = filterPlainText(sender, signedArgument);
        Component component = minecraftserver.getChatDecorator().decorate(sender.getPlayer(), signedArgument.decoratedContent());

        sender.getChatMessageChainer().append(completablefuture, (filteredtext) -> {
            PlayerChatMessage playerchatmessage1 = signedArgument.withUnsignedContent(component).filter(filteredtext.mask());

            task.accept(playerchatmessage1);
        });
    }

    private static void resolveDisguisedMessage(Consumer<PlayerChatMessage> task, CommandSourceStack sender, PlayerChatMessage argument) {
        ChatDecorator chatdecorator = sender.getServer().getChatDecorator();
        Component component = chatdecorator.decorate(sender.getPlayer(), argument.decoratedContent());

        task.accept(argument.withUnsignedContent(component));
    }

    private static CompletableFuture<FilteredText> filterPlainText(CommandSourceStack sender, PlayerChatMessage message) {
        ServerPlayer serverplayer = sender.getPlayer();

        return serverplayer != null && message.hasSignatureFrom(serverplayer.getUUID()) ? serverplayer.getTextFilter().processStreamMessage(message.signedContent()) : CompletableFuture.completedFuture(FilteredText.passThrough(message.signedContent()));
    }

    public MessageArgument.Message parse(StringReader reader) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(reader, true);
    }

    public <S> MessageArgument.Message parse(StringReader reader, @Nullable S source) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(reader, EntitySelectorParser.allowSelectors(source));
    }

    public Collection<String> getExamples() {
        return MessageArgument.EXAMPLES;
    }

    public static record Message(String text, MessageArgument.Part[] parts) {

        private Component resolveComponent(CommandSourceStack sender) throws CommandSyntaxException {
            return this.toComponent(sender, sender.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS));
        }

        public Component toComponent(CommandSourceStack sender, boolean allowSelectors) throws CommandSyntaxException {
            if (this.parts.length != 0 && allowSelectors) {
                MutableComponent mutablecomponent = Component.literal(this.text.substring(0, this.parts[0].start()));
                int i = this.parts[0].start();

                for (MessageArgument.Part messageargument_part : this.parts) {
                    Component component = messageargument_part.toComponent(sender);

                    if (i < messageargument_part.start()) {
                        mutablecomponent.append(this.text.substring(i, messageargument_part.start()));
                    }

                    mutablecomponent.append(component);
                    i = messageargument_part.end();
                }

                if (i < this.text.length()) {
                    mutablecomponent.append(this.text.substring(i));
                }

                return mutablecomponent;
            } else {
                return Component.literal(this.text);
            }
        }

        public static MessageArgument.Message parseText(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
            if (reader.getRemainingLength() > 256) {
                throw MessageArgument.TOO_LONG.create(reader.getRemainingLength(), 256);
            } else {
                String s = reader.getRemaining();

                if (!allowSelectors) {
                    reader.setCursor(reader.getTotalLength());
                    return new MessageArgument.Message(s, new MessageArgument.Part[0]);
                } else {
                    List<MessageArgument.Part> list = Lists.newArrayList();
                    int i = reader.getCursor();

                    while (true) {
                        int j;
                        EntitySelector entityselector;

                        while (true) {
                            if (!reader.canRead()) {
                                return new MessageArgument.Message(s, (MessageArgument.Part[]) list.toArray(new MessageArgument.Part[0]));
                            }

                            if (reader.peek() == '@') {
                                j = reader.getCursor();

                                try {
                                    EntitySelectorParser entityselectorparser = new EntitySelectorParser(reader, true);

                                    entityselector = entityselectorparser.parse();
                                    break;
                                } catch (CommandSyntaxException commandsyntaxexception) {
                                    if (commandsyntaxexception.getType() != EntitySelectorParser.ERROR_MISSING_SELECTOR_TYPE && commandsyntaxexception.getType() != EntitySelectorParser.ERROR_UNKNOWN_SELECTOR_TYPE) {
                                        throw commandsyntaxexception;
                                    }

                                    reader.setCursor(j + 1);
                                }
                            } else {
                                reader.skip();
                            }
                        }

                        list.add(new MessageArgument.Part(j - i, reader.getCursor() - i, entityselector));
                    }
                }
            }
        }
    }

    public static record Part(int start, int end, EntitySelector selector) {

        public Component toComponent(CommandSourceStack sender) throws CommandSyntaxException {
            return EntitySelector.joinNames(this.selector.findEntities(sender));
        }
    }
}
