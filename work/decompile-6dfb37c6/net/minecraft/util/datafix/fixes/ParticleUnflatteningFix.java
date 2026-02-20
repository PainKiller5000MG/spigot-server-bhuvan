package net.minecraft.util.datafix.fixes;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ParticleUnflatteningFix extends DataFix {

    private static final Logger LOGGER = LogUtils.getLogger();

    public ParticleUnflatteningFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.PARTICLE);
        Type<?> type1 = this.getOutputSchema().getType(References.PARTICLE);

        return this.writeFixAndRead("ParticleUnflatteningFix", type, type1, this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> input) {
        Optional<String> optional = input.asString().result();

        if (optional.isEmpty()) {
            return input;
        } else {
            String s = (String) optional.get();
            String[] astring = s.split(" ", 2);
            String s1 = NamespacedSchema.ensureNamespaced(astring[0]);
            Dynamic<T> dynamic1 = input.createMap(Map.of(input.createString("type"), input.createString(s1)));
            Dynamic dynamic2;

            switch (s1) {
                case "minecraft:item":
                    dynamic2 = astring.length > 1 ? this.updateItem(dynamic1, astring[1]) : dynamic1;
                    break;
                case "minecraft:block":
                case "minecraft:block_marker":
                case "minecraft:falling_dust":
                case "minecraft:dust_pillar":
                    dynamic2 = astring.length > 1 ? this.updateBlock(dynamic1, astring[1]) : dynamic1;
                    break;
                case "minecraft:dust":
                    dynamic2 = astring.length > 1 ? this.updateDust(dynamic1, astring[1]) : dynamic1;
                    break;
                case "minecraft:dust_color_transition":
                    dynamic2 = astring.length > 1 ? this.updateDustTransition(dynamic1, astring[1]) : dynamic1;
                    break;
                case "minecraft:sculk_charge":
                    dynamic2 = astring.length > 1 ? this.updateSculkCharge(dynamic1, astring[1]) : dynamic1;
                    break;
                case "minecraft:vibration":
                    dynamic2 = astring.length > 1 ? this.updateVibration(dynamic1, astring[1]) : dynamic1;
                    break;
                case "minecraft:shriek":
                    dynamic2 = astring.length > 1 ? this.updateShriek(dynamic1, astring[1]) : dynamic1;
                    break;
                default:
                    dynamic2 = dynamic1;
            }

            return dynamic2;
        }
    }

    private <T> Dynamic<T> updateItem(Dynamic<T> result, String contents) {
        int i = contents.indexOf("{");
        Dynamic<T> dynamic1 = result.createMap(Map.of(result.createString("Count"), result.createInt(1)));

        if (i == -1) {
            dynamic1 = dynamic1.set("id", result.createString(contents));
        } else {
            dynamic1 = dynamic1.set("id", result.createString(contents.substring(0, i)));
            Dynamic<T> dynamic2 = parseTag(result.getOps(), contents.substring(i));

            if (dynamic2 != null) {
                dynamic1 = dynamic1.set("tag", dynamic2);
            }
        }

        return result.set("item", dynamic1);
    }

    private static <T> @Nullable Dynamic<T> parseTag(DynamicOps<T> ops, String contents) {
        try {
            return new Dynamic(ops, TagParser.create(ops).parseFully(contents));
        } catch (Exception exception) {
            ParticleUnflatteningFix.LOGGER.warn("Failed to parse tag: {}", contents, exception);
            return null;
        }
    }

    private <T> Dynamic<T> updateBlock(Dynamic<T> result, String contents) {
        int i = contents.indexOf("[");
        Dynamic<T> dynamic1 = result.emptyMap();

        if (i == -1) {
            dynamic1 = dynamic1.set("Name", result.createString(NamespacedSchema.ensureNamespaced(contents)));
        } else {
            dynamic1 = dynamic1.set("Name", result.createString(NamespacedSchema.ensureNamespaced(contents.substring(0, i))));
            Map<Dynamic<T>, Dynamic<T>> map = parseBlockProperties(result, contents.substring(i));

            if (!map.isEmpty()) {
                dynamic1 = dynamic1.set("Properties", result.createMap(map));
            }
        }

        return result.set("block_state", dynamic1);
    }

    private static <T> Map<Dynamic<T>, Dynamic<T>> parseBlockProperties(Dynamic<T> dynamic, String contents) {
        try {
            Map<Dynamic<T>, Dynamic<T>> map = new HashMap();
            StringReader stringreader = new StringReader(contents);

            stringreader.expect('[');
            stringreader.skipWhitespace();

            while (stringreader.canRead() && stringreader.peek() != ']') {
                stringreader.skipWhitespace();
                String s1 = stringreader.readString();

                stringreader.skipWhitespace();
                stringreader.expect('=');
                stringreader.skipWhitespace();
                String s2 = stringreader.readString();

                stringreader.skipWhitespace();
                map.put(dynamic.createString(s1), dynamic.createString(s2));
                if (stringreader.canRead()) {
                    if (stringreader.peek() != ',') {
                        break;
                    }

                    stringreader.skip();
                }
            }

            stringreader.expect(']');
            return map;
        } catch (Exception exception) {
            ParticleUnflatteningFix.LOGGER.warn("Failed to parse block properties: {}", contents, exception);
            return Map.of();
        }
    }

    private static <T> Dynamic<T> readVector(Dynamic<T> result, StringReader reader) throws CommandSyntaxException {
        float f = reader.readFloat();

        reader.expect(' ');
        float f1 = reader.readFloat();

        reader.expect(' ');
        float f2 = reader.readFloat();
        Stream stream = Stream.of(f, f1, f2);

        Objects.requireNonNull(result);
        return result.createList(stream.map(result::createFloat));
    }

    private <T> Dynamic<T> updateDust(Dynamic<T> result, String contents) {
        try {
            StringReader stringreader = new StringReader(contents);
            Dynamic<T> dynamic1 = readVector(result, stringreader);

            stringreader.expect(' ');
            float f = stringreader.readFloat();

            return result.set("color", dynamic1).set("scale", result.createFloat(f));
        } catch (Exception exception) {
            ParticleUnflatteningFix.LOGGER.warn("Failed to parse particle options: {}", contents, exception);
            return result;
        }
    }

    private <T> Dynamic<T> updateDustTransition(Dynamic<T> result, String contents) {
        try {
            StringReader stringreader = new StringReader(contents);
            Dynamic<T> dynamic1 = readVector(result, stringreader);

            stringreader.expect(' ');
            float f = stringreader.readFloat();

            stringreader.expect(' ');
            Dynamic<T> dynamic2 = readVector(result, stringreader);

            return result.set("from_color", dynamic1).set("to_color", dynamic2).set("scale", result.createFloat(f));
        } catch (Exception exception) {
            ParticleUnflatteningFix.LOGGER.warn("Failed to parse particle options: {}", contents, exception);
            return result;
        }
    }

    private <T> Dynamic<T> updateSculkCharge(Dynamic<T> result, String contents) {
        try {
            StringReader stringreader = new StringReader(contents);
            float f = stringreader.readFloat();

            return result.set("roll", result.createFloat(f));
        } catch (Exception exception) {
            ParticleUnflatteningFix.LOGGER.warn("Failed to parse particle options: {}", contents, exception);
            return result;
        }
    }

    private <T> Dynamic<T> updateVibration(Dynamic<T> result, String contents) {
        try {
            StringReader stringreader = new StringReader(contents);
            float f = (float) stringreader.readDouble();

            stringreader.expect(' ');
            float f1 = (float) stringreader.readDouble();

            stringreader.expect(' ');
            float f2 = (float) stringreader.readDouble();

            stringreader.expect(' ');
            int i = stringreader.readInt();
            Dynamic<T> dynamic1 = result.createIntList(IntStream.of(new int[]{Mth.floor(f), Mth.floor(f1), Mth.floor(f2)}));
            Dynamic<T> dynamic2 = result.createMap(Map.of(result.createString("type"), result.createString("minecraft:block"), result.createString("pos"), dynamic1));

            return result.set("destination", dynamic2).set("arrival_in_ticks", result.createInt(i));
        } catch (Exception exception) {
            ParticleUnflatteningFix.LOGGER.warn("Failed to parse particle options: {}", contents, exception);
            return result;
        }
    }

    private <T> Dynamic<T> updateShriek(Dynamic<T> result, String contents) {
        try {
            StringReader stringreader = new StringReader(contents);
            int i = stringreader.readInt();

            return result.set("delay", result.createInt(i));
        } catch (Exception exception) {
            ParticleUnflatteningFix.LOGGER.warn("Failed to parse particle options: {}", contents, exception);
            return result;
        }
    }
}
