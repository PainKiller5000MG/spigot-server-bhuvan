package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

public class SignText {

    private static final Codec<Component[]> LINES_CODEC = ComponentSerialization.CODEC.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 4).map((list1) -> {
            return new Component[]{(Component) list1.get(0), (Component) list1.get(1), (Component) list1.get(2), (Component) list1.get(3)};
        });
    }, (acomponent) -> {
        return List.of(acomponent[0], acomponent[1], acomponent[2], acomponent[3]);
    });
    public static final Codec<SignText> DIRECT_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(SignText.LINES_CODEC.fieldOf("messages").forGetter((signtext) -> {
            return signtext.messages;
        }), SignText.LINES_CODEC.lenientOptionalFieldOf("filtered_messages").forGetter(SignText::filteredMessages), DyeColor.CODEC.fieldOf("color").orElse(DyeColor.BLACK).forGetter((signtext) -> {
            return signtext.color;
        }), Codec.BOOL.fieldOf("has_glowing_text").orElse(false).forGetter((signtext) -> {
            return signtext.hasGlowingText;
        })).apply(instance, SignText::load);
    });
    public static final int LINES = 4;
    private final Component[] messages;
    private final Component[] filteredMessages;
    private final DyeColor color;
    private final boolean hasGlowingText;
    private FormattedCharSequence @Nullable [] renderMessages;
    private boolean renderMessagedFiltered;

    public SignText() {
        this(emptyMessages(), emptyMessages(), DyeColor.BLACK, false);
    }

    public SignText(Component[] messages, Component[] filteredMessages, DyeColor color, boolean hasGlowingText) {
        this.messages = messages;
        this.filteredMessages = filteredMessages;
        this.color = color;
        this.hasGlowingText = hasGlowingText;
    }

    private static Component[] emptyMessages() {
        return new Component[]{CommonComponents.EMPTY, CommonComponents.EMPTY, CommonComponents.EMPTY, CommonComponents.EMPTY};
    }

    private static SignText load(Component[] messages, Optional<Component[]> filteredMessages, DyeColor color, boolean hasGlowingText) {
        return new SignText(messages, (Component[]) filteredMessages.orElse((Component[]) Arrays.copyOf(messages, messages.length)), color, hasGlowingText);
    }

    public boolean hasGlowingText() {
        return this.hasGlowingText;
    }

    public SignText setHasGlowingText(boolean hasGlowingText) {
        return hasGlowingText == this.hasGlowingText ? this : new SignText(this.messages, this.filteredMessages, this.color, hasGlowingText);
    }

    public DyeColor getColor() {
        return this.color;
    }

    public SignText setColor(DyeColor color) {
        return color == this.getColor() ? this : new SignText(this.messages, this.filteredMessages, color, this.hasGlowingText);
    }

    public Component getMessage(int index, boolean shouldFilter) {
        return this.getMessages(shouldFilter)[index];
    }

    public SignText setMessage(int index, Component message) {
        return this.setMessage(index, message, message);
    }

    public SignText setMessage(int index, Component rawMessage, Component filteredMessage) {
        Component[] acomponent = (Component[]) Arrays.copyOf(this.messages, this.messages.length);
        Component[] acomponent1 = (Component[]) Arrays.copyOf(this.filteredMessages, this.filteredMessages.length);

        acomponent[index] = rawMessage;
        acomponent1[index] = filteredMessage;
        return new SignText(acomponent, acomponent1, this.color, this.hasGlowingText);
    }

    public boolean hasMessage(Player player) {
        return Arrays.stream(this.getMessages(player.isTextFilteringEnabled())).anyMatch((component) -> {
            return !component.getString().isEmpty();
        });
    }

    public Component[] getMessages(boolean shouldFilter) {
        return shouldFilter ? this.filteredMessages : this.messages;
    }

    public FormattedCharSequence[] getRenderMessages(boolean shouldFilter, Function<Component, FormattedCharSequence> prepare) {
        if (this.renderMessages == null || this.renderMessagedFiltered != shouldFilter) {
            this.renderMessagedFiltered = shouldFilter;
            this.renderMessages = new FormattedCharSequence[4];

            for (int i = 0; i < 4; ++i) {
                this.renderMessages[i] = (FormattedCharSequence) prepare.apply(this.getMessage(i, shouldFilter));
            }
        }

        return this.renderMessages;
    }

    private Optional<Component[]> filteredMessages() {
        for (int i = 0; i < 4; ++i) {
            if (!this.filteredMessages[i].equals(this.messages[i])) {
                return Optional.of(this.filteredMessages);
            }
        }

        return Optional.empty();
    }

    public boolean hasAnyClickCommands(Player player) {
        for (Component component : this.getMessages(player.isTextFilteringEnabled())) {
            Style style = component.getStyle();
            ClickEvent clickevent = style.getClickEvent();

            if (clickevent != null && clickevent.action() == ClickEvent.Action.RUN_COMMAND) {
                return true;
            }
        }

        return false;
    }
}
