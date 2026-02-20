package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;
import net.minecraft.world.level.storage.loot.providers.nbt.NbtProvider;
import net.minecraft.world.level.storage.loot.providers.nbt.NbtProviders;
import org.apache.commons.lang3.mutable.MutableObject;

public class CopyCustomDataFunction extends LootItemConditionalFunction {

    public static final MapCodec<CopyCustomDataFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(NbtProviders.CODEC.fieldOf("source").forGetter((copycustomdatafunction) -> {
            return copycustomdatafunction.source;
        }), CopyCustomDataFunction.CopyOperation.CODEC.listOf().fieldOf("ops").forGetter((copycustomdatafunction) -> {
            return copycustomdatafunction.operations;
        }))).apply(instance, CopyCustomDataFunction::new);
    });
    private final NbtProvider source;
    private final List<CopyCustomDataFunction.CopyOperation> operations;

    private CopyCustomDataFunction(List<LootItemCondition> predicates, NbtProvider source, List<CopyCustomDataFunction.CopyOperation> operations) {
        super(predicates);
        this.source = source;
        this.operations = List.copyOf(operations);
    }

    @Override
    public LootItemFunctionType<CopyCustomDataFunction> getType() {
        return LootItemFunctions.COPY_CUSTOM_DATA;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.source.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        Tag tag = this.source.get(context);

        if (tag == null) {
            return itemStack;
        } else {
            MutableObject<CompoundTag> mutableobject = new MutableObject();
            Supplier<Tag> supplier = () -> {
                if (mutableobject.get() == null) {
                    mutableobject.setValue(((CustomData) itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag());
                }

                return (Tag) mutableobject.get();
            };

            this.operations.forEach((copycustomdatafunction_copyoperation) -> {
                copycustomdatafunction_copyoperation.apply(supplier, tag);
            });
            CompoundTag compoundtag = (CompoundTag) mutableobject.get();

            if (compoundtag != null) {
                CustomData.set(DataComponents.CUSTOM_DATA, itemStack, compoundtag);
            }

            return itemStack;
        }
    }

    /** @deprecated */
    @Deprecated
    public static CopyCustomDataFunction.Builder copyData(NbtProvider source) {
        return new CopyCustomDataFunction.Builder(source);
    }

    public static CopyCustomDataFunction.Builder copyData(LootContext.EntityTarget source) {
        return new CopyCustomDataFunction.Builder(ContextNbtProvider.forContextEntity(source));
    }

    private static record CopyOperation(NbtPathArgument.NbtPath sourcePath, NbtPathArgument.NbtPath targetPath, CopyCustomDataFunction.MergeStrategy op) {

        public static final Codec<CopyCustomDataFunction.CopyOperation> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(NbtPathArgument.NbtPath.CODEC.fieldOf("source").forGetter(CopyCustomDataFunction.CopyOperation::sourcePath), NbtPathArgument.NbtPath.CODEC.fieldOf("target").forGetter(CopyCustomDataFunction.CopyOperation::targetPath), CopyCustomDataFunction.MergeStrategy.CODEC.fieldOf("op").forGetter(CopyCustomDataFunction.CopyOperation::op)).apply(instance, CopyCustomDataFunction.CopyOperation::new);
        });

        public void apply(Supplier<Tag> target, Tag source) {
            try {
                List<Tag> list = this.sourcePath.get(source);

                if (!list.isEmpty()) {
                    this.op.merge((Tag) target.get(), this.targetPath, list);
                }
            } catch (CommandSyntaxException commandsyntaxexception) {
                ;
            }

        }
    }

    public static class Builder extends LootItemConditionalFunction.Builder<CopyCustomDataFunction.Builder> {

        private final NbtProvider source;
        private final List<CopyCustomDataFunction.CopyOperation> ops = Lists.newArrayList();

        private Builder(NbtProvider source) {
            this.source = source;
        }

        public CopyCustomDataFunction.Builder copy(String sourcePath, String targetPath, CopyCustomDataFunction.MergeStrategy mergeStrategy) {
            try {
                this.ops.add(new CopyCustomDataFunction.CopyOperation(NbtPathArgument.NbtPath.of(sourcePath), NbtPathArgument.NbtPath.of(targetPath), mergeStrategy));
                return this;
            } catch (CommandSyntaxException commandsyntaxexception) {
                throw new IllegalArgumentException(commandsyntaxexception);
            }
        }

        public CopyCustomDataFunction.Builder copy(String sourcePath, String targetPath) {
            return this.copy(sourcePath, targetPath, CopyCustomDataFunction.MergeStrategy.REPLACE);
        }

        @Override
        protected CopyCustomDataFunction.Builder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new CopyCustomDataFunction(this.getConditions(), this.source, this.ops);
        }
    }

    public static enum MergeStrategy implements StringRepresentable {

        REPLACE("replace") {
            @Override
            public void merge(Tag target, NbtPathArgument.NbtPath path, List<Tag> sources) throws CommandSyntaxException {
                path.set(target, (Tag) Iterables.getLast(sources));
            }
        },
        APPEND("append") {
            @Override
            public void merge(Tag target, NbtPathArgument.NbtPath path, List<Tag> sources) throws CommandSyntaxException {
                List<Tag> list1 = path.getOrCreate(target, ListTag::new);

                list1.forEach((tag1) -> {
                    if (tag1 instanceof ListTag) {
                        sources.forEach((tag2) -> {
                            ((ListTag) tag1).add(tag2.copy());
                        });
                    }

                });
            }
        },
        MERGE("merge") {
            @Override
            public void merge(Tag target, NbtPathArgument.NbtPath path, List<Tag> sources) throws CommandSyntaxException {
                List<Tag> list1 = path.getOrCreate(target, CompoundTag::new);

                list1.forEach((tag1) -> {
                    if (tag1 instanceof CompoundTag) {
                        sources.forEach((tag2) -> {
                            if (tag2 instanceof CompoundTag) {
                                ((CompoundTag) tag1).merge((CompoundTag) tag2);
                            }

                        });
                    }

                });
            }
        };

        public static final Codec<CopyCustomDataFunction.MergeStrategy> CODEC = StringRepresentable.<CopyCustomDataFunction.MergeStrategy>fromEnum(CopyCustomDataFunction.MergeStrategy::values);
        private final String name;

        public abstract void merge(Tag target, NbtPathArgument.NbtPath path, List<Tag> sources) throws CommandSyntaxException;

        private MergeStrategy(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
