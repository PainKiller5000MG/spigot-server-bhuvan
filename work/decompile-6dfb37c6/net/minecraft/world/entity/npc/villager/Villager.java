package net.minecraft.world.entity.npc.villager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Villager extends AbstractVillager implements VillagerDataHolder, ReputationEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.<VillagerData>defineId(Villager.class, EntityDataSerializers.VILLAGER_DATA);
    public static final int BREEDING_FOOD_THRESHOLD = 12;
    public static final Map<Item, Integer> FOOD_POINTS = ImmutableMap.of(Items.BREAD, 4, Items.POTATO, 1, Items.CARROT, 1, Items.BEETROOT, 1);
    private static final int TRADES_PER_LEVEL = 2;
    private static final int MAX_GOSSIP_TOPICS = 10;
    private static final int GOSSIP_COOLDOWN = 1200;
    private static final int GOSSIP_DECAY_INTERVAL = 24000;
    private static final int HOW_FAR_AWAY_TO_TALK_TO_OTHER_VILLAGERS_ABOUT_GOLEMS = 10;
    private static final int HOW_MANY_VILLAGERS_NEED_TO_AGREE_TO_SPAWN_A_GOLEM = 5;
    private static final long TIME_SINCE_SLEEPING_FOR_GOLEM_SPAWNING = 24000L;
    @VisibleForTesting
    public static final float SPEED_MODIFIER = 0.5F;
    private static final int DEFAULT_XP = 0;
    private static final byte DEFAULT_FOOD_LEVEL = 0;
    private static final int DEFAULT_LAST_RESTOCK = 0;
    private static final int DEFAULT_LAST_GOSSIP_DECAY = 0;
    private static final int DEFAULT_RESTOCKS_TODAY = 0;
    private static final boolean DEFAULT_ASSIGN_PROFESSION_WHEN_SPAWNED = false;
    private int updateMerchantTimer;
    private boolean increaseProfessionLevelOnUpdate;
    private @Nullable Player lastTradedPlayer;
    private boolean chasing;
    private int foodLevel;
    private final GossipContainer gossips;
    private long lastGossipTime;
    private long lastGossipDecayTime;
    private int villagerXp;
    private long lastRestockGameTime;
    private int numberOfRestocksToday;
    private long lastRestockCheckDay;
    private boolean assignProfessionWhenSpawned;
    private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.VISIBLE_VILLAGER_BABIES, MemoryModuleType.NEAREST_PLAYERS, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, new MemoryModuleType[]{MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET, MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.BREED_TARGET, MemoryModuleType.PATH, MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleType.NEAREST_BED, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.NEAREST_HOSTILE, MemoryModuleType.SECONDARY_JOB_SITE, MemoryModuleType.HIDING_PLACE, MemoryModuleType.HEARD_BELL_TIME, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.LAST_SLEPT, MemoryModuleType.LAST_WOKEN, MemoryModuleType.LAST_WORKED_AT_POI, MemoryModuleType.GOLEM_DETECTED_RECENTLY});
    private static final ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.NEAREST_BED, SensorType.HURT_BY, SensorType.VILLAGER_HOSTILES, SensorType.VILLAGER_BABIES, SensorType.SECONDARY_POIS, SensorType.GOLEM_DETECTED);
    public static final Map<MemoryModuleType<GlobalPos>, BiPredicate<Villager, Holder<PoiType>>> POI_MEMORIES = ImmutableMap.of(MemoryModuleType.HOME, (BiPredicate) (villager, holder) -> {
        return holder.is(PoiTypes.HOME);
    }, MemoryModuleType.JOB_SITE, (BiPredicate) (villager, holder) -> {
        return ((VillagerProfession) villager.getVillagerData().profession().value()).heldJobSite().test(holder);
    }, MemoryModuleType.POTENTIAL_JOB_SITE, (BiPredicate) (villager, holder) -> {
        return VillagerProfession.ALL_ACQUIRABLE_JOBS.test(holder);
    }, MemoryModuleType.MEETING_POINT, (BiPredicate) (villager, holder) -> {
        return holder.is(PoiTypes.MEETING);
    });

    public Villager(EntityType<? extends Villager> type, Level level) {
        this(type, level, VillagerType.PLAINS);
    }

    public Villager(EntityType<? extends Villager> entityType, Level level, ResourceKey<VillagerType> type) {
        this(entityType, level, level.registryAccess().getOrThrow(type));
    }

    public Villager(EntityType<? extends Villager> entityType, Level level, Holder<VillagerType> type) {
        super(entityType, level);
        this.foodLevel = 0;
        this.gossips = new GossipContainer();
        this.lastGossipDecayTime = 0L;
        this.villagerXp = 0;
        this.lastRestockGameTime = 0L;
        this.numberOfRestocksToday = 0;
        this.assignProfessionWhenSpawned = false;
        this.getNavigation().setCanOpenDoors(true);
        this.getNavigation().setCanFloat(true);
        this.getNavigation().setRequiredPathLength(48.0F);
        this.setCanPickUpLoot(true);
        this.setVillagerData(this.getVillagerData().withType(type).withProfession(level.registryAccess(), VillagerProfession.NONE));
    }

    @Override
    public Brain<Villager> getBrain() {
        return super.getBrain();
    }

    @Override
    protected Brain.Provider<Villager> brainProvider() {
        return Brain.<Villager>provider(Villager.MEMORY_TYPES, Villager.SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> input) {
        Brain<Villager> brain = this.brainProvider().makeBrain(input);

        this.registerBrainGoals(brain);
        return brain;
    }

    public void refreshBrain(ServerLevel level) {
        Brain<Villager> brain = this.getBrain();

        brain.stopAll(level, this);
        this.brain = brain.copyWithoutBehaviors();
        this.registerBrainGoals(this.getBrain());
    }

    private void registerBrainGoals(Brain<Villager> brain) {
        Holder<VillagerProfession> holder = this.getVillagerData().profession();

        if (this.isBaby()) {
            brain.setSchedule(EnvironmentAttributes.BABY_VILLAGER_ACTIVITY);
            brain.addActivity(Activity.PLAY, VillagerGoalPackages.getPlayPackage(0.5F));
        } else {
            brain.setSchedule(EnvironmentAttributes.VILLAGER_ACTIVITY);
            brain.addActivityWithConditions(Activity.WORK, VillagerGoalPackages.getWorkPackage(holder, 0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)));
        }

        brain.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(holder, 0.5F));
        brain.addActivityWithConditions(Activity.MEET, VillagerGoalPackages.getMeetPackage(holder, 0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
        brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(holder, 0.5F));
        brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(holder, 0.5F));
        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(holder, 0.5F));
        brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(holder, 0.5F));
        brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(holder, 0.5F));
        brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(holder, 0.5F));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.updateActivityFromSchedule(this.level().environmentAttributes(), this.level().getGameTime(), this.position());
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (this.level() instanceof ServerLevel) {
            this.refreshBrain((ServerLevel) this.level());
        }

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5D);
    }

    public boolean assignProfessionWhenSpawned() {
        return this.assignProfessionWhenSpawned;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("villagerBrain");
        this.getBrain().tick(level, this);
        profilerfiller.pop();
        if (this.assignProfessionWhenSpawned) {
            this.assignProfessionWhenSpawned = false;
        }

        if (!this.isTrading() && this.updateMerchantTimer > 0) {
            --this.updateMerchantTimer;
            if (this.updateMerchantTimer <= 0) {
                if (this.increaseProfessionLevelOnUpdate) {
                    this.increaseMerchantCareer(level);
                    this.increaseProfessionLevelOnUpdate = false;
                }

                this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            }
        }

        if (this.lastTradedPlayer != null) {
            level.onReputationEvent(ReputationEventType.TRADE, this.lastTradedPlayer, this);
            level.broadcastEntityEvent(this, (byte) 14);
            this.lastTradedPlayer = null;
        }

        if (!this.isNoAi() && this.random.nextInt(100) == 0) {
            Raid raid = level.getRaidAt(this.blockPosition());

            if (raid != null && raid.isActive() && !raid.isOver()) {
                level.broadcastEntityEvent(this, (byte) 42);
            }
        }

        if (this.getVillagerData().profession().is(VillagerProfession.NONE) && this.isTrading()) {
            this.stopTrading();
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getUnhappyCounter() > 0) {
            this.setUnhappyCounter(this.getUnhappyCounter() - 1);
        }

        this.maybeDecayGossip();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!itemstack.is(Items.VILLAGER_SPAWN_EGG) && this.isAlive() && !this.isTrading() && !this.isSleeping()) {
            if (this.isBaby()) {
                this.setUnhappy();
                return InteractionResult.SUCCESS;
            } else {
                if (!this.level().isClientSide()) {
                    boolean flag = this.getOffers().isEmpty();

                    if (hand == InteractionHand.MAIN_HAND) {
                        if (flag) {
                            this.setUnhappy();
                        }

                        player.awardStat(Stats.TALKED_TO_VILLAGER);
                    }

                    if (flag) {
                        return InteractionResult.CONSUME;
                    }

                    this.startTrading(player);
                }

                return InteractionResult.SUCCESS;
            }
        } else {
            return super.mobInteract(player, hand);
        }
    }

    public void setUnhappy() {
        this.setUnhappyCounter(40);
        if (!this.level().isClientSide()) {
            this.makeSound(SoundEvents.VILLAGER_NO);
        }

    }

    private void startTrading(Player player) {
        this.updateSpecialPrices(player);
        this.setTradingPlayer(player);
        this.openTradingScreen(player, this.getDisplayName(), this.getVillagerData().level());
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        boolean flag = this.getTradingPlayer() != null && player == null;

        super.setTradingPlayer(player);
        if (flag) {
            this.stopTrading();
        }

    }

    @Override
    protected void stopTrading() {
        super.stopTrading();
        this.resetSpecialPrices();
    }

    private void resetSpecialPrices() {
        if (!this.level().isClientSide()) {
            for (MerchantOffer merchantoffer : this.getOffers()) {
                merchantoffer.resetSpecialPriceDiff();
            }

        }
    }

    @Override
    public boolean canRestock() {
        return true;
    }

    public void restock() {
        this.updateDemand();

        for (MerchantOffer merchantoffer : this.getOffers()) {
            merchantoffer.resetUses();
        }

        this.resendOffersToTradingPlayer();
        this.lastRestockGameTime = this.level().getGameTime();
        ++this.numberOfRestocksToday;
    }

    private void resendOffersToTradingPlayer() {
        MerchantOffers merchantoffers = this.getOffers();
        Player player = this.getTradingPlayer();

        if (player != null && !merchantoffers.isEmpty()) {
            player.sendMerchantOffers(player.containerMenu.containerId, merchantoffers, this.getVillagerData().level(), this.getVillagerXp(), this.showProgressBar(), this.canRestock());
        }

    }

    private boolean needsToRestock() {
        for (MerchantOffer merchantoffer : this.getOffers()) {
            if (merchantoffer.needsRestock()) {
                return true;
            }
        }

        return false;
    }

    private boolean allowedToRestock() {
        return this.numberOfRestocksToday == 0 || this.numberOfRestocksToday < 2 && this.level().getGameTime() > this.lastRestockGameTime + 2400L;
    }

    public boolean shouldRestock(ServerLevel level) {
        long i = this.lastRestockGameTime + 12000L;
        long j = this.level().getGameTime();
        boolean flag = j > i;
        long k = level.getDayCount();

        flag |= this.lastRestockCheckDay > 0L && k > this.lastRestockCheckDay;
        this.lastRestockCheckDay = k;
        if (flag) {
            this.lastRestockGameTime = j;
            this.resetNumberOfRestocks();
        }

        return this.allowedToRestock() && this.needsToRestock();
    }

    private void catchUpDemand() {
        int i = 2 - this.numberOfRestocksToday;

        if (i > 0) {
            for (MerchantOffer merchantoffer : this.getOffers()) {
                merchantoffer.resetUses();
            }
        }

        for (int j = 0; j < i; ++j) {
            this.updateDemand();
        }

        this.resendOffersToTradingPlayer();
    }

    private void updateDemand() {
        for (MerchantOffer merchantoffer : this.getOffers()) {
            merchantoffer.updateDemand();
        }

    }

    private void updateSpecialPrices(Player player) {
        int i = this.getPlayerReputation(player);

        if (i != 0) {
            for (MerchantOffer merchantoffer : this.getOffers()) {
                merchantoffer.addToSpecialPriceDiff(-Mth.floor((float) i * merchantoffer.getPriceMultiplier()));
            }
        }

        if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            MobEffectInstance mobeffectinstance = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
            int j = mobeffectinstance.getAmplifier();

            for (MerchantOffer merchantoffer1 : this.getOffers()) {
                double d0 = 0.3D + 0.0625D * (double) j;
                int k = (int) Math.floor(d0 * (double) merchantoffer1.getBaseCostA().getCount());

                merchantoffer1.addToSpecialPriceDiff(-Math.max(k, 1));
            }
        }

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Villager.DATA_VILLAGER_DATA, createDefaultVillagerData());
    }

    public static VillagerData createDefaultVillagerData() {
        return new VillagerData(BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.PLAINS), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE), 1);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("VillagerData", VillagerData.CODEC, this.getVillagerData());
        output.putByte("FoodLevel", (byte) this.foodLevel);
        output.store("Gossips", GossipContainer.CODEC, this.gossips);
        output.putInt("Xp", this.villagerXp);
        output.putLong("LastRestock", this.lastRestockGameTime);
        output.putLong("LastGossipDecay", this.lastGossipDecayTime);
        output.putInt("RestocksToday", this.numberOfRestocksToday);
        if (this.assignProfessionWhenSpawned) {
            output.putBoolean("AssignProfessionWhenSpawned", true);
        }

    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(Villager.DATA_VILLAGER_DATA, (VillagerData) input.read("VillagerData", VillagerData.CODEC).orElseGet(Villager::createDefaultVillagerData));
        this.foodLevel = input.getByteOr("FoodLevel", (byte) 0);
        this.gossips.clear();
        Optional optional = input.read("Gossips", GossipContainer.CODEC);
        GossipContainer gossipcontainer = this.gossips;

        Objects.requireNonNull(this.gossips);
        optional.ifPresent(gossipcontainer::putAll);
        this.villagerXp = input.getIntOr("Xp", 0);
        this.lastRestockGameTime = input.getLongOr("LastRestock", 0L);
        this.lastGossipDecayTime = input.getLongOr("LastGossipDecay", 0L);
        if (this.level() instanceof ServerLevel) {
            this.refreshBrain((ServerLevel) this.level());
        }

        this.numberOfRestocksToday = input.getIntOr("RestocksToday", 0);
        this.assignProfessionWhenSpawned = input.getBooleanOr("AssignProfessionWhenSpawned", false);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.isSleeping() ? null : (this.isTrading() ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    public void playWorkSound() {
        this.makeSound(((VillagerProfession) this.getVillagerData().profession().value()).workSound());
    }

    @Override
    public void setVillagerData(VillagerData data) {
        VillagerData villagerdata1 = this.getVillagerData();

        if (!villagerdata1.profession().equals(data.profession())) {
            this.offers = null;
        }

        this.entityData.set(Villager.DATA_VILLAGER_DATA, data);
    }

    @Override
    public VillagerData getVillagerData() {
        return (VillagerData) this.entityData.get(Villager.DATA_VILLAGER_DATA);
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        int i = 3 + this.random.nextInt(4);

        this.villagerXp += offer.getXp();
        this.lastTradedPlayer = this.getTradingPlayer();
        if (this.shouldIncreaseLevel()) {
            this.updateMerchantTimer = 40;
            this.increaseProfessionLevelOnUpdate = true;
            i += 5;
        }

        if (offer.shouldRewardExp()) {
            this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5D, this.getZ(), i));
        }

    }

    @Override
    public void setLastHurtByMob(@Nullable LivingEntity hurtBy) {
        if (hurtBy != null && this.level() instanceof ServerLevel) {
            ((ServerLevel) this.level()).onReputationEvent(ReputationEventType.VILLAGER_HURT, hurtBy, this);
            if (this.isAlive() && hurtBy instanceof Player) {
                this.level().broadcastEntityEvent(this, (byte) 13);
            }
        }

        super.setLastHurtByMob(hurtBy);
    }

    @Override
    public void die(DamageSource source) {
        Villager.LOGGER.info("Villager {} died, message: '{}'", this, source.getLocalizedDeathMessage(this).getString());
        Entity entity = source.getEntity();

        if (entity != null) {
            this.tellWitnessesThatIWasMurdered(entity);
        }

        this.releaseAllPois();
        super.die(source);
    }

    public void releaseAllPois() {
        this.releasePoi(MemoryModuleType.HOME);
        this.releasePoi(MemoryModuleType.JOB_SITE);
        this.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        this.releasePoi(MemoryModuleType.MEETING_POINT);
    }

    private void tellWitnessesThatIWasMurdered(Entity murderer) {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            Optional<NearestVisibleLivingEntities> optional = this.brain.<NearestVisibleLivingEntities>getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);

            if (!optional.isEmpty()) {
                NearestVisibleLivingEntities nearestvisiblelivingentities = (NearestVisibleLivingEntities) optional.get();

                Objects.requireNonNull(ReputationEventHandler.class);
                nearestvisiblelivingentities.findAll(ReputationEventHandler.class::isInstance).forEach((livingentity) -> {
                    serverlevel.onReputationEvent(ReputationEventType.VILLAGER_KILLED, murderer, (ReputationEventHandler) livingentity);
                });
            }
        }
    }

    public void releasePoi(MemoryModuleType<GlobalPos> memoryType) {
        if (this.level() instanceof ServerLevel) {
            MinecraftServer minecraftserver = ((ServerLevel) this.level()).getServer();

            this.brain.getMemory(memoryType).ifPresent((globalpos) -> {
                ServerLevel serverlevel = minecraftserver.getLevel(globalpos.dimension());

                if (serverlevel != null) {
                    PoiManager poimanager = serverlevel.getPoiManager();
                    Optional<Holder<PoiType>> optional = poimanager.getType(globalpos.pos());
                    BiPredicate<Villager, Holder<PoiType>> bipredicate = (BiPredicate) Villager.POI_MEMORIES.get(memoryType);

                    if (optional.isPresent() && bipredicate.test(this, (Holder) optional.get())) {
                        poimanager.release(globalpos.pos());
                        serverlevel.debugSynchronizers().updatePoi(globalpos.pos());
                    }

                }
            });
        }
    }

    @Override
    public boolean canBreed() {
        return this.foodLevel + this.countFoodPointsInInventory() >= 12 && !this.isSleeping() && this.getAge() == 0;
    }

    private boolean hungry() {
        return this.foodLevel < 12;
    }

    private void eatUntilFull() {
        if (this.hungry() && this.countFoodPointsInInventory() != 0) {
            for (int i = 0; i < this.getInventory().getContainerSize(); ++i) {
                ItemStack itemstack = this.getInventory().getItem(i);

                if (!itemstack.isEmpty()) {
                    Integer integer = (Integer) Villager.FOOD_POINTS.get(itemstack.getItem());

                    if (integer != null) {
                        int j = itemstack.getCount();

                        for (int k = j; k > 0; --k) {
                            this.foodLevel += integer;
                            this.getInventory().removeItem(i, 1);
                            if (!this.hungry()) {
                                return;
                            }
                        }
                    }
                }
            }

        }
    }

    public int getPlayerReputation(Player player) {
        return this.gossips.getReputation(player.getUUID(), (gossiptype) -> {
            return true;
        });
    }

    private void digestFood(int amount) {
        this.foodLevel -= amount;
    }

    public void eatAndDigestFood() {
        this.eatUntilFull();
        this.digestFood(12);
    }

    public void setOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    private boolean shouldIncreaseLevel() {
        int i = this.getVillagerData().level();

        return VillagerData.canLevelUp(i) && this.villagerXp >= VillagerData.getMaxXpPerLevel(i);
    }

    public void increaseMerchantCareer(ServerLevel level) {
        this.setVillagerData(this.getVillagerData().withLevel(this.getVillagerData().level() + 1));
        this.updateTrades(level);
    }

    @Override
    protected Component getTypeName() {
        return ((VillagerProfession) this.getVillagerData().profession().value()).name();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 12) {
            this.addParticlesAroundSelf(ParticleTypes.HEART);
        } else if (id == 13) {
            this.addParticlesAroundSelf(ParticleTypes.ANGRY_VILLAGER);
        } else if (id == 14) {
            this.addParticlesAroundSelf(ParticleTypes.HAPPY_VILLAGER);
        } else if (id == 42) {
            this.addParticlesAroundSelf(ParticleTypes.SPLASH);
        } else {
            super.handleEntityEvent(id);
        }

    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        if (spawnReason == EntitySpawnReason.BREEDING) {
            this.setVillagerData(this.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NONE));
        }

        if (spawnReason == EntitySpawnReason.COMMAND || spawnReason == EntitySpawnReason.SPAWN_ITEM_USE || EntitySpawnReason.isSpawner(spawnReason) || spawnReason == EntitySpawnReason.DISPENSER) {
            this.setVillagerData(this.getVillagerData().withType(level.registryAccess(), VillagerType.byBiome(level.getBiome(this.blockPosition()))));
        }

        if (spawnReason == EntitySpawnReason.STRUCTURE) {
            this.assignProfessionWhenSpawned = true;
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    public @Nullable Villager getBreedOffspring(ServerLevel level, AgeableMob partner) {
        double d0 = this.random.nextDouble();
        Holder<VillagerType> holder;

        if (d0 < 0.5D) {
            holder = level.registryAccess().<VillagerType>getOrThrow(VillagerType.byBiome(level.getBiome(this.blockPosition())));
        } else if (d0 < 0.75D) {
            holder = this.getVillagerData().type();
        } else {
            holder = ((Villager) partner).getVillagerData().type();
        }

        Villager villager = new Villager(EntityType.VILLAGER, level, holder);

        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), EntitySpawnReason.BREEDING, (SpawnGroupData) null);
        return villager;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {
        if (level.getDifficulty() != Difficulty.PEACEFUL) {
            Villager.LOGGER.info("Villager {} was struck by lightning {}.", this, lightningBolt);
            Witch witch = (Witch) this.convertTo(EntityType.WITCH, ConversionParams.single(this, false, false), (witch1) -> {
                witch1.finalizeSpawn(level, level.getCurrentDifficultyAt(witch1.blockPosition()), EntitySpawnReason.CONVERSION, (SpawnGroupData) null);
                witch1.setPersistenceRequired();
                this.releaseAllPois();
            });

            if (witch == null) {
                super.thunderHit(level, lightningBolt);
            }
        } else {
            super.thunderHit(level, lightningBolt);
        }

    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        InventoryCarrier.pickUpItem(level, this, this, entity);
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack itemStack) {
        Item item = itemStack.getItem();

        return (itemStack.is(ItemTags.VILLAGER_PICKS_UP) || ((VillagerProfession) this.getVillagerData().profession().value()).requestedItems().contains(item)) && this.getInventory().canAddItem(itemStack);
    }

    public boolean hasExcessFood() {
        return this.countFoodPointsInInventory() >= 24;
    }

    public boolean wantsMoreFood() {
        return this.countFoodPointsInInventory() < 12;
    }

    private int countFoodPointsInInventory() {
        SimpleContainer simplecontainer = this.getInventory();

        return Villager.FOOD_POINTS.entrySet().stream().mapToInt((entry) -> {
            return simplecontainer.countItem((Item) entry.getKey()) * (Integer) entry.getValue();
        }).sum();
    }

    public boolean hasFarmSeeds() {
        return this.getInventory().hasAnyMatching((itemstack) -> {
            return itemstack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS);
        });
    }

    @Override
    protected void updateTrades(ServerLevel level) {
        VillagerData villagerdata = this.getVillagerData();
        ResourceKey<VillagerProfession> resourcekey = (ResourceKey) villagerdata.profession().unwrapKey().orElse((Object) null);

        if (resourcekey != null) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> int2objectmap;

            if (this.level().enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)) {
                Int2ObjectMap<VillagerTrades.ItemListing[]> int2objectmap1 = (Int2ObjectMap) VillagerTrades.EXPERIMENTAL_TRADES.get(resourcekey);

                int2objectmap = int2objectmap1 != null ? int2objectmap1 : (Int2ObjectMap) VillagerTrades.TRADES.get(resourcekey);
            } else {
                int2objectmap = (Int2ObjectMap) VillagerTrades.TRADES.get(resourcekey);
            }

            if (int2objectmap != null && !int2objectmap.isEmpty()) {
                VillagerTrades.ItemListing[] avillagertrades_itemlisting = (VillagerTrades.ItemListing[]) int2objectmap.get(villagerdata.level());

                if (avillagertrades_itemlisting != null) {
                    MerchantOffers merchantoffers = this.getOffers();

                    this.addOffersFromItemListings(level, merchantoffers, avillagertrades_itemlisting, 2);
                    if (SharedConstants.DEBUG_UNLOCK_ALL_TRADES && villagerdata.level() < int2objectmap.size()) {
                        this.increaseMerchantCareer(level);
                    }

                }
            }
        }
    }

    public void gossip(ServerLevel level, Villager target, long timestamp) {
        if ((timestamp < this.lastGossipTime || timestamp >= this.lastGossipTime + 1200L) && (timestamp < target.lastGossipTime || timestamp >= target.lastGossipTime + 1200L)) {
            this.gossips.transferFrom(target.gossips, this.random, 10);
            this.lastGossipTime = timestamp;
            target.lastGossipTime = timestamp;
            this.spawnGolemIfNeeded(level, timestamp, 5);
        }
    }

    private void maybeDecayGossip() {
        long i = this.level().getGameTime();

        if (this.lastGossipDecayTime == 0L) {
            this.lastGossipDecayTime = i;
        } else if (i >= this.lastGossipDecayTime + 24000L) {
            this.gossips.decay();
            this.lastGossipDecayTime = i;
        }
    }

    public void spawnGolemIfNeeded(ServerLevel level, long timestamp, int villagersNeededToAgree) {
        if (this.wantsToSpawnGolem(timestamp)) {
            AABB aabb = this.getBoundingBox().inflate(10.0D, 10.0D, 10.0D);
            List<Villager> list = level.<Villager>getEntitiesOfClass(Villager.class, aabb);
            List<Villager> list1 = list.stream().filter((villager) -> {
                return villager.wantsToSpawnGolem(timestamp);
            }).limit(5L).toList();

            if (list1.size() >= villagersNeededToAgree) {
                if (!SpawnUtil.trySpawnMob(EntityType.IRON_GOLEM, EntitySpawnReason.MOB_SUMMONED, level, this.blockPosition(), 10, 8, 6, SpawnUtil.Strategy.LEGACY_IRON_GOLEM, false).isEmpty()) {
                    list.forEach(GolemSensor::golemDetected);
                }
            }
        }
    }

    public boolean wantsToSpawnGolem(long timestamp) {
        return !this.golemSpawnConditionsMet(this.level().getGameTime()) ? false : !this.brain.hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
    }

    @Override
    public void onReputationEventFrom(ReputationEventType type, Entity source) {
        if (type == ReputationEventType.ZOMBIE_VILLAGER_CURED) {
            this.gossips.add(source.getUUID(), GossipType.MAJOR_POSITIVE, 20);
            this.gossips.add(source.getUUID(), GossipType.MINOR_POSITIVE, 25);
        } else if (type == ReputationEventType.TRADE) {
            this.gossips.add(source.getUUID(), GossipType.TRADING, 2);
        } else if (type == ReputationEventType.VILLAGER_HURT) {
            this.gossips.add(source.getUUID(), GossipType.MINOR_NEGATIVE, 25);
        } else if (type == ReputationEventType.VILLAGER_KILLED) {
            this.gossips.add(source.getUUID(), GossipType.MAJOR_NEGATIVE, 25);
        }

    }

    @Override
    public int getVillagerXp() {
        return this.villagerXp;
    }

    public void setVillagerXp(int value) {
        this.villagerXp = value;
    }

    private void resetNumberOfRestocks() {
        this.catchUpDemand();
        this.numberOfRestocksToday = 0;
    }

    public GossipContainer getGossips() {
        return this.gossips;
    }

    public void setGossips(GossipContainer gossips) {
        this.gossips.putAll(gossips);
    }

    @Override
    public void startSleeping(BlockPos bedPosition) {
        super.startSleeping(bedPosition);
        this.brain.setMemory(MemoryModuleType.LAST_SLEPT, this.level().getGameTime());
        this.brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        this.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    @Override
    public void stopSleeping() {
        super.stopSleeping();
        this.brain.setMemory(MemoryModuleType.LAST_WOKEN, this.level().getGameTime());
    }

    private boolean golemSpawnConditionsMet(long gameTime) {
        Optional<Long> optional = this.brain.<Long>getMemory(MemoryModuleType.LAST_SLEPT);

        return optional.filter((olong) -> {
            return gameTime - olong < 24000L;
        }).isPresent();
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.VILLAGER_VARIANT ? castComponentValue(type, this.getVillagerData().type()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.VILLAGER_VARIANT);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.VILLAGER_VARIANT) {
            Holder<VillagerType> holder = (Holder) castComponentValue(DataComponents.VILLAGER_VARIANT, value);

            this.setVillagerData(this.getVillagerData().withType(holder));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }
}
