package net.minecraft.commands.arguments.selector;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public record SelectorPattern(String pattern, EntitySelector resolved) {

    public static final Codec<SelectorPattern> CODEC = Codec.STRING.comapFlatMap(SelectorPattern::parse, SelectorPattern::pattern);

    public static DataResult<SelectorPattern> parse(String pattern) {
        try {
            EntitySelectorParser entityselectorparser = new EntitySelectorParser(new StringReader(pattern), true);

            return DataResult.success(new SelectorPattern(pattern, entityselectorparser.parse()));
        } catch (CommandSyntaxException commandsyntaxexception) {
            return DataResult.error(() -> {
                return "Invalid selector component: " + pattern + ": " + commandsyntaxexception.getMessage();
            });
        }
    }

    public boolean equals(Object obj) {
        boolean flag;

        if (obj instanceof SelectorPattern selectorpattern) {
            if (this.pattern.equals(selectorpattern.pattern)) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    public int hashCode() {
        return this.pattern.hashCode();
    }

    public String toString() {
        return this.pattern;
    }
}
