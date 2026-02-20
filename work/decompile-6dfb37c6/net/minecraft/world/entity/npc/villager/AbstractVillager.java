package net.minecraft.world.entity.npc.villager;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractVillager extends AgeableMob implements Npc, Merchant, InventoryCarrier {

    private static final EntityDataAccessor<Integer> DATA_UNHAPPY_COUNTER = SynchedEntityData.<Integer>defineId(AbstractVillager.class, EntityDataSerializers.INT);
    public static final int VILLAGER_SLOT_OFFSET = 300;
    private static final int VILLAGER_INVENTORY_SIZE = 8;
    private @Nullable Player tradingPlayer;
    protected @Nullable MerchantOffers offers;
    private final SimpleContainer inventory = new SimpleContainer(8);

    public AbstractVillager(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        if (groupData == null) {
            groupData = new AgeableMob.AgeableMobGroupData(false);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    public int getUnhappyCounter() {
        return (Integer) this.entityData.get(AbstractVillager.DATA_UNHAPPY_COUNTER);
    }

    public void setUnhappyCounter(int value) {
        this.entityData.set(AbstractVillager.DATA_UNHAPPY_COUNTER, value);
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(AbstractVillager.DATA_UNHAPPY_COUNTER, 0);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Override
    public @Nullable Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    public boolean isTrading() {
        return this.tradingPlayer != null;
    }

    @Override
    public MerchantOffers getOffers() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (this.offers == null) {
                this.offers = new MerchantOffers();
                this.updateTrades(serverlevel);
            }

            return this.offers;
        } else {
            throw new IllegalStateException("Cannot load Villager offers on the client");
        }
    }

    @Override
    public void overrideOffers(@Nullable MerchantOffers offers) {}

    @Override
    public void overrideXp(int xp) {}

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        this.rewardTradeXp(offer);
        if (this.tradingPlayer instanceof ServerPlayer) {
            CriteriaTriggers.TRADE.trigger((ServerPlayer) this.tradingPlayer, this, offer.getResult());
        }

    }

    protected abstract void rewardTradeXp(MerchantOffer offer);

    @Override
    public boolean showProgressBar() {
        return true;
    }

    @Override
    public void notifyTradeUpdated(ItemStack itemStack) {
        if (!this.level().isClientSide() && this.ambientSoundTime > -this.getAmbientSoundInterval() + 20) {
            this.ambientSoundTime = -this.getAmbientSoundInterval();
            this.makeSound(this.getTradeUpdatedSound(!itemStack.isEmpty()));
        }

    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    protected SoundEvent getTradeUpdatedSound(boolean validTrade) {
        return validTrade ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO;
    }

    public void playCelebrateSound() {
        this.makeSound(SoundEvents.VILLAGER_CELEBRATE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (!this.level().isClientSide()) {
            MerchantOffers merchantoffers = this.getOffers();

            if (!merchantoffers.isEmpty()) {
                output.store("Offers", MerchantOffers.CODEC, merchantoffers);
            }
        }

        this.writeInventoryToTag(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.offers = (MerchantOffers) input.read("Offers", MerchantOffers.CODEC).orElse((Object) null);
        this.readInventoryFromTag(input);
    }

    @Override
    public @Nullable Entity teleport(TeleportTransition transition) {
        this.stopTrading();
        return super.teleport(transition);
    }

    protected void stopTrading() {
        this.setTradingPlayer((Player) null);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.stopTrading();
    }

    protected void addParticlesAroundSelf(ParticleOptions particle) {
        for (int i = 0; i < 5; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.level().addParticle(particle, this.getRandomX(1.0D), this.getRandomY() + 1.0D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        int j = slot - 300;

        return j >= 0 && j < this.inventory.getContainerSize() ? this.inventory.getSlot(j) : super.getSlot(slot);
    }

    protected abstract void updateTrades(ServerLevel level);

    protected void addOffersFromItemListings(ServerLevel level, MerchantOffers merchantOffers, VillagerTrades.ItemListing[] itemListings, int numberOfOffers) {
        ArrayList<VillagerTrades.ItemListing> arraylist = Lists.newArrayList(itemListings);
        int j = 0;

        while (j < numberOfOffers && !arraylist.isEmpty()) {
            MerchantOffer merchantoffer = ((VillagerTrades.ItemListing) arraylist.remove(this.random.nextInt(arraylist.size()))).getOffer(level, this, this.random);

            if (merchantoffer != null) {
                merchantOffers.add(merchantoffer);
                ++j;
            }
        }

    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTickTime) {
        float f1 = Mth.lerp(partialTickTime, this.yBodyRotO, this.yBodyRot) * ((float) Math.PI / 180F);
        Vec3 vec3 = new Vec3(0.0D, this.getBoundingBox().getYsize() - 1.0D, 0.2D);

        return this.getPosition(partialTickTime).add(vec3.yRot(-f1));
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.getTradingPlayer() == player && this.isAlive() && player.isWithinEntityInteractionRange((Entity) this, 4.0D);
    }
}
