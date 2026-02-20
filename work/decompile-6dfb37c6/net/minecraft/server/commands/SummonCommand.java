package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SummonCommand {

    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed"));
    private static final SimpleCommandExceptionType ERROR_FAILED_PEACEFUL = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed.peaceful"));
    private static final SimpleCommandExceptionType ERROR_DUPLICATE_UUID = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed.uuid"));
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Component.translatable("commands.summon.invalidPosition"));

    public SummonCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("summon").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((RequiredArgumentBuilder) Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE)).suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES)).executes((commandcontext) -> {
            return spawnEntity((CommandSourceStack) commandcontext.getSource(), ResourceArgument.getSummonableEntityType(commandcontext, "entity"), ((CommandSourceStack) commandcontext.getSource()).getPosition(), new CompoundTag(), true);
        })).then(((RequiredArgumentBuilder) Commands.argument("pos", Vec3Argument.vec3()).executes((commandcontext) -> {
            return spawnEntity((CommandSourceStack) commandcontext.getSource(), ResourceArgument.getSummonableEntityType(commandcontext, "entity"), Vec3Argument.getVec3(commandcontext, "pos"), new CompoundTag(), true);
        })).then(Commands.argument("nbt", CompoundTagArgument.compoundTag()).executes((commandcontext) -> {
            return spawnEntity((CommandSourceStack) commandcontext.getSource(), ResourceArgument.getSummonableEntityType(commandcontext, "entity"), Vec3Argument.getVec3(commandcontext, "pos"), CompoundTagArgument.getCompoundTag(commandcontext, "nbt"), false);
        })))));
    }

    public static Entity createEntity(CommandSourceStack source, Holder.Reference<EntityType<?>> type, Vec3 pos, CompoundTag nbt, boolean finalize) throws CommandSyntaxException {
        BlockPos blockpos = BlockPos.containing(pos);

        if (!Level.isInSpawnableBounds(blockpos)) {
            throw SummonCommand.INVALID_POSITION.create();
        } else if (source.getLevel().getDifficulty() == Difficulty.PEACEFUL && !((EntityType) type.value()).isAllowedInPeaceful()) {
            throw SummonCommand.ERROR_FAILED_PEACEFUL.create();
        } else {
            CompoundTag compoundtag1 = nbt.copy();

            compoundtag1.putString("id", type.key().identifier().toString());
            ServerLevel serverlevel = source.getLevel();
            Entity entity = EntityType.loadEntityRecursive(compoundtag1, serverlevel, EntitySpawnReason.COMMAND, (entity1) -> {
                entity1.snapTo(pos.x, pos.y, pos.z, entity1.getYRot(), entity1.getXRot());
                return entity1;
            });

            if (entity == null) {
                throw SummonCommand.ERROR_FAILED.create();
            } else {
                if (finalize && entity instanceof Mob) {
                    Mob mob = (Mob) entity;

                    mob.finalizeSpawn(source.getLevel(), source.getLevel().getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.COMMAND, (SpawnGroupData) null);
                }

                if (!serverlevel.tryAddFreshEntityWithPassengers(entity)) {
                    throw SummonCommand.ERROR_DUPLICATE_UUID.create();
                } else {
                    return entity;
                }
            }
        }
    }

    private static int spawnEntity(CommandSourceStack source, Holder.Reference<EntityType<?>> type, Vec3 pos, CompoundTag nbt, boolean finalize) throws CommandSyntaxException {
        Entity entity = createEntity(source, type, pos, nbt, finalize);

        source.sendSuccess(() -> {
            return Component.translatable("commands.summon.success", entity.getDisplayName());
        }, true);
        return 1;
    }
}
