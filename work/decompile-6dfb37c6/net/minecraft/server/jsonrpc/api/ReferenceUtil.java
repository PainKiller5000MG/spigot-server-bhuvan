package net.minecraft.server.jsonrpc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class ReferenceUtil {

    public static final Codec<URI> REFERENCE_CODEC = Codec.STRING.comapFlatMap((s) -> {
        try {
            return DataResult.success(new URI(s));
        } catch (URISyntaxException urisyntaxexception) {
            Objects.requireNonNull(urisyntaxexception);
            return DataResult.error(urisyntaxexception::getMessage);
        }
    }, URI::toString);

    public ReferenceUtil() {}

    public static URI createLocalReference(String typeId) {
        return URI.create("#/components/schemas/" + typeId);
    }
}
