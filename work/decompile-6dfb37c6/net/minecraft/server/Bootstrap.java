package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;

@SuppressForbidden(reason = "System.out setup")
public class Bootstrap {

    public static final PrintStream STDOUT = System.out;
    private static volatile boolean isBootstrapped;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final AtomicLong bootstrapDuration = new AtomicLong(-1L);

    public Bootstrap() {}

    public static void bootStrap() {
        if (!Bootstrap.isBootstrapped) {
            Bootstrap.isBootstrapped = true;
            Instant instant = Instant.now();

            if (BuiltInRegistries.REGISTRY.keySet().isEmpty()) {
                throw new IllegalStateException("Unable to load registries");
            } else {
                FireBlock.bootStrap();
                ComposterBlock.bootStrap();
                if (EntityType.getKey(EntityType.PLAYER) == null) {
                    throw new IllegalStateException("Failed loading EntityTypes");
                } else {
                    EntitySelectorOptions.bootStrap();
                    DispenseItemBehavior.bootStrap();
                    CauldronInteraction.bootStrap();
                    BuiltInRegistries.bootStrap();
                    CreativeModeTabs.validate();
                    wrapStreams();
                    Bootstrap.bootstrapDuration.set(Duration.between(instant, Instant.now()).toMillis());
                }
            }
        }
    }

    private static <T> void checkTranslations(Iterable<T> registry, Function<T, String> descriptionGetter, Set<String> output) {
        Language language = Language.getInstance();

        registry.forEach((object) -> {
            String s = (String) descriptionGetter.apply(object);

            if (!language.has(s)) {
                output.add(s);
            }

        });
    }

    private static void checkGameruleTranslations(final Set<String> missing) {
        final Language language = Language.getInstance();
        GameRules gamerules = new GameRules(FeatureFlags.REGISTRY.allFlags());

        gamerules.visitGameRuleTypes(new GameRuleTypeVisitor() {
            @Override
            public <T> void visit(GameRule<T> gameRule) {
                if (!language.has(gameRule.getDescriptionId())) {
                    missing.add(gameRule.id());
                }

            }
        });
    }

    public static Set<String> getMissingTranslations() {
        Set<String> set = new TreeSet();

        checkTranslations(BuiltInRegistries.ATTRIBUTE, Attribute::getDescriptionId, set);
        checkTranslations(BuiltInRegistries.ENTITY_TYPE, EntityType::getDescriptionId, set);
        checkTranslations(BuiltInRegistries.MOB_EFFECT, MobEffect::getDescriptionId, set);
        checkTranslations(BuiltInRegistries.ITEM, Item::getDescriptionId, set);
        checkTranslations(BuiltInRegistries.BLOCK, BlockBehaviour::getDescriptionId, set);
        checkTranslations(BuiltInRegistries.CUSTOM_STAT, (identifier) -> {
            String s = identifier.toString();

            return "stat." + s.replace(':', '.');
        }, set);
        checkGameruleTranslations(set);
        return set;
    }

    public static void checkBootstrapCalled(Supplier<String> location) {
        if (!Bootstrap.isBootstrapped) {
            throw createBootstrapException(location);
        }
    }

    private static RuntimeException createBootstrapException(Supplier<String> location) {
        try {
            String s = (String) location.get();

            return new IllegalArgumentException("Not bootstrapped (called from " + s + ")");
        } catch (Exception exception) {
            RuntimeException runtimeexception = new IllegalArgumentException("Not bootstrapped (failed to resolve location)");

            runtimeexception.addSuppressed(exception);
            return runtimeexception;
        }
    }

    public static void validate() {
        checkBootstrapCalled(() -> {
            return "validate";
        });
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            getMissingTranslations().forEach((s) -> {
                Bootstrap.LOGGER.error("Missing translations: {}", s);
            });
            Commands.validate();
        }

        DefaultAttributes.validate();
    }

    private static void wrapStreams() {
        if (Bootstrap.LOGGER.isDebugEnabled()) {
            System.setErr(new DebugLoggedPrintStream("STDERR", System.err));
            System.setOut(new DebugLoggedPrintStream("STDOUT", Bootstrap.STDOUT));
        } else {
            System.setErr(new LoggedPrintStream("STDERR", System.err));
            System.setOut(new LoggedPrintStream("STDOUT", Bootstrap.STDOUT));
        }

    }

    public static void realStdoutPrintln(String string) {
        Bootstrap.STDOUT.println(string);
    }
}
