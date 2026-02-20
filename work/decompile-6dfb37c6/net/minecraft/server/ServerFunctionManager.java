package net.minecraft.server;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ServerFunctionManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier TICK_FUNCTION_TAG = Identifier.withDefaultNamespace("tick");
    private static final Identifier LOAD_FUNCTION_TAG = Identifier.withDefaultNamespace("load");
    private final MinecraftServer server;
    private List<CommandFunction<CommandSourceStack>> ticking = ImmutableList.of();
    private boolean postReload;
    private ServerFunctionLibrary library;

    public ServerFunctionManager(MinecraftServer server, ServerFunctionLibrary library) {
        this.server = server;
        this.library = library;
        this.postReload(library);
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.server.getCommands().getDispatcher();
    }

    public void tick() {
        if (this.server.tickRateManager().runsNormally()) {
            if (this.postReload) {
                this.postReload = false;
                Collection<CommandFunction<CommandSourceStack>> collection = this.library.getTag(ServerFunctionManager.LOAD_FUNCTION_TAG);

                this.executeTagFunctions(collection, ServerFunctionManager.LOAD_FUNCTION_TAG);
            }

            this.executeTagFunctions(this.ticking, ServerFunctionManager.TICK_FUNCTION_TAG);
        }
    }

    private void executeTagFunctions(Collection<CommandFunction<CommandSourceStack>> functions, Identifier loadFunctionTag) {
        ProfilerFiller profilerfiller = Profiler.get();

        Objects.requireNonNull(loadFunctionTag);
        profilerfiller.push(loadFunctionTag::toString);

        for (CommandFunction<CommandSourceStack> commandfunction : functions) {
            this.execute(commandfunction, this.getGameLoopSender());
        }

        Profiler.get().pop();
    }

    public void execute(CommandFunction<CommandSourceStack> functionIn, CommandSourceStack sender) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push(() -> {
            return "function " + String.valueOf(functionIn.id());
        });

        try {
            InstantiatedFunction<CommandSourceStack> instantiatedfunction = functionIn.instantiate((CompoundTag) null, this.getDispatcher());

            Commands.executeCommandInContext(sender, (executioncontext) -> {
                ExecutionContext.queueInitialFunctionCall(executioncontext, instantiatedfunction, sender, CommandResultCallback.EMPTY);
            });
        } catch (FunctionInstantiationException functioninstantiationexception) {
            ;
        } catch (Exception exception) {
            ServerFunctionManager.LOGGER.warn("Failed to execute function {}", functionIn.id(), exception);
        } finally {
            profilerfiller.pop();
        }

    }

    public void replaceLibrary(ServerFunctionLibrary library) {
        this.library = library;
        this.postReload(library);
    }

    private void postReload(ServerFunctionLibrary library) {
        this.ticking = List.copyOf(library.getTag(ServerFunctionManager.TICK_FUNCTION_TAG));
        this.postReload = true;
    }

    public CommandSourceStack getGameLoopSender() {
        return this.server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER).withSuppressedOutput();
    }

    public Optional<CommandFunction<CommandSourceStack>> get(Identifier id) {
        return this.library.getFunction(id);
    }

    public List<CommandFunction<CommandSourceStack>> getTag(Identifier id) {
        return this.library.getTag(id);
    }

    public Iterable<Identifier> getFunctionNames() {
        return this.library.getFunctions().keySet();
    }

    public Iterable<Identifier> getTagNames() {
        return this.library.getAvailableTags();
    }
}
