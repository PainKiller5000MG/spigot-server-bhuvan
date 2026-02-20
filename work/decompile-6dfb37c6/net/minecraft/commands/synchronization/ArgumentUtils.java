package net.minecraft.commands.synchronization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionProviderCheck;
import org.slf4j.Logger;

public class ArgumentUtils {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final byte NUMBER_FLAG_MIN = 1;
    private static final byte NUMBER_FLAG_MAX = 2;

    public ArgumentUtils() {}

    public static int createNumberFlags(boolean hasMin, boolean hasMax) {
        int i = 0;

        if (hasMin) {
            i |= 1;
        }

        if (hasMax) {
            i |= 2;
        }

        return i;
    }

    public static boolean numberHasMin(byte flags) {
        return (flags & 1) != 0;
    }

    public static boolean numberHasMax(byte flags) {
        return (flags & 2) != 0;
    }

    private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void serializeArgumentCap(JsonObject result, ArgumentTypeInfo<A, T> info, ArgumentTypeInfo.Template<A> argumentType) {
        info.serializeToJson(argumentType, result);
    }

    private static <T extends ArgumentType<?>> void serializeArgumentToJson(JsonObject result, T argument) {
        ArgumentTypeInfo.Template<T> argumenttypeinfo_template = ArgumentTypeInfos.<T>unpack(argument);

        result.addProperty("type", "argument");
        result.addProperty("parser", String.valueOf(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(argumenttypeinfo_template.type())));
        JsonObject jsonobject1 = new JsonObject();

        serializeArgumentCap(jsonobject1, argumenttypeinfo_template.type(), argumenttypeinfo_template);
        if (!jsonobject1.isEmpty()) {
            result.add("properties", jsonobject1);
        }

    }

    public static <S> JsonObject serializeNodeToJson(CommandDispatcher<S> dispatcher, CommandNode<S> node) {
        JsonObject jsonobject = new JsonObject();

        Objects.requireNonNull(node);
        byte b0 = 0;

        //$FF: b0->value
        //0->com/mojang/brigadier/tree/RootCommandNode
        //1->com/mojang/brigadier/tree/LiteralCommandNode
        //2->com/mojang/brigadier/tree/ArgumentCommandNode
        switch (node.typeSwitch<invokedynamic>(node, b0)) {
            case 0:
                RootCommandNode<S> rootcommandnode = (RootCommandNode)node;

                jsonobject.addProperty("type", "root");
                break;
            case 1:
                LiteralCommandNode<S> literalcommandnode = (LiteralCommandNode)node;

                jsonobject.addProperty("type", "literal");
                break;
            case 2:
                ArgumentCommandNode<S, ?> argumentcommandnode = (ArgumentCommandNode)node;

                serializeArgumentToJson(jsonobject, argumentcommandnode.getType());
                break;
            default:
                ArgumentUtils.LOGGER.error("Could not serialize node {} ({})!", node, node.getClass());
                jsonobject.addProperty("type", "unknown");
        }

        Collection<CommandNode<S>> collection = node.getChildren();

        if (!collection.isEmpty()) {
            JsonObject jsonobject1 = new JsonObject();

            for(CommandNode<S> commandnode1 : collection) {
                jsonobject1.add(commandnode1.getName(), serializeNodeToJson(dispatcher, commandnode1));
            }

            jsonobject.add("children", jsonobject1);
        }

        if (node.getCommand() != null) {
            jsonobject.addProperty("executable", true);
        }

        Predicate predicate = node.getRequirement();

        if (predicate instanceof PermissionProviderCheck<?> permissionprovidercheck) {
            JsonElement jsonelement = (JsonElement)PermissionCheck.CODEC.encodeStart(JsonOps.INSTANCE, permissionprovidercheck.test()).getOrThrow((s) -> {
                return new IllegalStateException("Failed to serialize requirement: " + s);
            });

            jsonobject.add("permissions", jsonelement);
        }

        if (node.getRedirect() != null) {
            Collection<String> collection1 = dispatcher.getPath(node.getRedirect());

            if (!collection1.isEmpty()) {
                JsonArray jsonarray = new JsonArray();

                for(String s : collection1) {
                    jsonarray.add(s);
                }

                jsonobject.add("redirect", jsonarray);
            }
        }

        return jsonobject;
    }

    public static <T> Set<ArgumentType<?>> findUsedArgumentTypes(CommandNode<T> node) {
        Set<CommandNode<T>> set = new ReferenceOpenHashSet();
        Set<ArgumentType<?>> set1 = new HashSet();

        findUsedArgumentTypes(node, set1, set);
        return set1;
    }

    private static <T> void findUsedArgumentTypes(CommandNode<T> node, Set<ArgumentType<?>> output, Set<CommandNode<T>> visitedNodes) {
        if (visitedNodes.add(node)) {
            if (node instanceof ArgumentCommandNode) {
                ArgumentCommandNode<T, ?> argumentcommandnode = (ArgumentCommandNode) node;

                output.add(argumentcommandnode.getType());
            }

            node.getChildren().forEach((commandnode1) -> {
                findUsedArgumentTypes(commandnode1, output, visitedNodes);
            });
            CommandNode<T> commandnode1 = node.getRedirect();

            if (commandnode1 != null) {
                findUsedArgumentTypes(commandnode1, output, visitedNodes);
            }

        }
    }
}
