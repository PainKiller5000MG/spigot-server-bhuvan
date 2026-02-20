package net.minecraft.world.level.validation;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class DirectoryValidator {

    private final PathMatcher symlinkTargetAllowList;

    public DirectoryValidator(PathMatcher symlinkTargetAllowList) {
        this.symlinkTargetAllowList = symlinkTargetAllowList;
    }

    public void validateSymlink(Path path, List<ForbiddenSymlinkInfo> issues) throws IOException {
        Path path1 = Files.readSymbolicLink(path);

        if (!this.symlinkTargetAllowList.matches(path1)) {
            issues.add(new ForbiddenSymlinkInfo(path, path1));
        }

    }

    public List<ForbiddenSymlinkInfo> validateSymlink(Path path) throws IOException {
        List<ForbiddenSymlinkInfo> list = new ArrayList();

        this.validateSymlink(path, list);
        return list;
    }

    public List<ForbiddenSymlinkInfo> validateDirectory(Path directory, boolean allowTopSymlink) throws IOException {
        List<ForbiddenSymlinkInfo> list = new ArrayList();

        BasicFileAttributes basicfileattributes;

        try {
            basicfileattributes = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException nosuchfileexception) {
            return list;
        }

        if (basicfileattributes.isRegularFile()) {
            throw new IOException("Path " + String.valueOf(directory) + " is not a directory");
        } else {
            if (basicfileattributes.isSymbolicLink()) {
                if (!allowTopSymlink) {
                    this.validateSymlink(directory, list);
                    return list;
                }

                directory = Files.readSymbolicLink(directory);
            }

            this.validateKnownDirectory(directory, list);
            return list;
        }
    }

    public void validateKnownDirectory(Path directory, final List<ForbiddenSymlinkInfo> issues) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            private void validateSymlink(Path path, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) {
                    DirectoryValidator.this.validateSymlink(path, issues);
                }

            }

            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                this.validateSymlink(dir, attrs);
                return super.preVisitDirectory(dir, attrs);
            }

            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                this.validateSymlink(file, attrs);
                return super.visitFile(file, attrs);
            }
        });
    }
}
