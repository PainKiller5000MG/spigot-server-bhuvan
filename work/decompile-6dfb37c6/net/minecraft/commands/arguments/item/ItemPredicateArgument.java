package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.parsing.packrat.commands.ParserBasedArgument;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemPredicateArgument extends ParserBasedArgument<ItemPredicateArgument.Result> {

    private static final Collection<String> EXAMPLES = Arrays.asList("stick", "minecraft:stick", "#stick", "#stick{foo:'bar'}");
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.item.id.invalid", object);
    });
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.item.tag.unknown", object);
    });
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.item.component.unknown", object);
    });
    private static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("arguments.item.component.malformed", object, object1);
    });
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_PREDICATE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.item.predicate.unknown", object);
    });
    private static final Dynamic2CommandExceptionType ERROR_MALFORMED_PREDICATE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("arguments.item.predicate.malformed", object, object1);
    });
    private static final Identifier COUNT_ID = Identifier.withDefaultNamespace("count");
    private static final Map<Identifier, ItemPredicateArgument.ComponentWrapper> PSEUDO_COMPONENTS = (Map) Stream.of(new ItemPredicateArgument.ComponentWrapper(ItemPredicateArgument.COUNT_ID, (itemstack) -> {
        return true;
    }, MinMaxBounds.Ints.CODEC.map((minmaxbounds_ints) -> {
        return (itemstack) -> {
            return minmaxbounds_ints.matches(itemstack.getCount());
        };
    }))).collect(Collectors.toUnmodifiableMap(ItemPredicateArgument.ComponentWrapper::id, (itempredicateargument_componentwrapper) -> {
        return itempredicateargument_componentwrapper;
    }));
    private static final Map<Identifier, ItemPredicateArgument.PredicateWrapper> PSEUDO_PREDICATES = (Map) Stream.of(new ItemPredicateArgument.PredicateWrapper(ItemPredicateArgument.COUNT_ID, MinMaxBounds.Ints.CODEC.map((minmaxbounds_ints) -> {
        return (itemstack) -> {
            return minmaxbounds_ints.matches(itemstack.getCount());
        };
    }))).collect(Collectors.toUnmodifiableMap(ItemPredicateArgument.PredicateWrapper::id, (itempredicateargument_predicatewrapper) -> {
        return itempredicateargument_predicatewrapper;
    }));

    private static ItemPredicateArgument.PredicateWrapper createComponentExistencePredicate(Holder.Reference<DataComponentType<?>> componentId) {
        Predicate<ItemStack> predicate = (itemstack) -> {
            return itemstack.has((DataComponentType) componentId.value());
        };

        return new ItemPredicateArgument.PredicateWrapper(componentId.key().identifier(), Unit.CODEC.map((unit) -> {
            return predicate;
        }));
    }

    public ItemPredicateArgument(CommandBuildContext registries) {
        super(ComponentPredicateParser.createGrammar(new ItemPredicateArgument.Context(registries)).mapResult((list) -> {
            Predicate predicate = Util.allOf(list);

            Objects.requireNonNull(predicate);
            return predicate::test;
        }));
    }

    public static ItemPredicateArgument itemPredicate(CommandBuildContext context) {
        return new ItemPredicateArgument(context);
    }

    public static ItemPredicateArgument.Result getItemPredicate(CommandContext<CommandSourceStack> context, String name) {
        return (ItemPredicateArgument.Result) context.getArgument(name, ItemPredicateArgument.Result.class);
    }

    public Collection<String> getExamples() {
        return ItemPredicateArgument.EXAMPLES;
    }

    private static record ComponentWrapper(Identifier id, Predicate<ItemStack> presenceChecker, Decoder<? extends Predicate<ItemStack>> valueChecker) {

        public static <T> ItemPredicateArgument.ComponentWrapper create(ImmutableStringReader reader, Identifier id, DataComponentType<T> type) throws CommandSyntaxException {
            Codec<T> codec = type.codec();

            if (codec == null) {
                throw ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(reader, id);
            } else {
                return new ItemPredicateArgument.ComponentWrapper(id, (itemstack) -> {
                    return itemstack.has(type);
                }, codec.map((object) -> {
                    return (itemstack) -> {
                        T t0 = (T) itemstack.get(type);

                        return Objects.equals(object, t0);
                    };
                }));
            }
        }

        public Predicate<ItemStack> decode(ImmutableStringReader reader, Dynamic<?> value) throws CommandSyntaxException {
            DataResult<? extends Predicate<ItemStack>> dataresult = this.valueChecker.parse(value);

            return (Predicate) dataresult.getOrThrow((s) -> {
                return ItemPredicateArgument.ERROR_MALFORMED_COMPONENT.createWithContext(reader, this.id.toString(), s);
            });
        }
    }

    private static record PredicateWrapper(Identifier id, Decoder<? extends Predicate<ItemStack>> type) {

        public PredicateWrapper(Holder.Reference<DataComponentPredicate.Type<?>> holder) {
            this(holder.key().identifier(), ((DataComponentPredicate.Type) holder.value()).codec().map((datacomponentpredicate) -> {
                Objects.requireNonNull(datacomponentpredicate);
                return datacomponentpredicate::matches;
            }));
        }

        public Predicate<ItemStack> decode(ImmutableStringReader reader, Dynamic<?> value) throws CommandSyntaxException {
            DataResult<? extends Predicate<ItemStack>> dataresult = this.type.parse(value);

            return (Predicate) dataresult.getOrThrow((s) -> {
                return ItemPredicateArgument.ERROR_MALFORMED_PREDICATE.createWithContext(reader, this.id.toString(), s);
            });
        }
    }

    private static class Context implements ComponentPredicateParser.Context<Predicate<ItemStack>, ItemPredicateArgument.ComponentWrapper, ItemPredicateArgument.PredicateWrapper> {

        private final HolderLookup.Provider registries;
        private final HolderLookup.RegistryLookup<Item> items;
        private final HolderLookup.RegistryLookup<DataComponentType<?>> components;
        private final HolderLookup.RegistryLookup<DataComponentPredicate.Type<?>> predicates;

        private Context(HolderLookup.Provider registries) {
            this.registries = registries;
            this.items = registries.lookupOrThrow(Registries.ITEM);
            this.components = registries.lookupOrThrow(Registries.DATA_COMPONENT_TYPE);
            this.predicates = registries.lookupOrThrow(Registries.DATA_COMPONENT_PREDICATE_TYPE);
        }

        @Override
        public Predicate<ItemStack> forElementType(ImmutableStringReader reader, Identifier id) throws CommandSyntaxException {
            Holder.Reference<Item> holder_reference = (Holder.Reference) this.items.get(ResourceKey.create(Registries.ITEM, id)).orElseThrow(() -> {
                return ItemPredicateArgument.ERROR_UNKNOWN_ITEM.createWithContext(reader, id);
            });

            return (itemstack) -> {
                return itemstack.is(holder_reference);
            };
        }

        @Override
        public Predicate<ItemStack> forTagType(ImmutableStringReader reader, Identifier id) throws CommandSyntaxException {
            HolderSet<Item> holderset = (HolderSet) this.items.get(TagKey.create(Registries.ITEM, id)).orElseThrow(() -> {
                return ItemPredicateArgument.ERROR_UNKNOWN_TAG.createWithContext(reader, id);
            });

            return (itemstack) -> {
                return itemstack.is(holderset);
            };
        }

        @Override
        public ItemPredicateArgument.ComponentWrapper lookupComponentType(ImmutableStringReader reader, Identifier componentId) throws CommandSyntaxException {
            ItemPredicateArgument.ComponentWrapper itempredicateargument_componentwrapper = (ItemPredicateArgument.ComponentWrapper) ItemPredicateArgument.PSEUDO_COMPONENTS.get(componentId);

            if (itempredicateargument_componentwrapper != null) {
                return itempredicateargument_componentwrapper;
            } else {
                DataComponentType<?> datacomponenttype = (DataComponentType) this.components.get(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, componentId)).map(Holder::value).orElseThrow(() -> {
                    return ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(reader, componentId);
                });

                return ItemPredicateArgument.ComponentWrapper.create(reader, componentId, datacomponenttype);
            }
        }

        public Predicate<ItemStack> createComponentTest(ImmutableStringReader reader, ItemPredicateArgument.ComponentWrapper componentType, Dynamic<?> value) throws CommandSyntaxException {
            return componentType.decode(reader, RegistryOps.injectRegistryContext(value, this.registries));
        }

        public Predicate<ItemStack> createComponentTest(ImmutableStringReader reader, ItemPredicateArgument.ComponentWrapper componentType) {
            return componentType.presenceChecker;
        }

        @Override
        public ItemPredicateArgument.PredicateWrapper lookupPredicateType(ImmutableStringReader reader, Identifier componentId) throws CommandSyntaxException {
            ItemPredicateArgument.PredicateWrapper itempredicateargument_predicatewrapper = (ItemPredicateArgument.PredicateWrapper) ItemPredicateArgument.PSEUDO_PREDICATES.get(componentId);

            return itempredicateargument_predicatewrapper != null ? itempredicateargument_predicatewrapper : (ItemPredicateArgument.PredicateWrapper) this.predicates.get(ResourceKey.create(Registries.DATA_COMPONENT_PREDICATE_TYPE, componentId)).map(ItemPredicateArgument.PredicateWrapper::new).or(() -> {
                return this.components.get(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, componentId)).map(ItemPredicateArgument::createComponentExistencePredicate);
            }).orElseThrow(() -> {
                return ItemPredicateArgument.ERROR_UNKNOWN_PREDICATE.createWithContext(reader, componentId);
            });
        }

        public Predicate<ItemStack> createPredicateTest(ImmutableStringReader reader, ItemPredicateArgument.PredicateWrapper predicateType, Dynamic<?> value) throws CommandSyntaxException {
            return predicateType.decode(reader, RegistryOps.injectRegistryContext(value, this.registries));
        }

        @Override
        public Stream<Identifier> listElementTypes() {
            return this.items.listElementIds().map(ResourceKey::identifier);
        }

        @Override
        public Stream<Identifier> listTagTypes() {
            return this.items.listTagIds().map(TagKey::location);
        }

        @Override
        public Stream<Identifier> listComponentTypes() {
            return Stream.concat(ItemPredicateArgument.PSEUDO_COMPONENTS.keySet().stream(), this.components.listElements().filter((holder_reference) -> {
                return !((DataComponentType) holder_reference.value()).isTransient();
            }).map((holder_reference) -> {
                return holder_reference.key().identifier();
            }));
        }

        @Override
        public Stream<Identifier> listPredicateTypes() {
            return Stream.concat(ItemPredicateArgument.PSEUDO_PREDICATES.keySet().stream(), this.predicates.listElementIds().map(ResourceKey::identifier));
        }

        public Predicate<ItemStack> negate(Predicate<ItemStack> value) {
            return value.negate();
        }

        @Override
        public Predicate<ItemStack> anyOf(List<Predicate<ItemStack>> alternatives) {
            return Util.anyOf(alternatives);
        }
    }

    public interface Result extends Predicate<ItemStack> {}
}
