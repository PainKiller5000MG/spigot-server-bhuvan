package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import java.util.List;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class AttributeIdPrefixFix extends AttributesRenameFix {

    private static final List<String> PREFIXES = List.of("generic.", "horse.", "player.", "zombie.");

    public AttributeIdPrefixFix(Schema outputSchema) {
        super(outputSchema, "AttributeIdPrefixFix", AttributeIdPrefixFix::replaceId);
    }

    private static String replaceId(String id) {
        String s1 = NamespacedSchema.ensureNamespaced(id);

        for (String s2 : AttributeIdPrefixFix.PREFIXES) {
            String s3 = NamespacedSchema.ensureNamespaced(s2);

            if (s1.startsWith(s3)) {
                String s4 = s1.substring(s3.length());

                return "minecraft:" + s4;
            }
        }

        return id;
    }
}
