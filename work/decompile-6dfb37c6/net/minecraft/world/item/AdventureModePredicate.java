package net.minecraft.world.item;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class AdventureModePredicate {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<AdventureModePredicate> CODEC = ExtraCodecs.compactListCodec(BlockPredicate.CODEC, ExtraCodecs.nonEmptyList(BlockPredicate.CODEC.listOf())).xmap(AdventureModePredicate::new, (adventuremodepredicate) -> {
        return adventuremodepredicate.predicates;
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, AdventureModePredicate> STREAM_CODEC = StreamCodec.composite(BlockPredicate.STREAM_CODEC.apply(ByteBufCodecs.list()), (adventuremodepredicate) -> {
        return adventuremodepredicate.predicates;
    }, AdventureModePredicate::new);
    public static final Component CAN_BREAK_HEADER = Component.translatable("item.canBreak").withStyle(ChatFormatting.GRAY);
    public static final Component CAN_PLACE_HEADER = Component.translatable("item.canPlace").withStyle(ChatFormatting.GRAY);
    private static final Component UNKNOWN_USE = Component.translatable("item.canUse.unknown").withStyle(ChatFormatting.GRAY);
    private final List<BlockPredicate> predicates;
    private @Nullable List<Component> cachedTooltip;
    private @Nullable BlockInWorld lastCheckedBlock;
    private boolean lastResult;
    private boolean checksBlockEntity;

    public AdventureModePredicate(List<BlockPredicate> predicates) {
        this.predicates = predicates;
    }

    private static boolean areSameBlocks(BlockInWorld blockInWorld, @Nullable BlockInWorld cachedBlock, boolean checkBlockEntity) {
        if (cachedBlock != null && blockInWorld.getState() == cachedBlock.getState()) {
            if (!checkBlockEntity) {
                return true;
            } else if (blockInWorld.getEntity() == null && cachedBlock.getEntity() == null) {
                return true;
            } else if (blockInWorld.getEntity() != null && cachedBlock.getEntity() != null) {
                try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(AdventureModePredicate.LOGGER)) {
                    RegistryAccess registryaccess = blockInWorld.getLevel().registryAccess();
                    CompoundTag compoundtag = saveBlockEntity(blockInWorld.getEntity(), registryaccess, problemreporter_scopedcollector);
                    CompoundTag compoundtag1 = saveBlockEntity(cachedBlock.getEntity(), registryaccess, problemreporter_scopedcollector);

                    return Objects.equals(compoundtag, compoundtag1);
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static CompoundTag saveBlockEntity(BlockEntity blockEntity, RegistryAccess registryAccess, ProblemReporter reporter) {
        TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(reporter.forChild(blockEntity.problemPath()), registryAccess);

        blockEntity.saveWithId(tagvalueoutput);
        return tagvalueoutput.buildResult();
    }

    public boolean test(BlockInWorld blockInWorld) {
        if (areSameBlocks(blockInWorld, this.lastCheckedBlock, this.checksBlockEntity)) {
            return this.lastResult;
        } else {
            this.lastCheckedBlock = blockInWorld;
            this.checksBlockEntity = false;

            for (BlockPredicate blockpredicate : this.predicates) {
                if (blockpredicate.matches(blockInWorld)) {
                    this.checksBlockEntity |= blockpredicate.requiresNbt();
                    this.lastResult = true;
                    return true;
                }
            }

            this.lastResult = false;
            return false;
        }
    }

    private List<Component> tooltip() {
        if (this.cachedTooltip == null) {
            this.cachedTooltip = computeTooltip(this.predicates);
        }

        return this.cachedTooltip;
    }

    public void addToTooltip(Consumer<Component> consumer) {
        this.tooltip().forEach(consumer);
    }

    private static List<Component> computeTooltip(List<BlockPredicate> predicates) {
        for (BlockPredicate blockpredicate : predicates) {
            if (blockpredicate.blocks().isEmpty()) {
                return List.of(AdventureModePredicate.UNKNOWN_USE);
            }
        }

        return predicates.stream().flatMap((blockpredicate1) -> {
            return ((HolderSet) blockpredicate1.blocks().orElseThrow()).stream();
        }).distinct().map((holder) -> {
            return ((Block) holder.value()).getName().withStyle(ChatFormatting.DARK_GRAY);
        }).toList();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof AdventureModePredicate) {
            AdventureModePredicate adventuremodepredicate = (AdventureModePredicate) obj;

            return this.predicates.equals(adventuremodepredicate.predicates);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.predicates.hashCode();
    }

    public String toString() {
        return "AdventureModePredicate{predicates=" + String.valueOf(this.predicates) + "}";
    }
}
