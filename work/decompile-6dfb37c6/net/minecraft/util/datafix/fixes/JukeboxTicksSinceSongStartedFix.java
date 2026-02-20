package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class JukeboxTicksSinceSongStartedFix extends NamedEntityFix {

    public JukeboxTicksSinceSongStartedFix(Schema outputSchema) {
        super(outputSchema, false, "JukeboxTicksSinceSongStartedFix", References.BLOCK_ENTITY, "minecraft:jukebox");
    }

    public Dynamic<?> fixTag(Dynamic<?> input) {
        long i = input.get("TickCount").asLong(0L) - input.get("RecordStartTick").asLong(0L);
        Dynamic<?> dynamic1 = input.remove("IsPlaying").remove("TickCount").remove("RecordStartTick");

        return i > 0L ? dynamic1.set("ticks_since_song_started", input.createLong(i)) : dynamic1;
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), this::fixTag);
    }
}
