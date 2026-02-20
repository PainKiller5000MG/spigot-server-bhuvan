package net.minecraft.world.level.timers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;

public record FunctionCallback(Identifier functionId) implements TimerCallback<MinecraftServer> {

    public static final MapCodec<FunctionCallback> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Identifier.CODEC.fieldOf("Name").forGetter(FunctionCallback::functionId)).apply(instance, FunctionCallback::new);
    });

    public void handle(MinecraftServer server, TimerQueue<MinecraftServer> queue, long time) {
        ServerFunctionManager serverfunctionmanager = server.getFunctions();

        serverfunctionmanager.get(this.functionId).ifPresent((commandfunction) -> {
            serverfunctionmanager.execute(commandfunction, serverfunctionmanager.getGameLoopSender());
        });
    }

    @Override
    public MapCodec<FunctionCallback> codec() {
        return FunctionCallback.CODEC;
    }
}
