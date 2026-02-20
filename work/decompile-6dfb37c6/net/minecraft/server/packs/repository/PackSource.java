package net.minecraft.server.packs.repository;

import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public interface PackSource {

    UnaryOperator<Component> NO_DECORATION = UnaryOperator.identity();
    PackSource DEFAULT = create(PackSource.NO_DECORATION, true);
    PackSource BUILT_IN = create(decorateWithSource("pack.source.builtin"), true);
    PackSource FEATURE = create(decorateWithSource("pack.source.feature"), false);
    PackSource WORLD = create(decorateWithSource("pack.source.world"), true);
    PackSource SERVER = create(decorateWithSource("pack.source.server"), true);

    Component decorate(Component packDescription);

    boolean shouldAddAutomatically();

    static PackSource create(final UnaryOperator<Component> decorator, final boolean addAutomatically) {
        return new PackSource() {
            @Override
            public Component decorate(Component packDescription) {
                return (Component) decorator.apply(packDescription);
            }

            @Override
            public boolean shouldAddAutomatically() {
                return addAutomatically;
            }
        };
    }

    private static UnaryOperator<Component> decorateWithSource(String descriptionId) {
        Component component = Component.translatable(descriptionId);

        return (component1) -> {
            return Component.translatable("pack.nameAndSource", component1, component).withStyle(ChatFormatting.GRAY);
        };
    }
}
