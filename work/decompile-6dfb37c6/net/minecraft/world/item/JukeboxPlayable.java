package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public record JukeboxPlayable(EitherHolder<JukeboxSong> song) implements TooltipProvider {

    public static final Codec<JukeboxPlayable> CODEC = EitherHolder.codec(Registries.JUKEBOX_SONG, JukeboxSong.CODEC).xmap(JukeboxPlayable::new, JukeboxPlayable::song);
    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxPlayable> STREAM_CODEC = StreamCodec.composite(EitherHolder.streamCodec(Registries.JUKEBOX_SONG, JukeboxSong.STREAM_CODEC), JukeboxPlayable::song, JukeboxPlayable::new);

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        HolderLookup.Provider holderlookup_provider = context.registries();

        if (holderlookup_provider != null) {
            this.song.unwrap(holderlookup_provider).ifPresent((holder) -> {
                Component component = ComponentUtils.mergeStyles(((JukeboxSong) holder.value()).description(), Style.EMPTY.withColor(ChatFormatting.GRAY));

                consumer.accept(component);
            });
        }

    }

    public static InteractionResult tryInsertIntoJukebox(Level level, BlockPos pos, ItemStack toInsert, Player player) {
        JukeboxPlayable jukeboxplayable = (JukeboxPlayable) toInsert.get(DataComponents.JUKEBOX_PLAYABLE);

        if (jukeboxplayable == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            BlockState blockstate = level.getBlockState(pos);

            if (blockstate.is(Blocks.JUKEBOX) && !(Boolean) blockstate.getValue(JukeboxBlock.HAS_RECORD)) {
                if (!level.isClientSide()) {
                    ItemStack itemstack1 = toInsert.consumeAndReturn(1, player);
                    BlockEntity blockentity = level.getBlockEntity(pos);

                    if (blockentity instanceof JukeboxBlockEntity) {
                        JukeboxBlockEntity jukeboxblockentity = (JukeboxBlockEntity) blockentity;

                        jukeboxblockentity.setTheItem(itemstack1);
                        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, blockstate));
                    }

                    player.awardStat(Stats.PLAY_RECORD);
                }

                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
        }
    }
}
