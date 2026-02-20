package net.minecraft.server.packs;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

public class CompositePackResources implements PackResources {

    private final PackResources primaryPackResources;
    private final List<PackResources> packResourcesStack;

    public CompositePackResources(PackResources primaryPackResources, List<PackResources> overlayPackResources) {
        this.primaryPackResources = primaryPackResources;
        List<PackResources> list1 = new ArrayList(overlayPackResources.size() + 1);

        list1.addAll(Lists.reverse(overlayPackResources));
        list1.add(primaryPackResources);
        this.packResourcesStack = List.copyOf(list1);
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return this.primaryPackResources.getRootResource(path);
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        for (PackResources packresources : this.packResourcesStack) {
            IoSupplier<InputStream> iosupplier = packresources.getResource(type, location);

            if (iosupplier != null) {
                return iosupplier;
            }
        }

        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, PackResources.ResourceOutput output) {
        Map<Identifier, IoSupplier<InputStream>> map = new HashMap();

        for (PackResources packresources : this.packResourcesStack) {
            Objects.requireNonNull(map);
            packresources.listResources(type, namespace, directory, map::putIfAbsent);
        }

        map.forEach(output);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> set = new HashSet();

        for (PackResources packresources : this.packResourcesStack) {
            set.addAll(packresources.getNamespaces(type));
        }

        return set;
    }

    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> metadataSerializer) throws IOException {
        return (T) this.primaryPackResources.getMetadataSection(metadataSerializer);
    }

    @Override
    public PackLocationInfo location() {
        return this.primaryPackResources.location();
    }

    @Override
    public void close() {
        this.packResourcesStack.forEach(PackResources::close);
    }
}
