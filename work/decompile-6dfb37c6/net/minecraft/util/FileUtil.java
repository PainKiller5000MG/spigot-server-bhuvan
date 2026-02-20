package net.minecraft.util;

import com.mojang.serialization.DataResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import org.apache.commons.io.FilenameUtils;

public class FileUtil {

    private static final Pattern COPY_COUNTER_PATTERN = Pattern.compile("(<name>.*) \\((<count>\\d*)\\)", 66);
    private static final int MAX_FILE_NAME = 255;
    private static final Pattern RESERVED_WINDOWS_FILENAMES = Pattern.compile(".*\\.|(?:COM|CLOCK\\$|CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?", 2);
    private static final Pattern STRICT_PATH_SEGMENT_CHECK = Pattern.compile("[-._a-z0-9]+");

    public FileUtil() {}

    public static String sanitizeName(String baseName) {
        for (char c0 : SharedConstants.ILLEGAL_FILE_CHARACTERS) {
            baseName = baseName.replace(c0, '_');
        }

        return baseName.replaceAll("[./\"]", "_");
    }

    public static String findAvailableName(Path baseDir, String baseName, String suffix) throws IOException {
        baseName = sanitizeName(baseName);
        if (!isPathPartPortable(baseName)) {
            baseName = "_" + baseName + "_";
        }

        Matcher matcher = FileUtil.COPY_COUNTER_PATTERN.matcher(baseName);
        int i = 0;

        if (matcher.matches()) {
            baseName = matcher.group("name");
            i = Integer.parseInt(matcher.group("count"));
        }

        if (baseName.length() > 255 - suffix.length()) {
            baseName = baseName.substring(0, 255 - suffix.length());
        }

        while (true) {
            String s2 = baseName;

            if (i != 0) {
                String s3 = " (" + i + ")";
                int j = 255 - s3.length();

                if (baseName.length() > j) {
                    s2 = baseName.substring(0, j);
                }

                s2 = s2 + s3;
            }

            s2 = s2 + suffix;
            Path path1 = baseDir.resolve(s2);

            try {
                Path path2 = Files.createDirectory(path1);

                Files.deleteIfExists(path2);
                return baseDir.relativize(path2).toString();
            } catch (FileAlreadyExistsException filealreadyexistsexception) {
                ++i;
            }
        }
    }

    public static boolean isPathNormalized(Path path) {
        Path path1 = path.normalize();

        return path1.equals(path);
    }

    public static boolean isPathPortable(Path path) {
        for (Path path1 : path) {
            if (!isPathPartPortable(path1.toString())) {
                return false;
            }
        }

        return true;
    }

    public static boolean isPathPartPortable(String name) {
        return !FileUtil.RESERVED_WINDOWS_FILENAMES.matcher(name).matches();
    }

    public static Path createPathToResource(Path resourceDirectory, String resource, String extension) {
        String s2 = resource + extension;
        Path path1 = Paths.get(s2);

        if (path1.endsWith(extension)) {
            throw new InvalidPathException(s2, "empty resource name");
        } else {
            return resourceDirectory.resolve(path1);
        }
    }

    public static String getFullResourcePath(String filename) {
        return FilenameUtils.getFullPath(filename).replace(File.separator, "/");
    }

    public static String normalizeResourcePath(String filename) {
        return FilenameUtils.normalize(filename).replace(File.separator, "/");
    }

    public static DataResult<List<String>> decomposePath(String path) {
        int i = path.indexOf(47);

        if (i == -1) {
            DataResult dataresult;

            switch (path) {
                case "":
                case ".":
                case "..":
                    dataresult = DataResult.error(() -> {
                        return "Invalid path '" + path + "'";
                    });
                    break;
                default:
                    dataresult = !containsAllowedCharactersOnly(path) ? DataResult.error(() -> {
                        return "Invalid path '" + path + "'";
                    }) : DataResult.success(List.of(path));
            }

            return dataresult;
        } else {
            List<String> list = new ArrayList();
            int j = 0;
            boolean flag = false;

            while (true) {
                switch (path.substring(j, i)) {
                    case "":
                    case ".":
                    case "..":
                        return DataResult.error(() -> {
                            return "Invalid segment '" + s1 + "' in path '" + path + "'";
                        });
                }

                if (!containsAllowedCharactersOnly(s1)) {
                    return DataResult.error(() -> {
                        return "Invalid segment '" + s1 + "' in path '" + path + "'";
                    });
                }

                list.add(s1);
                if (flag) {
                    return DataResult.success(list);
                }

                j = i + 1;
                i = path.indexOf(47, j);
                if (i == -1) {
                    i = path.length();
                    flag = true;
                }
            }
        }
    }

    public static Path resolvePath(Path root, List<String> segments) {
        int i = segments.size();
        Path path1;

        switch (i) {
            case 0:
                path1 = root;
                break;
            case 1:
                path1 = root.resolve((String) segments.get(0));
                break;
            default:
                String[] astring = new String[i - 1];

                for (int j = 1; j < i; ++j) {
                    astring[j - 1] = (String) segments.get(j);
                }

                path1 = root.resolve(root.getFileSystem().getPath((String) segments.get(0), astring));
        }

        return path1;
    }

    private static boolean containsAllowedCharactersOnly(String segment) {
        return FileUtil.STRICT_PATH_SEGMENT_CHECK.matcher(segment).matches();
    }

    public static boolean isValidPathSegment(String segment) {
        return !segment.equals("..") && !segment.equals(".") && containsAllowedCharactersOnly(segment);
    }

    public static void validatePath(String... path) {
        if (path.length == 0) {
            throw new IllegalArgumentException("Path must have at least one element");
        } else {
            for (String s : path) {
                if (!isValidPathSegment(s)) {
                    throw new IllegalArgumentException("Illegal segment " + s + " in path " + Arrays.toString(path));
                }
            }

        }
    }

    public static void createDirectoriesSafe(Path dir) throws IOException {
        Files.createDirectories(Files.exists(dir, new LinkOption[0]) ? dir.toRealPath() : dir);
    }
}
