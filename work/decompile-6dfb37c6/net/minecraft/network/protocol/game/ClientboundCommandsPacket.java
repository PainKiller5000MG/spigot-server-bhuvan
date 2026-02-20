package net.minecraft.network.protocol.game;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class ClientboundCommandsPacket implements Packet<ClientGamePacketListener> {

    public static final StreamCodec<FriendlyByteBuf, ClientboundCommandsPacket> STREAM_CODEC = Packet.<FriendlyByteBuf, ClientboundCommandsPacket>codec(ClientboundCommandsPacket::write, ClientboundCommandsPacket::new);
    private static final byte MASK_TYPE = 3;
    private static final byte FLAG_EXECUTABLE = 4;
    private static final byte FLAG_REDIRECT = 8;
    private static final byte FLAG_CUSTOM_SUGGESTIONS = 16;
    private static final byte FLAG_RESTRICTED = 32;
    private static final byte TYPE_ROOT = 0;
    private static final byte TYPE_LITERAL = 1;
    private static final byte TYPE_ARGUMENT = 2;
    private final int rootIndex;
    private final List<ClientboundCommandsPacket.Entry> entries;

    public <S> ClientboundCommandsPacket(RootCommandNode<S> root, ClientboundCommandsPacket.NodeInspector<S> inspector) {
        Object2IntMap<CommandNode<S>> object2intmap = enumerateNodes(root);

        this.entries = createEntries(object2intmap, inspector);
        this.rootIndex = object2intmap.getInt(root);
    }

    private ClientboundCommandsPacket(FriendlyByteBuf input) {
        this.entries = input.<ClientboundCommandsPacket.Entry>readList(ClientboundCommandsPacket::readNode);
        this.rootIndex = input.readVarInt();
        validateEntries(this.entries);
    }

    private void write(FriendlyByteBuf output) {
        output.writeCollection(this.entries, (friendlybytebuf1, clientboundcommandspacket_entry) -> {
            clientboundcommandspacket_entry.write(friendlybytebuf1);
        });
        output.writeVarInt(this.rootIndex);
    }

    private static void validateEntries(List<ClientboundCommandsPacket.Entry> entries, BiPredicate<ClientboundCommandsPacket.Entry, IntSet> validator) {
        IntSet intset = new IntOpenHashSet(IntSets.fromTo(0, entries.size()));

        while (!((IntSet) intset).isEmpty()) {
            boolean flag = intset.removeIf((i) -> {
                return validator.test((ClientboundCommandsPacket.Entry) entries.get(i), intset);
            });

            if (!flag) {
                throw new IllegalStateException("Server sent an impossible command tree");
            }
        }

    }

    private static void validateEntries(List<ClientboundCommandsPacket.Entry> entries) {
        validateEntries(entries, ClientboundCommandsPacket.Entry::canBuild);
        validateEntries(entries, ClientboundCommandsPacket.Entry::canResolve);
    }

    private static <S> Object2IntMap<CommandNode<S>> enumerateNodes(RootCommandNode<S> root) {
        Object2IntMap<CommandNode<S>> object2intmap = new Object2IntOpenHashMap();
        Queue<CommandNode<S>> queue = new ArrayDeque();

        queue.add(root);

        CommandNode<S> commandnode;

        while ((commandnode = (CommandNode) ((Queue) queue).poll()) != null) {
            if (!object2intmap.containsKey(commandnode)) {
                int i = object2intmap.size();

                object2intmap.put(commandnode, i);
                queue.addAll(commandnode.getChildren());
                if (commandnode.getRedirect() != null) {
                    queue.add(commandnode.getRedirect());
                }
            }
        }

        return object2intmap;
    }

    private static <S> List<ClientboundCommandsPacket.Entry> createEntries(Object2IntMap<CommandNode<S>> nodeToId, ClientboundCommandsPacket.NodeInspector<S> inspector) {
        ObjectArrayList<ClientboundCommandsPacket.Entry> objectarraylist = new ObjectArrayList(nodeToId.size());

        objectarraylist.size(nodeToId.size());
        ObjectIterator objectiterator = Object2IntMaps.fastIterable(nodeToId).iterator();

        while (objectiterator.hasNext()) {
            Object2IntMap.Entry<CommandNode<S>> object2intmap_entry = (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry) objectiterator.next();

            objectarraylist.set(object2intmap_entry.getIntValue(), createEntry((CommandNode) object2intmap_entry.getKey(), inspector, nodeToId));
        }

        return objectarraylist;
    }

    private static ClientboundCommandsPacket.Entry readNode(FriendlyByteBuf input) {
        byte b0 = input.readByte();
        int[] aint = input.readVarIntArray();
        int i = (b0 & 8) != 0 ? input.readVarInt() : 0;
        ClientboundCommandsPacket.NodeStub clientboundcommandspacket_nodestub = read(input, b0);

        return new ClientboundCommandsPacket.Entry(clientboundcommandspacket_nodestub, b0, i, aint);
    }

    private static ClientboundCommandsPacket.@Nullable NodeStub read(FriendlyByteBuf input, byte flags) {
        int i = flags & 3;

        if (i == 2) {
            String s = input.readUtf();
            int j = input.readVarInt();
            ArgumentTypeInfo<?, ?> argumenttypeinfo = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.byId(j);

            if (argumenttypeinfo == null) {
                return null;
            } else {
                ArgumentTypeInfo.Template<?> argumenttypeinfo_template = argumenttypeinfo.deserializeFromNetwork(input);
                Identifier identifier = (flags & 16) != 0 ? input.readIdentifier() : null;

                return new ClientboundCommandsPacket.ArgumentNodeStub(s, argumenttypeinfo_template, identifier);
            }
        } else if (i == 1) {
            String s1 = input.readUtf();

            return new ClientboundCommandsPacket.LiteralNodeStub(s1);
        } else {
            return null;
        }
    }

    private static <S> ClientboundCommandsPacket.Entry createEntry(CommandNode<S> node, ClientboundCommandsPacket.NodeInspector<S> inspector, Object2IntMap<CommandNode<S>> ids) {
        int i = 0;
        int j;

        if (node.getRedirect() != null) {
            i |= 8;
            j = ids.getInt(node.getRedirect());
        } else {
            j = 0;
        }

        if (inspector.isExecutable(node)) {
            i |= 4;
        }

        if (inspector.isRestricted(node)) {
            i |= 32;
        }

        Objects.requireNonNull(node);
        byte b0 = 0;
        ClientboundCommandsPacket.NodeStub clientboundcommandspacket_nodestub;

        //$FF: b0->value
        //0->com/mojang/brigadier/tree/RootCommandNode
        //1->com/mojang/brigadier/tree/ArgumentCommandNode
        //2->com/mojang/brigadier/tree/LiteralCommandNode
        switch (node.typeSwitch<invokedynamic>(node, b0)) {
            case 0:
                RootCommandNode<S> rootcommandnode = (RootCommandNode)node;

                i |= 0;
                clientboundcommandspacket_nodestub = null;
                break;
            case 1:
                ArgumentCommandNode<S, ?> argumentcommandnode = (ArgumentCommandNode)node;
                Identifier identifier = inspector.suggestionId(argumentcommandnode);

                clientboundcommandspacket_nodestub = new ClientboundCommandsPacket.ArgumentNodeStub(argumentcommandnode.getName(), ArgumentTypeInfos.unpack(argumentcommandnode.getType()), identifier);
                i |= 2;
                if (identifier != null) {
                    i |= 16;
                }
                break;
            case 2:
                LiteralCommandNode<S> literalcommandnode = (LiteralCommandNode)node;

                clientboundcommandspacket_nodestub = new ClientboundCommandsPacket.LiteralNodeStub(literalcommandnode.getLiteral());
                i |= 1;
                break;
            default:
                throw new UnsupportedOperationException("Unknown node type " + String.valueOf(node));
        }

        Stream stream = node.getChildren().stream();

        Objects.requireNonNull(ids);
        int[] aint = stream.mapToInt(ids::getInt).toArray();

        return new ClientboundCommandsPacket.Entry(clientboundcommandspacket_nodestub, i, j, aint);
    }

    @Override
    public PacketType<ClientboundCommandsPacket> type() {
        return GamePacketTypes.CLIENTBOUND_COMMANDS;
    }

    public void handle(ClientGamePacketListener listener) {
        listener.handleCommands(this);
    }

    public <S> RootCommandNode<S> getRoot(CommandBuildContext context, ClientboundCommandsPacket.NodeBuilder<S> builder) {
        return (RootCommandNode) (new ClientboundCommandsPacket.NodeResolver<S>(context, builder, this.entries)).resolve(this.rootIndex);
    }

    private static record LiteralNodeStub(String id) implements ClientboundCommandsPacket.NodeStub {

        @Override
        public <S> ArgumentBuilder<S, ?> build(CommandBuildContext context, ClientboundCommandsPacket.NodeBuilder<S> builder) {
            return builder.createLiteral(this.id);
        }

        @Override
        public void write(FriendlyByteBuf output) {
            output.writeUtf(this.id);
        }
    }

    private static record ArgumentNodeStub(String id, ArgumentTypeInfo.Template<?> argumentType, @Nullable Identifier suggestionId) implements ClientboundCommandsPacket.NodeStub {

        @Override
        public <S> ArgumentBuilder<S, ?> build(CommandBuildContext context, ClientboundCommandsPacket.NodeBuilder<S> builder) {
            ArgumentType<?> argumenttype = this.argumentType.instantiate(context);

            return builder.createArgument(this.id, argumenttype, this.suggestionId);
        }

        @Override
        public void write(FriendlyByteBuf output) {
            output.writeUtf(this.id);
            serializeCap(output, this.argumentType);
            if (this.suggestionId != null) {
                output.writeIdentifier(this.suggestionId);
            }

        }

        private static <A extends ArgumentType<?>> void serializeCap(FriendlyByteBuf output, ArgumentTypeInfo.Template<A> argumentType) {
            serializeCap(output, argumentType.type(), argumentType);
        }

        private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void serializeCap(FriendlyByteBuf output, ArgumentTypeInfo<A, T> info, ArgumentTypeInfo.Template<A> argumentType) {
            output.writeVarInt(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getId(info));
            info.serializeToNetwork(argumentType, output);
        }
    }

    private static record Entry(ClientboundCommandsPacket.@Nullable NodeStub stub, int flags, int redirect, int[] children) {

        public void write(FriendlyByteBuf output) {
            output.writeByte(this.flags);
            output.writeVarIntArray(this.children);
            if ((this.flags & 8) != 0) {
                output.writeVarInt(this.redirect);
            }

            if (this.stub != null) {
                this.stub.write(output);
            }

        }

        public boolean canBuild(IntSet unbuiltNodes) {
            return (this.flags & 8) != 0 ? !unbuiltNodes.contains(this.redirect) : true;
        }

        public boolean canResolve(IntSet unresolvedNodes) {
            for (int i : this.children) {
                if (unresolvedNodes.contains(i)) {
                    return false;
                }
            }

            return true;
        }
    }

    private static class NodeResolver<S> {

        private final CommandBuildContext context;
        private final ClientboundCommandsPacket.NodeBuilder<S> builder;
        private final List<ClientboundCommandsPacket.Entry> entries;
        private final List<CommandNode<S>> nodes;

        private NodeResolver(CommandBuildContext context, ClientboundCommandsPacket.NodeBuilder<S> builder, List<ClientboundCommandsPacket.Entry> entries) {
            this.context = context;
            this.builder = builder;
            this.entries = entries;
            ObjectArrayList<CommandNode<S>> objectarraylist = new ObjectArrayList();

            objectarraylist.size(entries.size());
            this.nodes = objectarraylist;
        }

        public CommandNode<S> resolve(int index) {
            CommandNode<S> commandnode = (CommandNode) this.nodes.get(index);

            if (commandnode != null) {
                return commandnode;
            } else {
                ClientboundCommandsPacket.Entry clientboundcommandspacket_entry = (ClientboundCommandsPacket.Entry) this.entries.get(index);
                CommandNode<S> commandnode1;

                if (clientboundcommandspacket_entry.stub == null) {
                    commandnode1 = new RootCommandNode();
                } else {
                    ArgumentBuilder<S, ?> argumentbuilder = clientboundcommandspacket_entry.stub.build(this.context, this.builder);

                    if ((clientboundcommandspacket_entry.flags & 8) != 0) {
                        argumentbuilder.redirect(this.resolve(clientboundcommandspacket_entry.redirect));
                    }

                    boolean flag = (clientboundcommandspacket_entry.flags & 4) != 0;
                    boolean flag1 = (clientboundcommandspacket_entry.flags & 32) != 0;

                    commandnode1 = this.builder.configure(argumentbuilder, flag, flag1).build();
                }

                this.nodes.set(index, commandnode1);

                for (int j : clientboundcommandspacket_entry.children) {
                    CommandNode<S> commandnode2 = this.resolve(j);

                    if (!(commandnode2 instanceof RootCommandNode)) {
                        commandnode1.addChild(commandnode2);
                    }
                }

                return commandnode1;
            }
        }
    }

    public interface NodeBuilder<S> {

        ArgumentBuilder<S, ?> createLiteral(String id);

        ArgumentBuilder<S, ?> createArgument(String id, ArgumentType<?> argumentType, @Nullable Identifier suggestionId);

        ArgumentBuilder<S, ?> configure(ArgumentBuilder<S, ?> input, boolean executable, boolean restricted);
    }

    public interface NodeInspector<S> {

        @Nullable
        Identifier suggestionId(ArgumentCommandNode<S, ?> node);

        boolean isExecutable(CommandNode<S> node);

        boolean isRestricted(CommandNode<S> node);
    }

    private interface NodeStub {

        <S> ArgumentBuilder<S, ?> build(CommandBuildContext context, ClientboundCommandsPacket.NodeBuilder<S> builder);

        void write(FriendlyByteBuf output);
    }
}
