package net.minecraft.server.packs.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.jspecify.annotations.Nullable;

public abstract class PackDetector<T> {

    private final DirectoryValidator validator;

    protected PackDetector(DirectoryValidator validator) {
        this.validator = validator;
    }

    public @Nullable T detectPackResources(Path content, List<ForbiddenSymlinkInfo> issues) throws IOException {
        Path path1 = content;

        BasicFileAttributes basicfileattributes;

        try {
            basicfileattributes = Files.readAttributes(content, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException nosuchfileexception) {
            return null;
        }

        if (basicfileattributes.isSymbolicLink()) {
            this.validator.validateSymlink(content, issues);
            if (!issues.isEmpty()) {
                return null;
            }

            path1 = Files.readSymbolicLink(content);
            basicfileattributes = Files.readAttributes(path1, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }

        if (basicfileattributes.isDirectory()) {
            this.validator.validateKnownDirectory(path1, issues);
            return (T) (!issues.isEmpty() ? null : (!Files.isRegularFile(path1.resolve("pack.mcmeta"), new LinkOption[0]) ? null : this.createDirectoryPack(path1)));
        } else {
            return (T) (basicfileattributes.isRegularFile() && path1.getFileName().toString().endsWith(".zip") ? this.createZipPack(path1) : null);
        }
    }

    protected abstract @Nullable T createZipPack(Path content) throws IOException;

    protected abstract @Nullable T createDirectoryPack(Path content) throws IOException;
}
