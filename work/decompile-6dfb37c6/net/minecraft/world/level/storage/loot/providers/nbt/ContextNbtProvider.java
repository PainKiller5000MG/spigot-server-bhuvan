package net.minecraft.world.level.storage.loot.providers.nbt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContextArg;
import org.jspecify.annotations.Nullable;

public class ContextNbtProvider implements NbtProvider {

    private static final Codec<LootContextArg<Tag>> GETTER_CODEC = LootContextArg.createArgCodec((lootcontextarg_argcodecbuilder) -> {
        return lootcontextarg_argcodecbuilder.anyBlockEntity(ContextNbtProvider.BlockEntitySource::new).anyEntity(ContextNbtProvider.EntitySource::new);
    });
    public static final MapCodec<ContextNbtProvider> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(ContextNbtProvider.GETTER_CODEC.fieldOf("target").forGetter((contextnbtprovider) -> {
            return contextnbtprovider.source;
        })).apply(instance, ContextNbtProvider::new);
    });
    public static final Codec<ContextNbtProvider> INLINE_CODEC = ContextNbtProvider.GETTER_CODEC.xmap(ContextNbtProvider::new, (contextnbtprovider) -> {
        return contextnbtprovider.source;
    });
    private final LootContextArg<Tag> source;

    private ContextNbtProvider(LootContextArg<Tag> source) {
        this.source = source;
    }

    @Override
    public LootNbtProviderType getType() {
        return NbtProviders.CONTEXT;
    }

    @Override
    public @Nullable Tag get(LootContext context) {
        return this.source.get(context);
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(this.source.contextParam());
    }

    public static NbtProvider forContextEntity(LootContext.EntityTarget source) {
        return new ContextNbtProvider(new ContextNbtProvider.EntitySource(source.contextParam()));
    }

    private static record BlockEntitySource(ContextKey<? extends BlockEntity> contextParam) implements LootContextArg.Getter<BlockEntity, Tag> {

        public Tag get(BlockEntity blockEntity) {
            return blockEntity.saveWithFullMetadata((HolderLookup.Provider) blockEntity.getLevel().registryAccess());
        }
    }

    private static record EntitySource(ContextKey<? extends Entity> contextParam) implements LootContextArg.Getter<Entity, Tag> {

        public Tag get(Entity entity) {
            return NbtPredicate.getEntityTagToCompare(entity);
        }
    }
}
