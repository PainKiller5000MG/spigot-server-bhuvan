package net.minecraft.world.level.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public class PlayerDataStorage {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final File playerDir;
    protected final DataFixer fixerUpper;

    public PlayerDataStorage(LevelStorageSource.LevelStorageAccess levelAccess, DataFixer fixerUpper) {
        this.fixerUpper = fixerUpper;
        this.playerDir = levelAccess.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
        this.playerDir.mkdirs();
    }

    public void save(Player player) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(player.problemPath(), PlayerDataStorage.LOGGER)) {
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, player.registryAccess());

            player.saveWithoutId(tagvalueoutput);
            Path path = this.playerDir.toPath();
            Path path1 = Files.createTempFile(path, player.getStringUUID() + "-", ".dat");
            CompoundTag compoundtag = tagvalueoutput.buildResult();

            NbtIo.writeCompressed(compoundtag, path1);
            Path path2 = path.resolve(player.getStringUUID() + ".dat");
            Path path3 = path.resolve(player.getStringUUID() + ".dat_old");

            Util.safeReplaceFile(path2, path1, path3);
        } catch (Exception exception) {
            PlayerDataStorage.LOGGER.warn("Failed to save player data for {}", player.getPlainTextName());
        }

    }

    private void backup(NameAndId nameAndId, String suffix) {
        Path path = this.playerDir.toPath();
        String s1 = nameAndId.id().toString();
        Path path1 = path.resolve(s1 + suffix);
        Path path2 = path.resolve(s1 + "_corrupted_" + ZonedDateTime.now().format(FileNameDateFormatter.FORMATTER) + suffix);

        if (Files.isRegularFile(path1, new LinkOption[0])) {
            try {
                Files.copy(path1, path2, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception exception) {
                PlayerDataStorage.LOGGER.warn("Failed to copy the player.dat file for {}", nameAndId.name(), exception);
            }

        }
    }

    private Optional<CompoundTag> load(NameAndId nameAndId, String suffix) {
        File file = this.playerDir;
        String s1 = String.valueOf(nameAndId.id());
        File file1 = new File(file, s1 + suffix);

        if (file1.exists() && file1.isFile()) {
            try {
                return Optional.of(NbtIo.readCompressed(file1.toPath(), NbtAccounter.unlimitedHeap()));
            } catch (Exception exception) {
                PlayerDataStorage.LOGGER.warn("Failed to load player data for {}", nameAndId.name());
            }
        }

        return Optional.empty();
    }

    public Optional<CompoundTag> load(NameAndId nameAndId) {
        Optional<CompoundTag> optional = this.load(nameAndId, ".dat");

        if (optional.isEmpty()) {
            this.backup(nameAndId, ".dat");
        }

        return optional.or(() -> {
            return this.load(nameAndId, ".dat_old");
        }).map((compoundtag) -> {
            int i = NbtUtils.getDataVersion(compoundtag);

            compoundtag = DataFixTypes.PLAYER.updateToCurrentVersion(this.fixerUpper, compoundtag, i);
            return compoundtag;
        });
    }
}
