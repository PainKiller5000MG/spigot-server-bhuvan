package net.minecraft.gametest.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public abstract class TestFunctionLoader {

    private static final List<TestFunctionLoader> loaders = new ArrayList();

    public TestFunctionLoader() {}

    public static void registerLoader(TestFunctionLoader loader) {
        TestFunctionLoader.loaders.add(loader);
    }

    public static void runLoaders(Registry<Consumer<GameTestHelper>> registry) {
        for (TestFunctionLoader testfunctionloader : TestFunctionLoader.loaders) {
            testfunctionloader.load((resourcekey, consumer) -> {
                Registry.register(registry, resourcekey, consumer);
            });
        }

    }

    public abstract void load(BiConsumer<ResourceKey<Consumer<GameTestHelper>>, Consumer<GameTestHelper>> register);
}
