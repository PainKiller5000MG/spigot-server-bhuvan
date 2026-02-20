package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Map;

public class EntityFieldsRenameFix extends NamedEntityFix {

    private final Map<String, String> renames;

    public EntityFieldsRenameFix(Schema outputSchema, String name, String entityType, Map<String, String> renames) {
        super(outputSchema, false, name, References.ENTITY, entityType);
        this.renames = renames;
    }

    public Dynamic<?> fixTag(Dynamic<?> data) {
        for (Map.Entry<String, String> map_entry : this.renames.entrySet()) {
            data = data.renameField((String) map_entry.getKey(), (String) map_entry.getValue());
        }

        return data;
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), this::fixTag);
    }
}
