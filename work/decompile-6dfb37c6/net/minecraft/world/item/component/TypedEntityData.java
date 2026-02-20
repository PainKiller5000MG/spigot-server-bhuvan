package net.minecraft.world.item.component;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.Logger;

public final class TypedEntityData<IdType> implements TooltipProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TYPE_TAG = "id";
    private final IdType type;
    private final CompoundTag tag;

    public static <T> Codec<TypedEntityData<T>> codec(final Codec<T> typeCodec) {
        return new Codec<TypedEntityData<T>>() {
            public <V> DataResult<Pair<TypedEntityData<T>, V>> decode(DynamicOps<V> ops, V input) {
                return CustomData.COMPOUND_TAG_CODEC.decode(ops, input).flatMap((pair) -> {
                    CompoundTag compoundtag = ((CompoundTag) pair.getFirst()).copy();
                    Tag tag = compoundtag.remove("id");

                    return tag == null ? DataResult.error(() -> {
                        return "Expected 'id' field in " + String.valueOf(input);
                    }) : typeCodec.parse(asNbtOps(ops), tag).map((object) -> {
                        return Pair.of(new TypedEntityData(object, compoundtag), pair.getSecond());
                    });
                });
            }

            public <V> DataResult<V> encode(TypedEntityData<T> input, DynamicOps<V> ops, V prefix) {
                return typeCodec.encodeStart(asNbtOps(ops), input.type).flatMap((tag) -> {
                    CompoundTag compoundtag = input.tag.copy();

                    compoundtag.put("id", tag);
                    return CustomData.COMPOUND_TAG_CODEC.encode(compoundtag, ops, prefix);
                });
            }

            private static <T> DynamicOps<Tag> asNbtOps(DynamicOps<T> ops) {
                if (ops instanceof RegistryOps<T> registryops) {
                    return registryops.<Tag>withParent(NbtOps.INSTANCE);
                } else {
                    return NbtOps.INSTANCE;
                }
            }
        };
    }

    public static <B extends ByteBuf, T> StreamCodec<B, TypedEntityData<T>> streamCodec(StreamCodec<B, T> typeCodec) {
        return StreamCodec.composite(typeCodec, TypedEntityData::type, ByteBufCodecs.COMPOUND_TAG, TypedEntityData::tag, TypedEntityData::new);
    }

    private TypedEntityData(IdType type, CompoundTag data) {
        this.type = type;
        this.tag = stripId(data);
    }

    public static <T> TypedEntityData<T> of(T type, CompoundTag data) {
        return new TypedEntityData<T>(type, data);
    }

    private static CompoundTag stripId(CompoundTag tag) {
        if (tag.contains("id")) {
            CompoundTag compoundtag1 = tag.copy();

            compoundtag1.remove("id");
            return compoundtag1;
        } else {
            return tag;
        }
    }

    public IdType type() {
        return this.type;
    }

    public boolean contains(String name) {
        return this.tag.contains(name);
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof TypedEntityData)) {
            return false;
        } else {
            TypedEntityData<?> typedentitydata = (TypedEntityData) obj;

            return this.type == typedentitydata.type && this.tag.equals(typedentitydata.tag);
        }
    }

    public int hashCode() {
        return 31 * this.type.hashCode() + this.tag.hashCode();
    }

    public String toString() {
        String s = String.valueOf(this.type);

        return s + " " + String.valueOf(this.tag);
    }

    public void loadInto(Entity entity) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(entity.problemPath(), TypedEntityData.LOGGER)) {
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, entity.registryAccess());

            entity.saveWithoutId(tagvalueoutput);
            CompoundTag compoundtag = tagvalueoutput.buildResult();
            UUID uuid = entity.getUUID();

            compoundtag.merge(this.getUnsafe());
            entity.load(TagValueInput.create(problemreporter_scopedcollector, entity.registryAccess(), compoundtag));
            entity.setUUID(uuid);
        }

    }

    public boolean loadInto(BlockEntity blockEntity, HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), TypedEntityData.LOGGER)) {
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, registries);

            blockEntity.saveCustomOnly((ValueOutput) tagvalueoutput);
            CompoundTag compoundtag = tagvalueoutput.buildResult();
            CompoundTag compoundtag1 = compoundtag.copy();

            compoundtag.merge(this.getUnsafe());
            if (!compoundtag.equals(compoundtag1)) {
                try {
                    blockEntity.loadCustomOnly(TagValueInput.create(problemreporter_scopedcollector, registries, compoundtag));
                    blockEntity.setChanged();
                    return true;
                } catch (Exception exception) {
                    TypedEntityData.LOGGER.warn("Failed to apply custom data to block entity at {}", blockEntity.getBlockPos(), exception);

                    try {
                        blockEntity.loadCustomOnly(TagValueInput.create(problemreporter_scopedcollector.forChild(() -> {
                            return "(rollback)";
                        }), registries, compoundtag1));
                    } catch (Exception exception1) {
                        TypedEntityData.LOGGER.warn("Failed to rollback block entity at {} after failure", blockEntity.getBlockPos(), exception1);
                    }
                }
            }

            return false;
        }
    }

    private CompoundTag tag() {
        return this.tag;
    }

    /** @deprecated */
    @Deprecated
    public CompoundTag getUnsafe() {
        return this.tag;
    }

    public CompoundTag copyTagWithoutId() {
        return this.tag.copy();
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        if (this.type.getClass() == EntityType.class) {
            EntityType<?> entitytype = (EntityType) this.type;

            if (context.isPeaceful() && !entitytype.isAllowedInPeaceful()) {
                consumer.accept(Component.translatable("item.spawn_egg.peaceful").withStyle(ChatFormatting.RED));
            }
        }

    }
}
