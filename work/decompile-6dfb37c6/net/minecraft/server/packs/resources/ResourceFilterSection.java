package net.minecraft.server.packs.resources;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.IdentifierPattern;

public class ResourceFilterSection {

    private static final Codec<ResourceFilterSection> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.list(IdentifierPattern.CODEC).fieldOf("block").forGetter((resourcefiltersection) -> {
            return resourcefiltersection.blockList;
        })).apply(instance, ResourceFilterSection::new);
    });
    public static final MetadataSectionType<ResourceFilterSection> TYPE = new MetadataSectionType<ResourceFilterSection>("filter", ResourceFilterSection.CODEC);
    private final List<IdentifierPattern> blockList;

    public ResourceFilterSection(List<IdentifierPattern> blockList) {
        this.blockList = List.copyOf(blockList);
    }

    public boolean isNamespaceFiltered(String namespace) {
        return this.blockList.stream().anyMatch((identifierpattern) -> {
            return identifierpattern.namespacePredicate().test(namespace);
        });
    }

    public boolean isPathFiltered(String path) {
        return this.blockList.stream().anyMatch((identifierpattern) -> {
            return identifierpattern.pathPredicate().test(path);
        });
    }
}
