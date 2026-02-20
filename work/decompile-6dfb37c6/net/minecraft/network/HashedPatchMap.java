package net.minecraft.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record HashedPatchMap(Map<DataComponentType<?>, Integer> addedComponents, Set<DataComponentType<?>> removedComponents) {

    public static final StreamCodec<RegistryFriendlyByteBuf, HashedPatchMap> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.registry(Registries.DATA_COMPONENT_TYPE), ByteBufCodecs.INT, 256), HashedPatchMap::addedComponents, ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.registry(Registries.DATA_COMPONENT_TYPE), 256), HashedPatchMap::removedComponents, HashedPatchMap::new);

    public static HashedPatchMap create(DataComponentPatch patch, HashedPatchMap.HashGenerator hasher) {
        DataComponentPatch.SplitResult datacomponentpatch_splitresult = patch.split();
        Map<DataComponentType<?>, Integer> map = new IdentityHashMap(datacomponentpatch_splitresult.added().size());

        datacomponentpatch_splitresult.added().forEach((typeddatacomponent) -> {
            map.put(typeddatacomponent.type(), (Integer) hasher.apply(typeddatacomponent));
        });
        return new HashedPatchMap(map, datacomponentpatch_splitresult.removed());
    }

    public boolean matches(DataComponentPatch patch, HashedPatchMap.HashGenerator hasher) {
        DataComponentPatch.SplitResult datacomponentpatch_splitresult = patch.split();

        if (!datacomponentpatch_splitresult.removed().equals(this.removedComponents)) {
            return false;
        } else if (this.addedComponents.size() != datacomponentpatch_splitresult.added().size()) {
            return false;
        } else {
            for (TypedDataComponent<?> typeddatacomponent : datacomponentpatch_splitresult.added()) {
                Integer integer = (Integer) this.addedComponents.get(typeddatacomponent.type());

                if (integer == null) {
                    return false;
                }

                Integer integer1 = (Integer) hasher.apply(typeddatacomponent);

                if (!integer1.equals(integer)) {
                    return false;
                }
            }

            return true;
        }
    }

    @FunctionalInterface
    public interface HashGenerator extends Function<TypedDataComponent<?>, Integer> {}
}
