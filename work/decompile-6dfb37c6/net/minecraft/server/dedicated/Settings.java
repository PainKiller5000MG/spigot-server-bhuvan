package net.minecraft.server.dedicated;

import com.google.common.base.MoreObjects;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class Settings<T extends Settings<T>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    public final Properties properties;

    public Settings(Properties properties) {
        this.properties = properties;
    }

    public static Properties loadFromFile(Path file) {
        try {
            try (InputStream inputstream = Files.newInputStream(file)) {
                CharsetDecoder charsetdecoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
                Properties properties = new Properties();

                properties.load(new InputStreamReader(inputstream, charsetdecoder));
                return properties;
            } catch (CharacterCodingException charactercodingexception) {
                Settings.LOGGER.info("Failed to load properties as UTF-8 from file {}, trying ISO_8859_1", file);

                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
                    Properties properties1 = new Properties();

                    properties1.load(reader);
                    return properties1;
                }
            }
        } catch (IOException ioexception) {
            Settings.LOGGER.error("Failed to load properties from file: {}", file, ioexception);
            return new Properties();
        }
    }

    public void store(Path output) {
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            this.properties.store(writer, "Minecraft server properties");
        } catch (IOException ioexception) {
            Settings.LOGGER.error("Failed to store properties to file: {}", output);
        }

    }

    private static <V extends Number> Function<String, @Nullable V> wrapNumberDeserializer(Function<String, V> inner) {
        return (s) -> {
            try {
                return (Number) inner.apply(s);
            } catch (NumberFormatException numberformatexception) {
                return null;
            }
        };
    }

    protected static <V> Function<String, @Nullable V> dispatchNumberOrString(IntFunction<@Nullable V> intDeserializer, Function<String, @Nullable V> stringDeserializer) {
        return (s) -> {
            try {
                return intDeserializer.apply(Integer.parseInt(s));
            } catch (NumberFormatException numberformatexception) {
                return stringDeserializer.apply(s);
            }
        };
    }

    private @Nullable String getStringRaw(String key) {
        return (String) this.properties.get(key);
    }

    protected <V> @Nullable V getLegacy(String key, Function<String, V> deserializer) {
        String s1 = this.getStringRaw(key);

        if (s1 == null) {
            return null;
        } else {
            this.properties.remove(key);
            return (V) deserializer.apply(s1);
        }
    }

    protected <V> V get(String key, Function<String, @Nullable V> deserializer, Function<V, String> serializer, V defaultValue) {
        String s1 = this.getStringRaw(key);
        V v1 = (V) MoreObjects.firstNonNull(s1 != null ? deserializer.apply(s1) : null, defaultValue);

        this.properties.put(key, serializer.apply(v1));
        return v1;
    }

    protected <V> Settings<T>.MutableValue<V> getMutable(String key, Function<String, @Nullable V> deserializer, Function<V, String> serializer, V defaultValue) {
        String s1 = this.getStringRaw(key);
        V v1 = (V) MoreObjects.firstNonNull(s1 != null ? deserializer.apply(s1) : null, defaultValue);

        this.properties.put(key, serializer.apply(v1));
        return new Settings.MutableValue<V>(key, v1, serializer);
    }

    protected <V> V get(String key, Function<String, @Nullable V> deserializer, UnaryOperator<V> validator, Function<V, String> serializer, V defaultValue) {
        return (V) this.get(key, (s1) -> {
            V v1 = (V) deserializer.apply(s1);

            return v1 != null ? validator.apply(v1) : null;
        }, serializer, defaultValue);
    }

    protected <V> V get(String key, Function<String, V> deserializer, V defaultValue) {
        return (V) this.get(key, deserializer, Objects::toString, defaultValue);
    }

    protected <V> Settings<T>.MutableValue<V> getMutable(String key, Function<String, V> deserializer, V defaultValue) {
        return this.<V>getMutable(key, deserializer, Objects::toString, defaultValue);
    }

    protected String get(String key, String defaultValue) {
        return (String) this.get(key, Function.identity(), Function.identity(), defaultValue);
    }

    protected @Nullable String getLegacyString(String key) {
        return (String) this.getLegacy(key, Function.identity());
    }

    protected int get(String key, int defaultValue) {
        return (Integer) this.get(key, wrapNumberDeserializer(Integer::parseInt), defaultValue);
    }

    protected Settings<T>.MutableValue<Integer> getMutable(String key, int defaultValue) {
        return this.<Integer>getMutable(key, wrapNumberDeserializer(Integer::parseInt), defaultValue);
    }

    protected Settings<T>.MutableValue<String> getMutable(String key, String defaultValue) {
        return this.<String>getMutable(key, String::new, defaultValue);
    }

    protected int get(String key, UnaryOperator<Integer> validator, int defaultValue) {
        return (Integer) this.get(key, wrapNumberDeserializer(Integer::parseInt), validator, Objects::toString, defaultValue);
    }

    protected long get(String key, long defaultValue) {
        return (Long) this.get(key, wrapNumberDeserializer(Long::parseLong), defaultValue);
    }

    protected boolean get(String key, boolean defaultValue) {
        return (Boolean) this.get(key, Boolean::valueOf, defaultValue);
    }

    protected Settings<T>.MutableValue<Boolean> getMutable(String key, boolean defaultValue) {
        return this.<Boolean>getMutable(key, Boolean::valueOf, defaultValue);
    }

    protected @Nullable Boolean getLegacyBoolean(String key) {
        return (Boolean) this.getLegacy(key, Boolean::valueOf);
    }

    protected Properties cloneProperties() {
        Properties properties = new Properties();

        properties.putAll(this.properties);
        return properties;
    }

    protected abstract T reload(RegistryAccess registryAccess, Properties properties);

    public class MutableValue<V> implements Supplier<V> {

        private final String key;
        private final V value;
        private final Function<V, String> serializer;

        private MutableValue(String key, V value, Function<V, String> serializer) {
            this.key = key;
            this.value = value;
            this.serializer = serializer;
        }

        public V get() {
            return this.value;
        }

        public T update(RegistryAccess registryAccess, V value) {
            Properties properties = Settings.this.cloneProperties();

            properties.put(this.key, this.serializer.apply(value));
            return (T) Settings.this.reload(registryAccess, properties);
        }
    }
}
