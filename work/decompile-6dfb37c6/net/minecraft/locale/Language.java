package net.minecraft.locale;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringDecomposer;
import org.slf4j.Logger;

public abstract class Language {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Pattern UNSUPPORTED_FORMAT_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");
    public static final String DEFAULT = "en_us";
    private static volatile Language instance = loadDefault();

    public Language() {}

    private static Language loadDefault() {
        DeprecatedTranslationsInfo deprecatedtranslationsinfo = DeprecatedTranslationsInfo.loadFromDefaultResource();
        Map<String, String> map = new HashMap();

        Objects.requireNonNull(map);
        BiConsumer<String, String> biconsumer = map::put;

        parseTranslations(biconsumer, "/assets/minecraft/lang/en_us.json");
        deprecatedtranslationsinfo.applyToMap(map);
        final Map<String, String> map1 = Map.copyOf(map);

        return new Language() {
            @Override
            public String getOrDefault(String elementId, String defaultValue) {
                return (String) map1.getOrDefault(elementId, defaultValue);
            }

            @Override
            public boolean has(String elementId) {
                return map1.containsKey(elementId);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText logicalOrderText) {
                return (formattedcharsink) -> {
                    return logicalOrderText.visit((style, s) -> {
                        return StringDecomposer.iterateFormatted(s, style, formattedcharsink) ? Optional.empty() : FormattedText.STOP_ITERATION;
                    }, Style.EMPTY).isPresent();
                };
            }
        };
    }

    private static void parseTranslations(BiConsumer<String, String> output, String path) {
        try (InputStream inputstream = Language.class.getResourceAsStream(path)) {
            loadFromJson(inputstream, output);
        } catch (JsonParseException | IOException ioexception) {
            Language.LOGGER.error("Couldn't read strings from {}", path, ioexception);
        }

    }

    public static void loadFromJson(InputStream stream, BiConsumer<String, String> output) {
        JsonObject jsonobject = (JsonObject) Language.GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);

        for (Map.Entry<String, JsonElement> map_entry : jsonobject.entrySet()) {
            String s = Language.UNSUPPORTED_FORMAT_PATTERN.matcher(GsonHelper.convertToString((JsonElement) map_entry.getValue(), (String) map_entry.getKey())).replaceAll("%$1s");

            output.accept((String) map_entry.getKey(), s);
        }

    }

    public static Language getInstance() {
        return Language.instance;
    }

    public static void inject(Language language) {
        Language.instance = language;
    }

    public String getOrDefault(String elementId) {
        return this.getOrDefault(elementId, elementId);
    }

    public abstract String getOrDefault(String elementId, String defaultValue);

    public abstract boolean has(String elementId);

    public abstract boolean isDefaultRightToLeft();

    public abstract FormattedCharSequence getVisualOrder(FormattedText logicalOrderText);

    public List<FormattedCharSequence> getVisualOrder(List<FormattedText> lines) {
        return (List) lines.stream().map(this::getVisualOrder).collect(ImmutableList.toImmutableList());
    }
}
