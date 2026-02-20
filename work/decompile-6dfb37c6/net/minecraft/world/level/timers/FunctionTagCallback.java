package net.minecraft.world.level.timers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;

public record FunctionTagCallback(Identifier tagId) implements TimerCallback<MinecraftServer> {

    public static final MapCodec<FunctionTagCallback> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Identifier.CODEC.fieldOf("Name").forGetter(FunctionTagCallback::tagId)).apply(instance, FunctionTagCallback::new);
    });

    public void handle(MinecraftServer server, TimerQueue<MinecraftServer> queue, long time) {
        ServerFunctionManager serverfunctionmanager = server.getFunctions();

        for (CommandFunction<CommandSourceStack> commandfunction : serverfunctionmanager.getTag(this.tagId)) {
            serverfunctionmanager.execute(commandfunction, serverfunctionmanager.getGameLoopSender());
        }

    }

    @Override
    public MapCodec<FunctionTagCallback> codec() {
        return FunctionTagCallback.CODEC;
    }
}
