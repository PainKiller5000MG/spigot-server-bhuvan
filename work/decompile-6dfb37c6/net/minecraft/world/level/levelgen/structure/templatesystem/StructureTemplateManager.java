package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.IdentifierException;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.FileUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class StructureTemplateManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String STRUCTURE_RESOURCE_DIRECTORY_NAME = "structure";
    private static final String STRUCTURE_GENERATED_DIRECTORY_NAME = "structures";
    private static final String STRUCTURE_FILE_EXTENSION = ".nbt";
    private static final String STRUCTURE_TEXT_FILE_EXTENSION = ".snbt";
    public final Map<Identifier, Optional<StructureTemplate>> structureRepository = Maps.newConcurrentMap();
    private final DataFixer fixerUpper;
    private ResourceManager resourceManager;
    private final Path generatedDir;
    private final List<StructureTemplateManager.Source> sources;
    private final HolderGetter<Block> blockLookup;
    private static final FileToIdConverter RESOURCE_LISTER = new FileToIdConverter("structure", ".nbt");

    public StructureTemplateManager(ResourceManager resourceManager, LevelStorageSource.LevelStorageAccess storage, DataFixer fixerUpper, HolderGetter<Block> blockLookup) {
        this.resourceManager = resourceManager;
        this.fixerUpper = fixerUpper;
        this.generatedDir = storage.getLevelPath(LevelResource.GENERATED_DIR).normalize();
        this.blockLookup = blockLookup;
        ImmutableList.Builder<StructureTemplateManager.Source> immutablelist_builder = ImmutableList.builder();

        immutablelist_builder.add(new StructureTemplateManager.Source(this::loadFromGenerated, this::listGenerated));
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            immutablelist_builder.add(new StructureTemplateManager.Source(this::loadFromTestStructures, this::listTestStructures));
        }

        immutablelist_builder.add(new StructureTemplateManager.Source(this::loadFromResource, this::listResources));
        this.sources = immutablelist_builder.build();
    }

    public StructureTemplate getOrCreate(Identifier id) {
        Optional<StructureTemplate> optional = this.get(id);

        if (optional.isPresent()) {
            return (StructureTemplate) optional.get();
        } else {
            StructureTemplate structuretemplate = new StructureTemplate();

            this.structureRepository.put(id, Optional.of(structuretemplate));
            return structuretemplate;
        }
    }

    public Optional<StructureTemplate> get(Identifier id) {
        return (Optional) this.structureRepository.computeIfAbsent(id, this::tryLoad);
    }

    public Stream<Identifier> listTemplates() {
        return this.sources.stream().flatMap((structuretemplatemanager_source) -> {
            return (Stream) structuretemplatemanager_source.lister().get();
        }).distinct();
    }

    private Optional<StructureTemplate> tryLoad(Identifier id) {
        for (StructureTemplateManager.Source structuretemplatemanager_source : this.sources) {
            try {
                Optional<StructureTemplate> optional = (Optional) structuretemplatemanager_source.loader().apply(id);

                if (optional.isPresent()) {
                    return optional;
                }
            } catch (Exception exception) {
                ;
            }
        }

        return Optional.empty();
    }

    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        this.structureRepository.clear();
    }

    public Optional<StructureTemplate> loadFromResource(Identifier id) {
        Identifier identifier1 = StructureTemplateManager.RESOURCE_LISTER.idToFile(id);

        return this.load(() -> {
            return this.resourceManager.open(identifier1);
        }, (throwable) -> {
            StructureTemplateManager.LOGGER.error("Couldn't load structure {}", id, throwable);
        });
    }

    private Stream<Identifier> listResources() {
        Stream stream = StructureTemplateManager.RESOURCE_LISTER.listMatchingResources(this.resourceManager).keySet().stream();
        FileToIdConverter filetoidconverter = StructureTemplateManager.RESOURCE_LISTER;

        Objects.requireNonNull(filetoidconverter);
        return stream.map(filetoidconverter::fileToId);
    }

    private Optional<StructureTemplate> loadFromTestStructures(Identifier id) {
        return this.loadFromSnbt(id, StructureUtils.testStructuresDir);
    }

    private Stream<Identifier> listTestStructures() {
        if (!Files.isDirectory(StructureUtils.testStructuresDir, new LinkOption[0])) {
            return Stream.empty();
        } else {
            List<Identifier> list = new ArrayList();
            Path path = StructureUtils.testStructuresDir;

            Objects.requireNonNull(list);
            this.listFolderContents(path, "minecraft", ".snbt", list::add);
            return list.stream();
        }
    }

    public Optional<StructureTemplate> loadFromGenerated(Identifier id) {
        if (!Files.isDirectory(this.generatedDir, new LinkOption[0])) {
            return Optional.empty();
        } else {
            Path path = this.createAndValidatePathToGeneratedStructure(id, ".nbt");

            return this.load(() -> {
                return new FileInputStream(path.toFile());
            }, (throwable) -> {
                StructureTemplateManager.LOGGER.error("Couldn't load structure from {}", path, throwable);
            });
        }
    }

    private Stream<Identifier> listGenerated() {
        if (!Files.isDirectory(this.generatedDir, new LinkOption[0])) {
            return Stream.empty();
        } else {
            try {
                List<Identifier> list = new ArrayList();

                try (DirectoryStream<Path> directorystream = Files.newDirectoryStream(this.generatedDir, (path) -> {
                    return Files.isDirectory(path, new LinkOption[0]);
                })) {
                    for (Path path : directorystream) {
                        String s = path.getFileName().toString();
                        Path path1 = path.resolve("structures");

                        Objects.requireNonNull(list);
                        this.listFolderContents(path1, s, ".nbt", list::add);
                    }
                }

                return list.stream();
            } catch (IOException ioexception) {
                return Stream.empty();
            }
        }
    }

    private void listFolderContents(Path folder, String namespace, String extension, Consumer<Identifier> output) {
        int i = extension.length();
        Function<String, String> function = (s2) -> {
            return s2.substring(0, s2.length() - i);
        };

        try (Stream<Path> stream = Files.find(folder, Integer.MAX_VALUE, (path1, basicfileattributes) -> {
            return basicfileattributes.isRegularFile() && path1.toString().endsWith(extension);
        }, new FileVisitOption[0])) {
            stream.forEach((path1) -> {
                try {
                    output.accept(Identifier.fromNamespaceAndPath(namespace, (String) function.apply(this.relativize(folder, path1))));
                } catch (IdentifierException identifierexception) {
                    StructureTemplateManager.LOGGER.error("Invalid location while listing folder {} contents", folder, identifierexception);
                }

            });
        } catch (IOException ioexception) {
            StructureTemplateManager.LOGGER.error("Failed to list folder {} contents", folder, ioexception);
        }

    }

    private String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace(File.separator, "/");
    }

    private Optional<StructureTemplate> loadFromSnbt(Identifier id, Path dir) {
        if (!Files.isDirectory(dir, new LinkOption[0])) {
            return Optional.empty();
        } else {
            Path path1 = FileUtil.createPathToResource(dir, id.getPath(), ".snbt");

            try (BufferedReader bufferedreader = Files.newBufferedReader(path1)) {
                String s = IOUtils.toString(bufferedreader);

                return Optional.of(this.readStructure(NbtUtils.snbtToStructure(s)));
            } catch (NoSuchFileException nosuchfileexception) {
                return Optional.empty();
            } catch (CommandSyntaxException | IOException ioexception) {
                StructureTemplateManager.LOGGER.error("Couldn't load structure from {}", path1, ioexception);
                return Optional.empty();
            }
        }
    }

    private Optional<StructureTemplate> load(StructureTemplateManager.InputStreamOpener opener, Consumer<Throwable> onError) {
        try {
            Optional optional;

            try (InputStream inputstream = opener.open(); InputStream inputstream1 = new FastBufferedInputStream(inputstream);) {
                optional = Optional.of(this.readStructure(inputstream1));
            }

            return optional;
        } catch (FileNotFoundException filenotfoundexception) {
            return Optional.empty();
        } catch (Throwable throwable) {
            onError.accept(throwable);
            return Optional.empty();
        }
    }

    public StructureTemplate readStructure(InputStream input) throws IOException {
        CompoundTag compoundtag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());

        return this.readStructure(compoundtag);
    }

    public StructureTemplate readStructure(CompoundTag tag) {
        StructureTemplate structuretemplate = new StructureTemplate();
        int i = NbtUtils.getDataVersion(tag, 500);

        structuretemplate.load(this.blockLookup, DataFixTypes.STRUCTURE.updateToCurrentVersion(this.fixerUpper, tag, i));
        return structuretemplate;
    }

    public boolean save(Identifier id) {
        Optional<StructureTemplate> optional = (Optional) this.structureRepository.get(id);

        if (optional.isEmpty()) {
            return false;
        } else {
            StructureTemplate structuretemplate = (StructureTemplate) optional.get();
            Path path = this.createAndValidatePathToGeneratedStructure(id, SharedConstants.DEBUG_SAVE_STRUCTURES_AS_SNBT ? ".snbt" : ".nbt");
            Path path1 = path.getParent();

            if (path1 == null) {
                return false;
            } else {
                try {
                    Files.createDirectories(Files.exists(path1, new LinkOption[0]) ? path1.toRealPath() : path1);
                } catch (IOException ioexception) {
                    StructureTemplateManager.LOGGER.error("Failed to create parent directory: {}", path1);
                    return false;
                }

                CompoundTag compoundtag = structuretemplate.save(new CompoundTag());

                if (SharedConstants.DEBUG_SAVE_STRUCTURES_AS_SNBT) {
                    try {
                        NbtToSnbt.writeSnbt(CachedOutput.NO_CACHE, path, NbtUtils.structureToSnbt(compoundtag));
                    } catch (Throwable throwable) {
                        return false;
                    }
                } else {
                    try (OutputStream outputstream = new FileOutputStream(path.toFile())) {
                        NbtIo.writeCompressed(compoundtag, outputstream);
                    } catch (Throwable throwable1) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    public Path createAndValidatePathToGeneratedStructure(Identifier id, String extension) {
        if (id.getPath().contains("//")) {
            throw new IdentifierException("Invalid resource path: " + String.valueOf(id));
        } else {
            try {
                Path path = this.generatedDir.resolve(id.getNamespace());
                Path path1 = path.resolve("structures");
                Path path2 = FileUtil.createPathToResource(path1, id.getPath(), extension);

                if (path2.startsWith(this.generatedDir) && FileUtil.isPathNormalized(path2) && FileUtil.isPathPortable(path2)) {
                    return path2;
                } else {
                    throw new IdentifierException("Invalid resource path: " + String.valueOf(path2));
                }
            } catch (InvalidPathException invalidpathexception) {
                throw new IdentifierException("Invalid resource path: " + String.valueOf(id), invalidpathexception);
            }
        }
    }

    public void remove(Identifier id) {
        this.structureRepository.remove(id);
    }

    private static record Source(Function<Identifier, Optional<StructureTemplate>> loader, Supplier<Stream<Identifier>> lister) {

    }

    @FunctionalInterface
    private interface InputStreamOpener {

        InputStream open() throws IOException;
    }
}
