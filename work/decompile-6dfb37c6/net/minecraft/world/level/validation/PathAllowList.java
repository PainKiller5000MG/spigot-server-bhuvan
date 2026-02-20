package net.minecraft.world.level.validation;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public class PathAllowList implements PathMatcher {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COMMENT_PREFIX = "#";
    private final List<PathAllowList.ConfigEntry> entries;
    private final Map<String, PathMatcher> compiledPaths = new ConcurrentHashMap();

    public PathAllowList(List<PathAllowList.ConfigEntry> entries) {
        this.entries = entries;
    }

    public PathMatcher getForFileSystem(FileSystem fileSystem) {
        return (PathMatcher) this.compiledPaths.computeIfAbsent(fileSystem.provider().getScheme(), (s) -> {
            List<PathMatcher> list;

            try {
                list = this.entries.stream().map((pathallowlist_configentry) -> {
                    return pathallowlist_configentry.compile(fileSystem);
                }).toList();
            } catch (Exception exception) {
                PathAllowList.LOGGER.error("Failed to compile file pattern list", exception);
                return (path) -> {
                    return false;
                };
            }

            PathMatcher pathmatcher;

            switch (list.size()) {
                case 0:
                    pathmatcher = (path) -> {
                        return false;
                    };
                    break;
                case 1:
                    pathmatcher = (PathMatcher) list.get(0);
                    break;
                default:
                    pathmatcher = (path) -> {
                        for (PathMatcher pathmatcher1 : list) {
                            if (pathmatcher1.matches(path)) {
                                return true;
                            }
                        }

                        return false;
                    };
            }

            return pathmatcher;
        });
    }

    public boolean matches(Path path) {
        return this.getForFileSystem(path.getFileSystem()).matches(path);
    }

    public static PathAllowList readPlain(BufferedReader reader) {
        return new PathAllowList(reader.lines().flatMap((s) -> {
            return PathAllowList.ConfigEntry.parse(s).stream();
        }).toList());
    }

    @FunctionalInterface
    public interface EntryType {

        PathAllowList.EntryType FILESYSTEM = FileSystem::getPathMatcher;
        PathAllowList.EntryType PREFIX = (filesystem, s) -> {
            return (path) -> {
                return path.toString().startsWith(s);
            };
        };

        PathMatcher compile(FileSystem fileSystem, String pattern);
    }

    public static record ConfigEntry(PathAllowList.EntryType type, String pattern) {

        public PathMatcher compile(FileSystem fileSystem) {
            return this.type().compile(fileSystem, this.pattern);
        }

        static Optional<PathAllowList.ConfigEntry> parse(String definition) {
            if (!definition.isBlank() && !definition.startsWith("#")) {
                if (!definition.startsWith("[")) {
                    return Optional.of(new PathAllowList.ConfigEntry(PathAllowList.EntryType.PREFIX, definition));
                } else {
                    int i = definition.indexOf(93, 1);

                    if (i == -1) {
                        throw new IllegalArgumentException("Unterminated type in line '" + definition + "'");
                    } else {
                        String s1 = definition.substring(1, i);
                        String s2 = definition.substring(i + 1);
                        Optional optional;

                        switch (s1) {
                            case "glob":
                            case "regex":
                                optional = Optional.of(new PathAllowList.ConfigEntry(PathAllowList.EntryType.FILESYSTEM, s1 + ":" + s2));
                                break;
                            case "prefix":
                                optional = Optional.of(new PathAllowList.ConfigEntry(PathAllowList.EntryType.PREFIX, s2));
                                break;
                            default:
                                throw new IllegalArgumentException("Unsupported definition type in line '" + definition + "'");
                        }

                        return optional;
                    }
                }
            } else {
                return Optional.empty();
            }
        }

        static PathAllowList.ConfigEntry glob(String pattern) {
            return new PathAllowList.ConfigEntry(PathAllowList.EntryType.FILESYSTEM, "glob:" + pattern);
        }

        static PathAllowList.ConfigEntry regex(String pattern) {
            return new PathAllowList.ConfigEntry(PathAllowList.EntryType.FILESYSTEM, "regex:" + pattern);
        }

        static PathAllowList.ConfigEntry prefix(String pattern) {
            return new PathAllowList.ConfigEntry(PathAllowList.EntryType.PREFIX, pattern);
        }
    }
}
