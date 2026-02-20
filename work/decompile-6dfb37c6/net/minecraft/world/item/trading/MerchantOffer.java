package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class MerchantOffer {

    public static final Codec<MerchantOffer> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ItemCost.CODEC.fieldOf("buy").forGetter((merchantoffer) -> {
            return merchantoffer.baseCostA;
        }), ItemCost.CODEC.lenientOptionalFieldOf("buyB").forGetter((merchantoffer) -> {
            return merchantoffer.costB;
        }), ItemStack.CODEC.fieldOf("sell").forGetter((merchantoffer) -> {
            return merchantoffer.result;
        }), Codec.INT.lenientOptionalFieldOf("uses", 0).forGetter((merchantoffer) -> {
            return merchantoffer.uses;
        }), Codec.INT.lenientOptionalFieldOf("maxUses", 4).forGetter((merchantoffer) -> {
            return merchantoffer.maxUses;
        }), Codec.BOOL.lenientOptionalFieldOf("rewardExp", true).forGetter((merchantoffer) -> {
            return merchantoffer.rewardExp;
        }), Codec.INT.lenientOptionalFieldOf("specialPrice", 0).forGetter((merchantoffer) -> {
            return merchantoffer.specialPriceDiff;
        }), Codec.INT.lenientOptionalFieldOf("demand", 0).forGetter((merchantoffer) -> {
            return merchantoffer.demand;
        }), Codec.FLOAT.lenientOptionalFieldOf("priceMultiplier", 0.0F).forGetter((merchantoffer) -> {
            return merchantoffer.priceMultiplier;
        }), Codec.INT.lenientOptionalFieldOf("xp", 1).forGetter((merchantoffer) -> {
            return merchantoffer.xp;
        })).apply(instance, MerchantOffer::new);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, MerchantOffer> STREAM_CODEC = StreamCodec.<RegistryFriendlyByteBuf, MerchantOffer>of(MerchantOffer::writeToStream, MerchantOffer::createFromStream);
    public ItemCost baseCostA;
    public Optional<ItemCost> costB;
    public final ItemStack result;
    public int uses;
    public int maxUses;
    public boolean rewardExp;
    public int specialPriceDiff;
    public int demand;
    public float priceMultiplier;
    public int xp;

    private MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int uses, int maxUses, boolean rewardExp, int specialPriceDiff, int demand, float priceMultiplier, int xp) {
        this.baseCostA = baseCostA;
        this.costB = costB;
        this.result = result;
        this.uses = uses;
        this.maxUses = maxUses;
        this.rewardExp = rewardExp;
        this.specialPriceDiff = specialPriceDiff;
        this.demand = demand;
        this.priceMultiplier = priceMultiplier;
        this.xp = xp;
    }

    public MerchantOffer(ItemCost buy, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        this(buy, Optional.empty(), result, maxUses, xp, priceMultiplier);
    }

    public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        this(baseCostA, costB, result, 0, maxUses, xp, priceMultiplier);
    }

    public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int uses, int maxUses, int xp, float priceMultiplier) {
        this(baseCostA, costB, result, uses, maxUses, xp, priceMultiplier, 0);
    }

    public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int uses, int maxUses, int xp, float priceMultiplier, int demand) {
        this(baseCostA, costB, result, uses, maxUses, true, 0, demand, priceMultiplier, xp);
    }

    private MerchantOffer(MerchantOffer offer) {
        this(offer.baseCostA, offer.costB, offer.result.copy(), offer.uses, offer.maxUses, offer.rewardExp, offer.specialPriceDiff, offer.demand, offer.priceMultiplier, offer.xp);
    }

    public ItemStack getBaseCostA() {
        return this.baseCostA.itemStack();
    }

    public ItemStack getCostA() {
        return this.baseCostA.itemStack().copyWithCount(this.getModifiedCostCount(this.baseCostA));
    }

    private int getModifiedCostCount(ItemCost cost) {
        int i = cost.count();
        int j = Math.max(0, Mth.floor((float) (i * this.demand) * this.priceMultiplier));

        return Mth.clamp(i + j + this.specialPriceDiff, 1, cost.itemStack().getMaxStackSize());
    }

    public ItemStack getCostB() {
        return (ItemStack) this.costB.map(ItemCost::itemStack).orElse(ItemStack.EMPTY);
    }

    public ItemCost getItemCostA() {
        return this.baseCostA;
    }

    public Optional<ItemCost> getItemCostB() {
        return this.costB;
    }

    public ItemStack getResult() {
        return this.result;
    }

    public void updateDemand() {
        this.demand = this.demand + this.uses - (this.maxUses - this.uses);
    }

    public ItemStack assemble() {
        return this.result.copy();
    }

    public int getUses() {
        return this.uses;
    }

    public void resetUses() {
        this.uses = 0;
    }

    public int getMaxUses() {
        return this.maxUses;
    }

    public void increaseUses() {
        ++this.uses;
    }

    public int getDemand() {
        return this.demand;
    }

    public void addToSpecialPriceDiff(int add) {
        this.specialPriceDiff += add;
    }

    public void resetSpecialPriceDiff() {
        this.specialPriceDiff = 0;
    }

    public int getSpecialPriceDiff() {
        return this.specialPriceDiff;
    }

    public void setSpecialPriceDiff(int value) {
        this.specialPriceDiff = value;
    }

    public float getPriceMultiplier() {
        return this.priceMultiplier;
    }

    public int getXp() {
        return this.xp;
    }

    public boolean isOutOfStock() {
        return this.uses >= this.maxUses;
    }

    public void setToOutOfStock() {
        this.uses = this.maxUses;
    }

    public boolean needsRestock() {
        return this.uses > 0;
    }

    public boolean shouldRewardExp() {
        return this.rewardExp;
    }

    public boolean satisfiedBy(ItemStack buyA, ItemStack buyB) {
        return this.baseCostA.test(buyA) && buyA.getCount() >= this.getModifiedCostCount(this.baseCostA) ? (!this.costB.isPresent() ? buyB.isEmpty() : ((ItemCost) this.costB.get()).test(buyB) && buyB.getCount() >= ((ItemCost) this.costB.get()).count()) : false;
    }

    public boolean take(ItemStack buyA, ItemStack buyB) {
        if (!this.satisfiedBy(buyA, buyB)) {
            return false;
        } else {
            buyA.shrink(this.getCostA().getCount());
            if (!this.getCostB().isEmpty()) {
                buyB.shrink(this.getCostB().getCount());
            }

            return true;
        }
    }

    public MerchantOffer copy() {
        return new MerchantOffer(this);
    }

    private static void writeToStream(RegistryFriendlyByteBuf output, MerchantOffer offer) {
        ItemCost.STREAM_CODEC.encode(output, offer.getItemCostA());
        ItemStack.STREAM_CODEC.encode(output, offer.getResult());
        ItemCost.OPTIONAL_STREAM_CODEC.encode(output, offer.getItemCostB());
        output.writeBoolean(offer.isOutOfStock());
        output.writeInt(offer.getUses());
        output.writeInt(offer.getMaxUses());
        output.writeInt(offer.getXp());
        output.writeInt(offer.getSpecialPriceDiff());
        output.writeFloat(offer.getPriceMultiplier());
        output.writeInt(offer.getDemand());
    }

    public static MerchantOffer createFromStream(RegistryFriendlyByteBuf input) {
        ItemCost itemcost = (ItemCost) ItemCost.STREAM_CODEC.decode(input);
        ItemStack itemstack = (ItemStack) ItemStack.STREAM_CODEC.decode(input);
        Optional<ItemCost> optional = (Optional) ItemCost.OPTIONAL_STREAM_CODEC.decode(input);
        boolean flag = input.readBoolean();
        int i = input.readInt();
        int j = input.readInt();
        int k = input.readInt();
        int l = input.readInt();
        float f = input.readFloat();
        int i1 = input.readInt();
        MerchantOffer merchantoffer = new MerchantOffer(itemcost, optional, itemstack, i, j, k, f, i1);

        if (flag) {
            merchantoffer.setToOutOfStock();
        }

        merchantoffer.setSpecialPriceDiff(l);
        return merchantoffer;
    }
}
