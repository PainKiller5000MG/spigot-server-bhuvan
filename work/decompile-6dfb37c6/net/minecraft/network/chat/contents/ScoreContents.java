package net.minecraft.network.chat.contents;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.SelectorPattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

public record ScoreContents(Either<SelectorPattern, String> name, String objective) implements ComponentContents {

    public static final MapCodec<ScoreContents> INNER_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.either(SelectorPattern.CODEC, Codec.STRING).fieldOf("name").forGetter(ScoreContents::name), Codec.STRING.fieldOf("objective").forGetter(ScoreContents::objective)).apply(instance, ScoreContents::new);
    });
    public static final MapCodec<ScoreContents> MAP_CODEC = ScoreContents.INNER_CODEC.fieldOf("score");

    @Override
    public MapCodec<ScoreContents> codec() {
        return ScoreContents.MAP_CODEC;
    }

    private ScoreHolder findTargetName(CommandSourceStack source) throws CommandSyntaxException {
        Optional<SelectorPattern> optional = this.name.left();

        if (optional.isPresent()) {
            List<? extends Entity> list = ((SelectorPattern) optional.get()).resolved().findEntities(source);

            if (!list.isEmpty()) {
                if (list.size() != 1) {
                    throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
                } else {
                    return (ScoreHolder) list.getFirst();
                }
            } else {
                return ScoreHolder.forNameOnly(((SelectorPattern) optional.get()).pattern());
            }
        } else {
            return ScoreHolder.forNameOnly((String) this.name.right().orElseThrow());
        }
    }

    private MutableComponent getScore(ScoreHolder name, CommandSourceStack source) {
        MinecraftServer minecraftserver = source.getServer();

        if (minecraftserver != null) {
            Scoreboard scoreboard = minecraftserver.getScoreboard();
            Objective objective = scoreboard.getObjective(this.objective);

            if (objective != null) {
                ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(name, objective);

                if (readonlyscoreinfo != null) {
                    return readonlyscoreinfo.formatValue(objective.numberFormatOrDefault(StyledFormat.NO_STYLE));
                }
            }
        }

        return Component.empty();
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack source, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        if (source == null) {
            return Component.empty();
        } else {
            ScoreHolder scoreholder = this.findTargetName(source);
            ScoreHolder scoreholder1 = (ScoreHolder) (entity != null && scoreholder.equals(ScoreHolder.WILDCARD) ? entity : scoreholder);

            return this.getScore(scoreholder1, source);
        }
    }

    public String toString() {
        String s = String.valueOf(this.name);

        return "score{name='" + s + "', objective='" + this.objective + "'}";
    }
}
