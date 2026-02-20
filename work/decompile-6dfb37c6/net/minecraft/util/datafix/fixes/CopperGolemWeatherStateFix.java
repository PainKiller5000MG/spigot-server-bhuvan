package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class CopperGolemWeatherStateFix extends NamedEntityFix {

    public CopperGolemWeatherStateFix(Schema outputSchema) {
        super(outputSchema, false, "CopperGolemWeatherStateFix", References.ENTITY, "minecraft:copper_golem");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), (dynamic) -> {
            return dynamic.update("weather_state", CopperGolemWeatherStateFix::fixWeatherState);
        });
    }

    private static Dynamic<?> fixWeatherState(Dynamic<?> value) {
        Dynamic dynamic1;

        switch (value.asInt(0)) {
            case 1:
                dynamic1 = value.createString("exposed");
                break;
            case 2:
                dynamic1 = value.createString("weathered");
                break;
            case 3:
                dynamic1 = value.createString("oxidized");
                break;
            default:
                dynamic1 = value.createString("unaffected");
        }

        return dynamic1;
    }
}
