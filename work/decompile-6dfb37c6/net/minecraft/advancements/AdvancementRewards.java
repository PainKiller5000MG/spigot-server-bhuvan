package net.minecraft.advancements;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CacheableFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record AdvancementRewards(int experience, List<ResourceKey<LootTable>> loot, List<ResourceKey<Recipe<?>>> recipes, Optional<CacheableFunction> function) {

    public static final Codec<AdvancementRewards> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.INT.optionalFieldOf("experience", 0).forGetter(AdvancementRewards::experience), LootTable.KEY_CODEC.listOf().optionalFieldOf("loot", List.of()).forGetter(AdvancementRewards::loot), Recipe.KEY_CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(AdvancementRewards::recipes), CacheableFunction.CODEC.optionalFieldOf("function").forGetter(AdvancementRewards::function)).apply(instance, AdvancementRewards::new);
    });
    public static final AdvancementRewards EMPTY = new AdvancementRewards(0, List.of(), List.of(), Optional.empty());

    public void grant(ServerPlayer player) {
        player.giveExperiencePoints(this.experience);
        ServerLevel serverlevel = player.level();
        MinecraftServer minecraftserver = serverlevel.getServer();
        LootParams lootparams = (new LootParams.Builder(serverlevel)).withParameter(LootContextParams.THIS_ENTITY, player).withParameter(LootContextParams.ORIGIN, player.position()).create(LootContextParamSets.ADVANCEMENT_REWARD);
        boolean flag = false;

        for (ResourceKey<LootTable> resourcekey : this.loot) {
            ObjectListIterator objectlistiterator = minecraftserver.reloadableRegistries().getLootTable(resourcekey).getRandomItems(lootparams).iterator();

            while (objectlistiterator.hasNext()) {
                ItemStack itemstack = (ItemStack) objectlistiterator.next();

                if (player.addItem(itemstack)) {
                    serverlevel.playSound((Entity) null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
                    flag = true;
                } else {
                    ItemEntity itementity = player.drop(itemstack, false);

                    if (itementity != null) {
                        itementity.setNoPickUpDelay();
                        itementity.setTarget(player.getUUID());
                    }
                }
            }
        }

        if (flag) {
            player.containerMenu.broadcastChanges();
        }

        if (!this.recipes.isEmpty()) {
            player.awardRecipesByKey(this.recipes);
        }

        this.function.flatMap((cacheablefunction) -> {
            return cacheablefunction.get(minecraftserver.getFunctions());
        }).ifPresent((commandfunction) -> {
            minecraftserver.getFunctions().execute(commandfunction, player.createCommandSourceStack().withSuppressedOutput().withPermission(LevelBasedPermissionSet.GAMEMASTER));
        });
    }

    public static class Builder {

        private int experience;
        private final ImmutableList.Builder<ResourceKey<LootTable>> loot = ImmutableList.builder();
        private final ImmutableList.Builder<ResourceKey<Recipe<?>>> recipes = ImmutableList.builder();
        private Optional<Identifier> function = Optional.empty();

        public Builder() {}

        public static AdvancementRewards.Builder experience(int amount) {
            return (new AdvancementRewards.Builder()).addExperience(amount);
        }

        public AdvancementRewards.Builder addExperience(int amount) {
            this.experience += amount;
            return this;
        }

        public static AdvancementRewards.Builder loot(ResourceKey<LootTable> id) {
            return (new AdvancementRewards.Builder()).addLootTable(id);
        }

        public AdvancementRewards.Builder addLootTable(ResourceKey<LootTable> id) {
            this.loot.add(id);
            return this;
        }

        public static AdvancementRewards.Builder recipe(ResourceKey<Recipe<?>> id) {
            return (new AdvancementRewards.Builder()).addRecipe(id);
        }

        public AdvancementRewards.Builder addRecipe(ResourceKey<Recipe<?>> id) {
            this.recipes.add(id);
            return this;
        }

        public static AdvancementRewards.Builder function(Identifier id) {
            return (new AdvancementRewards.Builder()).runs(id);
        }

        public AdvancementRewards.Builder runs(Identifier function) {
            this.function = Optional.of(function);
            return this;
        }

        public AdvancementRewards build() {
            return new AdvancementRewards(this.experience, this.loot.build(), this.recipes.build(), this.function.map(CacheableFunction::new));
        }
    }
}
