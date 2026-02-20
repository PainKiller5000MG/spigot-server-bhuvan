package net.minecraft.resources;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.function.UnaryOperator;
import net.minecraft.IdentifierException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

public final class Identifier implements Comparable<Identifier> {

    public static final Codec<Identifier> CODEC = Codec.STRING.comapFlatMap(Identifier::read, Identifier::toString).stable();
    public static final StreamCodec<ByteBuf, Identifier> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Identifier::parse, Identifier::toString);
    public static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.id.invalid"));
    public static final char NAMESPACE_SEPARATOR = ':';
    public static final String DEFAULT_NAMESPACE = "minecraft";
    public static final String REALMS_NAMESPACE = "realms";
    private final String namespace;
    private final String path;

    private Identifier(String namespace, String path) {
        assert isValidNamespace(namespace);

        assert isValidPath(path);

        this.namespace = namespace;
        this.path = path;
    }

    private static Identifier createUntrusted(String namespace, String path) {
        return new Identifier(assertValidNamespace(namespace, path), assertValidPath(namespace, path));
    }

    public static Identifier fromNamespaceAndPath(String namespace, String path) {
        return createUntrusted(namespace, path);
    }

    public static Identifier parse(String identifier) {
        return bySeparator(identifier, ':');
    }

    public static Identifier withDefaultNamespace(String path) {
        return new Identifier("minecraft", assertValidPath("minecraft", path));
    }

    public static @Nullable Identifier tryParse(String identifier) {
        return tryBySeparator(identifier, ':');
    }

    public static @Nullable Identifier tryBuild(String namespace, String path) {
        return isValidNamespace(namespace) && isValidPath(path) ? new Identifier(namespace, path) : null;
    }

    public static Identifier bySeparator(String identifier, char separator) {
        int i = identifier.indexOf(separator);

        if (i >= 0) {
            String s1 = identifier.substring(i + 1);

            if (i != 0) {
                String s2 = identifier.substring(0, i);

                return createUntrusted(s2, s1);
            } else {
                return withDefaultNamespace(s1);
            }
        } else {
            return withDefaultNamespace(identifier);
        }
    }

    public static @Nullable Identifier tryBySeparator(String identifier, char separator) {
        int i = identifier.indexOf(separator);

        if (i >= 0) {
            String s1 = identifier.substring(i + 1);

            if (!isValidPath(s1)) {
                return null;
            } else if (i != 0) {
                String s2 = identifier.substring(0, i);

                return isValidNamespace(s2) ? new Identifier(s2, s1) : null;
            } else {
                return new Identifier("minecraft", s1);
            }
        } else {
            return isValidPath(identifier) ? new Identifier("minecraft", identifier) : null;
        }
    }

    public static DataResult<Identifier> read(String input) {
        try {
            return DataResult.success(parse(input));
        } catch (IdentifierException identifierexception) {
            return DataResult.error(() -> {
                return "Not a valid resource location: " + input + " " + identifierexception.getMessage();
            });
        }
    }

    public String getPath() {
        return this.path;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public Identifier withPath(String newPath) {
        return new Identifier(this.namespace, assertValidPath(this.namespace, newPath));
    }

    public Identifier withPath(UnaryOperator<String> modifier) {
        return this.withPath((String) modifier.apply(this.path));
    }

    public Identifier withPrefix(String prefix) {
        return this.withPath(prefix + this.path);
    }

    public Identifier withSuffix(String suffix) {
        return this.withPath(this.path + suffix);
    }

    public String toString() {
        return this.namespace + ":" + this.path;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Identifier)) {
            return false;
        } else {
            Identifier identifier = (Identifier) o;

            return this.namespace.equals(identifier.namespace) && this.path.equals(identifier.path);
        }
    }

    public int hashCode() {
        return 31 * this.namespace.hashCode() + this.path.hashCode();
    }

    public int compareTo(Identifier o) {
        int i = this.path.compareTo(o.path);

        if (i == 0) {
            i = this.namespace.compareTo(o.namespace);
        }

        return i;
    }

    public String toDebugFileName() {
        return this.toString().replace('/', '_').replace(':', '_');
    }

    public String toLanguageKey() {
        return this.namespace + "." + this.path;
    }

    public String toShortLanguageKey() {
        return this.namespace.equals("minecraft") ? this.path : this.toLanguageKey();
    }

    public String toShortString() {
        return this.namespace.equals("minecraft") ? this.path : this.toString();
    }

    public String toLanguageKey(String prefix) {
        return prefix + "." + this.toLanguageKey();
    }

    public String toLanguageKey(String prefix, String suffix) {
        return prefix + "." + this.toLanguageKey() + "." + suffix;
    }

    private static String readGreedy(StringReader reader) {
        int i = reader.getCursor();

        while (reader.canRead() && isAllowedInIdentifier(reader.peek())) {
            reader.skip();
        }

        return reader.getString().substring(i, reader.getCursor());
    }

    public static Identifier read(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        String s = readGreedy(reader);

        try {
            return parse(s);
        } catch (IdentifierException identifierexception) {
            reader.setCursor(i);
            throw Identifier.ERROR_INVALID.createWithContext(reader);
        }
    }

    public static Identifier readNonEmpty(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        String s = readGreedy(reader);

        if (s.isEmpty()) {
            throw Identifier.ERROR_INVALID.createWithContext(reader);
        } else {
            try {
                return parse(s);
            } catch (IdentifierException identifierexception) {
                reader.setCursor(i);
                throw Identifier.ERROR_INVALID.createWithContext(reader);
            }
        }
    }

    public static boolean isAllowedInIdentifier(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c == '_' || c == ':' || c == '/' || c == '.' || c == '-';
    }

    public static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); ++i) {
            if (!validPathChar(path.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidNamespace(String namespace) {
        for (int i = 0; i < namespace.length(); ++i) {
            if (!validNamespaceChar(namespace.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static String assertValidNamespace(String namespace, String path) {
        if (!isValidNamespace(namespace)) {
            throw new IdentifierException("Non [a-z0-9_.-] character in namespace of location: " + namespace + ":" + path);
        } else {
            return namespace;
        }
    }

    public static boolean validPathChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' || c == '.';
    }

    private static boolean validNamespaceChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.';
    }

    private static String assertValidPath(String namespace, String path) {
        if (!isValidPath(path)) {
            throw new IdentifierException("Non [a-z0-9/._-] character in path of location: " + namespace + ":" + path);
        } else {
            return path;
        }
    }
}
