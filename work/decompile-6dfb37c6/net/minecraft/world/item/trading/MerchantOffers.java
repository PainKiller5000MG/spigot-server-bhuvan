package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class MerchantOffers extends ArrayList<MerchantOffer> {

    public static final Codec<MerchantOffers> CODEC = MerchantOffer.CODEC.listOf().optionalFieldOf("Recipes", List.of()).xmap(MerchantOffers::new, Function.identity()).codec();
    public static final StreamCodec<RegistryFriendlyByteBuf, MerchantOffers> STREAM_CODEC = MerchantOffer.STREAM_CODEC.apply(ByteBufCodecs.collection(MerchantOffers::new));

    public MerchantOffers() {}

    private MerchantOffers(int initialCapacity) {
        super(initialCapacity);
    }

    private MerchantOffers(Collection<MerchantOffer> offers) {
        super(offers);
    }

    public @Nullable MerchantOffer getRecipeFor(ItemStack buyA, ItemStack buyB, int selectionHint) {
        if (selectionHint > 0 && selectionHint < this.size()) {
            MerchantOffer merchantoffer = (MerchantOffer) this.get(selectionHint);

            return merchantoffer.satisfiedBy(buyA, buyB) ? merchantoffer : null;
        } else {
            for (int j = 0; j < this.size(); ++j) {
                MerchantOffer merchantoffer1 = (MerchantOffer) this.get(j);

                if (merchantoffer1.satisfiedBy(buyA, buyB)) {
                    return merchantoffer1;
                }
            }

            return null;
        }
    }

    public MerchantOffers copy() {
        MerchantOffers merchantoffers = new MerchantOffers(this.size());

        for (MerchantOffer merchantoffer : this) {
            merchantoffers.add(merchantoffer.copy());
        }

        return merchantoffers;
    }
}
