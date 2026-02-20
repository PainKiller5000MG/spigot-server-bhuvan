package net.minecraft.world.level.storage.loot.functions;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SetNameFunction extends LootItemConditionalFunction {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SetNameFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(ComponentSerialization.CODEC.optionalFieldOf("name").forGetter((setnamefunction) -> {
            return setnamefunction.name;
        }), LootContext.EntityTarget.CODEC.optionalFieldOf("entity").forGetter((setnamefunction) -> {
            return setnamefunction.resolutionContext;
        }), SetNameFunction.Target.CODEC.optionalFieldOf("target", SetNameFunction.Target.CUSTOM_NAME).forGetter((setnamefunction) -> {
            return setnamefunction.target;
        }))).apply(instance, SetNameFunction::new);
    });
    private final Optional<Component> name;
    private final Optional<LootContext.EntityTarget> resolutionContext;
    private final SetNameFunction.Target target;

    private SetNameFunction(List<LootItemCondition> predicates, Optional<Component> name, Optional<LootContext.EntityTarget> resolutionContext, SetNameFunction.Target target) {
        super(predicates);
        this.name = name;
        this.resolutionContext = resolutionContext;
        this.target = target;
    }

    @Override
    public LootItemFunctionType<SetNameFunction> getType() {
        return LootItemFunctions.SET_NAME;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return (Set) this.resolutionContext.map((lootcontext_entitytarget) -> {
            return Set.of(lootcontext_entitytarget.contextParam());
        }).orElse(Set.of());
    }

    public static UnaryOperator<Component> createResolver(LootContext context, LootContext.@Nullable EntityTarget entityTarget) {
        if (entityTarget != null) {
            Entity entity = (Entity) context.getOptionalParameter(entityTarget.contextParam());

            if (entity != null) {
                CommandSourceStack commandsourcestack = entity.createCommandSourceStackForNameResolution(context.getLevel()).withPermission(LevelBasedPermissionSet.GAMEMASTER);

                return (component) -> {
                    try {
                        return ComponentUtils.updateForEntity(commandsourcestack, component, entity, 0);
                    } catch (CommandSyntaxException commandsyntaxexception) {
                        SetNameFunction.LOGGER.warn("Failed to resolve text component", commandsyntaxexception);
                        return component;
                    }
                };
            }
        }

        return (component) -> {
            return component;
        };
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        this.name.ifPresent((component) -> {
            itemStack.set(this.target.component(), (Component) createResolver(context, (LootContext.EntityTarget) this.resolutionContext.orElse((Object) null)).apply(component));
        });
        return itemStack;
    }

    public static LootItemConditionalFunction.Builder<?> setName(Component value, SetNameFunction.Target target) {
        return simpleBuilder((list) -> {
            return new SetNameFunction(list, Optional.of(value), Optional.empty(), target);
        });
    }

    public static LootItemConditionalFunction.Builder<?> setName(Component value, SetNameFunction.Target target, LootContext.EntityTarget resolutionContext) {
        return simpleBuilder((list) -> {
            return new SetNameFunction(list, Optional.of(value), Optional.of(resolutionContext), target);
        });
    }

    public static enum Target implements StringRepresentable {

        CUSTOM_NAME("custom_name"), ITEM_NAME("item_name");

        public static final Codec<SetNameFunction.Target> CODEC = StringRepresentable.<SetNameFunction.Target>fromEnum(SetNameFunction.Target::values);
        private final String name;

        private Target(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public DataComponentType<Component> component() {
            DataComponentType datacomponenttype;

            switch (this.ordinal()) {
                case 0:
                    datacomponenttype = DataComponents.CUSTOM_NAME;
                    break;
                case 1:
                    datacomponenttype = DataComponents.ITEM_NAME;
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return datacomponenttype;
        }
    }
}
