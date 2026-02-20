package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType.StringType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;

public class StringArgumentSerializer implements ArgumentTypeInfo<StringArgumentType, StringArgumentSerializer.Template> {

    public StringArgumentSerializer() {}

    public void serializeToNetwork(StringArgumentSerializer.Template template, FriendlyByteBuf out) {
        out.writeEnum(template.type);
    }

    @Override
    public StringArgumentSerializer.Template deserializeFromNetwork(FriendlyByteBuf in) {
        StringType stringtype = (StringType) in.readEnum(StringType.class);

        return new StringArgumentSerializer.Template(stringtype);
    }

    public void serializeToJson(StringArgumentSerializer.Template template, JsonObject out) {
        String s;

        switch (template.type) {
            case SINGLE_WORD:
                s = "word";
                break;
            case QUOTABLE_PHRASE:
                s = "phrase";
                break;
            case GREEDY_PHRASE:
                s = "greedy";
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        out.addProperty("type", s);
    }

    public StringArgumentSerializer.Template unpack(StringArgumentType argument) {
        return new StringArgumentSerializer.Template(argument.getType());
    }

    public final class Template implements ArgumentTypeInfo.Template<StringArgumentType> {

        private final StringType type;

        public Template(StringType type) {
            this.type = type;
        }

        @Override
        public StringArgumentType instantiate(CommandBuildContext context) {
            StringArgumentType stringargumenttype;

            switch (this.type) {
                case SINGLE_WORD:
                    stringargumenttype = StringArgumentType.word();
                    break;
                case QUOTABLE_PHRASE:
                    stringargumenttype = StringArgumentType.string();
                    break;
                case GREEDY_PHRASE:
                    stringargumenttype = StringArgumentType.greedyString();
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return stringargumenttype;
        }

        @Override
        public ArgumentTypeInfo<StringArgumentType, ?> type() {
            return StringArgumentSerializer.this;
        }
    }
}
