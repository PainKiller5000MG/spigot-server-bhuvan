package net.minecraft.server.dialog.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.functions.StringTemplate;

public class ParsedTemplate {

    public static final Codec<ParsedTemplate> CODEC = Codec.STRING.comapFlatMap(ParsedTemplate::parse, (parsedtemplate) -> {
        return parsedtemplate.raw;
    });
    public static final Codec<String> VARIABLE_CODEC = Codec.STRING.validate((s) -> {
        return StringTemplate.isValidVariableName(s) ? DataResult.success(s) : DataResult.error(() -> {
            return s + " is not a valid input name";
        });
    });
    private final String raw;
    private final StringTemplate parsed;

    private ParsedTemplate(String raw, StringTemplate parsed) {
        this.raw = raw;
        this.parsed = parsed;
    }

    private static DataResult<ParsedTemplate> parse(String value) {
        StringTemplate stringtemplate;

        try {
            stringtemplate = StringTemplate.fromString(value);
        } catch (Exception exception) {
            return DataResult.error(() -> {
                return "Failed to parse template " + value + ": " + exception.getMessage();
            });
        }

        return DataResult.success(new ParsedTemplate(value, stringtemplate));
    }

    public String instantiate(Map<String, String> arguments) {
        List<String> list = this.parsed.variables().stream().map((s) -> {
            return (String) arguments.getOrDefault(s, "");
        }).toList();

        return this.parsed.substitute(list);
    }
}
