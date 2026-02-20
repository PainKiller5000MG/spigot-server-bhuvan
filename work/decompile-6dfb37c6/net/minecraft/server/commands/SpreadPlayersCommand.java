package net.minecraft.server.commands;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic4CommandExceptionType;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.scores.Team;

public class SpreadPlayersCommand {

    private static final int MAX_ITERATION_COUNT = 10000;
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_TEAMS = new Dynamic4CommandExceptionType((object, object1, object2, object3) -> {
        return Component.translatableEscape("commands.spreadplayers.failed.teams", object, object1, object2, object3);
    });
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_ENTITIES = new Dynamic4CommandExceptionType((object, object1, object2, object3) -> {
        return Component.translatableEscape("commands.spreadplayers.failed.entities", object, object1, object2, object3);
    });
    private static final Dynamic2CommandExceptionType ERROR_INVALID_MAX_HEIGHT = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.spreadplayers.failed.invalid.height", object, object1);
    });

    public SpreadPlayersCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("spreadplayers").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("center", Vec2Argument.vec2()).then(Commands.argument("spreadDistance", FloatArgumentType.floatArg(0.0F)).then(((RequiredArgumentBuilder) Commands.argument("maxRange", FloatArgumentType.floatArg(1.0F)).then(Commands.argument("respectTeams", BoolArgumentType.bool()).then(Commands.argument("targets", EntityArgument.entities()).executes((commandcontext) -> {
            return spreadPlayers((CommandSourceStack) commandcontext.getSource(), Vec2Argument.getVec2(commandcontext, "center"), FloatArgumentType.getFloat(commandcontext, "spreadDistance"), FloatArgumentType.getFloat(commandcontext, "maxRange"), ((CommandSourceStack) commandcontext.getSource()).getLevel().getMaxY() + 1, BoolArgumentType.getBool(commandcontext, "respectTeams"), EntityArgument.getEntities(commandcontext, "targets"));
        })))).then(Commands.literal("under").then(Commands.argument("maxHeight", IntegerArgumentType.integer()).then(Commands.argument("respectTeams", BoolArgumentType.bool()).then(Commands.argument("targets", EntityArgument.entities()).executes((commandcontext) -> {
            return spreadPlayers((CommandSourceStack) commandcontext.getSource(), Vec2Argument.getVec2(commandcontext, "center"), FloatArgumentType.getFloat(commandcontext, "spreadDistance"), FloatArgumentType.getFloat(commandcontext, "maxRange"), IntegerArgumentType.getInteger(commandcontext, "maxHeight"), BoolArgumentType.getBool(commandcontext, "respectTeams"), EntityArgument.getEntities(commandcontext, "targets"));
        })))))))));
    }

    private static int spreadPlayers(CommandSourceStack source, Vec2 center, float spreadDistance, float maxRange, int maxHeight, boolean respectTeams, Collection<? extends Entity> entities) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();
        int j = serverlevel.getMinY();

        if (maxHeight < j) {
            throw SpreadPlayersCommand.ERROR_INVALID_MAX_HEIGHT.create(maxHeight, j);
        } else {
            RandomSource randomsource = RandomSource.create();
            double d0 = (double) (center.x - maxRange);
            double d1 = (double) (center.y - maxRange);
            double d2 = (double) (center.x + maxRange);
            double d3 = (double) (center.y + maxRange);
            SpreadPlayersCommand.Position[] aspreadplayerscommand_position = createInitialPositions(randomsource, respectTeams ? getNumberOfTeams(entities) : entities.size(), d0, d1, d2, d3);

            spreadPositions(center, (double) spreadDistance, serverlevel, randomsource, d0, d1, d2, d3, maxHeight, aspreadplayerscommand_position, respectTeams);
            double d4 = setPlayerPositions(entities, serverlevel, aspreadplayerscommand_position, maxHeight, respectTeams);

            source.sendSuccess(() -> {
                return Component.translatable("commands.spreadplayers.success." + (respectTeams ? "teams" : "entities"), aspreadplayerscommand_position.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", d4));
            }, true);
            return aspreadplayerscommand_position.length;
        }
    }

    private static int getNumberOfTeams(Collection<? extends Entity> players) {
        Set<Team> set = Sets.newHashSet();

        for (Entity entity : players) {
            if (entity instanceof Player) {
                set.add(entity.getTeam());
            } else {
                set.add((Object) null);
            }
        }

        return set.size();
    }

    private static void spreadPositions(Vec2 center, double spreadDist, ServerLevel level, RandomSource random, double minX, double minZ, double maxX, double maxZ, int maxHeight, SpreadPlayersCommand.Position[] positions, boolean respectTeams) throws CommandSyntaxException {
        boolean flag1 = true;
        double d5 = (double) Float.MAX_VALUE;

        int j;

        for (j = 0; j < 10000 && flag1; ++j) {
            flag1 = false;
            d5 = (double) Float.MAX_VALUE;

            for (int k = 0; k < positions.length; ++k) {
                SpreadPlayersCommand.Position spreadplayerscommand_position = positions[k];
                int l = 0;
                SpreadPlayersCommand.Position spreadplayerscommand_position1 = new SpreadPlayersCommand.Position();

                for (int i1 = 0; i1 < positions.length; ++i1) {
                    if (k != i1) {
                        SpreadPlayersCommand.Position spreadplayerscommand_position2 = positions[i1];
                        double d6 = spreadplayerscommand_position.dist(spreadplayerscommand_position2);

                        d5 = Math.min(d6, d5);
                        if (d6 < spreadDist) {
                            ++l;
                            spreadplayerscommand_position1.x += spreadplayerscommand_position2.x - spreadplayerscommand_position.x;
                            spreadplayerscommand_position1.z += spreadplayerscommand_position2.z - spreadplayerscommand_position.z;
                        }
                    }
                }

                if (l > 0) {
                    spreadplayerscommand_position1.x /= (double) l;
                    spreadplayerscommand_position1.z /= (double) l;
                    double d7 = spreadplayerscommand_position1.getLength();

                    if (d7 > 0.0D) {
                        spreadplayerscommand_position1.normalize();
                        spreadplayerscommand_position.moveAway(spreadplayerscommand_position1);
                    } else {
                        spreadplayerscommand_position.randomize(random, minX, minZ, maxX, maxZ);
                    }

                    flag1 = true;
                }

                if (spreadplayerscommand_position.clamp(minX, minZ, maxX, maxZ)) {
                    flag1 = true;
                }
            }

            if (!flag1) {
                for (SpreadPlayersCommand.Position spreadplayerscommand_position3 : positions) {
                    if (!spreadplayerscommand_position3.isSafe(level, maxHeight)) {
                        spreadplayerscommand_position3.randomize(random, minX, minZ, maxX, maxZ);
                        flag1 = true;
                    }
                }
            }
        }

        if (d5 == (double) Float.MAX_VALUE) {
            d5 = 0.0D;
        }

        if (j >= 10000) {
            if (respectTeams) {
                throw SpreadPlayersCommand.ERROR_FAILED_TO_SPREAD_TEAMS.create(positions.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", d5));
            } else {
                throw SpreadPlayersCommand.ERROR_FAILED_TO_SPREAD_ENTITIES.create(positions.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", d5));
            }
        }
    }

    private static double setPlayerPositions(Collection<? extends Entity> entities, ServerLevel level, SpreadPlayersCommand.Position[] positions, int maxHeight, boolean respectTeams) {
        double d0 = 0.0D;
        int j = 0;
        Map<Team, SpreadPlayersCommand.Position> map = Maps.newHashMap();

        for (Entity entity : entities) {
            SpreadPlayersCommand.Position spreadplayerscommand_position;

            if (respectTeams) {
                Team team = entity instanceof Player ? entity.getTeam() : null;

                if (!map.containsKey(team)) {
                    map.put(team, positions[j++]);
                }

                spreadplayerscommand_position = (SpreadPlayersCommand.Position) map.get(team);
            } else {
                spreadplayerscommand_position = positions[j++];
            }

            entity.teleportTo(level, (double) Mth.floor(spreadplayerscommand_position.x) + 0.5D, (double) spreadplayerscommand_position.getSpawnY(level, maxHeight), (double) Mth.floor(spreadplayerscommand_position.z) + 0.5D, Set.of(), entity.getYRot(), entity.getXRot(), true);
            double d1 = Double.MAX_VALUE;

            for (SpreadPlayersCommand.Position spreadplayerscommand_position1 : positions) {
                if (spreadplayerscommand_position != spreadplayerscommand_position1) {
                    double d2 = spreadplayerscommand_position.dist(spreadplayerscommand_position1);

                    d1 = Math.min(d2, d1);
                }
            }

            d0 += d1;
        }

        if (entities.size() < 2) {
            return 0.0D;
        } else {
            d0 /= (double) entities.size();
            return d0;
        }
    }

    private static SpreadPlayersCommand.Position[] createInitialPositions(RandomSource random, int count, double minX, double minZ, double maxX, double maxZ) {
        SpreadPlayersCommand.Position[] aspreadplayerscommand_position = new SpreadPlayersCommand.Position[count];

        for (int j = 0; j < aspreadplayerscommand_position.length; ++j) {
            SpreadPlayersCommand.Position spreadplayerscommand_position = new SpreadPlayersCommand.Position();

            spreadplayerscommand_position.randomize(random, minX, minZ, maxX, maxZ);
            aspreadplayerscommand_position[j] = spreadplayerscommand_position;
        }

        return aspreadplayerscommand_position;
    }

    private static class Position {

        private double x;
        private double z;

        private Position() {}

        double dist(SpreadPlayersCommand.Position target) {
            double d0 = this.x - target.x;
            double d1 = this.z - target.z;

            return Math.sqrt(d0 * d0 + d1 * d1);
        }

        void normalize() {
            double d0 = this.getLength();

            this.x /= d0;
            this.z /= d0;
        }

        double getLength() {
            return Math.sqrt(this.x * this.x + this.z * this.z);
        }

        public void moveAway(SpreadPlayersCommand.Position pos) {
            this.x -= pos.x;
            this.z -= pos.z;
        }

        public boolean clamp(double minX, double minZ, double maxX, double maxZ) {
            boolean flag = false;

            if (this.x < minX) {
                this.x = minX;
                flag = true;
            } else if (this.x > maxX) {
                this.x = maxX;
                flag = true;
            }

            if (this.z < minZ) {
                this.z = minZ;
                flag = true;
            } else if (this.z > maxZ) {
                this.z = maxZ;
                flag = true;
            }

            return flag;
        }

        public int getSpawnY(BlockGetter level, int maxHeight) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(this.x, (double) (maxHeight + 1), this.z);
            boolean flag = level.getBlockState(blockpos_mutableblockpos).isAir();

            blockpos_mutableblockpos.move(Direction.DOWN);

            boolean flag1;

            for (boolean flag2 = level.getBlockState(blockpos_mutableblockpos).isAir(); blockpos_mutableblockpos.getY() > level.getMinY(); flag2 = flag1) {
                blockpos_mutableblockpos.move(Direction.DOWN);
                flag1 = level.getBlockState(blockpos_mutableblockpos).isAir();
                if (!flag1 && flag2 && flag) {
                    return blockpos_mutableblockpos.getY() + 1;
                }

                flag = flag2;
            }

            return maxHeight + 1;
        }

        public boolean isSafe(BlockGetter level, int maxHeight) {
            BlockPos blockpos = BlockPos.containing(this.x, (double) (this.getSpawnY(level, maxHeight) - 1), this.z);
            BlockState blockstate = level.getBlockState(blockpos);

            return blockpos.getY() < maxHeight && !blockstate.liquid() && !blockstate.is(BlockTags.FIRE);
        }

        public void randomize(RandomSource random, double minX, double minZ, double maxX, double maxZ) {
            this.x = Mth.nextDouble(random, minX, maxX);
            this.z = Mth.nextDouble(random, minZ, maxZ);
        }
    }
}
