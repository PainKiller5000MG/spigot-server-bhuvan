package net.minecraft.world.entity.variant;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class VariantUtils {

    public static final String TAG_VARIANT = "variant";

    public VariantUtils() {}

    public static <T> Holder<T> getDefaultOrAny(RegistryAccess registryAccess, ResourceKey<T> id) {
        Registry<T> registry = registryAccess.lookupOrThrow(id.registryKey());
        Optional optional = registry.get(id);

        Objects.requireNonNull(registry);
        return (Holder) optional.or(registry::getAny).orElseThrow();
    }

    public static <T> Holder<T> getAny(RegistryAccess registryAccess, ResourceKey<? extends Registry<T>> registryId) {
        return (Holder) registryAccess.lookupOrThrow(registryId).getAny().orElseThrow();
    }

    public static <T> void writeVariant(ValueOutput output, Holder<T> holder) {
        holder.unwrapKey().ifPresent((resourcekey) -> {
            output.store("variant", Identifier.CODEC, resourcekey.identifier());
        });
    }

    public static <T> Optional<Holder<T>> readVariant(ValueInput input, ResourceKey<? extends Registry<T>> registryId) {
        Optional optional = input.read("variant", Identifier.CODEC).map((identifier) -> {
            return ResourceKey.create(registryId, identifier);
        });
        HolderLookup.Provider holderlookup_provider = input.lookup();

        Objects.requireNonNull(holderlookup_provider);
        return optional.flatMap(holderlookup_provider::get);
    }

    public static <T extends PriorityProvider<SpawnContext, ?>> Optional<Holder.Reference<T>> selectVariantToSpawn(SpawnContext context, ResourceKey<Registry<T>> resourcekey) {
        ServerLevelAccessor serverlevelaccessor = context.level();
        Stream<Holder.Reference<T>> stream = serverlevelaccessor.registryAccess().lookupOrThrow(resourcekey).listElements();

        return PriorityProvider.pick(stream, Holder::value, serverlevelaccessor.getRandom(), context);
    }
}
