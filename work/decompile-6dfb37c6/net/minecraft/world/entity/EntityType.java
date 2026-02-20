package net.minecraft.world.entity;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.DependantName;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.nautilus.Nautilus;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilus;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.entity.monster.skeleton.Parched;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.entity.vehicle.boat.ChestRaft;
import net.minecraft.world.entity.vehicle.boat.Raft;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.entity.vehicle.minecart.MinecartSpawner;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EntityType<T extends Entity> implements EntityTypeTest<Entity, T>, FeatureElement {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Holder.Reference<EntityType<?>> builtInRegistryHolder;
    public static final Codec<EntityType<?>> CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityType<?>> STREAM_CODEC = ByteBufCodecs.registry(Registries.ENTITY_TYPE);
    private static final float MAGIC_HORSE_WIDTH = 1.3964844F;
    private static final int DISPLAY_TRACKING_RANGE = 10;
    public static final EntityType<Boat> ACACIA_BOAT = register("acacia_boat", EntityType.Builder.of(boatFactory(() -> {
        return Items.ACACIA_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<ChestBoat> ACACIA_CHEST_BOAT = register("acacia_chest_boat", EntityType.Builder.of(chestBoatFactory(() -> {
        return Items.ACACIA_CHEST_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<Allay> ALLAY = register("allay", EntityType.Builder.of(Allay::new, MobCategory.CREATURE).sized(0.35F, 0.6F).eyeHeight(0.36F).ridingOffset(0.04F).clientTrackingRange(8).updateInterval(2));
    public static final EntityType<AreaEffectCloud> AREA_EFFECT_CLOUD = register("area_effect_cloud", EntityType.Builder.of(AreaEffectCloud::new, MobCategory.MISC).noLootTable().fireImmune().sized(6.0F, 0.5F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<Armadillo> ARMADILLO = register("armadillo", EntityType.Builder.of(Armadillo::new, MobCategory.CREATURE).sized(0.7F, 0.65F).eyeHeight(0.26F).clientTrackingRange(10));
    public static final EntityType<ArmorStand> ARMOR_STAND = register("armor_stand", EntityType.Builder.of(ArmorStand::new, MobCategory.MISC).sized(0.5F, 1.975F).eyeHeight(1.7775F).clientTrackingRange(10));
    public static final EntityType<Arrow> ARROW = register("arrow", EntityType.Builder.of(Arrow::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
    public static final EntityType<Axolotl> AXOLOTL = register("axolotl", EntityType.Builder.of(Axolotl::new, MobCategory.AXOLOTLS).sized(0.75F, 0.42F).eyeHeight(0.2751F).clientTrackingRange(10));
    public static final EntityType<ChestRaft> BAMBOO_CHEST_RAFT = register("bamboo_chest_raft", EntityType.Builder.of(chestRaftFactory(() -> {
        return Items.BAMBOO_CHEST_RAFT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<Raft> BAMBOO_RAFT = register("bamboo_raft", EntityType.Builder.of(raftFactory(() -> {
        return Items.BAMBOO_RAFT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<Bat> BAT = register("bat", EntityType.Builder.of(Bat::new, MobCategory.AMBIENT).sized(0.5F, 0.9F).eyeHeight(0.45F).clientTrackingRange(5));
    public static final EntityType<Bee> BEE = register("bee", EntityType.Builder.of(Bee::new, MobCategory.CREATURE).sized(0.7F, 0.6F).eyeHeight(0.3F).clientTrackingRange(8));
    public static final EntityType<Boat> BIRCH_BOAT = register("birch_boat", EntityType.Builder.of(boatFactory(() -> {
        return Items.BIRCH_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<ChestBoat> BIRCH_CHEST_BOAT = register("birch_chest_boat", EntityType.Builder.of(chestBoatFactory(() -> {
        return Items.BIRCH_CHEST_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<Blaze> BLAZE = register("blaze", EntityType.Builder.of(Blaze::new, MobCategory.MONSTER).fireImmune().sized(0.6F, 1.8F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Display.BlockDisplay> BLOCK_DISPLAY = register("block_display", EntityType.Builder.of(Display.BlockDisplay::new, MobCategory.MISC).noLootTable().sized(0.0F, 0.0F).clientTrackingRange(10).updateInterval(1));
    public static final EntityType<Bogged> BOGGED = register("bogged", EntityType.Builder.of(Bogged::new, MobCategory.MONSTER).sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Breeze> BREEZE = register("breeze", EntityType.Builder.of(Breeze::new, MobCategory.MONSTER).sized(0.6F, 1.77F).eyeHeight(1.3452F).clientTrackingRange(10).notInPeaceful());
    public static final EntityType<BreezeWindCharge> BREEZE_WIND_CHARGE = register("breeze_wind_charge", EntityType.Builder.of(BreezeWindCharge::new, MobCategory.MISC).noLootTable().sized(0.3125F, 0.3125F).eyeHeight(0.0F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Camel> CAMEL = register("camel", EntityType.Builder.of(Camel::new, MobCategory.CREATURE).sized(1.7F, 2.375F).eyeHeight(2.275F).clientTrackingRange(10));
    public static final EntityType<CamelHusk> CAMEL_HUSK = register("camel_husk", EntityType.Builder.of(CamelHusk::new, MobCategory.MONSTER).sized(1.7F, 2.375F).eyeHeight(2.275F).clientTrackingRange(10));
    public static final EntityType<Cat> CAT = register("cat", EntityType.Builder.of(Cat::new, MobCategory.CREATURE).sized(0.6F, 0.7F).eyeHeight(0.35F).passengerAttachments(0.5125F).clientTrackingRange(8));
    public static final EntityType<CaveSpider> CAVE_SPIDER = register("cave_spider", EntityType.Builder.of(CaveSpider::new, MobCategory.MONSTER).sized(0.7F, 0.5F).eyeHeight(0.45F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Boat> CHERRY_BOAT = register("cherry_boat", EntityType.Builder.of(boatFactory(() -> {
        return Items.CHERRY_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<ChestBoat> CHERRY_CHEST_BOAT = register("cherry_chest_boat", EntityType.Builder.of(chestBoatFactory(() -> {
        return Items.CHERRY_CHEST_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<MinecartChest> CHEST_MINECART = register("chest_minecart", EntityType.Builder.of(MinecartChest::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityType<Chicken> CHICKEN = register("chicken", EntityType.Builder.of(Chicken::new, MobCategory.CREATURE).sized(0.4F, 0.7F).eyeHeight(0.644F).passengerAttachments(new Vec3(0.0D, 0.7D, -0.1D)).clientTrackingRange(10));
    public static final EntityType<Cod> COD = register("cod", EntityType.Builder.of(Cod::new, MobCategory.WATER_AMBIENT).sized(0.5F, 0.3F).eyeHeight(0.195F).clientTrackingRange(4));
    public static final EntityType<CopperGolem> COPPER_GOLEM = register("copper_golem", EntityType.Builder.of(CopperGolem::new, MobCategory.MISC).sized(0.49F, 0.98F).eyeHeight(0.8125F).clientTrackingRange(10));
    public static final EntityType<MinecartCommandBlock> COMMAND_BLOCK_MINECART = register("command_block_minecart", EntityType.Builder.of(MinecartCommandBlock::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityType<Cow> COW = register("cow", EntityType.Builder.of(Cow::new, MobCategory.CREATURE).sized(0.9F, 1.4F).eyeHeight(1.3F).passengerAttachments(1.36875F).clientTrackingRange(10));
    public static final EntityType<Creaking> CREAKING = register("creaking", EntityType.Builder.of(Creaking::new, MobCategory.MONSTER).sized(0.9F, 2.7F).eyeHeight(2.3F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Creeper> CREEPER = register("creeper", EntityType.Builder.of(Creeper::new, MobCategory.MONSTER).sized(0.6F, 1.7F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Boat> DARK_OAK_BOAT = register("dark_oak_boat", EntityType.Builder.of(boatFactory(() -> {
        return Items.DARK_OAK_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<ChestBoat> DARK_OAK_CHEST_BOAT = register("dark_oak_chest_boat", EntityType.Builder.of(chestBoatFactory(() -> {
        return Items.DARK_OAK_CHEST_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<Dolphin> DOLPHIN = register("dolphin", EntityType.Builder.of(Dolphin::new, MobCategory.WATER_CREATURE).sized(0.9F, 0.6F).eyeHeight(0.3F));
    public static final EntityType<Donkey> DONKEY = register("donkey", EntityType.Builder.of(Donkey::new, MobCategory.CREATURE).sized(1.3964844F, 1.5F).eyeHeight(1.425F).passengerAttachments(1.1125F).clientTrackingRange(10));
    public static final EntityType<DragonFireball> DRAGON_FIREBALL = register("dragon_fireball", EntityType.Builder.of(DragonFireball::new, MobCategory.MISC).noLootTable().sized(1.0F, 1.0F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Drowned> DROWNED = register("drowned", EntityType.Builder.of(Drowned::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<ThrownEgg> EGG = register("egg", EntityType.Builder.of(ThrownEgg::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<ElderGuardian> ELDER_GUARDIAN = register("elder_guardian", EntityType.Builder.of(ElderGuardian::new, MobCategory.MONSTER).sized(1.9975F, 1.9975F).eyeHeight(0.99875F).passengerAttachments(2.350625F).clientTrackingRange(10).notInPeaceful());
    public static final EntityType<EnderMan> ENDERMAN = register("enderman", EntityType.Builder.of(EnderMan::new, MobCategory.MONSTER).sized(0.6F, 2.9F).eyeHeight(2.55F).passengerAttachments(2.80625F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Endermite> ENDERMITE = register("endermite", EntityType.Builder.of(Endermite::new, MobCategory.MONSTER).sized(0.4F, 0.3F).eyeHeight(0.13F).passengerAttachments(0.2375F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<EnderDragon> ENDER_DRAGON = register("ender_dragon", EntityType.Builder.of(EnderDragon::new, MobCategory.MONSTER).fireImmune().sized(16.0F, 8.0F).passengerAttachments(3.0F).clientTrackingRange(10));
    public static final EntityType<ThrownEnderpearl> ENDER_PEARL = register("ender_pearl", EntityType.Builder.of(ThrownEnderpearl::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<EndCrystal> END_CRYSTAL = register("end_crystal", EntityType.Builder.of(EndCrystal::new, MobCategory.MISC).noLootTable().fireImmune().sized(2.0F, 2.0F).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<Evoker> EVOKER = register("evoker", EntityType.Builder.of(Evoker::new, MobCategory.MONSTER).sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<EvokerFangs> EVOKER_FANGS = register("evoker_fangs", EntityType.Builder.of(EvokerFangs::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.8F).clientTrackingRange(6).updateInterval(2));
    public static final EntityType<ThrownExperienceBottle> EXPERIENCE_BOTTLE = register("experience_bottle", EntityType.Builder.of(ThrownExperienceBottle::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<ExperienceOrb> EXPERIENCE_ORB = register("experience_orb", EntityType.Builder.of(ExperienceOrb::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(20));
    public static final EntityType<EyeOfEnder> EYE_OF_ENDER = register("eye_of_ender", EntityType.Builder.of(EyeOfEnder::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(4));
    public static final EntityType<FallingBlockEntity> FALLING_BLOCK = register("falling_block", EntityType.Builder.of(FallingBlockEntity::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(20));
    public static final EntityType<LargeFireball> FIREBALL = register("fireball", EntityType.Builder.of(LargeFireball::new, MobCategory.MISC).noLootTable().sized(1.0F, 1.0F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<FireworkRocketEntity> FIREWORK_ROCKET = register("firework_rocket", EntityType.Builder.of(FireworkRocketEntity::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Fox> FOX = register("fox", EntityType.Builder.of(Fox::new, MobCategory.CREATURE).sized(0.6F, 0.7F).eyeHeight(0.4F).passengerAttachments(new Vec3(0.0D, 0.6375D, -0.25D)).clientTrackingRange(8).immuneTo(Blocks.SWEET_BERRY_BUSH));
    public static final EntityType<Frog> FROG = register("frog", EntityType.Builder.of(Frog::new, MobCategory.CREATURE).sized(0.5F, 0.5F).passengerAttachments(new Vec3(0.0D, 0.375D, -0.25D)).clientTrackingRange(10));
    public static final EntityType<MinecartFurnace> FURNACE_MINECART = register("furnace_minecart", EntityType.Builder.of(MinecartFurnace::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityType<Ghast> GHAST = register("ghast", EntityType.Builder.of(Ghast::new, MobCategory.MONSTER).fireImmune().sized(4.0F, 4.0F).eyeHeight(2.6F).passengerAttachments(4.0625F).ridingOffset(0.5F).clientTrackingRange(10).notInPeaceful());
    public static final EntityType<HappyGhast> HAPPY_GHAST = register("happy_ghast", EntityType.Builder.of(HappyGhast::new, MobCategory.CREATURE).sized(4.0F, 4.0F).eyeHeight(2.6F).passengerAttachments(new Vec3(0.0D, 4.0D, 1.7D), new Vec3(-1.7D, 4.0D, 0.0D), new Vec3(0.0D, 4.0D, -1.7D), new Vec3(1.7D, 4.0D, 0.0D)).ridingOffset(0.5F).clientTrackingRange(10));
    public static final EntityType<Giant> GIANT = register("giant", EntityType.Builder.of(Giant::new, MobCategory.MONSTER).sized(3.6F, 12.0F).eyeHeight(10.44F).ridingOffset(-3.75F).clientTrackingRange(10).notInPeaceful());
    public static final EntityType<GlowItemFrame> GLOW_ITEM_FRAME = register("glow_item_frame", EntityType.Builder.of(GlowItemFrame::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.0F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<GlowSquid> GLOW_SQUID = register("glow_squid", EntityType.Builder.of(GlowSquid::new, MobCategory.UNDERGROUND_WATER_CREATURE).sized(0.8F, 0.8F).eyeHeight(0.4F).clientTrackingRange(10));
    public static final EntityType<Goat> GOAT = register("goat", EntityType.Builder.of(Goat::new, MobCategory.CREATURE).sized(0.9F, 1.3F).passengerAttachments(1.1125F).clientTrackingRange(10));
    public static final EntityType<Guardian> GUARDIAN = register("guardian", EntityType.Builder.of(Guardian::new, MobCategory.MONSTER).sized(0.85F, 0.85F).eyeHeight(0.425F).passengerAttachments(0.975F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Hoglin> HOGLIN = register("hoglin", EntityType.Builder.of(Hoglin::new, MobCategory.MONSTER).sized(1.3964844F, 1.4F).passengerAttachments(1.49375F).clientTrackingRange(8));
    public static final EntityType<MinecartHopper> HOPPER_MINECART = register("hopper_minecart", EntityType.Builder.of(MinecartHopper::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityType<Horse> HORSE = register("horse", EntityType.Builder.of(Horse::new, MobCategory.CREATURE).sized(1.3964844F, 1.6F).eyeHeight(1.52F).passengerAttachments(1.44375F).clientTrackingRange(10));
    public static final EntityType<Husk> HUSK = register("husk", EntityType.Builder.of(Husk::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.075F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Illusioner> ILLUSIONER = register("illusioner", EntityType.Builder.of(Illusioner::new, MobCategory.MONSTER).sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Interaction> INTERACTION = register("interaction", EntityType.Builder.of(Interaction::new, MobCategory.MISC).noLootTable().sized(0.0F, 0.0F).clientTrackingRange(10));
    public static final EntityType<IronGolem> IRON_GOLEM = register("iron_golem", EntityType.Builder.of(IronGolem::new, MobCategory.MISC).sized(1.4F, 2.7F).clientTrackingRange(10));
    public static final EntityType<ItemEntity> ITEM = register("item", EntityType.Builder.of(ItemEntity::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).eyeHeight(0.2125F).clientTrackingRange(6).updateInterval(20));
    public static final EntityType<Display.ItemDisplay> ITEM_DISPLAY = register("item_display", EntityType.Builder.of(Display.ItemDisplay::new, MobCategory.MISC).noLootTable().sized(0.0F, 0.0F).clientTrackingRange(10).updateInterval(1));
    public static final EntityType<ItemFrame> ITEM_FRAME = register("item_frame", EntityType.Builder.of(ItemFrame::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.0F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<Boat> JUNGLE_BOAT = register("jungle_boat", EntityType.Builder.of(boatFactory(() -> {
        return Items.JUNGLE_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<ChestBoat> JUNGLE_CHEST_BOAT = register("jungle_chest_boat", EntityType.Builder.of(chestBoatFactory(() -> {
        return Items.JUNGLE_CHEST_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<LeashFenceKnotEntity> LEASH_KNOT = register("leash_knot", EntityType.Builder.of(LeashFenceKnotEntity::new, MobCategory.MISC).noLootTable().noSave().sized(0.375F, 0.5F).eyeHeight(0.0625F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<LightningBolt> LIGHTNING_BOLT = register("lightning_bolt", EntityType.Builder.of(LightningBolt::new, MobCategory.MISC).noLootTable().noSave().sized(0.0F, 0.0F).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<Llama> LLAMA = register("llama", EntityType.Builder.of(Llama::new, MobCategory.CREATURE).sized(0.9F, 1.87F).eyeHeight(1.7765F).passengerAttachments(new Vec3(0.0D, 1.37D, -0.3D)).clientTrackingRange(10));
    public static final EntityType<LlamaSpit> LLAMA_SPIT = register("llama_spit", EntityType.Builder.of(LlamaSpit::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<MagmaCube> MAGMA_CUBE = register("magma_cube", EntityType.Builder.of(MagmaCube::new, MobCategory.MONSTER).fireImmune().sized(0.52F, 0.52F).eyeHeight(0.325F).spawnDimensionsScale(4.0F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Boat> MANGROVE_BOAT = register("mangrove_boat", EntityType.Builder.of(boatFactory(() -> {
        return Items.MANGROVE_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<ChestBoat> MANGROVE_CHEST_BOAT = register("mangrove_chest_boat", EntityType.Builder.of(chestBoatFactory(() -> {
        return Items.MANGROVE_CHEST_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<Mannequin> MANNEQUIN = register("mannequin", EntityType.Builder.of(Mannequin::create, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).vehicleAttachment(Avatar.DEFAULT_VEHICLE_ATTACHMENT).clientTrackingRange(32).updateInterval(2));
    public static final EntityType<Marker> MARKER = register("marker", EntityType.Builder.of(Marker::new, MobCategory.MISC).noLootTable().sized(0.0F, 0.0F).clientTrackingRange(0));
    public static final EntityType<Minecart> MINECART = register("minecart", EntityType.Builder.of(Minecart::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityType<MushroomCow> MOOSHROOM = register("mooshroom", EntityType.Builder.of(MushroomCow::new, MobCategory.CREATURE).sized(0.9F, 1.4F).eyeHeight(1.3F).passengerAttachments(1.36875F).clientTrackingRange(10));
    public static final EntityType<Mule> MULE = register("mule", EntityType.Builder.of(Mule::new, MobCategory.CREATURE).sized(1.3964844F, 1.6F).eyeHeight(1.52F).passengerAttachments(1.2125F).clientTrackingRange(8));
    public static final EntityType<Nautilus> NAUTILUS = register("nautilus", EntityType.Builder.of(Nautilus::new, MobCategory.WATER_CREATURE).sized(0.875F, 0.95F).passengerAttachments(1.1375F).eyeHeight(0.2751F).clientTrackingRange(10));
    public static final EntityType<Boat> OAK_BOAT = register("oak_boat", EntityType.Builder.of(boatFactory(() -> {
        return Items.OAK_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<ChestBoat> OAK_CHEST_BOAT = register("oak_chest_boat", EntityType.Builder.of(chestBoatFactory(() -> {
        return Items.OAK_CHEST_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<Ocelot> OCELOT = register("ocelot", EntityType.Builder.of(Ocelot::new, MobCategory.CREATURE).sized(0.6F, 0.7F).passengerAttachments(0.6375F).clientTrackingRange(10));
    public static final EntityType<OminousItemSpawner> OMINOUS_ITEM_SPAWNER = register("ominous_item_spawner", EntityType.Builder.of(OminousItemSpawner::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(8));
    public static final EntityType<Painting> PAINTING = register("painting", EntityType.Builder.of(Painting::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<Boat> PALE_OAK_BOAT = register("pale_oak_boat", EntityType.Builder.of(boatFactory(() -> {
        return Items.PALE_OAK_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<ChestBoat> PALE_OAK_CHEST_BOAT = register("pale_oak_chest_boat", EntityType.Builder.of(chestBoatFactory(() -> {
        return Items.PALE_OAK_CHEST_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<Panda> PANDA = register("panda", EntityType.Builder.of(Panda::new, MobCategory.CREATURE).sized(1.3F, 1.25F).clientTrackingRange(10));
    public static final EntityType<Parched> PARCHED = register("parched", EntityType.Builder.of(Parched::new, MobCategory.MONSTER).sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Parrot> PARROT = register("parrot", EntityType.Builder.of(Parrot::new, MobCategory.CREATURE).sized(0.5F, 0.9F).eyeHeight(0.54F).passengerAttachments(0.4625F).clientTrackingRange(8));
    public static final EntityType<Phantom> PHANTOM = register("phantom", EntityType.Builder.of(Phantom::new, MobCategory.MONSTER).sized(0.9F, 0.5F).eyeHeight(0.175F).passengerAttachments(0.3375F).ridingOffset(-0.125F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Pig> PIG = register("pig", EntityType.Builder.of(Pig::new, MobCategory.CREATURE).sized(0.9F, 0.9F).passengerAttachments(0.86875F).clientTrackingRange(10));
    public static final EntityType<Piglin> PIGLIN = register("piglin", EntityType.Builder.of(Piglin::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.79F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8));
    public static final EntityType<PiglinBrute> PIGLIN_BRUTE = register("piglin_brute", EntityType.Builder.of(PiglinBrute::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.79F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Pillager> PILLAGER = register("pillager", EntityType.Builder.of(Pillager::new, MobCategory.MONSTER).canSpawnFarFromPlayer().sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<PolarBear> POLAR_BEAR = register("polar_bear", EntityType.Builder.of(PolarBear::new, MobCategory.CREATURE).immuneTo(Blocks.POWDER_SNOW).sized(1.4F, 1.4F).clientTrackingRange(10));
    public static final EntityType<ThrownSplashPotion> SPLASH_POTION = register("splash_potion", EntityType.Builder.of(ThrownSplashPotion::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<ThrownLingeringPotion> LINGERING_POTION = register("lingering_potion", EntityType.Builder.of(ThrownLingeringPotion::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Pufferfish> PUFFERFISH = register("pufferfish", EntityType.Builder.of(Pufferfish::new, MobCategory.WATER_AMBIENT).sized(0.7F, 0.7F).eyeHeight(0.455F).clientTrackingRange(4));
    public static final EntityType<Rabbit> RABBIT = register("rabbit", EntityType.Builder.of(Rabbit::new, MobCategory.CREATURE).sized(0.4F, 0.5F).clientTrackingRange(8));
    public static final EntityType<Ravager> RAVAGER = register("ravager", EntityType.Builder.of(Ravager::new, MobCategory.MONSTER).sized(1.95F, 2.2F).passengerAttachments(new Vec3(0.0D, 2.2625D, -0.0625D)).clientTrackingRange(10).notInPeaceful());
    public static final EntityType<Salmon> SALMON = register("salmon", EntityType.Builder.of(Salmon::new, MobCategory.WATER_AMBIENT).sized(0.7F, 0.4F).eyeHeight(0.26F).clientTrackingRange(4));
    public static final EntityType<Sheep> SHEEP = register("sheep", EntityType.Builder.of(Sheep::new, MobCategory.CREATURE).sized(0.9F, 1.3F).eyeHeight(1.235F).passengerAttachments(1.2375F).clientTrackingRange(10));
    public static final EntityType<Shulker> SHULKER = register("shulker", EntityType.Builder.of(Shulker::new, MobCategory.MONSTER).fireImmune().canSpawnFarFromPlayer().sized(1.0F, 1.0F).eyeHeight(0.5F).clientTrackingRange(10));
    public static final EntityType<ShulkerBullet> SHULKER_BULLET = register("shulker_bullet", EntityType.Builder.of(ShulkerBullet::new, MobCategory.MISC).noLootTable().sized(0.3125F, 0.3125F).clientTrackingRange(8));
    public static final EntityType<Silverfish> SILVERFISH = register("silverfish", EntityType.Builder.of(Silverfish::new, MobCategory.MONSTER).sized(0.4F, 0.3F).eyeHeight(0.13F).passengerAttachments(0.2375F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Skeleton> SKELETON = register("skeleton", EntityType.Builder.of(Skeleton::new, MobCategory.MONSTER).sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<SkeletonHorse> SKELETON_HORSE = register("skeleton_horse", EntityType.Builder.of(SkeletonHorse::new, MobCategory.CREATURE).sized(1.3964844F, 1.6F).eyeHeight(1.52F).passengerAttachments(1.31875F).clientTrackingRange(10));
    public static final EntityType<Slime> SLIME = register("slime", EntityType.Builder.of(Slime::new, MobCategory.MONSTER).sized(0.52F, 0.52F).eyeHeight(0.325F).spawnDimensionsScale(4.0F).clientTrackingRange(10).notInPeaceful());
    public static final EntityType<SmallFireball> SMALL_FIREBALL = register("small_fireball", EntityType.Builder.of(SmallFireball::new, MobCategory.MISC).noLootTable().sized(0.3125F, 0.3125F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Sniffer> SNIFFER = register("sniffer", EntityType.Builder.of(Sniffer::new, MobCategory.CREATURE).sized(1.9F, 1.75F).eyeHeight(1.05F).passengerAttachments(2.09375F).nameTagOffset(2.05F).clientTrackingRange(10));
    public static final EntityType<Snowball> SNOWBALL = register("snowball", EntityType.Builder.of(Snowball::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<SnowGolem> SNOW_GOLEM = register("snow_golem", EntityType.Builder.of(SnowGolem::new, MobCategory.MISC).immuneTo(Blocks.POWDER_SNOW).sized(0.7F, 1.9F).eyeHeight(1.7F).clientTrackingRange(8));
    public static final EntityType<MinecartSpawner> SPAWNER_MINECART = register("spawner_minecart", EntityType.Builder.of(MinecartSpawner::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityType<SpectralArrow> SPECTRAL_ARROW = register("spectral_arrow", EntityType.Builder.of(SpectralArrow::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
    public static final EntityType<Spider> SPIDER = register("spider", EntityType.Builder.of(Spider::new, MobCategory.MONSTER).sized(1.4F, 0.9F).eyeHeight(0.65F).passengerAttachments(0.765F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Boat> SPRUCE_BOAT = register("spruce_boat", EntityType.Builder.of(boatFactory(() -> {
        return Items.SPRUCE_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<ChestBoat> SPRUCE_CHEST_BOAT = register("spruce_chest_boat", EntityType.Builder.of(chestBoatFactory(() -> {
        return Items.SPRUCE_CHEST_BOAT;
    }), MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityType<Squid> SQUID = register("squid", EntityType.Builder.of(Squid::new, MobCategory.WATER_CREATURE).sized(0.8F, 0.8F).eyeHeight(0.4F).clientTrackingRange(8));
    public static final EntityType<Stray> STRAY = register("stray", EntityType.Builder.of(Stray::new, MobCategory.MONSTER).sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F).immuneTo(Blocks.POWDER_SNOW).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Strider> STRIDER = register("strider", EntityType.Builder.of(Strider::new, MobCategory.CREATURE).fireImmune().sized(0.9F, 1.7F).clientTrackingRange(10));
    public static final EntityType<Tadpole> TADPOLE = register("tadpole", EntityType.Builder.of(Tadpole::new, MobCategory.CREATURE).sized(0.4F, 0.3F).eyeHeight(0.19500001F).clientTrackingRange(10));
    public static final EntityType<Display.TextDisplay> TEXT_DISPLAY = register("text_display", EntityType.Builder.of(Display.TextDisplay::new, MobCategory.MISC).noLootTable().sized(0.0F, 0.0F).clientTrackingRange(10).updateInterval(1));
    public static final EntityType<PrimedTnt> TNT = register("tnt", EntityType.Builder.of(PrimedTnt::new, MobCategory.MISC).noLootTable().fireImmune().sized(0.98F, 0.98F).eyeHeight(0.15F).clientTrackingRange(10).updateInterval(10));
    public static final EntityType<MinecartTNT> TNT_MINECART = register("tnt_minecart", EntityType.Builder.of(MinecartTNT::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityType<TraderLlama> TRADER_LLAMA = register("trader_llama", EntityType.Builder.of(TraderLlama::new, MobCategory.CREATURE).sized(0.9F, 1.87F).eyeHeight(1.7765F).passengerAttachments(new Vec3(0.0D, 1.37D, -0.3D)).clientTrackingRange(10));
    public static final EntityType<ThrownTrident> TRIDENT = register("trident", EntityType.Builder.of(ThrownTrident::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
    public static final EntityType<TropicalFish> TROPICAL_FISH = register("tropical_fish", EntityType.Builder.of(TropicalFish::new, MobCategory.WATER_AMBIENT).sized(0.5F, 0.4F).eyeHeight(0.26F).clientTrackingRange(4));
    public static final EntityType<Turtle> TURTLE = register("turtle", EntityType.Builder.of(Turtle::new, MobCategory.CREATURE).sized(1.2F, 0.4F).passengerAttachments(new Vec3(0.0D, 0.55625D, -0.25D)).clientTrackingRange(10));
    public static final EntityType<Vex> VEX = register("vex", EntityType.Builder.of(Vex::new, MobCategory.MONSTER).fireImmune().sized(0.4F, 0.8F).eyeHeight(0.51875F).passengerAttachments(0.7375F).ridingOffset(0.04F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Villager> VILLAGER = register("villager", EntityType.Builder.of(Villager::new, MobCategory.MISC).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
    public static final EntityType<Vindicator> VINDICATOR = register("vindicator", EntityType.Builder.of(Vindicator::new, MobCategory.MONSTER).sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<WanderingTrader> WANDERING_TRADER = register("wandering_trader", EntityType.Builder.of(WanderingTrader::new, MobCategory.CREATURE).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
    public static final EntityType<Warden> WARDEN = register("warden", EntityType.Builder.of(Warden::new, MobCategory.MONSTER).sized(0.9F, 2.9F).passengerAttachments(3.15F).attach(EntityAttachment.WARDEN_CHEST, 0.0F, 1.6F, 0.0F).clientTrackingRange(16).fireImmune().notInPeaceful());
    public static final EntityType<WindCharge> WIND_CHARGE = register("wind_charge", EntityType.Builder.of(WindCharge::new, MobCategory.MISC).noLootTable().sized(0.3125F, 0.3125F).eyeHeight(0.0F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Witch> WITCH = register("witch", EntityType.Builder.of(Witch::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.62F).passengerAttachments(2.2625F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<WitherBoss> WITHER = register("wither", EntityType.Builder.of(WitherBoss::new, MobCategory.MONSTER).fireImmune().immuneTo(Blocks.WITHER_ROSE).sized(0.9F, 3.5F).clientTrackingRange(10).notInPeaceful());
    public static final EntityType<WitherSkeleton> WITHER_SKELETON = register("wither_skeleton", EntityType.Builder.of(WitherSkeleton::new, MobCategory.MONSTER).fireImmune().immuneTo(Blocks.WITHER_ROSE).sized(0.7F, 2.4F).eyeHeight(2.1F).ridingOffset(-0.875F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<WitherSkull> WITHER_SKULL = register("wither_skull", EntityType.Builder.of(WitherSkull::new, MobCategory.MISC).noLootTable().sized(0.3125F, 0.3125F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Wolf> WOLF = register("wolf", EntityType.Builder.of(Wolf::new, MobCategory.CREATURE).sized(0.6F, 0.85F).eyeHeight(0.68F).passengerAttachments(new Vec3(0.0D, 0.81875D, -0.0625D)).clientTrackingRange(10));
    public static final EntityType<Zoglin> ZOGLIN = register("zoglin", EntityType.Builder.of(Zoglin::new, MobCategory.MONSTER).fireImmune().sized(1.3964844F, 1.4F).passengerAttachments(1.49375F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Zombie> ZOMBIE = register("zombie", EntityType.Builder.of(Zombie::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<ZombieHorse> ZOMBIE_HORSE = register("zombie_horse", EntityType.Builder.of(ZombieHorse::new, MobCategory.MONSTER).sized(1.3964844F, 1.6F).eyeHeight(1.52F).passengerAttachments(1.31875F).clientTrackingRange(10));
    public static final EntityType<ZombieNautilus> ZOMBIE_NAUTILUS = register("zombie_nautilus", EntityType.Builder.of(ZombieNautilus::new, MobCategory.MONSTER).sized(0.875F, 0.95F).passengerAttachments(1.1375F).eyeHeight(0.2751F).clientTrackingRange(10));
    public static final EntityType<ZombieVillager> ZOMBIE_VILLAGER = register("zombie_villager", EntityType.Builder.of(ZombieVillager::new, MobCategory.MONSTER).sized(0.6F, 1.95F).passengerAttachments(2.125F).ridingOffset(-0.7F).eyeHeight(1.74F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<ZombifiedPiglin> ZOMBIFIED_PIGLIN = register("zombified_piglin", EntityType.Builder.of(ZombifiedPiglin::new, MobCategory.MONSTER).fireImmune().sized(0.6F, 1.95F).eyeHeight(1.79F).passengerAttachments(2.0F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<Player> PLAYER = register("player", EntityType.Builder.createNothing(MobCategory.MISC).noSave().noSummon().sized(0.6F, 1.8F).eyeHeight(1.62F).vehicleAttachment(Avatar.DEFAULT_VEHICLE_ATTACHMENT).clientTrackingRange(32).updateInterval(2));
    public static final EntityType<FishingHook> FISHING_BOBBER = register("fishing_bobber", EntityType.Builder.of(FishingHook::new, MobCategory.MISC).noLootTable().noSave().noSummon().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(5));
    private static final Set<EntityType<?>> OP_ONLY_CUSTOM_DATA = Set.of(EntityType.FALLING_BLOCK, EntityType.COMMAND_BLOCK_MINECART, EntityType.SPAWNER_MINECART);
    private final EntityType.EntityFactory<T> factory;
    private final MobCategory category;
    private final ImmutableSet<Block> immuneTo;
    private final boolean serialize;
    private final boolean summon;
    private final boolean fireImmune;
    private final boolean canSpawnFarFromPlayer;
    private final int clientTrackingRange;
    private final int updateInterval;
    private final String descriptionId;
    private @Nullable Component description;
    private final Optional<ResourceKey<LootTable>> lootTable;
    private final EntityDimensions dimensions;
    private final float spawnDimensionsScale;
    private final FeatureFlagSet requiredFeatures;
    private final boolean allowedInPeaceful;

    private static <T extends Entity> EntityType<T> register(ResourceKey<EntityType<?>> id, EntityType.Builder<T> builder) {
        return (EntityType) Registry.register(BuiltInRegistries.ENTITY_TYPE, id, builder.build(id));
    }

    private static ResourceKey<EntityType<?>> vanillaEntityId(String vanillaId) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.withDefaultNamespace(vanillaId));
    }

    private static <T extends Entity> EntityType<T> register(String vanillaId, EntityType.Builder<T> builder) {
        return register(vanillaEntityId(vanillaId), builder);
    }

    public static Identifier getKey(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    public static Optional<EntityType<?>> byString(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(id));
    }

    public EntityType(EntityType.EntityFactory<T> factory, MobCategory category, boolean serialize, boolean summon, boolean fireImmune, boolean canSpawnFarFromPlayer, ImmutableSet<Block> immuneTo, EntityDimensions dimensions, float spawnDimensionsScale, int clientTrackingRange, int updateInterval, String descriptionId, Optional<ResourceKey<LootTable>> lootTable, FeatureFlagSet requiredFeatures, boolean allowedInPeaceful) {
        this.builtInRegistryHolder = BuiltInRegistries.ENTITY_TYPE.createIntrusiveHolder(this);
        this.factory = factory;
        this.category = category;
        this.canSpawnFarFromPlayer = canSpawnFarFromPlayer;
        this.serialize = serialize;
        this.summon = summon;
        this.fireImmune = fireImmune;
        this.immuneTo = immuneTo;
        this.dimensions = dimensions;
        this.spawnDimensionsScale = spawnDimensionsScale;
        this.clientTrackingRange = clientTrackingRange;
        this.updateInterval = updateInterval;
        this.descriptionId = descriptionId;
        this.lootTable = lootTable;
        this.requiredFeatures = requiredFeatures;
        this.allowedInPeaceful = allowedInPeaceful;
    }

    public @Nullable T spawn(ServerLevel level, @Nullable ItemStack itemStack, @Nullable LivingEntity user, BlockPos spawnPos, EntitySpawnReason spawnReason, boolean tryMoveDown, boolean movedUp) {
        Consumer<T> consumer;

        if (itemStack != null) {
            consumer = createDefaultStackConfig(level, itemStack, user);
        } else {
            consumer = (entity) -> {
            };
        }

        return (T) this.spawn(level, consumer, spawnPos, spawnReason, tryMoveDown, movedUp);
    }

    public static <T extends Entity> Consumer<T> createDefaultStackConfig(Level level, ItemStack itemStack, @Nullable LivingEntity user) {
        return appendDefaultStackConfig((entity) -> {
        }, level, itemStack, user);
    }

    public static <T extends Entity> Consumer<T> appendDefaultStackConfig(Consumer<T> initialConfig, Level level, ItemStack itemStack, @Nullable LivingEntity user) {
        return appendCustomEntityStackConfig(appendComponentsConfig(initialConfig, itemStack), level, itemStack, user);
    }

    public static <T extends Entity> Consumer<T> appendComponentsConfig(Consumer<T> initialConfig, ItemStack itemStack) {
        return initialConfig.andThen((entity) -> {
            entity.applyComponentsFromItemStack(itemStack);
        });
    }

    public static <T extends Entity> Consumer<T> appendCustomEntityStackConfig(Consumer<T> initialConfig, Level level, ItemStack itemStack, @Nullable LivingEntity user) {
        TypedEntityData<EntityType<?>> typedentitydata = (TypedEntityData) itemStack.get(DataComponents.ENTITY_DATA);

        return typedentitydata != null ? initialConfig.andThen((entity) -> {
            updateCustomEntityTag(level, user, entity, typedentitydata);
        }) : initialConfig;
    }

    public @Nullable T spawn(ServerLevel level, BlockPos spawnPos, EntitySpawnReason spawnReason) {
        return (T) this.spawn(level, (Consumer) null, spawnPos, spawnReason, false, false);
    }

    public @Nullable T spawn(ServerLevel level, @Nullable Consumer<T> postSpawnConfig, BlockPos spawnPos, EntitySpawnReason spawnReason, boolean tryMoveDown, boolean movedUp) {
        T t0 = this.create(level, postSpawnConfig, spawnPos, spawnReason, tryMoveDown, movedUp);

        if (t0 != null) {
            level.addFreshEntityWithPassengers(t0);
            if (t0 instanceof Mob) {
                Mob mob = (Mob) t0;

                mob.playAmbientSound();
            }
        }

        return t0;
    }

    public @Nullable T create(ServerLevel level, @Nullable Consumer<T> postSpawnConfig, BlockPos spawnPos, EntitySpawnReason spawnReason, boolean tryMoveDown, boolean movedUp) {
        T t0 = this.create(level, spawnReason);

        if (t0 == null) {
            return null;
        } else {
            double d0;

            if (tryMoveDown) {
                t0.setPos((double) spawnPos.getX() + 0.5D, (double) (spawnPos.getY() + 1), (double) spawnPos.getZ() + 0.5D);
                d0 = getYOffset(level, spawnPos, movedUp, t0.getBoundingBox());
            } else {
                d0 = 0.0D;
            }

            t0.snapTo((double) spawnPos.getX() + 0.5D, (double) spawnPos.getY() + d0, (double) spawnPos.getZ() + 0.5D, Mth.wrapDegrees(level.random.nextFloat() * 360.0F), 0.0F);
            if (t0 instanceof Mob) {
                Mob mob = (Mob) t0;

                mob.yHeadRot = mob.getYRot();
                mob.yBodyRot = mob.getYRot();
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), spawnReason, (SpawnGroupData) null);
            }

            if (postSpawnConfig != null) {
                postSpawnConfig.accept(t0);
            }

            return t0;
        }
    }

    protected static double getYOffset(LevelReader level, BlockPos spawnPos, boolean movedUp, AABB entityBox) {
        AABB aabb1 = new AABB(spawnPos);

        if (movedUp) {
            aabb1 = aabb1.expandTowards(0.0D, -1.0D, 0.0D);
        }

        Iterable<VoxelShape> iterable = level.getCollisions((Entity) null, aabb1);

        return 1.0D + Shapes.collide(Direction.Axis.Y, entityBox, iterable, movedUp ? -2.0D : -1.0D);
    }

    public static void updateCustomEntityTag(Level level, @Nullable LivingEntity user, @Nullable Entity entity, TypedEntityData<EntityType<?>> entityData) {
        MinecraftServer minecraftserver = level.getServer();

        if (minecraftserver != null && entity != null) {
            if (entity.getType() == entityData.type()) {
                if (!level.isClientSide() && entity.getType().onlyOpCanSetNbt()) {
                    if (!(user instanceof Player)) {
                        return;
                    }

                    Player player = (Player) user;

                    if (!minecraftserver.getPlayerList().isOp(player.nameAndId())) {
                        return;
                    }
                }

                entityData.loadInto(entity);
            }
        }
    }

    public boolean canSerialize() {
        return this.serialize;
    }

    public boolean canSummon() {
        return this.summon;
    }

    public boolean fireImmune() {
        return this.fireImmune;
    }

    public boolean canSpawnFarFromPlayer() {
        return this.canSpawnFarFromPlayer;
    }

    public MobCategory getCategory() {
        return this.category;
    }

    public String getDescriptionId() {
        return this.descriptionId;
    }

    public Component getDescription() {
        if (this.description == null) {
            this.description = Component.translatable(this.getDescriptionId());
        }

        return this.description;
    }

    public String toString() {
        return this.getDescriptionId();
    }

    public String toShortString() {
        int i = this.getDescriptionId().lastIndexOf(46);

        return i == -1 ? this.getDescriptionId() : this.getDescriptionId().substring(i + 1);
    }

    public Optional<ResourceKey<LootTable>> getDefaultLootTable() {
        return this.lootTable;
    }

    public float getWidth() {
        return this.dimensions.width();
    }

    public float getHeight() {
        return this.dimensions.height();
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    public @Nullable T create(Level level, EntitySpawnReason reason) {
        return (T) (!this.isEnabled(level.enabledFeatures()) ? null : this.factory.create(this, level));
    }

    public static Optional<Entity> create(ValueInput input, Level level, EntitySpawnReason reason) {
        return Util.<Entity>ifElse(by(input).map((entitytype) -> {
            return entitytype.create(level, reason);
        }), (entity) -> {
            entity.load(input);
        }, () -> {
            EntityType.LOGGER.warn("Skipping Entity with id {}", input.getStringOr("id", "[invalid]"));
        });
    }

    public static Optional<Entity> create(EntityType<?> type, ValueInput input, Level level, EntitySpawnReason reason) {
        Optional<Entity> optional = Optional.ofNullable(type.create(level, reason));

        optional.ifPresent((entity) -> {
            entity.load(input);
        });
        return optional;
    }

    public AABB getSpawnAABB(double x, double y, double z) {
        float f = this.spawnDimensionsScale * this.getWidth() / 2.0F;
        float f1 = this.spawnDimensionsScale * this.getHeight();

        return new AABB(x - (double) f, y, z - (double) f, x + (double) f, y + (double) f1, z + (double) f);
    }

    public boolean isBlockDangerous(BlockState state) {
        return this.immuneTo.contains(state.getBlock()) ? false : (!this.fireImmune && NodeEvaluator.isBurningBlock(state) ? true : state.is(Blocks.WITHER_ROSE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW));
    }

    public EntityDimensions getDimensions() {
        return this.dimensions;
    }

    public static Optional<EntityType<?>> by(ValueInput input) {
        return input.<EntityType<?>>read("id", EntityType.CODEC);
    }

    public static @Nullable Entity loadEntityRecursive(CompoundTag tag, Level level, EntitySpawnReason reason, EntityProcessor postLoad) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(EntityType.LOGGER)) {
            return loadEntityRecursive(TagValueInput.create(problemreporter_scopedcollector, level.registryAccess(), tag), level, reason, postLoad);
        }
    }

    public static @Nullable Entity loadEntityRecursive(EntityType<?> type, CompoundTag tag, Level level, EntitySpawnReason reason, EntityProcessor postLoad) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(EntityType.LOGGER)) {
            return loadEntityRecursive(type, TagValueInput.create(problemreporter_scopedcollector, level.registryAccess(), tag), level, reason, postLoad);
        }
    }

    public static @Nullable Entity loadEntityRecursive(ValueInput input, Level level, EntitySpawnReason reason, EntityProcessor postLoad) {
        Optional optional = loadStaticEntity(input, level, reason);

        Objects.requireNonNull(postLoad);
        return (Entity) optional.map(postLoad::process).map((entity) -> {
            return loadPassengersRecursive(entity, input, level, reason, postLoad);
        }).orElse((Object) null);
    }

    public static @Nullable Entity loadEntityRecursive(EntityType<?> type, ValueInput input, Level level, EntitySpawnReason reason, EntityProcessor postLoad) {
        Optional optional = loadStaticEntity(type, input, level, reason);

        Objects.requireNonNull(postLoad);
        return (Entity) optional.map(postLoad::process).map((entity) -> {
            return loadPassengersRecursive(entity, input, level, reason, postLoad);
        }).orElse((Object) null);
    }

    private static Entity loadPassengersRecursive(Entity entity, ValueInput input, Level level, EntitySpawnReason reason, EntityProcessor postLoad) {
        for (ValueInput valueinput1 : input.childrenListOrEmpty("Passengers")) {
            Entity entity1 = loadEntityRecursive(valueinput1, level, reason, postLoad);

            if (entity1 != null) {
                entity1.startRiding(entity, true, false);
            }
        }

        return entity;
    }

    public static Stream<Entity> loadEntitiesRecursive(ValueInput.ValueInputList entities, Level level, EntitySpawnReason reason) {
        return entities.stream().mapMulti((valueinput, consumer) -> {
            loadEntityRecursive(valueinput, level, reason, (entity) -> {
                consumer.accept(entity);
                return entity;
            });
        });
    }

    private static Optional<Entity> loadStaticEntity(ValueInput input, Level level, EntitySpawnReason reason) {
        try {
            return create(input, level, reason);
        } catch (RuntimeException runtimeexception) {
            EntityType.LOGGER.warn("Exception loading entity: ", runtimeexception);
            return Optional.empty();
        }
    }

    private static Optional<Entity> loadStaticEntity(EntityType<?> type, ValueInput input, Level level, EntitySpawnReason reason) {
        try {
            return create(type, input, level, reason);
        } catch (RuntimeException runtimeexception) {
            EntityType.LOGGER.warn("Exception loading entity: ", runtimeexception);
            return Optional.empty();
        }
    }

    public int clientTrackingRange() {
        return this.clientTrackingRange;
    }

    public int updateInterval() {
        return this.updateInterval;
    }

    public boolean trackDeltas() {
        return this != EntityType.PLAYER && this != EntityType.LLAMA_SPIT && this != EntityType.WITHER && this != EntityType.BAT && this != EntityType.ITEM_FRAME && this != EntityType.GLOW_ITEM_FRAME && this != EntityType.LEASH_KNOT && this != EntityType.PAINTING && this != EntityType.END_CRYSTAL && this != EntityType.EVOKER_FANGS;
    }

    public boolean is(TagKey<EntityType<?>> tag) {
        return this.builtInRegistryHolder.is(tag);
    }

    public boolean is(HolderSet<EntityType<?>> holderSet) {
        return holderSet.contains(this.builtInRegistryHolder);
    }

    public @Nullable T tryCast(Entity entity) {
        return (T) (entity.getType() == this ? entity : null);
    }

    @Override
    public Class<? extends Entity> getBaseClass() {
        return Entity.class;
    }

    /** @deprecated */
    @Deprecated
    public Holder.Reference<EntityType<?>> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    public boolean isAllowedInPeaceful() {
        return this.allowedInPeaceful;
    }

    private static EntityType.EntityFactory<Boat> boatFactory(Supplier<Item> boatItem) {
        return (entitytype, level) -> {
            return new Boat(entitytype, level, boatItem);
        };
    }

    private static EntityType.EntityFactory<ChestBoat> chestBoatFactory(Supplier<Item> dropItem) {
        return (entitytype, level) -> {
            return new ChestBoat(entitytype, level, dropItem);
        };
    }

    private static EntityType.EntityFactory<Raft> raftFactory(Supplier<Item> dropItem) {
        return (entitytype, level) -> {
            return new Raft(entitytype, level, dropItem);
        };
    }

    private static EntityType.EntityFactory<ChestRaft> chestRaftFactory(Supplier<Item> dropItem) {
        return (entitytype, level) -> {
            return new ChestRaft(entitytype, level, dropItem);
        };
    }

    public boolean onlyOpCanSetNbt() {
        return EntityType.OP_ONLY_CUSTOM_DATA.contains(this);
    }

    public static class Builder<T extends Entity> {

        private final EntityType.EntityFactory<T> factory;
        private final MobCategory category;
        private ImmutableSet<Block> immuneTo = ImmutableSet.of();
        private boolean serialize = true;
        private boolean summon = true;
        private boolean fireImmune;
        private boolean canSpawnFarFromPlayer;
        private int clientTrackingRange = 5;
        private int updateInterval = 3;
        private EntityDimensions dimensions = EntityDimensions.scalable(0.6F, 1.8F);
        private float spawnDimensionsScale = 1.0F;
        private EntityAttachments.Builder attachments = EntityAttachments.builder();
        private FeatureFlagSet requiredFeatures;
        private DependantName<EntityType<?>, Optional<ResourceKey<LootTable>>> lootTable;
        private final DependantName<EntityType<?>, String> descriptionId;
        private boolean allowedInPeaceful;

        private Builder(EntityType.EntityFactory<T> factory, MobCategory category) {
            this.requiredFeatures = FeatureFlags.VANILLA_SET;
            this.lootTable = (resourcekey) -> {
                return Optional.of(ResourceKey.create(Registries.LOOT_TABLE, resourcekey.identifier().withPrefix("entities/")));
            };
            this.descriptionId = (resourcekey) -> {
                return Util.makeDescriptionId("entity", resourcekey.identifier());
            };
            this.allowedInPeaceful = true;
            this.factory = factory;
            this.category = category;
            this.canSpawnFarFromPlayer = category == MobCategory.CREATURE || category == MobCategory.MISC;
        }

        public static <T extends Entity> EntityType.Builder<T> of(EntityType.EntityFactory<T> factory, MobCategory category) {
            return new EntityType.Builder<T>(factory, category);
        }

        public static <T extends Entity> EntityType.Builder<T> createNothing(MobCategory category) {
            return new EntityType.Builder<T>((entitytype, level) -> {
                return null;
            }, category);
        }

        public EntityType.Builder<T> sized(float width, float height) {
            this.dimensions = EntityDimensions.scalable(width, height);
            return this;
        }

        public EntityType.Builder<T> spawnDimensionsScale(float scale) {
            this.spawnDimensionsScale = scale;
            return this;
        }

        public EntityType.Builder<T> eyeHeight(float eyeHeight) {
            this.dimensions = this.dimensions.withEyeHeight(eyeHeight);
            return this;
        }

        public EntityType.Builder<T> passengerAttachments(float... offsetYs) {
            for (float f : offsetYs) {
                this.attachments = this.attachments.attach(EntityAttachment.PASSENGER, 0.0F, f, 0.0F);
            }

            return this;
        }

        public EntityType.Builder<T> passengerAttachments(Vec3... points) {
            for (Vec3 vec3 : points) {
                this.attachments = this.attachments.attach(EntityAttachment.PASSENGER, vec3);
            }

            return this;
        }

        public EntityType.Builder<T> vehicleAttachment(Vec3 point) {
            return this.attach(EntityAttachment.VEHICLE, point);
        }

        public EntityType.Builder<T> ridingOffset(float ridingOffset) {
            return this.attach(EntityAttachment.VEHICLE, 0.0F, -ridingOffset, 0.0F);
        }

        public EntityType.Builder<T> nameTagOffset(float nameTagOffset) {
            return this.attach(EntityAttachment.NAME_TAG, 0.0F, nameTagOffset, 0.0F);
        }

        public EntityType.Builder<T> attach(EntityAttachment attachment, float x, float y, float z) {
            this.attachments = this.attachments.attach(attachment, x, y, z);
            return this;
        }

        public EntityType.Builder<T> attach(EntityAttachment attachment, Vec3 point) {
            this.attachments = this.attachments.attach(attachment, point);
            return this;
        }

        public EntityType.Builder<T> noSummon() {
            this.summon = false;
            return this;
        }

        public EntityType.Builder<T> noSave() {
            this.serialize = false;
            return this;
        }

        public EntityType.Builder<T> fireImmune() {
            this.fireImmune = true;
            return this;
        }

        public EntityType.Builder<T> immuneTo(Block... blocks) {
            this.immuneTo = ImmutableSet.copyOf(blocks);
            return this;
        }

        public EntityType.Builder<T> canSpawnFarFromPlayer() {
            this.canSpawnFarFromPlayer = true;
            return this;
        }

        public EntityType.Builder<T> clientTrackingRange(int clientChunkRange) {
            this.clientTrackingRange = clientChunkRange;
            return this;
        }

        public EntityType.Builder<T> updateInterval(int updateInterval) {
            this.updateInterval = updateInterval;
            return this;
        }

        public EntityType.Builder<T> requiredFeatures(FeatureFlag... flags) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(flags);
            return this;
        }

        public EntityType.Builder<T> noLootTable() {
            this.lootTable = DependantName.<EntityType<?>, Optional<ResourceKey<LootTable>>>fixed(Optional.empty());
            return this;
        }

        public EntityType.Builder<T> notInPeaceful() {
            this.allowedInPeaceful = false;
            return this;
        }

        public EntityType<T> build(ResourceKey<EntityType<?>> name) {
            if (this.serialize) {
                Util.fetchChoiceType(References.ENTITY_TREE, name.identifier().toString());
            }

            return new EntityType<T>(this.factory, this.category, this.serialize, this.summon, this.fireImmune, this.canSpawnFarFromPlayer, this.immuneTo, this.dimensions.withAttachments(this.attachments), this.spawnDimensionsScale, this.clientTrackingRange, this.updateInterval, this.descriptionId.get(name), this.lootTable.get(name), this.requiredFeatures, this.allowedInPeaceful);
        }
    }

    @FunctionalInterface
    public interface EntityFactory<T extends Entity> {

        @Nullable
        T create(EntityType<T> entityType, Level level);
    }
}
