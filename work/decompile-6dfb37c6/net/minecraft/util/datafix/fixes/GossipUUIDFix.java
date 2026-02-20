package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;

public class GossipUUIDFix extends NamedEntityFix {

    public GossipUUIDFix(Schema outputSchema, String entityName) {
        super(outputSchema, false, "Gossip for for " + entityName, References.ENTITY, entityName);
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), (dynamic) -> {
            return dynamic.update("Gossips", (dynamic1) -> {
                Optional optional = dynamic1.asStreamOpt().result().map((stream) -> {
                    return stream.map((dynamic2) -> {
                        return (Dynamic) AbstractUUIDFix.replaceUUIDLeastMost(dynamic2, "Target", "Target").orElse(dynamic2);
                    });
                });

                Objects.requireNonNull(dynamic1);
                return (Dynamic) DataFixUtils.orElse(optional.map(dynamic1::createList), dynamic1);
            });
        });
    }
}
