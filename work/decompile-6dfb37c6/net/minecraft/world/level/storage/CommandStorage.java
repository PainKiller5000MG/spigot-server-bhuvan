package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

public class CommandStorage {

    private static final String ID_PREFIX = "command_storage_";
    private final Map<String, CommandStorage.Container> namespaces = new HashMap();
    private final DimensionDataStorage storage;

    public CommandStorage(DimensionDataStorage storage) {
        this.storage = storage;
    }

    public CompoundTag get(Identifier id) {
        CommandStorage.Container commandstorage_container = this.getContainer(id.getNamespace());

        return commandstorage_container != null ? commandstorage_container.get(id.getPath()) : new CompoundTag();
    }

    private CommandStorage.@Nullable Container getContainer(String namespace) {
        CommandStorage.Container commandstorage_container = (CommandStorage.Container) this.namespaces.get(namespace);

        if (commandstorage_container != null) {
            return commandstorage_container;
        } else {
            CommandStorage.Container commandstorage_container1 = (CommandStorage.Container) this.storage.get(CommandStorage.Container.type(namespace));

            if (commandstorage_container1 != null) {
                this.namespaces.put(namespace, commandstorage_container1);
            }

            return commandstorage_container1;
        }
    }

    private CommandStorage.Container getOrCreateContainer(String namespace) {
        CommandStorage.Container commandstorage_container = (CommandStorage.Container) this.namespaces.get(namespace);

        if (commandstorage_container != null) {
            return commandstorage_container;
        } else {
            CommandStorage.Container commandstorage_container1 = (CommandStorage.Container) this.storage.computeIfAbsent(CommandStorage.Container.type(namespace));

            this.namespaces.put(namespace, commandstorage_container1);
            return commandstorage_container1;
        }
    }

    public void set(Identifier id, CompoundTag contents) {
        this.getOrCreateContainer(id.getNamespace()).put(id.getPath(), contents);
    }

    public Stream<Identifier> keys() {
        return this.namespaces.entrySet().stream().flatMap((entry) -> {
            return ((CommandStorage.Container) entry.getValue()).getKeys((String) entry.getKey());
        });
    }

    private static String createId(String namespace) {
        return "command_storage_" + namespace;
    }

    private static class Container extends SavedData {

        public static final Codec<CommandStorage.Container> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.unboundedMap(ExtraCodecs.RESOURCE_PATH_CODEC, CompoundTag.CODEC).fieldOf("contents").forGetter((commandstorage_container) -> {
                return commandstorage_container.storage;
            })).apply(instance, CommandStorage.Container::new);
        });
        private final Map<String, CompoundTag> storage;

        private Container(Map<String, CompoundTag> storage) {
            this.storage = new HashMap(storage);
        }

        private Container() {
            this(new HashMap());
        }

        public static SavedDataType<CommandStorage.Container> type(String namespace) {
            return new SavedDataType<CommandStorage.Container>(CommandStorage.createId(namespace), CommandStorage.Container::new, CommandStorage.Container.CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
        }

        public CompoundTag get(String id) {
            CompoundTag compoundtag = (CompoundTag) this.storage.get(id);

            return compoundtag != null ? compoundtag : new CompoundTag();
        }

        public void put(String id, CompoundTag contents) {
            if (contents.isEmpty()) {
                this.storage.remove(id);
            } else {
                this.storage.put(id, contents);
            }

            this.setDirty();
        }

        public Stream<Identifier> getKeys(String namespace) {
            return this.storage.keySet().stream().map((s1) -> {
                return Identifier.fromNamespaceAndPath(namespace, s1);
            });
        }
    }
}
