package net.minecraft.world.item;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class KnowledgeBookItem extends Item {

    private static final Logger LOGGER = LogUtils.getLogger();

    public KnowledgeBookItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        List<ResourceKey<Recipe<?>>> list = (List) itemstack.getOrDefault(DataComponents.RECIPES, List.of());

        itemstack.consume(1, player);
        if (list.isEmpty()) {
            return InteractionResult.FAIL;
        } else {
            if (!level.isClientSide()) {
                RecipeManager recipemanager = level.getServer().getRecipeManager();
                List<RecipeHolder<?>> list1 = new ArrayList(list.size());

                for (ResourceKey<Recipe<?>> resourcekey : list) {
                    Optional<RecipeHolder<?>> optional = recipemanager.byKey(resourcekey);

                    if (!optional.isPresent()) {
                        KnowledgeBookItem.LOGGER.error("Invalid recipe: {}", resourcekey);
                        return InteractionResult.FAIL;
                    }

                    list1.add((RecipeHolder) optional.get());
                }

                player.awardRecipes(list1);
                player.awardStat(Stats.ITEM_USED.get(this));
            }

            return InteractionResult.SUCCESS;
        }
    }
}
