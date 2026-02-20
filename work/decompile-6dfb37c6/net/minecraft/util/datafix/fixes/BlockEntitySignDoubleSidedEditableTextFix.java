package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class BlockEntitySignDoubleSidedEditableTextFix extends NamedEntityWriteReadFix {

    public static final List<String> FIELDS_TO_DROP = List.of("Text1", "Text2", "Text3", "Text4", "FilteredText1", "FilteredText2", "FilteredText3", "FilteredText4", "Color", "GlowingText");
    public static final String FILTERED_CORRECT = "_filtered_correct";
    private static final String DEFAULT_COLOR = "black";

    public BlockEntitySignDoubleSidedEditableTextFix(Schema outputSchema, String name, String entityName) {
        super(outputSchema, true, name, References.BLOCK_ENTITY, entityName);
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> input) {
        input = input.set("front_text", fixFrontTextTag(input)).set("back_text", createDefaultText(input)).set("is_waxed", input.createBoolean(false)).set("_filtered_correct", input.createBoolean(true));

        for (String s : BlockEntitySignDoubleSidedEditableTextFix.FIELDS_TO_DROP) {
            input = input.remove(s);
        }

        return input;
    }

    private static <T> Dynamic<T> fixFrontTextTag(Dynamic<T> tag) {
        Dynamic<T> dynamic1 = LegacyComponentDataFixUtils.<T>createEmptyComponent(tag.getOps());
        List<Dynamic<T>> list = getLines(tag, "Text").map((optional) -> {
            return (Dynamic) optional.orElse(dynamic1);
        }).toList();
        Dynamic<T> dynamic2 = tag.emptyMap().set("messages", tag.createList(list.stream())).set("color", (Dynamic) tag.get("Color").result().orElse(tag.createString("black"))).set("has_glowing_text", (Dynamic) tag.get("GlowingText").result().orElse(tag.createBoolean(false)));
        List<Optional<Dynamic<T>>> list1 = getLines(tag, "FilteredText").toList();

        if (list1.stream().anyMatch(Optional::isPresent)) {
            dynamic2 = dynamic2.set("filtered_messages", tag.createList(Streams.mapWithIndex(list1.stream(), (optional, i) -> {
                Dynamic<T> dynamic3 = (Dynamic) list.get((int) i);

                return (Dynamic) optional.orElse(dynamic3);
            })));
        }

        return dynamic2;
    }

    private static <T> Stream<Optional<Dynamic<T>>> getLines(Dynamic<T> tag, String linePrefix) {
        return Stream.of(tag.get(linePrefix + "1").result(), tag.get(linePrefix + "2").result(), tag.get(linePrefix + "3").result(), tag.get(linePrefix + "4").result());
    }

    private static <T> Dynamic<T> createDefaultText(Dynamic<T> tag) {
        return tag.emptyMap().set("messages", createEmptyLines(tag)).set("color", tag.createString("black")).set("has_glowing_text", tag.createBoolean(false));
    }

    private static <T> Dynamic<T> createEmptyLines(Dynamic<T> tag) {
        Dynamic<T> dynamic1 = LegacyComponentDataFixUtils.<T>createEmptyComponent(tag.getOps());

        return tag.createList(Stream.of(dynamic1, dynamic1, dynamic1, dynamic1));
    }
}
