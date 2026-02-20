package net.minecraft.server.dialog.action;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public class ActionTypes {

    public ActionTypes() {}

    public static MapCodec<? extends Action> bootstrap(Registry<MapCodec<? extends Action>> registry) {
        StaticAction.WRAPPED_CODECS.forEach((clickevent_action, mapcodec) -> {
            Registry.register(registry, Identifier.withDefaultNamespace(clickevent_action.getSerializedName()), mapcodec);
        });
        Registry.register(registry, Identifier.withDefaultNamespace("dynamic/run_command"), CommandTemplate.MAP_CODEC);
        return (MapCodec) Registry.register(registry, Identifier.withDefaultNamespace("dynamic/custom"), CustomAll.MAP_CODEC);
    }
}
