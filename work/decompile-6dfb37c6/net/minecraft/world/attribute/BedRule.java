package net.minecraft.world.attribute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public record BedRule(BedRule.Rule canSleep, BedRule.Rule canSetSpawn, boolean explodes, Optional<Component> errorMessage) {

    public static final BedRule CAN_SLEEP_WHEN_DARK = new BedRule(BedRule.Rule.WHEN_DARK, BedRule.Rule.ALWAYS, false, Optional.of(Component.translatable("block.minecraft.bed.no_sleep")));
    public static final BedRule EXPLODES = new BedRule(BedRule.Rule.NEVER, BedRule.Rule.NEVER, true, Optional.empty());
    public static final Codec<BedRule> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(BedRule.Rule.CODEC.fieldOf("can_sleep").forGetter(BedRule::canSleep), BedRule.Rule.CODEC.fieldOf("can_set_spawn").forGetter(BedRule::canSetSpawn), Codec.BOOL.optionalFieldOf("explodes", false).forGetter(BedRule::explodes), ComponentSerialization.CODEC.optionalFieldOf("error_message").forGetter(BedRule::errorMessage)).apply(instance, BedRule::new);
    });

    public boolean canSleep(Level level) {
        return this.canSleep.test(level);
    }

    public boolean canSetSpawn(Level level) {
        return this.canSetSpawn.test(level);
    }

    public Player.BedSleepingProblem asProblem() {
        return new Player.BedSleepingProblem((Component) this.errorMessage.orElse((Object) null));
    }

    public static enum Rule implements StringRepresentable {

        ALWAYS("always"), WHEN_DARK("when_dark"), NEVER("never");

        public static final Codec<BedRule.Rule> CODEC = StringRepresentable.<BedRule.Rule>fromEnum(BedRule.Rule::values);
        private final String name;

        private Rule(String name) {
            this.name = name;
        }

        public boolean test(Level level) {
            boolean flag;

            switch (this.ordinal()) {
                case 0:
                    flag = true;
                    break;
                case 1:
                    flag = level.isDarkOutside();
                    break;
                case 2:
                    flag = false;
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return flag;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
