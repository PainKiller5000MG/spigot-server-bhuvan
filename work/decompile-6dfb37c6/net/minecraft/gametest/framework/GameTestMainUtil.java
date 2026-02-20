package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.SuppressForbidden;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class GameTestMainUtil {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_UNIVERSE_DIR = "gametestserver";
    private static final String LEVEL_NAME = "gametestworld";
    private static final OptionParser parser = new OptionParser();
    private static final OptionSpec<String> universe = GameTestMainUtil.parser.accepts("universe", "The path to where the test server world will be created. Any existing folder will be replaced.").withRequiredArg().defaultsTo("gametestserver", new String[0]);
    private static final OptionSpec<File> report = GameTestMainUtil.parser.accepts("report", "Exports results in a junit-like XML report at the given path.").withRequiredArg().ofType(File.class);
    private static final OptionSpec<String> tests = GameTestMainUtil.parser.accepts("tests", "Which test(s) to run (namespaced ID selector using wildcards). Empty means run all.").withRequiredArg();
    private static final OptionSpec<Boolean> verify = GameTestMainUtil.parser.accepts("verify", "Runs the tests specified with `test` or `testNamespace` 100 times for each 90 degree rotation step").withRequiredArg().ofType(Boolean.class).defaultsTo(false, new Boolean[0]);
    private static final OptionSpec<String> packs = GameTestMainUtil.parser.accepts("packs", "A folder of datapacks to include in the world").withRequiredArg();
    private static final OptionSpec<Void> help = GameTestMainUtil.parser.accepts("help").forHelp();

    public GameTestMainUtil() {}

    @SuppressForbidden(reason = "Using System.err due to no bootstrap")
    public static void runGameTestServer(String[] args, Consumer<String> onUniverseCreated) throws Exception {
        GameTestMainUtil.parser.allowsUnrecognizedOptions();
        OptionSet optionset = GameTestMainUtil.parser.parse(args);

        if (optionset.has(GameTestMainUtil.help)) {
            GameTestMainUtil.parser.printHelpOn(System.err);
        } else {
            if ((Boolean) optionset.valueOf(GameTestMainUtil.verify) && !optionset.has(GameTestMainUtil.tests)) {
                GameTestMainUtil.LOGGER.error("Please specify a test selection to run the verify option. For example: --verify --tests example:test_something_*");
                System.exit(-1);
            }

            GameTestMainUtil.LOGGER.info("Running GameTestMain with cwd '{}', universe path '{}'", System.getProperty("user.dir"), optionset.valueOf(GameTestMainUtil.universe));
            if (optionset.has(GameTestMainUtil.report)) {
                GlobalTestReporter.replaceWith(new JUnitLikeTestReporter((File) GameTestMainUtil.report.value(optionset)));
            }

            Bootstrap.bootStrap();
            Util.startTimerHackThread();
            String s = (String) optionset.valueOf(GameTestMainUtil.universe);

            createOrResetDir(s);
            onUniverseCreated.accept(s);
            if (optionset.has(GameTestMainUtil.packs)) {
                String s1 = (String) optionset.valueOf(GameTestMainUtil.packs);

                copyPacks(s, s1);
            }

            LevelStorageSource.LevelStorageAccess levelstoragesource_levelstorageaccess = LevelStorageSource.createDefault(Paths.get(s)).createAccess("gametestworld");
            PackRepository packrepository = ServerPacksSource.createPackRepository(levelstoragesource_levelstorageaccess);

            MinecraftServer.spin((thread) -> {
                return GameTestServer.create(thread, levelstoragesource_levelstorageaccess, packrepository, optionalFromOption(optionset, GameTestMainUtil.tests), optionset.has(GameTestMainUtil.verify));
            });
        }
    }

    private static Optional<String> optionalFromOption(OptionSet options, OptionSpec<String> option) {
        return options.has(option) ? Optional.of((String) options.valueOf(option)) : Optional.empty();
    }

    private static void createOrResetDir(String universePath) throws IOException {
        Path path = Paths.get(universePath);

        if (Files.exists(path, new LinkOption[0])) {
            FileUtils.deleteDirectory(path.toFile());
        }

        Files.createDirectories(path);
    }

    private static void copyPacks(String serverPath, String packSourcePath) throws IOException {
        Path path = Paths.get(serverPath).resolve("gametestworld").resolve("datapacks");

        if (!Files.exists(path, new LinkOption[0])) {
            Files.createDirectories(path);
        }

        Path path1 = Paths.get(packSourcePath);

        if (Files.exists(path1, new LinkOption[0])) {
            try (Stream<Path> stream = Files.list(path1)) {
                for (Path path2 : stream.toList()) {
                    Path path3 = path.resolve(path2.getFileName());

                    if (Files.isDirectory(path2, new LinkOption[0])) {
                        if (Files.isRegularFile(path2.resolve("pack.mcmeta"), new LinkOption[0])) {
                            FileUtils.copyDirectory(path2.toFile(), path3.toFile());
                            GameTestMainUtil.LOGGER.info("Included folder pack {}", path2.getFileName());
                        }
                    } else if (path2.toString().endsWith(".zip")) {
                        Files.copy(path2, path3);
                        GameTestMainUtil.LOGGER.info("Included zip pack {}", path2.getFileName());
                    }
                }
            }
        }

    }
}
