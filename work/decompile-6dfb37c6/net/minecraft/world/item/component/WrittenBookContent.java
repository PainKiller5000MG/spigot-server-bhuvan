package net.minecraft.world.item.component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;

public record WrittenBookContent(Filterable<String> title, String author, int generation, List<Filterable<Component>> pages, boolean resolved) implements BookContent<Component, WrittenBookContent>, TooltipProvider {

    public static final WrittenBookContent EMPTY = new WrittenBookContent(Filterable.passThrough(""), "", 0, List.of(), true);
    public static final int PAGE_LENGTH = 32767;
    public static final int TITLE_LENGTH = 16;
    public static final int TITLE_MAX_LENGTH = 32;
    public static final int MAX_GENERATION = 3;
    public static final int MAX_CRAFTABLE_GENERATION = 2;
    public static final Codec<Component> CONTENT_CODEC = ComponentSerialization.flatRestrictedCodec(32767);
    public static final Codec<List<Filterable<Component>>> PAGES_CODEC = pagesCodec(WrittenBookContent.CONTENT_CODEC);
    public static final Codec<WrittenBookContent> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Filterable.codec(Codec.string(0, 32)).fieldOf("title").forGetter(WrittenBookContent::title), Codec.STRING.fieldOf("author").forGetter(WrittenBookContent::author), ExtraCodecs.intRange(0, 3).optionalFieldOf("generation", 0).forGetter(WrittenBookContent::generation), WrittenBookContent.PAGES_CODEC.optionalFieldOf("pages", List.of()).forGetter(WrittenBookContent::pages), Codec.BOOL.optionalFieldOf("resolved", false).forGetter(WrittenBookContent::resolved)).apply(instance, WrittenBookContent::new);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, WrittenBookContent> STREAM_CODEC = StreamCodec.composite(Filterable.streamCodec(ByteBufCodecs.stringUtf8(32)), WrittenBookContent::title, ByteBufCodecs.STRING_UTF8, WrittenBookContent::author, ByteBufCodecs.VAR_INT, WrittenBookContent::generation, Filterable.streamCodec(ComponentSerialization.STREAM_CODEC).apply(ByteBufCodecs.list()), WrittenBookContent::pages, ByteBufCodecs.BOOL, WrittenBookContent::resolved, WrittenBookContent::new);

    public WrittenBookContent(Filterable<String> title, String author, int generation, List<Filterable<Component>> pages, boolean resolved) {
        if (generation >= 0 && generation <= 3) {
            this.title = title;
            this.author = author;
            this.generation = generation;
            this.pages = pages;
            this.resolved = resolved;
        } else {
            throw new IllegalArgumentException("Generation was " + generation + ", but must be between 0 and 3");
        }
    }

    private static Codec<Filterable<Component>> pageCodec(Codec<Component> contentCodec) {
        return Filterable.codec(contentCodec);
    }

    public static Codec<List<Filterable<Component>>> pagesCodec(Codec<Component> contentCodec) {
        return pageCodec(contentCodec).listOf();
    }

    public @Nullable WrittenBookContent tryCraftCopy() {
        return this.generation >= 2 ? null : new WrittenBookContent(this.title, this.author, this.generation + 1, this.pages, this.resolved);
    }

    public static boolean resolveForItem(ItemStack itemStack, CommandSourceStack source, @Nullable Player player) {
        WrittenBookContent writtenbookcontent = (WrittenBookContent) itemStack.get(DataComponents.WRITTEN_BOOK_CONTENT);

        if (writtenbookcontent != null && !writtenbookcontent.resolved()) {
            WrittenBookContent writtenbookcontent1 = writtenbookcontent.resolve(source, player);

            if (writtenbookcontent1 != null) {
                itemStack.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenbookcontent1);
                return true;
            }

            itemStack.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenbookcontent.markResolved());
        }

        return false;
    }

    public @Nullable WrittenBookContent resolve(CommandSourceStack source, @Nullable Player player) {
        if (this.resolved) {
            return null;
        } else {
            ImmutableList.Builder<Filterable<Component>> immutablelist_builder = ImmutableList.builderWithExpectedSize(this.pages.size());

            for (Filterable<Component> filterable : this.pages) {
                Optional<Filterable<Component>> optional = resolvePage(source, player, filterable);

                if (optional.isEmpty()) {
                    return null;
                }

                immutablelist_builder.add((Filterable) optional.get());
            }

            return new WrittenBookContent(this.title, this.author, this.generation, immutablelist_builder.build(), true);
        }
    }

    public WrittenBookContent markResolved() {
        return new WrittenBookContent(this.title, this.author, this.generation, this.pages, true);
    }

    private static Optional<Filterable<Component>> resolvePage(CommandSourceStack source, @Nullable Player player, Filterable<Component> page) {
        return page.resolve((component) -> {
            try {
                Component component1 = ComponentUtils.updateForEntity(source, component, player, 0);

                return isPageTooLarge(component1, source.registryAccess()) ? Optional.empty() : Optional.of(component1);
            } catch (Exception exception) {
                return Optional.of(component);
            }
        });
    }

    private static boolean isPageTooLarge(Component page, HolderLookup.Provider registries) {
        DataResult<JsonElement> dataresult = ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), page);

        return dataresult.isSuccess() && GsonHelper.encodesLongerThan((JsonElement) dataresult.getOrThrow(), 32767);
    }

    public List<Component> getPages(boolean filterEnabled) {
        return Lists.transform(this.pages, (filterable) -> {
            return (Component) filterable.get(filterEnabled);
        });
    }

    @Override
    public WrittenBookContent withReplacedPages(List<Filterable<Component>> newPages) {
        return new WrittenBookContent(this.title, this.author, this.generation, newPages, false);
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        if (!StringUtil.isBlank(this.author)) {
            consumer.accept(Component.translatable("book.byAuthor", this.author).withStyle(ChatFormatting.GRAY));
        }

        consumer.accept(Component.translatable("book.generation." + this.generation).withStyle(ChatFormatting.GRAY));
    }
}
