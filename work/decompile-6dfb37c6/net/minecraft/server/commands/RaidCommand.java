package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RaidCommand {

    public RaidCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("raid").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(Commands.literal("start").then(Commands.argument("omenlvl", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return start((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "omenlvl"));
        })))).then(Commands.literal("stop").executes((commandcontext) -> {
            return stop((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("check").executes((commandcontext) -> {
            return check((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("sound").then(Commands.argument("type", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return playSound((CommandSourceStack) commandcontext.getSource(), ComponentArgument.getResolvedComponent(commandcontext, "type"));
        })))).then(Commands.literal("spawnleader").executes((commandcontext) -> {
            return spawnLeader((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("setomen").then(Commands.argument("level", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return setRaidOmenLevel((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "level"));
        })))).then(Commands.literal("glow").executes((commandcontext) -> {
            return glow((CommandSourceStack) commandcontext.getSource());
        })));
    }

    private static int glow(CommandSourceStack source) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());

        if (raid != null) {
            for (Raider raider : raid.getAllRaiders()) {
                raider.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000, 1));
            }
        }

        return 1;
    }

    private static int setRaidOmenLevel(CommandSourceStack source, int level) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());

        if (raid != null) {
            int j = raid.getMaxRaidOmenLevel();

            if (level > j) {
                source.sendFailure(Component.literal("Sorry, the max raid omen level you can set is " + j));
            } else {
                int k = raid.getRaidOmenLevel();

                raid.setRaidOmenLevel(level);
                source.sendSuccess(() -> {
                    return Component.literal("Changed village's raid omen level from " + k + " to " + level);
                }, false);
            }
        } else {
            source.sendFailure(Component.literal("No raid found here"));
        }

        return 1;
    }

    private static int spawnLeader(CommandSourceStack source) {
        source.sendSuccess(() -> {
            return Component.literal("Spawned a raid captain");
        }, false);
        Raider raider = EntityType.PILLAGER.create(source.getLevel(), EntitySpawnReason.COMMAND);

        if (raider == null) {
            source.sendFailure(Component.literal("Pillager failed to spawn"));
            return 0;
        } else {
            raider.setPatrolLeader(true);
            raider.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(source.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
            raider.setPos(source.getPosition().x, source.getPosition().y, source.getPosition().z);
            raider.finalizeSpawn(source.getLevel(), source.getLevel().getCurrentDifficultyAt(BlockPos.containing(source.getPosition())), EntitySpawnReason.COMMAND, (SpawnGroupData) null);
            source.getLevel().addFreshEntityWithPassengers(raider);
            return 1;
        }
    }

    private static int playSound(CommandSourceStack source, @Nullable Component type) {
        if (type != null && type.getString().equals("local")) {
            ServerLevel serverlevel = source.getLevel();
            Vec3 vec3 = source.getPosition().add(5.0D, 0.0D, 0.0D);

            serverlevel.playSeededSound((Entity) null, vec3.x, vec3.y, vec3.z, SoundEvents.RAID_HORN, SoundSource.NEUTRAL, 2.0F, 1.0F, serverlevel.random.nextLong());
        }

        return 1;
    }

    private static int start(CommandSourceStack source, int raidOmenLevel) throws CommandSyntaxException {
        ServerPlayer serverplayer = source.getPlayerOrException();
        BlockPos blockpos = serverplayer.blockPosition();

        if (serverplayer.level().isRaided(blockpos)) {
            source.sendFailure(Component.literal("Raid already started close by"));
            return -1;
        } else {
            Raids raids = serverplayer.level().getRaids();
            Raid raid = raids.createOrExtendRaid(serverplayer, serverplayer.blockPosition());

            if (raid != null) {
                raid.setRaidOmenLevel(raidOmenLevel);
                raids.setDirty();
                source.sendSuccess(() -> {
                    return Component.literal("Created a raid in your local village");
                }, false);
            } else {
                source.sendFailure(Component.literal("Failed to create a raid in your local village"));
            }

            return 1;
        }
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer serverplayer = source.getPlayerOrException();
        BlockPos blockpos = serverplayer.blockPosition();
        Raid raid = serverplayer.level().getRaidAt(blockpos);

        if (raid != null) {
            raid.stop();
            source.sendSuccess(() -> {
                return Component.literal("Stopped raid");
            }, false);
            return 1;
        } else {
            source.sendFailure(Component.literal("No raid here"));
            return -1;
        }
    }

    private static int check(CommandSourceStack source) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());

        if (raid != null) {
            StringBuilder stringbuilder = new StringBuilder();

            stringbuilder.append("Found a started raid! ");
            source.sendSuccess(() -> {
                return Component.literal(stringbuilder.toString());
            }, false);
            StringBuilder stringbuilder1 = new StringBuilder();

            stringbuilder1.append("Num groups spawned: ");
            stringbuilder1.append(raid.getGroupsSpawned());
            stringbuilder1.append(" Raid omen level: ");
            stringbuilder1.append(raid.getRaidOmenLevel());
            stringbuilder1.append(" Num mobs: ");
            stringbuilder1.append(raid.getTotalRaidersAlive());
            stringbuilder1.append(" Raid health: ");
            stringbuilder1.append(raid.getHealthOfLivingRaiders());
            stringbuilder1.append(" / ");
            stringbuilder1.append(raid.getTotalHealth());
            source.sendSuccess(() -> {
                return Component.literal(stringbuilder1.toString());
            }, false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Found no started raids"));
            return 0;
        }
    }

    private static @Nullable Raid getRaid(ServerPlayer player) {
        return player.level().getRaidAt(player.blockPosition());
    }
}
