package net.minecraft.world.level.block;

import com.google.common.collect.UnmodifiableIterator;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.references.Items;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class Blocks {

    private static final BlockBehaviour.StatePredicate NOT_CLOSED_SHULKER = (blockstate, blockgetter, blockpos) -> {
        BlockEntity blockentity = blockgetter.getBlockEntity(blockpos);

        if (blockentity instanceof ShulkerBoxBlockEntity shulkerboxblockentity) {
            return shulkerboxblockentity.isClosed();
        } else {
            return true;
        }
    };
    private static final BlockBehaviour.StatePredicate NOT_EXTENDED_PISTON = (blockstate, blockgetter, blockpos) -> {
        return !(Boolean) blockstate.getValue(PistonBaseBlock.EXTENDED);
    };
    public static final Block AIR = register("air", AirBlock::new, BlockBehaviour.Properties.of().replaceable().noCollision().noLootTable().air());
    public static final Block STONE = register("stone", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block GRANITE = register("granite", BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block POLISHED_GRANITE = register("polished_granite", BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block DIORITE = register("diorite", BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block POLISHED_DIORITE = register("polished_diorite", BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block ANDESITE = register("andesite", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block POLISHED_ANDESITE = register("polished_andesite", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block GRASS_BLOCK = register("grass_block", GrassBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).randomTicks().strength(0.6F).sound(SoundType.GRASS));
    public static final Block DIRT = register("dirt", BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.GRAVEL));
    public static final Block COARSE_DIRT = register("coarse_dirt", BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.GRAVEL));
    public static final Block PODZOL = register("podzol", SnowyDirtBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).strength(0.5F).sound(SoundType.GRAVEL));
    public static final Block COBBLESTONE = register("cobblestone", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block OAK_PLANKS = register("oak_planks", BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block SPRUCE_PLANKS = register("spruce_planks", BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BIRCH_PLANKS = register("birch_planks", BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block JUNGLE_PLANKS = register("jungle_planks", BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block ACACIA_PLANKS = register("acacia_planks", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block CHERRY_PLANKS = register("cherry_planks", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.CHERRY_WOOD).ignitedByLava());
    public static final Block DARK_OAK_PLANKS = register("dark_oak_planks", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block PALE_OAK_WOOD = register("pale_oak_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block PALE_OAK_PLANKS = register("pale_oak_planks", BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block MANGROVE_PLANKS = register("mangrove_planks", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BAMBOO_PLANKS = register("bamboo_planks", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.BAMBOO_WOOD).ignitedByLava());
    public static final Block BAMBOO_MOSAIC = register("bamboo_mosaic", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.BAMBOO_WOOD).ignitedByLava());
    public static final Block OAK_SAPLING = register("oak_sapling", (blockbehaviour_properties) -> {
        return new SaplingBlock(TreeGrower.OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block SPRUCE_SAPLING = register("spruce_sapling", (blockbehaviour_properties) -> {
        return new SaplingBlock(TreeGrower.SPRUCE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block BIRCH_SAPLING = register("birch_sapling", (blockbehaviour_properties) -> {
        return new SaplingBlock(TreeGrower.BIRCH, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block JUNGLE_SAPLING = register("jungle_sapling", (blockbehaviour_properties) -> {
        return new SaplingBlock(TreeGrower.JUNGLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block ACACIA_SAPLING = register("acacia_sapling", (blockbehaviour_properties) -> {
        return new SaplingBlock(TreeGrower.ACACIA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block CHERRY_SAPLING = register("cherry_sapling", (blockbehaviour_properties) -> {
        return new SaplingBlock(TreeGrower.CHERRY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).noCollision().randomTicks().instabreak().sound(SoundType.CHERRY_SAPLING).pushReaction(PushReaction.DESTROY));
    public static final Block DARK_OAK_SAPLING = register("dark_oak_sapling", (blockbehaviour_properties) -> {
        return new SaplingBlock(TreeGrower.DARK_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block PALE_OAK_SAPLING = register("pale_oak_sapling", (blockbehaviour_properties) -> {
        return new SaplingBlock(TreeGrower.PALE_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block MANGROVE_PROPAGULE = register("mangrove_propagule", (blockbehaviour_properties) -> {
        return new MangrovePropaguleBlock(TreeGrower.MANGROVE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block BEDROCK = register("bedrock", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).strength(-1.0F, 3600000.0F).noLootTable().isValidSpawn(Blocks::never));
    public static final Block WATER = register("water", (blockbehaviour_properties) -> {
        return new LiquidBlock(Fluids.WATER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WATER).replaceable().noCollision().strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid().sound(SoundType.EMPTY));
    public static final Block LAVA = register("lava", (blockbehaviour_properties) -> {
        return new LiquidBlock(Fluids.LAVA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).replaceable().noCollision().randomTicks().strength(100.0F).lightLevel((blockstate) -> {
        return 15;
    }).pushReaction(PushReaction.DESTROY).noLootTable().liquid().sound(SoundType.EMPTY));
    public static final Block SAND = register("sand", (blockbehaviour_properties) -> {
        return new SandBlock(new ColorRGBA(14406560), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block SUSPICIOUS_SAND = register("suspicious_sand", (blockbehaviour_properties) -> {
        return new BrushableBlock(Blocks.SAND, SoundEvents.BRUSH_SAND, SoundEvents.BRUSH_SAND, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.SNARE).strength(0.25F).sound(SoundType.SUSPICIOUS_SAND).pushReaction(PushReaction.DESTROY));
    public static final Block RED_SAND = register("red_sand", (blockbehaviour_properties) -> {
        return new SandBlock(new ColorRGBA(11098145), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block GRAVEL = register("gravel", (blockbehaviour_properties) -> {
        return new ColoredFallingBlock(new ColorRGBA(-8356741), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.SNARE).strength(0.6F).sound(SoundType.GRAVEL));
    public static final Block SUSPICIOUS_GRAVEL = register("suspicious_gravel", (blockbehaviour_properties) -> {
        return new BrushableBlock(Blocks.GRAVEL, SoundEvents.BRUSH_GRAVEL, SoundEvents.BRUSH_GRAVEL, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.SNARE).strength(0.25F).sound(SoundType.SUSPICIOUS_GRAVEL).pushReaction(PushReaction.DESTROY));
    public static final Block GOLD_ORE = register("gold_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(ConstantInt.of(0), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final Block DEEPSLATE_GOLD_ORE = register("deepslate_gold_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(ConstantInt.of(0), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.GOLD_ORE).mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final Block IRON_ORE = register("iron_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(ConstantInt.of(0), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final Block DEEPSLATE_IRON_ORE = register("deepslate_iron_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(ConstantInt.of(0), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.IRON_ORE).mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final Block COAL_ORE = register("coal_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(UniformInt.of(0, 2), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final Block DEEPSLATE_COAL_ORE = register("deepslate_coal_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(UniformInt.of(0, 2), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.COAL_ORE).mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final Block NETHER_GOLD_ORE = register("nether_gold_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(UniformInt.of(0, 1), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F).sound(SoundType.NETHER_GOLD_ORE));
    public static final Block OAK_LOG = register("oak_log", RotatedPillarBlock::new, logProperties(MapColor.WOOD, MapColor.PODZOL, SoundType.WOOD));
    public static final Block SPRUCE_LOG = register("spruce_log", RotatedPillarBlock::new, logProperties(MapColor.PODZOL, MapColor.COLOR_BROWN, SoundType.WOOD));
    public static final Block BIRCH_LOG = register("birch_log", RotatedPillarBlock::new, logProperties(MapColor.SAND, MapColor.QUARTZ, SoundType.WOOD));
    public static final Block JUNGLE_LOG = register("jungle_log", RotatedPillarBlock::new, logProperties(MapColor.DIRT, MapColor.PODZOL, SoundType.WOOD));
    public static final Block ACACIA_LOG = register("acacia_log", RotatedPillarBlock::new, logProperties(MapColor.COLOR_ORANGE, MapColor.STONE, SoundType.WOOD));
    public static final Block CHERRY_LOG = register("cherry_log", RotatedPillarBlock::new, logProperties(MapColor.TERRACOTTA_WHITE, MapColor.TERRACOTTA_GRAY, SoundType.CHERRY_WOOD));
    public static final Block DARK_OAK_LOG = register("dark_oak_log", RotatedPillarBlock::new, logProperties(MapColor.COLOR_BROWN, MapColor.COLOR_BROWN, SoundType.WOOD));
    public static final Block PALE_OAK_LOG = register("pale_oak_log", RotatedPillarBlock::new, logProperties(Blocks.PALE_OAK_PLANKS.defaultMapColor(), Blocks.PALE_OAK_WOOD.defaultMapColor(), SoundType.WOOD));
    public static final Block MANGROVE_LOG = register("mangrove_log", RotatedPillarBlock::new, logProperties(MapColor.COLOR_RED, MapColor.PODZOL, SoundType.WOOD));
    public static final Block MANGROVE_ROOTS = register("mangrove_roots", MangroveRootsBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).instrument(NoteBlockInstrument.BASS).strength(0.7F).sound(SoundType.MANGROVE_ROOTS).noOcclusion().isSuffocating(Blocks::never).isViewBlocking(Blocks::never).noOcclusion().ignitedByLava());
    public static final Block MUDDY_MANGROVE_ROOTS = register("muddy_mangrove_roots", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).strength(0.7F).sound(SoundType.MUDDY_MANGROVE_ROOTS));
    public static final Block BAMBOO_BLOCK = register("bamboo_block", RotatedPillarBlock::new, logProperties(MapColor.COLOR_YELLOW, MapColor.PLANT, SoundType.BAMBOO_WOOD));
    public static final Block STRIPPED_SPRUCE_LOG = register("stripped_spruce_log", RotatedPillarBlock::new, logProperties(MapColor.PODZOL, MapColor.PODZOL, SoundType.WOOD));
    public static final Block STRIPPED_BIRCH_LOG = register("stripped_birch_log", RotatedPillarBlock::new, logProperties(MapColor.SAND, MapColor.SAND, SoundType.WOOD));
    public static final Block STRIPPED_JUNGLE_LOG = register("stripped_jungle_log", RotatedPillarBlock::new, logProperties(MapColor.DIRT, MapColor.DIRT, SoundType.WOOD));
    public static final Block STRIPPED_ACACIA_LOG = register("stripped_acacia_log", RotatedPillarBlock::new, logProperties(MapColor.COLOR_ORANGE, MapColor.COLOR_ORANGE, SoundType.WOOD));
    public static final Block STRIPPED_CHERRY_LOG = register("stripped_cherry_log", RotatedPillarBlock::new, logProperties(MapColor.TERRACOTTA_WHITE, MapColor.TERRACOTTA_PINK, SoundType.CHERRY_WOOD));
    public static final Block STRIPPED_DARK_OAK_LOG = register("stripped_dark_oak_log", RotatedPillarBlock::new, logProperties(MapColor.COLOR_BROWN, MapColor.COLOR_BROWN, SoundType.WOOD));
    public static final Block STRIPPED_PALE_OAK_LOG = register("stripped_pale_oak_log", RotatedPillarBlock::new, logProperties(Blocks.PALE_OAK_PLANKS.defaultMapColor(), Blocks.PALE_OAK_PLANKS.defaultMapColor(), SoundType.WOOD));
    public static final Block STRIPPED_OAK_LOG = register("stripped_oak_log", RotatedPillarBlock::new, logProperties(MapColor.WOOD, MapColor.WOOD, SoundType.WOOD));
    public static final Block STRIPPED_MANGROVE_LOG = register("stripped_mangrove_log", RotatedPillarBlock::new, logProperties(MapColor.COLOR_RED, MapColor.COLOR_RED, SoundType.WOOD));
    public static final Block STRIPPED_BAMBOO_BLOCK = register("stripped_bamboo_block", RotatedPillarBlock::new, logProperties(MapColor.COLOR_YELLOW, MapColor.COLOR_YELLOW, SoundType.BAMBOO_WOOD));
    public static final Block OAK_WOOD = register("oak_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block SPRUCE_WOOD = register("spruce_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BIRCH_WOOD = register("birch_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block JUNGLE_WOOD = register("jungle_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block ACACIA_WOOD = register("acacia_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block CHERRY_WOOD = register("cherry_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.CHERRY_WOOD).ignitedByLava());
    public static final Block DARK_OAK_WOOD = register("dark_oak_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block MANGROVE_WOOD = register("mangrove_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block STRIPPED_OAK_WOOD = register("stripped_oak_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block STRIPPED_SPRUCE_WOOD = register("stripped_spruce_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block STRIPPED_BIRCH_WOOD = register("stripped_birch_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block STRIPPED_JUNGLE_WOOD = register("stripped_jungle_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block STRIPPED_ACACIA_WOOD = register("stripped_acacia_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block STRIPPED_CHERRY_WOOD = register("stripped_cherry_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PINK).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.CHERRY_WOOD).ignitedByLava());
    public static final Block STRIPPED_DARK_OAK_WOOD = register("stripped_dark_oak_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block STRIPPED_PALE_OAK_WOOD = register("stripped_pale_oak_wood", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block STRIPPED_MANGROVE_WOOD = register("stripped_mangrove_wood", RotatedPillarBlock::new, logProperties(MapColor.COLOR_RED, MapColor.COLOR_RED, SoundType.WOOD));
    public static final Block OAK_LEAVES = register("oak_leaves", (blockbehaviour_properties) -> {
        return new TintedParticleLeavesBlock(0.01F, blockbehaviour_properties);
    }, leavesProperties(SoundType.GRASS));
    public static final Block SPRUCE_LEAVES = register("spruce_leaves", (blockbehaviour_properties) -> {
        return new TintedParticleLeavesBlock(0.01F, blockbehaviour_properties);
    }, leavesProperties(SoundType.GRASS));
    public static final Block BIRCH_LEAVES = register("birch_leaves", (blockbehaviour_properties) -> {
        return new TintedParticleLeavesBlock(0.01F, blockbehaviour_properties);
    }, leavesProperties(SoundType.GRASS));
    public static final Block JUNGLE_LEAVES = register("jungle_leaves", (blockbehaviour_properties) -> {
        return new TintedParticleLeavesBlock(0.01F, blockbehaviour_properties);
    }, leavesProperties(SoundType.GRASS));
    public static final Block ACACIA_LEAVES = register("acacia_leaves", (blockbehaviour_properties) -> {
        return new TintedParticleLeavesBlock(0.01F, blockbehaviour_properties);
    }, leavesProperties(SoundType.GRASS));
    public static final Block CHERRY_LEAVES = register("cherry_leaves", (blockbehaviour_properties) -> {
        return new UntintedParticleLeavesBlock(0.1F, ParticleTypes.CHERRY_LEAVES, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.2F).randomTicks().sound(SoundType.CHERRY_LEAVES).noOcclusion().isValidSpawn(Blocks::ocelotOrParrot).isSuffocating(Blocks::never).isViewBlocking(Blocks::never).ignitedByLava().pushReaction(PushReaction.DESTROY).isRedstoneConductor(Blocks::never));
    public static final Block DARK_OAK_LEAVES = register("dark_oak_leaves", (blockbehaviour_properties) -> {
        return new TintedParticleLeavesBlock(0.01F, blockbehaviour_properties);
    }, leavesProperties(SoundType.GRASS));
    public static final Block PALE_OAK_LEAVES = register("pale_oak_leaves", (blockbehaviour_properties) -> {
        return new UntintedParticleLeavesBlock(0.02F, ParticleTypes.PALE_OAK_LEAVES, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion().isValidSpawn(Blocks::ocelotOrParrot).isSuffocating(Blocks::never).isViewBlocking(Blocks::never).ignitedByLava().pushReaction(PushReaction.DESTROY).isRedstoneConductor(Blocks::never));
    public static final Block MANGROVE_LEAVES = register("mangrove_leaves", (blockbehaviour_properties) -> {
        return new MangroveLeavesBlock(0.01F, blockbehaviour_properties);
    }, leavesProperties(SoundType.GRASS));
    public static final Block AZALEA_LEAVES = register("azalea_leaves", (blockbehaviour_properties) -> {
        return new UntintedParticleLeavesBlock(0.01F, ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, -9399763), blockbehaviour_properties);
    }, leavesProperties(SoundType.AZALEA_LEAVES));
    public static final Block FLOWERING_AZALEA_LEAVES = register("flowering_azalea_leaves", (blockbehaviour_properties) -> {
        return new UntintedParticleLeavesBlock(0.01F, ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, -9399763), blockbehaviour_properties);
    }, leavesProperties(SoundType.AZALEA_LEAVES));
    public static final Block SPONGE = register("sponge", SpongeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.6F).sound(SoundType.SPONGE));
    public static final Block WET_SPONGE = register("wet_sponge", WetSpongeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.6F).sound(SoundType.WET_SPONGE));
    public static final Block GLASS = register("glass", TransparentBlock::new, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion().isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never).isViewBlocking(Blocks::never));
    public static final Block LAPIS_ORE = register("lapis_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(UniformInt.of(2, 5), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final Block DEEPSLATE_LAPIS_ORE = register("deepslate_lapis_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(UniformInt.of(2, 5), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.LAPIS_ORE).mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final Block LAPIS_BLOCK = register("lapis_block", BlockBehaviour.Properties.of().mapColor(MapColor.LAPIS).requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final Block DISPENSER = register("dispenser", DispenserBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.5F));
    public static final Block SANDSTONE = register("sandstone", BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.8F));
    public static final Block CHISELED_SANDSTONE = register("chiseled_sandstone", BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.8F));
    public static final Block CUT_SANDSTONE = register("cut_sandstone", BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.8F));
    public static final Block NOTE_BLOCK = register("note_block", NoteBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).sound(SoundType.WOOD).strength(0.8F).ignitedByLava());
    public static final Block WHITE_BED = registerBed("white_bed", DyeColor.WHITE);
    public static final Block ORANGE_BED = registerBed("orange_bed", DyeColor.ORANGE);
    public static final Block MAGENTA_BED = registerBed("magenta_bed", DyeColor.MAGENTA);
    public static final Block LIGHT_BLUE_BED = registerBed("light_blue_bed", DyeColor.LIGHT_BLUE);
    public static final Block YELLOW_BED = registerBed("yellow_bed", DyeColor.YELLOW);
    public static final Block LIME_BED = registerBed("lime_bed", DyeColor.LIME);
    public static final Block PINK_BED = registerBed("pink_bed", DyeColor.PINK);
    public static final Block GRAY_BED = registerBed("gray_bed", DyeColor.GRAY);
    public static final Block LIGHT_GRAY_BED = registerBed("light_gray_bed", DyeColor.LIGHT_GRAY);
    public static final Block CYAN_BED = registerBed("cyan_bed", DyeColor.CYAN);
    public static final Block PURPLE_BED = registerBed("purple_bed", DyeColor.PURPLE);
    public static final Block BLUE_BED = registerBed("blue_bed", DyeColor.BLUE);
    public static final Block BROWN_BED = registerBed("brown_bed", DyeColor.BROWN);
    public static final Block GREEN_BED = registerBed("green_bed", DyeColor.GREEN);
    public static final Block RED_BED = registerBed("red_bed", DyeColor.RED);
    public static final Block BLACK_BED = registerBed("black_bed", DyeColor.BLACK);
    public static final Block POWERED_RAIL = register("powered_rail", PoweredRailBlock::new, BlockBehaviour.Properties.of().noCollision().strength(0.7F).sound(SoundType.METAL));
    public static final Block DETECTOR_RAIL = register("detector_rail", DetectorRailBlock::new, BlockBehaviour.Properties.of().noCollision().strength(0.7F).sound(SoundType.METAL));
    public static final Block STICKY_PISTON = register("sticky_piston", (blockbehaviour_properties) -> {
        return new PistonBaseBlock(true, blockbehaviour_properties);
    }, pistonProperties());
    public static final Block COBWEB = register("cobweb", WebBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).sound(SoundType.COBWEB).forceSolidOn().noCollision().requiresCorrectToolForDrops().strength(4.0F).pushReaction(PushReaction.DESTROY));
    public static final Block SHORT_GRASS = register("short_grass", TallGrassBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XYZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block FERN = register("fern", TallGrassBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XYZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block DEAD_BUSH = register("dead_bush", DryVegetationBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).replaceable().noCollision().instabreak().sound(SoundType.GRASS).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block BUSH = register("bush", BushBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().instabreak().sound(SoundType.GRASS).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block SHORT_DRY_GRASS = register("short_dry_grass", ShortDryGrassBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).replaceable().noCollision().instabreak().sound(SoundType.GRASS).ignitedByLava().offsetType(BlockBehaviour.OffsetType.XYZ).pushReaction(PushReaction.DESTROY));
    public static final Block TALL_DRY_GRASS = register("tall_dry_grass", TallDryGrassBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).replaceable().noCollision().instabreak().sound(SoundType.GRASS).ignitedByLava().offsetType(BlockBehaviour.OffsetType.XYZ).pushReaction(PushReaction.DESTROY));
    public static final Block SEAGRASS = register("seagrass", SeagrassBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WATER).replaceable().noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block TALL_SEAGRASS = register("tall_seagrass", TallSeagrassBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WATER).replaceable().noCollision().instabreak().sound(SoundType.WET_GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block PISTON = register("piston", (blockbehaviour_properties) -> {
        return new PistonBaseBlock(false, blockbehaviour_properties);
    }, pistonProperties());
    public static final Block PISTON_HEAD = register("piston_head", PistonHeadBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F).noLootTable().pushReaction(PushReaction.BLOCK));
    public static final Block WHITE_WOOL = register("white_wool", BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block ORANGE_WOOL = register("orange_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block MAGENTA_WOOL = register("magenta_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block LIGHT_BLUE_WOOL = register("light_blue_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block YELLOW_WOOL = register("yellow_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block LIME_WOOL = register("lime_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block PINK_WOOL = register("pink_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block GRAY_WOOL = register("gray_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block LIGHT_GRAY_WOOL = register("light_gray_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block CYAN_WOOL = register("cyan_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block PURPLE_WOOL = register("purple_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block BLUE_WOOL = register("blue_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block BROWN_WOOL = register("brown_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block GREEN_WOOL = register("green_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block RED_WOOL = register("red_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block BLACK_WOOL = register("black_wool", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block MOVING_PISTON = register("moving_piston", MovingPistonBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).forceSolidOn().strength(-1.0F).dynamicShape().noLootTable().noOcclusion().isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never).isViewBlocking(Blocks::never).pushReaction(PushReaction.BLOCK));
    public static final Block DANDELION = register("dandelion", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.SATURATION, 0.35F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block TORCHFLOWER = register("torchflower", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.NIGHT_VISION, 5.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block POPPY = register("poppy", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.NIGHT_VISION, 5.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block BLUE_ORCHID = register("blue_orchid", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.SATURATION, 0.35F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block ALLIUM = register("allium", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.FIRE_RESISTANCE, 3.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block AZURE_BLUET = register("azure_bluet", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.BLINDNESS, 11.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block RED_TULIP = register("red_tulip", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.WEAKNESS, 7.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block ORANGE_TULIP = register("orange_tulip", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.WEAKNESS, 7.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block WHITE_TULIP = register("white_tulip", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.WEAKNESS, 7.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block PINK_TULIP = register("pink_tulip", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.WEAKNESS, 7.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block OXEYE_DAISY = register("oxeye_daisy", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.REGENERATION, 7.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block CORNFLOWER = register("cornflower", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.JUMP_BOOST, 5.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block WITHER_ROSE = register("wither_rose", (blockbehaviour_properties) -> {
        return new WitherRoseBlock(MobEffects.WITHER, 7.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block LILY_OF_THE_VALLEY = register("lily_of_the_valley", (blockbehaviour_properties) -> {
        return new FlowerBlock(MobEffects.POISON, 11.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block BROWN_MUSHROOM = register("brown_mushroom", (blockbehaviour_properties) -> {
        return new MushroomBlock(TreeFeatures.HUGE_BROWN_MUSHROOM, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).lightLevel((blockstate) -> {
        return 1;
    }).hasPostProcess(Blocks::always).pushReaction(PushReaction.DESTROY));
    public static final Block RED_MUSHROOM = register("red_mushroom", (blockbehaviour_properties) -> {
        return new MushroomBlock(TreeFeatures.HUGE_RED_MUSHROOM, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).hasPostProcess(Blocks::always).pushReaction(PushReaction.DESTROY));
    public static final Block GOLD_BLOCK = register("gold_block", BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).instrument(NoteBlockInstrument.BELL).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.METAL));
    public static final Block IRON_BLOCK = register("iron_block", BlockBehaviour.Properties.of().mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.IRON));
    public static final Block BRICKS = register("bricks", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block TNT = register("tnt", TntBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).instabreak().sound(SoundType.GRASS).ignitedByLava().isRedstoneConductor(Blocks::never));
    public static final Block BOOKSHELF = register("bookshelf", BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(1.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block CHISELED_BOOKSHELF = register("chiseled_bookshelf", ChiseledBookShelfBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(1.5F).sound(SoundType.CHISELED_BOOKSHELF).ignitedByLava());
    public static final Block ACACIA_SHELF = register("acacia_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.ACACIA_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block BAMBOO_SHELF = register("bamboo_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.BAMBOO_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block BIRCH_SHELF = register("birch_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.BIRCH_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block CHERRY_SHELF = register("cherry_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.CHERRY_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block CRIMSON_SHELF = register("crimson_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_STEM).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block DARK_OAK_SHELF = register("dark_oak_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.DARK_OAK_LOG.defaultMapColor()).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block JUNGLE_SHELF = register("jungle_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.JUNGLE_LOG.defaultMapColor()).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block MANGROVE_SHELF = register("mangrove_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.MANGROVE_LOG.defaultMapColor()).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block OAK_SHELF = register("oak_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.OAK_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block PALE_OAK_SHELF = register("pale_oak_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block SPRUCE_SHELF = register("spruce_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.SPRUCE_LOG.defaultMapColor()).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block WARPED_SHELF = register("warped_shelf", ShelfBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_STEM).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));
    public static final Block MOSSY_COBBLESTONE = register("mossy_cobblestone", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block OBSIDIAN = register("obsidian", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(50.0F, 1200.0F));
    public static final Block TORCH = register("torch", (blockbehaviour_properties) -> {
        return new TorchBlock(ParticleTypes.FLAME, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().noCollision().instabreak().lightLevel((blockstate) -> {
        return 14;
    }).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block WALL_TORCH = register("wall_torch", (blockbehaviour_properties) -> {
        return new WallTorchBlock(ParticleTypes.FLAME, blockbehaviour_properties);
    }, wallVariant(Blocks.TORCH, true).noCollision().instabreak().lightLevel((blockstate) -> {
        return 14;
    }).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block FIRE = register("fire", FireBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).replaceable().noCollision().instabreak().lightLevel((blockstate) -> {
        return 15;
    }).sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY));
    public static final Block SOUL_FIRE = register("soul_fire", SoulFireBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).replaceable().noCollision().instabreak().lightLevel((blockstate) -> {
        return 10;
    }).sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY));
    public static final Block SPAWNER = register("spawner", SpawnerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(5.0F).sound(SoundType.SPAWNER).noOcclusion());
    public static final Block CREAKING_HEART = register("creaking_heart", CreakingHeartBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).strength(10.0F).sound(SoundType.CREAKING_HEART));
    public static final Block OAK_STAIRS = registerLegacyStair("oak_stairs", Blocks.OAK_PLANKS);
    public static final Block CHEST = register("chest", (blockbehaviour_properties) -> {
        return new ChestBlock(() -> {
            return BlockEntityType.CHEST;
        }, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block REDSTONE_WIRE = register("redstone_wire", RedStoneWireBlock::new, BlockBehaviour.Properties.of().noCollision().instabreak().pushReaction(PushReaction.DESTROY));
    public static final Block DIAMOND_ORE = register("diamond_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(UniformInt.of(3, 7), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final Block DEEPSLATE_DIAMOND_ORE = register("deepslate_diamond_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(UniformInt.of(3, 7), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.DIAMOND_ORE).mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final Block DIAMOND_BLOCK = register("diamond_block", BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL));
    public static final Block CRAFTING_TABLE = register("crafting_table", CraftingTableBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block WHEAT = register("wheat", CropBlock::new, BlockBehaviour.Properties.of().mapColor((blockstate) -> {
        return (Integer) blockstate.getValue(CropBlock.AGE) >= 6 ? MapColor.COLOR_YELLOW : MapColor.PLANT;
    }).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
    public static final Block FARMLAND = register("farmland", FarmBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).randomTicks().strength(0.6F).sound(SoundType.GRAVEL).isViewBlocking(Blocks::always).isSuffocating(Blocks::always));
    public static final Block FURNACE = register("furnace", FurnaceBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.5F).lightLevel(litBlockEmission(13)));
    public static final Block OAK_SIGN = register("oak_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block SPRUCE_SIGN = register("spruce_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.SPRUCE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.SPRUCE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block BIRCH_SIGN = register("birch_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.BIRCH, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block ACACIA_SIGN = register("acacia_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.ACACIA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block CHERRY_SIGN = register("cherry_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.CHERRY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.CHERRY_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block JUNGLE_SIGN = register("jungle_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.JUNGLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.JUNGLE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block DARK_OAK_SIGN = register("dark_oak_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.DARK_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.DARK_OAK_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block PALE_OAK_SIGN = register("pale_oak_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.PALE_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block MANGROVE_SIGN = register("mangrove_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.MANGROVE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.MANGROVE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block BAMBOO_SIGN = register("bamboo_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.BAMBOO, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.BAMBOO_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block OAK_DOOR = register("oak_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.OAK_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block LADDER = register("ladder", LadderBlock::new, BlockBehaviour.Properties.of().forceSolidOff().strength(0.4F).sound(SoundType.LADDER).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block RAIL = register("rail", RailBlock::new, BlockBehaviour.Properties.of().noCollision().strength(0.7F).sound(SoundType.METAL));
    public static final Block COBBLESTONE_STAIRS = registerLegacyStair("cobblestone_stairs", Blocks.COBBLESTONE);
    public static final Block OAK_WALL_SIGN = register("oak_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.OAK, blockbehaviour_properties);
    }, wallVariant(Blocks.OAK_SIGN, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block SPRUCE_WALL_SIGN = register("spruce_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.SPRUCE, blockbehaviour_properties);
    }, wallVariant(Blocks.SPRUCE_SIGN, true).mapColor(Blocks.SPRUCE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block BIRCH_WALL_SIGN = register("birch_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.BIRCH, blockbehaviour_properties);
    }, wallVariant(Blocks.BIRCH_SIGN, true).mapColor(MapColor.SAND).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block ACACIA_WALL_SIGN = register("acacia_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.ACACIA, blockbehaviour_properties);
    }, wallVariant(Blocks.ACACIA_SIGN, true).mapColor(MapColor.COLOR_ORANGE).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block CHERRY_WALL_SIGN = register("cherry_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.CHERRY, blockbehaviour_properties);
    }, wallVariant(Blocks.CHERRY_SIGN, true).mapColor(Blocks.CHERRY_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block JUNGLE_WALL_SIGN = register("jungle_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.JUNGLE, blockbehaviour_properties);
    }, wallVariant(Blocks.JUNGLE_SIGN, true).mapColor(Blocks.JUNGLE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block DARK_OAK_WALL_SIGN = register("dark_oak_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.DARK_OAK, blockbehaviour_properties);
    }, wallVariant(Blocks.DARK_OAK_SIGN, true).mapColor(Blocks.DARK_OAK_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block PALE_OAK_WALL_SIGN = register("pale_oak_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.PALE_OAK, blockbehaviour_properties);
    }, wallVariant(Blocks.PALE_OAK_SIGN, true).mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block MANGROVE_WALL_SIGN = register("mangrove_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.MANGROVE, blockbehaviour_properties);
    }, wallVariant(Blocks.MANGROVE_SIGN, true).mapColor(Blocks.MANGROVE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block BAMBOO_WALL_SIGN = register("bamboo_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.BAMBOO, blockbehaviour_properties);
    }, wallVariant(Blocks.BAMBOO_SIGN, true).mapColor(Blocks.BAMBOO_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block OAK_HANGING_SIGN = register("oak_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.OAK_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block SPRUCE_HANGING_SIGN = register("spruce_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.SPRUCE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.SPRUCE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block BIRCH_HANGING_SIGN = register("birch_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.BIRCH, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block ACACIA_HANGING_SIGN = register("acacia_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.ACACIA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block CHERRY_HANGING_SIGN = register("cherry_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.CHERRY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PINK).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block JUNGLE_HANGING_SIGN = register("jungle_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.JUNGLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.JUNGLE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block DARK_OAK_HANGING_SIGN = register("dark_oak_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.DARK_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.DARK_OAK_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block PALE_OAK_HANGING_SIGN = register("pale_oak_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.PALE_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block CRIMSON_HANGING_SIGN = register("crimson_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.CRIMSON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_STEM).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F));
    public static final Block WARPED_HANGING_SIGN = register("warped_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.WARPED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_STEM).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F));
    public static final Block MANGROVE_HANGING_SIGN = register("mangrove_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.MANGROVE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.MANGROVE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block BAMBOO_HANGING_SIGN = register("bamboo_hanging_sign", (blockbehaviour_properties) -> {
        return new CeilingHangingSignBlock(WoodType.BAMBOO, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block OAK_WALL_HANGING_SIGN = register("oak_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.OAK, blockbehaviour_properties);
    }, wallVariant(Blocks.OAK_HANGING_SIGN, true).mapColor(Blocks.OAK_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block SPRUCE_WALL_HANGING_SIGN = register("spruce_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.SPRUCE, blockbehaviour_properties);
    }, wallVariant(Blocks.SPRUCE_HANGING_SIGN, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block BIRCH_WALL_HANGING_SIGN = register("birch_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.BIRCH, blockbehaviour_properties);
    }, wallVariant(Blocks.BIRCH_HANGING_SIGN, true).mapColor(MapColor.SAND).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block ACACIA_WALL_HANGING_SIGN = register("acacia_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.ACACIA, blockbehaviour_properties);
    }, wallVariant(Blocks.ACACIA_HANGING_SIGN, true).mapColor(MapColor.COLOR_ORANGE).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block CHERRY_WALL_HANGING_SIGN = register("cherry_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.CHERRY, blockbehaviour_properties);
    }, wallVariant(Blocks.CHERRY_HANGING_SIGN, true).mapColor(MapColor.TERRACOTTA_PINK).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block JUNGLE_WALL_HANGING_SIGN = register("jungle_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.JUNGLE, blockbehaviour_properties);
    }, wallVariant(Blocks.JUNGLE_HANGING_SIGN, true).mapColor(Blocks.JUNGLE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block DARK_OAK_WALL_HANGING_SIGN = register("dark_oak_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.DARK_OAK, blockbehaviour_properties);
    }, wallVariant(Blocks.DARK_OAK_HANGING_SIGN, true).mapColor(Blocks.DARK_OAK_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block PALE_OAK_WALL_HANGING_SIGN = register("pale_oak_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.PALE_OAK, blockbehaviour_properties);
    }, wallVariant(Blocks.PALE_OAK_HANGING_SIGN, true).mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block MANGROVE_WALL_HANGING_SIGN = register("mangrove_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.MANGROVE, blockbehaviour_properties);
    }, wallVariant(Blocks.MANGROVE_HANGING_SIGN, true).mapColor(Blocks.MANGROVE_LOG.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block CRIMSON_WALL_HANGING_SIGN = register("crimson_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.CRIMSON, blockbehaviour_properties);
    }, wallVariant(Blocks.CRIMSON_HANGING_SIGN, true).mapColor(MapColor.CRIMSON_STEM).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F));
    public static final Block WARPED_WALL_HANGING_SIGN = register("warped_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.WARPED, blockbehaviour_properties);
    }, wallVariant(Blocks.WARPED_HANGING_SIGN, true).mapColor(MapColor.WARPED_STEM).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F));
    public static final Block BAMBOO_WALL_HANGING_SIGN = register("bamboo_wall_hanging_sign", (blockbehaviour_properties) -> {
        return new WallHangingSignBlock(WoodType.BAMBOO, blockbehaviour_properties);
    }, wallVariant(Blocks.BAMBOO_HANGING_SIGN, true).mapColor(MapColor.COLOR_YELLOW).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava());
    public static final Block LEVER = register("lever", LeverBlock::new, BlockBehaviour.Properties.of().noCollision().strength(0.5F).sound(SoundType.STONE).pushReaction(PushReaction.DESTROY));
    public static final Block STONE_PRESSURE_PLATE = register("stone_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.STONE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).noCollision().strength(0.5F).pushReaction(PushReaction.DESTROY));
    public static final Block IRON_DOOR = register("iron_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.IRON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block OAK_PRESSURE_PLATE = register("oak_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block SPRUCE_PRESSURE_PLATE = register("spruce_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.SPRUCE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.SPRUCE_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block BIRCH_PRESSURE_PLATE = register("birch_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.BIRCH, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.BIRCH_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block JUNGLE_PRESSURE_PLATE = register("jungle_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.JUNGLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.JUNGLE_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block ACACIA_PRESSURE_PLATE = register("acacia_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.ACACIA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.ACACIA_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block CHERRY_PRESSURE_PLATE = register("cherry_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.CHERRY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.CHERRY_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block DARK_OAK_PRESSURE_PLATE = register("dark_oak_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.DARK_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.DARK_OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block PALE_OAK_PRESSURE_PLATE = register("pale_oak_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.PALE_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block MANGROVE_PRESSURE_PLATE = register("mangrove_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.MANGROVE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.MANGROVE_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block BAMBOO_PRESSURE_PLATE = register("bamboo_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.BAMBOO, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.BAMBOO_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block REDSTONE_ORE = register("redstone_ore", RedStoneOreBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().randomTicks().lightLevel(litBlockEmission(9)).strength(3.0F, 3.0F));
    public static final Block DEEPSLATE_REDSTONE_ORE = register("deepslate_redstone_ore", RedStoneOreBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.REDSTONE_ORE).mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final Block REDSTONE_TORCH = register("redstone_torch", RedstoneTorchBlock::new, BlockBehaviour.Properties.of().noCollision().instabreak().lightLevel(litBlockEmission(7)).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block REDSTONE_WALL_TORCH = register("redstone_wall_torch", RedstoneWallTorchBlock::new, wallVariant(Blocks.REDSTONE_TORCH, true).noCollision().instabreak().lightLevel(litBlockEmission(7)).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block STONE_BUTTON = register("stone_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.STONE, 20, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block SNOW = register("snow", SnowLayerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).replaceable().forceSolidOff().randomTicks().strength(0.1F).requiresCorrectToolForDrops().sound(SoundType.SNOW).isViewBlocking((blockstate, blockgetter, blockpos) -> {
        return (Integer) blockstate.getValue(SnowLayerBlock.LAYERS) >= 8;
    }).pushReaction(PushReaction.DESTROY));
    public static final Block ICE = register("ice", IceBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.ICE).friction(0.98F).randomTicks().strength(0.5F).sound(SoundType.GLASS).noOcclusion().isValidSpawn((blockstate, blockgetter, blockpos, entitytype) -> {
        return entitytype == EntityType.POLAR_BEAR;
    }).isRedstoneConductor(Blocks::never));
    public static final Block SNOW_BLOCK = register("snow_block", BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).requiresCorrectToolForDrops().strength(0.2F).sound(SoundType.SNOW));
    public static final Block CACTUS = register("cactus", CactusBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).randomTicks().strength(0.4F).sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY));
    public static final Block CACTUS_FLOWER = register("cactus_flower", CactusFlowerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).noCollision().instabreak().ignitedByLava().sound(SoundType.CACTUS_FLOWER).pushReaction(PushReaction.DESTROY));
    public static final Block CLAY = register("clay", BlockBehaviour.Properties.of().mapColor(MapColor.CLAY).instrument(NoteBlockInstrument.FLUTE).strength(0.6F).sound(SoundType.GRAVEL));
    public static final Block SUGAR_CANE = register("sugar_cane", SugarCaneBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block JUKEBOX = register("jukebox", JukeboxBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.BASS).strength(2.0F, 6.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block OAK_FENCE = register("oak_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block NETHERRACK = register("netherrack", NetherrackBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.4F).sound(SoundType.NETHERRACK));
    public static final Block SOUL_SAND = register("soul_sand", SoulSandBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.COW_BELL).strength(0.5F).speedFactor(0.4F).sound(SoundType.SOUL_SAND).isValidSpawn(Blocks::always).isRedstoneConductor(Blocks::always).isViewBlocking(Blocks::always).isSuffocating(Blocks::always));
    public static final Block SOUL_SOIL = register("soul_soil", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.5F).sound(SoundType.SOUL_SOIL));
    public static final Block BASALT = register("basalt", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F).sound(SoundType.BASALT));
    public static final Block POLISHED_BASALT = register("polished_basalt", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F).sound(SoundType.BASALT));
    public static final Block SOUL_TORCH = register("soul_torch", (blockbehaviour_properties) -> {
        return new TorchBlock(ParticleTypes.SOUL_FIRE_FLAME, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().noCollision().instabreak().lightLevel((blockstate) -> {
        return 10;
    }).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block SOUL_WALL_TORCH = register("soul_wall_torch", (blockbehaviour_properties) -> {
        return new WallTorchBlock(ParticleTypes.SOUL_FIRE_FLAME, blockbehaviour_properties);
    }, wallVariant(Blocks.SOUL_TORCH, true).noCollision().instabreak().lightLevel((blockstate) -> {
        return 10;
    }).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block COPPER_TORCH = register("copper_torch", (blockbehaviour_properties) -> {
        return new TorchBlock(ParticleTypes.COPPER_FIRE_FLAME, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().noCollision().instabreak().lightLevel((blockstate) -> {
        return 14;
    }).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block COPPER_WALL_TORCH = register("copper_wall_torch", (blockbehaviour_properties) -> {
        return new WallTorchBlock(ParticleTypes.COPPER_FIRE_FLAME, blockbehaviour_properties);
    }, wallVariant(Blocks.COPPER_TORCH, true).noCollision().instabreak().lightLevel((blockstate) -> {
        return 14;
    }).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block GLOWSTONE = register("glowstone", BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.PLING).strength(0.3F).sound(SoundType.GLASS).lightLevel((blockstate) -> {
        return 15;
    }).isRedstoneConductor(Blocks::never));
    public static final Block NETHER_PORTAL = register("nether_portal", NetherPortalBlock::new, BlockBehaviour.Properties.of().noCollision().randomTicks().strength(-1.0F).sound(SoundType.GLASS).lightLevel((blockstate) -> {
        return 11;
    }).pushReaction(PushReaction.BLOCK));
    public static final Block CARVED_PUMPKIN = register("carved_pumpkin", CarvedPumpkinBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.0F).sound(SoundType.WOOD).isValidSpawn(Blocks::always).pushReaction(PushReaction.DESTROY));
    public static final Block JACK_O_LANTERN = register("jack_o_lantern", CarvedPumpkinBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.0F).sound(SoundType.WOOD).lightLevel((blockstate) -> {
        return 15;
    }).isValidSpawn(Blocks::always).pushReaction(PushReaction.DESTROY));
    public static final Block CAKE = register("cake", CakeBlock::new, BlockBehaviour.Properties.of().forceSolidOn().strength(0.5F).sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY));
    public static final Block REPEATER = register("repeater", RepeaterBlock::new, BlockBehaviour.Properties.of().instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY));
    public static final Block WHITE_STAINED_GLASS = registerStainedGlass("white_stained_glass", DyeColor.WHITE);
    public static final Block ORANGE_STAINED_GLASS = registerStainedGlass("orange_stained_glass", DyeColor.ORANGE);
    public static final Block MAGENTA_STAINED_GLASS = registerStainedGlass("magenta_stained_glass", DyeColor.MAGENTA);
    public static final Block LIGHT_BLUE_STAINED_GLASS = registerStainedGlass("light_blue_stained_glass", DyeColor.LIGHT_BLUE);
    public static final Block YELLOW_STAINED_GLASS = registerStainedGlass("yellow_stained_glass", DyeColor.YELLOW);
    public static final Block LIME_STAINED_GLASS = registerStainedGlass("lime_stained_glass", DyeColor.LIME);
    public static final Block PINK_STAINED_GLASS = registerStainedGlass("pink_stained_glass", DyeColor.PINK);
    public static final Block GRAY_STAINED_GLASS = registerStainedGlass("gray_stained_glass", DyeColor.GRAY);
    public static final Block LIGHT_GRAY_STAINED_GLASS = registerStainedGlass("light_gray_stained_glass", DyeColor.LIGHT_GRAY);
    public static final Block CYAN_STAINED_GLASS = registerStainedGlass("cyan_stained_glass", DyeColor.CYAN);
    public static final Block PURPLE_STAINED_GLASS = registerStainedGlass("purple_stained_glass", DyeColor.PURPLE);
    public static final Block BLUE_STAINED_GLASS = registerStainedGlass("blue_stained_glass", DyeColor.BLUE);
    public static final Block BROWN_STAINED_GLASS = registerStainedGlass("brown_stained_glass", DyeColor.BROWN);
    public static final Block GREEN_STAINED_GLASS = registerStainedGlass("green_stained_glass", DyeColor.GREEN);
    public static final Block RED_STAINED_GLASS = registerStainedGlass("red_stained_glass", DyeColor.RED);
    public static final Block BLACK_STAINED_GLASS = registerStainedGlass("black_stained_glass", DyeColor.BLACK);
    public static final Block OAK_TRAPDOOR = register("oak_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).ignitedByLava());
    public static final Block SPRUCE_TRAPDOOR = register("spruce_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.SPRUCE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).ignitedByLava());
    public static final Block BIRCH_TRAPDOOR = register("birch_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.BIRCH, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).ignitedByLava());
    public static final Block JUNGLE_TRAPDOOR = register("jungle_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.JUNGLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).ignitedByLava());
    public static final Block ACACIA_TRAPDOOR = register("acacia_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.ACACIA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).ignitedByLava());
    public static final Block CHERRY_TRAPDOOR = register("cherry_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.CHERRY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).ignitedByLava());
    public static final Block DARK_OAK_TRAPDOOR = register("dark_oak_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.DARK_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).ignitedByLava());
    public static final Block PALE_OAK_TRAPDOOR = register("pale_oak_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.PALE_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).ignitedByLava());
    public static final Block MANGROVE_TRAPDOOR = register("mangrove_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.MANGROVE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).ignitedByLava());
    public static final Block BAMBOO_TRAPDOOR = register("bamboo_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.BAMBOO, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).ignitedByLava());
    public static final Block STONE_BRICKS = register("stone_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block MOSSY_STONE_BRICKS = register("mossy_stone_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block CRACKED_STONE_BRICKS = register("cracked_stone_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block CHISELED_STONE_BRICKS = register("chiseled_stone_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block PACKED_MUD = register("packed_mud", BlockBehaviour.Properties.ofLegacyCopy(Blocks.DIRT).strength(1.0F, 3.0F).sound(SoundType.PACKED_MUD));
    public static final Block MUD_BRICKS = register("mud_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 3.0F).sound(SoundType.MUD_BRICKS));
    public static final Block INFESTED_STONE = register("infested_stone", (blockbehaviour_properties) -> {
        return new InfestedBlock(Blocks.STONE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.CLAY));
    public static final Block INFESTED_COBBLESTONE = register("infested_cobblestone", (blockbehaviour_properties) -> {
        return new InfestedBlock(Blocks.COBBLESTONE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.CLAY));
    public static final Block INFESTED_STONE_BRICKS = register("infested_stone_bricks", (blockbehaviour_properties) -> {
        return new InfestedBlock(Blocks.STONE_BRICKS, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.CLAY));
    public static final Block INFESTED_MOSSY_STONE_BRICKS = register("infested_mossy_stone_bricks", (blockbehaviour_properties) -> {
        return new InfestedBlock(Blocks.MOSSY_STONE_BRICKS, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.CLAY));
    public static final Block INFESTED_CRACKED_STONE_BRICKS = register("infested_cracked_stone_bricks", (blockbehaviour_properties) -> {
        return new InfestedBlock(Blocks.CRACKED_STONE_BRICKS, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.CLAY));
    public static final Block INFESTED_CHISELED_STONE_BRICKS = register("infested_chiseled_stone_bricks", (blockbehaviour_properties) -> {
        return new InfestedBlock(Blocks.CHISELED_STONE_BRICKS, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.CLAY));
    public static final Block BROWN_MUSHROOM_BLOCK = register("brown_mushroom_block", HugeMushroomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.BASS).strength(0.2F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block RED_MUSHROOM_BLOCK = register("red_mushroom_block", HugeMushroomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASS).strength(0.2F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block MUSHROOM_STEM = register("mushroom_stem", HugeMushroomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).instrument(NoteBlockInstrument.BASS).strength(0.2F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block IRON_BARS = register("iron_bars", IronBarsBlock::new, BlockBehaviour.Properties.of().requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.IRON).noOcclusion());
    public static final WeatheringCopperBlocks COPPER_BARS = WeatheringCopperBlocks.create("copper_bars", Blocks::register, IronBarsBlock::new, WeatheringCopperBarsBlock::new, (weatheringcopper_weatherstate) -> {
        return BlockBehaviour.Properties.of().requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.COPPER).noOcclusion();
    });
    public static final Block IRON_CHAIN = register("iron_chain", ChainBlock::new, BlockBehaviour.Properties.of().forceSolidOn().requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.CHAIN).noOcclusion());
    public static final WeatheringCopperBlocks COPPER_CHAIN = WeatheringCopperBlocks.create("copper_chain", Blocks::register, ChainBlock::new, WeatheringCopperChainBlock::new, (weatheringcopper_weatherstate) -> {
        return BlockBehaviour.Properties.of().forceSolidOn().requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.CHAIN).noOcclusion();
    });
    public static final Block GLASS_PANE = register("glass_pane", IronBarsBlock::new, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block PUMPKIN = register(net.minecraft.references.Blocks.PUMPKIN, PumpkinBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.DIDGERIDOO).strength(1.0F).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block MELON = register(net.minecraft.references.Blocks.MELON, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(1.0F).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block ATTACHED_PUMPKIN_STEM = register(net.minecraft.references.Blocks.ATTACHED_PUMPKIN_STEM, (blockbehaviour_properties) -> {
        return new AttachedStemBlock(net.minecraft.references.Blocks.PUMPKIN_STEM, net.minecraft.references.Blocks.PUMPKIN, Items.PUMPKIN_SEEDS, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block ATTACHED_MELON_STEM = register(net.minecraft.references.Blocks.ATTACHED_MELON_STEM, (blockbehaviour_properties) -> {
        return new AttachedStemBlock(net.minecraft.references.Blocks.MELON_STEM, net.minecraft.references.Blocks.MELON, Items.MELON_SEEDS, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block PUMPKIN_STEM = register(net.minecraft.references.Blocks.PUMPKIN_STEM, (blockbehaviour_properties) -> {
        return new StemBlock(net.minecraft.references.Blocks.PUMPKIN, net.minecraft.references.Blocks.ATTACHED_PUMPKIN_STEM, Items.PUMPKIN_SEEDS, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.HARD_CROP).pushReaction(PushReaction.DESTROY));
    public static final Block MELON_STEM = register(net.minecraft.references.Blocks.MELON_STEM, (blockbehaviour_properties) -> {
        return new StemBlock(net.minecraft.references.Blocks.MELON, net.minecraft.references.Blocks.ATTACHED_MELON_STEM, Items.MELON_SEEDS, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.HARD_CROP).pushReaction(PushReaction.DESTROY));
    public static final Block VINE = register("vine", VineBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().randomTicks().strength(0.2F).sound(SoundType.VINE).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block GLOW_LICHEN = register("glow_lichen", GlowLichenBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.GLOW_LICHEN).replaceable().noCollision().strength(0.2F).sound(SoundType.GLOW_LICHEN).lightLevel(GlowLichenBlock.emission(7)).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block RESIN_CLUMP = register("resin_clump", MultifaceBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).replaceable().noCollision().sound(SoundType.RESIN).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block OAK_FENCE_GATE = register("oak_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
    public static final Block BRICK_STAIRS = registerLegacyStair("brick_stairs", Blocks.BRICKS);
    public static final Block STONE_BRICK_STAIRS = registerLegacyStair("stone_brick_stairs", Blocks.STONE_BRICKS);
    public static final Block MUD_BRICK_STAIRS = registerLegacyStair("mud_brick_stairs", Blocks.MUD_BRICKS);
    public static final Block MYCELIUM = register("mycelium", MyceliumBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).randomTicks().strength(0.6F).sound(SoundType.GRASS));
    public static final Block LILY_PAD = register("lily_pad", WaterlilyBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).instabreak().sound(SoundType.LILY_PAD).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block RESIN_BLOCK = register("resin_block", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.RESIN));
    public static final Block RESIN_BRICKS = register("resin_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().sound(SoundType.RESIN_BRICKS).strength(1.5F, 6.0F));
    public static final Block RESIN_BRICK_STAIRS = registerLegacyStair("resin_brick_stairs", Blocks.RESIN_BRICKS);
    public static final Block RESIN_BRICK_SLAB = register("resin_brick_slab", (blockbehaviour_properties) -> {
        return new SlabBlock(blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().sound(SoundType.RESIN_BRICKS).strength(1.5F, 6.0F));
    public static final Block RESIN_BRICK_WALL = register("resin_brick_wall", (blockbehaviour_properties) -> {
        return new WallBlock(blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().sound(SoundType.RESIN_BRICKS).strength(1.5F, 6.0F));
    public static final Block CHISELED_RESIN_BRICKS = register("chiseled_resin_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().sound(SoundType.RESIN_BRICKS).strength(1.5F, 6.0F));
    public static final Block NETHER_BRICKS = register("nether_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.NETHER_BRICKS));
    public static final Block NETHER_BRICK_FENCE = register("nether_brick_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.NETHER_BRICKS));
    public static final Block NETHER_BRICK_STAIRS = registerLegacyStair("nether_brick_stairs", Blocks.NETHER_BRICKS);
    public static final Block NETHER_WART = register("nether_wart", NetherWartBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollision().randomTicks().sound(SoundType.NETHER_WART).pushReaction(PushReaction.DESTROY));
    public static final Block ENCHANTING_TABLE = register("enchanting_table", EnchantingTableBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().lightLevel((blockstate) -> {
        return 7;
    }).strength(5.0F, 1200.0F));
    public static final Block BREWING_STAND = register("brewing_stand", BrewingStandBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5F).lightLevel((blockstate) -> {
        return 1;
    }).noOcclusion());
    public static final Block CAULDRON = register("cauldron", CauldronBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(2.0F).noOcclusion());
    public static final Block WATER_CAULDRON = register("water_cauldron", (blockbehaviour_properties) -> {
        return new LayeredCauldronBlock(Biome.Precipitation.RAIN, CauldronInteraction.WATER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CAULDRON));
    public static final Block LAVA_CAULDRON = register("lava_cauldron", LavaCauldronBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CAULDRON).lightLevel((blockstate) -> {
        return 15;
    }));
    public static final Block POWDER_SNOW_CAULDRON = register("powder_snow_cauldron", (blockbehaviour_properties) -> {
        return new LayeredCauldronBlock(Biome.Precipitation.SNOW, CauldronInteraction.POWDER_SNOW, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CAULDRON));
    public static final Block END_PORTAL = register("end_portal", EndPortalBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).noCollision().lightLevel((blockstate) -> {
        return 15;
    }).strength(-1.0F, 3600000.0F).noLootTable().pushReaction(PushReaction.BLOCK));
    public static final Block END_PORTAL_FRAME = register("end_portal_frame", EndPortalFrameBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.GLASS).lightLevel((blockstate) -> {
        return 1;
    }).strength(-1.0F, 3600000.0F).noLootTable());
    public static final Block END_STONE = register("end_stone", BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 9.0F));
    public static final Block DRAGON_EGG = register("dragon_egg", DragonEggBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 9.0F).lightLevel((blockstate) -> {
        return 1;
    }).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block REDSTONE_LAMP = register("redstone_lamp", RedstoneLampBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).lightLevel(litBlockEmission(15)).strength(0.3F).sound(SoundType.GLASS).isValidSpawn(Blocks::always));
    public static final Block COCOA = register("cocoa", CocoaBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).randomTicks().strength(0.2F, 3.0F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block SANDSTONE_STAIRS = registerLegacyStair("sandstone_stairs", Blocks.SANDSTONE);
    public static final Block EMERALD_ORE = register("emerald_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(UniformInt.of(3, 7), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final Block DEEPSLATE_EMERALD_ORE = register("deepslate_emerald_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(UniformInt.of(3, 7), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.EMERALD_ORE).mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final Block ENDER_CHEST = register("ender_chest", EnderChestBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).strength(22.5F, 600.0F).lightLevel((blockstate) -> {
        return 7;
    }));
    public static final Block TRIPWIRE_HOOK = register("tripwire_hook", TripWireHookBlock::new, BlockBehaviour.Properties.of().noCollision().sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final Block TRIPWIRE = register("tripwire", (blockbehaviour_properties) -> {
        return new TripWireBlock(Blocks.TRIPWIRE_HOOK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().noCollision().pushReaction(PushReaction.DESTROY));
    public static final Block EMERALD_BLOCK = register("emerald_block", BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).instrument(NoteBlockInstrument.BIT).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL));
    public static final Block SPRUCE_STAIRS = registerLegacyStair("spruce_stairs", Blocks.SPRUCE_PLANKS);
    public static final Block BIRCH_STAIRS = registerLegacyStair("birch_stairs", Blocks.BIRCH_PLANKS);
    public static final Block JUNGLE_STAIRS = registerLegacyStair("jungle_stairs", Blocks.JUNGLE_PLANKS);
    public static final Block COMMAND_BLOCK = register("command_block", (blockbehaviour_properties) -> {
        return new CommandBlock(false, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).requiresCorrectToolForDrops().strength(-1.0F, 3600000.0F).noLootTable());
    public static final Block BEACON = register("beacon", BeaconBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).instrument(NoteBlockInstrument.HAT).strength(3.0F).lightLevel((blockstate) -> {
        return 15;
    }).noOcclusion().isRedstoneConductor(Blocks::never));
    public static final Block COBBLESTONE_WALL = register("cobblestone_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.COBBLESTONE).forceSolidOn());
    public static final Block MOSSY_COBBLESTONE_WALL = register("mossy_cobblestone_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.COBBLESTONE).forceSolidOn());
    public static final Block FLOWER_POT = register("flower_pot", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.AIR, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_TORCHFLOWER = register("potted_torchflower", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.TORCHFLOWER, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_OAK_SAPLING = register("potted_oak_sapling", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.OAK_SAPLING, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_SPRUCE_SAPLING = register("potted_spruce_sapling", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.SPRUCE_SAPLING, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_BIRCH_SAPLING = register("potted_birch_sapling", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.BIRCH_SAPLING, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_JUNGLE_SAPLING = register("potted_jungle_sapling", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.JUNGLE_SAPLING, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_ACACIA_SAPLING = register("potted_acacia_sapling", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.ACACIA_SAPLING, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_CHERRY_SAPLING = register("potted_cherry_sapling", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.CHERRY_SAPLING, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_DARK_OAK_SAPLING = register("potted_dark_oak_sapling", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.DARK_OAK_SAPLING, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_PALE_OAK_SAPLING = register("potted_pale_oak_sapling", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.PALE_OAK_SAPLING, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_MANGROVE_PROPAGULE = register("potted_mangrove_propagule", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.MANGROVE_PROPAGULE, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_FERN = register("potted_fern", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.FERN, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_DANDELION = register("potted_dandelion", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.DANDELION, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_POPPY = register("potted_poppy", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.POPPY, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_BLUE_ORCHID = register("potted_blue_orchid", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.BLUE_ORCHID, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_ALLIUM = register("potted_allium", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.ALLIUM, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_AZURE_BLUET = register("potted_azure_bluet", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.AZURE_BLUET, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_RED_TULIP = register("potted_red_tulip", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.RED_TULIP, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_ORANGE_TULIP = register("potted_orange_tulip", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.ORANGE_TULIP, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_WHITE_TULIP = register("potted_white_tulip", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.WHITE_TULIP, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_PINK_TULIP = register("potted_pink_tulip", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.PINK_TULIP, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_OXEYE_DAISY = register("potted_oxeye_daisy", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.OXEYE_DAISY, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_CORNFLOWER = register("potted_cornflower", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.CORNFLOWER, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_LILY_OF_THE_VALLEY = register("potted_lily_of_the_valley", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.LILY_OF_THE_VALLEY, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_WITHER_ROSE = register("potted_wither_rose", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.WITHER_ROSE, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_RED_MUSHROOM = register("potted_red_mushroom", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.RED_MUSHROOM, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_BROWN_MUSHROOM = register("potted_brown_mushroom", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.BROWN_MUSHROOM, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_DEAD_BUSH = register("potted_dead_bush", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.DEAD_BUSH, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_CACTUS = register("potted_cactus", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.CACTUS, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block CARROTS = register("carrots", CarrotBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
    public static final Block POTATOES = register("potatoes", PotatoBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
    public static final Block OAK_BUTTON = register("oak_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.OAK, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block SPRUCE_BUTTON = register("spruce_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.SPRUCE, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block BIRCH_BUTTON = register("birch_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.BIRCH, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block JUNGLE_BUTTON = register("jungle_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.JUNGLE, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block ACACIA_BUTTON = register("acacia_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.ACACIA, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block CHERRY_BUTTON = register("cherry_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.CHERRY, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block DARK_OAK_BUTTON = register("dark_oak_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.DARK_OAK, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block PALE_OAK_BUTTON = register("pale_oak_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.PALE_OAK, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block MANGROVE_BUTTON = register("mangrove_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.MANGROVE, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block BAMBOO_BUTTON = register("bamboo_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.BAMBOO, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block SKELETON_SKULL = register("skeleton_skull", (blockbehaviour_properties) -> {
        return new SkullBlock(SkullBlock.Types.SKELETON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.SKELETON).strength(1.0F).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final Block SKELETON_WALL_SKULL = register("skeleton_wall_skull", (blockbehaviour_properties) -> {
        return new WallSkullBlock(SkullBlock.Types.SKELETON, blockbehaviour_properties);
    }, wallVariant(Blocks.SKELETON_SKULL, true).strength(1.0F).pushReaction(PushReaction.DESTROY));
    public static final Block WITHER_SKELETON_SKULL = register("wither_skeleton_skull", WitherSkullBlock::new, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.WITHER_SKELETON).strength(1.0F).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final Block WITHER_SKELETON_WALL_SKULL = register("wither_skeleton_wall_skull", WitherWallSkullBlock::new, wallVariant(Blocks.WITHER_SKELETON_SKULL, true).strength(1.0F).pushReaction(PushReaction.DESTROY));
    public static final Block ZOMBIE_HEAD = register("zombie_head", (blockbehaviour_properties) -> {
        return new SkullBlock(SkullBlock.Types.ZOMBIE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.ZOMBIE).strength(1.0F).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final Block ZOMBIE_WALL_HEAD = register("zombie_wall_head", (blockbehaviour_properties) -> {
        return new WallSkullBlock(SkullBlock.Types.ZOMBIE, blockbehaviour_properties);
    }, wallVariant(Blocks.ZOMBIE_HEAD, true).strength(1.0F).pushReaction(PushReaction.DESTROY));
    public static final Block PLAYER_HEAD = register("player_head", PlayerHeadBlock::new, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.CUSTOM_HEAD).strength(1.0F).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final Block PLAYER_WALL_HEAD = register("player_wall_head", PlayerWallHeadBlock::new, wallVariant(Blocks.PLAYER_HEAD, true).strength(1.0F).pushReaction(PushReaction.DESTROY));
    public static final Block CREEPER_HEAD = register("creeper_head", (blockbehaviour_properties) -> {
        return new SkullBlock(SkullBlock.Types.CREEPER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.CREEPER).strength(1.0F).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final Block CREEPER_WALL_HEAD = register("creeper_wall_head", (blockbehaviour_properties) -> {
        return new WallSkullBlock(SkullBlock.Types.CREEPER, blockbehaviour_properties);
    }, wallVariant(Blocks.CREEPER_HEAD, true).strength(1.0F).pushReaction(PushReaction.DESTROY));
    public static final Block DRAGON_HEAD = register("dragon_head", (blockbehaviour_properties) -> {
        return new SkullBlock(SkullBlock.Types.DRAGON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.DRAGON).strength(1.0F).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final Block DRAGON_WALL_HEAD = register("dragon_wall_head", (blockbehaviour_properties) -> {
        return new WallSkullBlock(SkullBlock.Types.DRAGON, blockbehaviour_properties);
    }, wallVariant(Blocks.DRAGON_HEAD, true).strength(1.0F).pushReaction(PushReaction.DESTROY));
    public static final Block PIGLIN_HEAD = register("piglin_head", (blockbehaviour_properties) -> {
        return new SkullBlock(SkullBlock.Types.PIGLIN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.PIGLIN).strength(1.0F).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final Block PIGLIN_WALL_HEAD = register("piglin_wall_head", PiglinWallSkullBlock::new, wallVariant(Blocks.PIGLIN_HEAD, true).strength(1.0F).pushReaction(PushReaction.DESTROY));
    public static final Block ANVIL = register("anvil", AnvilBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 1200.0F).sound(SoundType.ANVIL).pushReaction(PushReaction.BLOCK));
    public static final Block CHIPPED_ANVIL = register("chipped_anvil", AnvilBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 1200.0F).sound(SoundType.ANVIL).pushReaction(PushReaction.BLOCK));
    public static final Block DAMAGED_ANVIL = register("damaged_anvil", AnvilBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 1200.0F).sound(SoundType.ANVIL).pushReaction(PushReaction.BLOCK));
    public static final Block TRAPPED_CHEST = register("trapped_chest", TrappedChestBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block LIGHT_WEIGHTED_PRESSURE_PLATE = register("light_weighted_pressure_plate", (blockbehaviour_properties) -> {
        return new WeightedPressurePlateBlock(15, BlockSetType.GOLD, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).forceSolidOn().noCollision().strength(0.5F).pushReaction(PushReaction.DESTROY));
    public static final Block HEAVY_WEIGHTED_PRESSURE_PLATE = register("heavy_weighted_pressure_plate", (blockbehaviour_properties) -> {
        return new WeightedPressurePlateBlock(150, BlockSetType.IRON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).forceSolidOn().noCollision().strength(0.5F).pushReaction(PushReaction.DESTROY));
    public static final Block COMPARATOR = register("comparator", ComparatorBlock::new, BlockBehaviour.Properties.of().instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY));
    public static final Block DAYLIGHT_DETECTOR = register("daylight_detector", DaylightDetectorBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(0.2F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block REDSTONE_BLOCK = register("redstone_block", PoweredBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL).isRedstoneConductor(Blocks::never));
    public static final Block NETHER_QUARTZ_ORE = register("nether_quartz_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(UniformInt.of(2, 5), blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F).sound(SoundType.NETHER_ORE));
    public static final Block HOPPER = register("hopper", HopperBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(3.0F, 4.8F).sound(SoundType.METAL).noOcclusion());
    public static final Block QUARTZ_BLOCK = register("quartz_block", BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.8F));
    public static final Block CHISELED_QUARTZ_BLOCK = register("chiseled_quartz_block", BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.8F));
    public static final Block QUARTZ_PILLAR = register("quartz_pillar", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.8F));
    public static final Block QUARTZ_STAIRS = registerLegacyStair("quartz_stairs", Blocks.QUARTZ_BLOCK);
    public static final Block ACTIVATOR_RAIL = register("activator_rail", PoweredRailBlock::new, BlockBehaviour.Properties.of().noCollision().strength(0.7F).sound(SoundType.METAL));
    public static final Block DROPPER = register("dropper", DropperBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.5F));
    public static final Block WHITE_TERRACOTTA = register("white_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block ORANGE_TERRACOTTA = register("orange_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block MAGENTA_TERRACOTTA = register("magenta_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_MAGENTA).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block LIGHT_BLUE_TERRACOTTA = register("light_blue_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_BLUE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block YELLOW_TERRACOTTA = register("yellow_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block LIME_TERRACOTTA = register("lime_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GREEN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block PINK_TERRACOTTA = register("pink_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PINK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block GRAY_TERRACOTTA = register("gray_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block LIGHT_GRAY_TERRACOTTA = register("light_gray_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block CYAN_TERRACOTTA = register("cyan_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_CYAN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block PURPLE_TERRACOTTA = register("purple_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PURPLE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block BLUE_TERRACOTTA = register("blue_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BLUE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block BROWN_TERRACOTTA = register("brown_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block GREEN_TERRACOTTA = register("green_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GREEN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block RED_TERRACOTTA = register("red_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block BLACK_TERRACOTTA = register("black_terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block WHITE_STAINED_GLASS_PANE = register("white_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.WHITE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block ORANGE_STAINED_GLASS_PANE = register("orange_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.ORANGE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block MAGENTA_STAINED_GLASS_PANE = register("magenta_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.MAGENTA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block LIGHT_BLUE_STAINED_GLASS_PANE = register("light_blue_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.LIGHT_BLUE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block YELLOW_STAINED_GLASS_PANE = register("yellow_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.YELLOW, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block LIME_STAINED_GLASS_PANE = register("lime_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.LIME, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block PINK_STAINED_GLASS_PANE = register("pink_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.PINK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block GRAY_STAINED_GLASS_PANE = register("gray_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.GRAY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block LIGHT_GRAY_STAINED_GLASS_PANE = register("light_gray_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.LIGHT_GRAY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block CYAN_STAINED_GLASS_PANE = register("cyan_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.CYAN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block PURPLE_STAINED_GLASS_PANE = register("purple_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.PURPLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block BLUE_STAINED_GLASS_PANE = register("blue_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.BLUE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block BROWN_STAINED_GLASS_PANE = register("brown_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.BROWN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block GREEN_STAINED_GLASS_PANE = register("green_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.GREEN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block RED_STAINED_GLASS_PANE = register("red_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.RED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block BLACK_STAINED_GLASS_PANE = register("black_stained_glass_pane", (blockbehaviour_properties) -> {
        return new StainedGlassPaneBlock(DyeColor.BLACK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    public static final Block ACACIA_STAIRS = registerLegacyStair("acacia_stairs", Blocks.ACACIA_PLANKS);
    public static final Block CHERRY_STAIRS = registerLegacyStair("cherry_stairs", Blocks.CHERRY_PLANKS);
    public static final Block DARK_OAK_STAIRS = registerLegacyStair("dark_oak_stairs", Blocks.DARK_OAK_PLANKS);
    public static final Block PALE_OAK_STAIRS = registerLegacyStair("pale_oak_stairs", Blocks.PALE_OAK_PLANKS);
    public static final Block MANGROVE_STAIRS = registerLegacyStair("mangrove_stairs", Blocks.MANGROVE_PLANKS);
    public static final Block BAMBOO_STAIRS = registerLegacyStair("bamboo_stairs", Blocks.BAMBOO_PLANKS);
    public static final Block BAMBOO_MOSAIC_STAIRS = registerLegacyStair("bamboo_mosaic_stairs", Blocks.BAMBOO_MOSAIC);
    public static final Block SLIME_BLOCK = register("slime_block", SlimeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).friction(0.8F).sound(SoundType.SLIME_BLOCK).noOcclusion());
    public static final Block BARRIER = register("barrier", BarrierBlock::new, BlockBehaviour.Properties.of().strength(-1.0F, 3600000.8F).mapColor(waterloggedMapColor(MapColor.NONE)).noLootTable().noOcclusion().isValidSpawn(Blocks::never).noTerrainParticles().pushReaction(PushReaction.BLOCK));
    public static final Block LIGHT = register("light", LightBlock::new, BlockBehaviour.Properties.of().replaceable().strength(-1.0F, 3600000.8F).mapColor(waterloggedMapColor(MapColor.NONE)).noLootTable().noOcclusion().lightLevel(LightBlock.LIGHT_EMISSION));
    public static final Block IRON_TRAPDOOR = register("iron_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.IRON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F).noOcclusion().isValidSpawn(Blocks::never));
    public static final Block PRISMARINE = register("prismarine", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block PRISMARINE_BRICKS = register("prismarine_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block DARK_PRISMARINE = register("dark_prismarine", BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block PRISMARINE_STAIRS = registerLegacyStair("prismarine_stairs", Blocks.PRISMARINE);
    public static final Block PRISMARINE_BRICK_STAIRS = registerLegacyStair("prismarine_brick_stairs", Blocks.PRISMARINE_BRICKS);
    public static final Block DARK_PRISMARINE_STAIRS = registerLegacyStair("dark_prismarine_stairs", Blocks.DARK_PRISMARINE);
    public static final Block PRISMARINE_SLAB = register("prismarine_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block PRISMARINE_BRICK_SLAB = register("prismarine_brick_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block DARK_PRISMARINE_SLAB = register("dark_prismarine_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block SEA_LANTERN = register("sea_lantern", BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).lightLevel((blockstate) -> {
        return 15;
    }).isRedstoneConductor(Blocks::never));
    public static final Block HAY_BLOCK = register("hay_block", HayBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BANJO).strength(0.5F).sound(SoundType.GRASS));
    public static final Block WHITE_CARPET = register("white_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.WHITE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block ORANGE_CARPET = register("orange_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.ORANGE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block MAGENTA_CARPET = register("magenta_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.MAGENTA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block LIGHT_BLUE_CARPET = register("light_blue_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.LIGHT_BLUE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block YELLOW_CARPET = register("yellow_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.YELLOW, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block LIME_CARPET = register("lime_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.LIME, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block PINK_CARPET = register("pink_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.PINK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block GRAY_CARPET = register("gray_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.GRAY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block LIGHT_GRAY_CARPET = register("light_gray_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.LIGHT_GRAY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block CYAN_CARPET = register("cyan_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.CYAN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block PURPLE_CARPET = register("purple_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.PURPLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block BLUE_CARPET = register("blue_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.BLUE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block BROWN_CARPET = register("brown_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.BROWN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block GREEN_CARPET = register("green_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.GREEN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block RED_CARPET = register("red_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.RED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block BLACK_CARPET = register("black_carpet", (blockbehaviour_properties) -> {
        return new WoolCarpetBlock(DyeColor.BLACK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
    public static final Block TERRACOTTA = register("terracotta", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.25F, 4.2F));
    public static final Block COAL_BLOCK = register("coal_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(5.0F, 6.0F));
    public static final Block PACKED_ICE = register("packed_ice", BlockBehaviour.Properties.of().mapColor(MapColor.ICE).instrument(NoteBlockInstrument.CHIME).friction(0.98F).strength(0.5F).sound(SoundType.GLASS));
    public static final Block SUNFLOWER = register("sunflower", TallFlowerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block LILAC = register("lilac", TallFlowerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block ROSE_BUSH = register("rose_bush", TallFlowerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block PEONY = register("peony", TallFlowerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block TALL_GRASS = register("tall_grass", DoublePlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block LARGE_FERN = register("large_fern", DoublePlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block WHITE_BANNER = register("white_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.WHITE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block ORANGE_BANNER = register("orange_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.ORANGE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block MAGENTA_BANNER = register("magenta_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.MAGENTA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block LIGHT_BLUE_BANNER = register("light_blue_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.LIGHT_BLUE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block YELLOW_BANNER = register("yellow_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.YELLOW, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block LIME_BANNER = register("lime_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.LIME, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block PINK_BANNER = register("pink_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.PINK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block GRAY_BANNER = register("gray_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.GRAY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block LIGHT_GRAY_BANNER = register("light_gray_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.LIGHT_GRAY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block CYAN_BANNER = register("cyan_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.CYAN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block PURPLE_BANNER = register("purple_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.PURPLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BLUE_BANNER = register("blue_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.BLUE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BROWN_BANNER = register("brown_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.BROWN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block GREEN_BANNER = register("green_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.GREEN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block RED_BANNER = register("red_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.RED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BLACK_BANNER = register("black_banner", (blockbehaviour_properties) -> {
        return new BannerBlock(DyeColor.BLACK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block WHITE_WALL_BANNER = register("white_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.WHITE, blockbehaviour_properties);
    }, wallVariant(Blocks.WHITE_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block ORANGE_WALL_BANNER = register("orange_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.ORANGE, blockbehaviour_properties);
    }, wallVariant(Blocks.ORANGE_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block MAGENTA_WALL_BANNER = register("magenta_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.MAGENTA, blockbehaviour_properties);
    }, wallVariant(Blocks.MAGENTA_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block LIGHT_BLUE_WALL_BANNER = register("light_blue_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.LIGHT_BLUE, blockbehaviour_properties);
    }, wallVariant(Blocks.LIGHT_BLUE_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block YELLOW_WALL_BANNER = register("yellow_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.YELLOW, blockbehaviour_properties);
    }, wallVariant(Blocks.YELLOW_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block LIME_WALL_BANNER = register("lime_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.LIME, blockbehaviour_properties);
    }, wallVariant(Blocks.LIME_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block PINK_WALL_BANNER = register("pink_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.PINK, blockbehaviour_properties);
    }, wallVariant(Blocks.PINK_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block GRAY_WALL_BANNER = register("gray_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.GRAY, blockbehaviour_properties);
    }, wallVariant(Blocks.GRAY_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block LIGHT_GRAY_WALL_BANNER = register("light_gray_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.LIGHT_GRAY, blockbehaviour_properties);
    }, wallVariant(Blocks.LIGHT_GRAY_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block CYAN_WALL_BANNER = register("cyan_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.CYAN, blockbehaviour_properties);
    }, wallVariant(Blocks.CYAN_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block PURPLE_WALL_BANNER = register("purple_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.PURPLE, blockbehaviour_properties);
    }, wallVariant(Blocks.PURPLE_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BLUE_WALL_BANNER = register("blue_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.BLUE, blockbehaviour_properties);
    }, wallVariant(Blocks.BLUE_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BROWN_WALL_BANNER = register("brown_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.BROWN, blockbehaviour_properties);
    }, wallVariant(Blocks.BROWN_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block GREEN_WALL_BANNER = register("green_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.GREEN, blockbehaviour_properties);
    }, wallVariant(Blocks.GREEN_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block RED_WALL_BANNER = register("red_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.RED, blockbehaviour_properties);
    }, wallVariant(Blocks.RED_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BLACK_WALL_BANNER = register("black_wall_banner", (blockbehaviour_properties) -> {
        return new WallBannerBlock(DyeColor.BLACK, blockbehaviour_properties);
    }, wallVariant(Blocks.BLACK_BANNER, true).mapColor(MapColor.WOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block RED_SANDSTONE = register("red_sandstone", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.8F));
    public static final Block CHISELED_RED_SANDSTONE = register("chiseled_red_sandstone", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.8F));
    public static final Block CUT_RED_SANDSTONE = register("cut_red_sandstone", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.8F));
    public static final Block RED_SANDSTONE_STAIRS = registerLegacyStair("red_sandstone_stairs", Blocks.RED_SANDSTONE);
    public static final Block OAK_SLAB = register("oak_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block SPRUCE_SLAB = register("spruce_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BIRCH_SLAB = register("birch_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block JUNGLE_SLAB = register("jungle_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block ACACIA_SLAB = register("acacia_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block CHERRY_SLAB = register("cherry_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.CHERRY_WOOD).ignitedByLava());
    public static final Block DARK_OAK_SLAB = register("dark_oak_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block PALE_OAK_SLAB = register("pale_oak_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block MANGROVE_SLAB = register("mangrove_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BAMBOO_SLAB = register("bamboo_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.BAMBOO_WOOD).ignitedByLava());
    public static final Block BAMBOO_MOSAIC_SLAB = register("bamboo_mosaic_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.BAMBOO_WOOD).ignitedByLava());
    public static final Block STONE_SLAB = register("stone_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block SMOOTH_STONE_SLAB = register("smooth_stone_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block SANDSTONE_SLAB = register("sandstone_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block CUT_SANDSTONE_SLAB = register("cut_sandstone_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block PETRIFIED_OAK_SLAB = register("petrified_oak_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block COBBLESTONE_SLAB = register("cobblestone_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block BRICK_SLAB = register("brick_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block STONE_BRICK_SLAB = register("stone_brick_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block MUD_BRICK_SLAB = register("mud_brick_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 3.0F).sound(SoundType.MUD_BRICKS));
    public static final Block NETHER_BRICK_SLAB = register("nether_brick_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.NETHER_BRICKS));
    public static final Block QUARTZ_SLAB = register("quartz_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block RED_SANDSTONE_SLAB = register("red_sandstone_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block CUT_RED_SANDSTONE_SLAB = register("cut_red_sandstone_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block PURPUR_SLAB = register("purpur_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block SMOOTH_STONE = register("smooth_stone", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block SMOOTH_SANDSTONE = register("smooth_sandstone", BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block SMOOTH_QUARTZ = register("smooth_quartz", BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block SMOOTH_RED_SANDSTONE = register("smooth_red_sandstone", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final Block SPRUCE_FENCE_GATE = register("spruce_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.SPRUCE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.SPRUCE_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
    public static final Block BIRCH_FENCE_GATE = register("birch_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.BIRCH, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.BIRCH_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
    public static final Block JUNGLE_FENCE_GATE = register("jungle_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.JUNGLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.JUNGLE_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
    public static final Block ACACIA_FENCE_GATE = register("acacia_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.ACACIA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.ACACIA_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
    public static final Block CHERRY_FENCE_GATE = register("cherry_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.CHERRY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.CHERRY_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
    public static final Block DARK_OAK_FENCE_GATE = register("dark_oak_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.DARK_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.DARK_OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
    public static final Block PALE_OAK_FENCE_GATE = register("pale_oak_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.PALE_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
    public static final Block MANGROVE_FENCE_GATE = register("mangrove_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.MANGROVE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.MANGROVE_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
    public static final Block BAMBOO_FENCE_GATE = register("bamboo_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.BAMBOO, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.BAMBOO_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
    public static final Block SPRUCE_FENCE = register("spruce_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.SPRUCE_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava().sound(SoundType.WOOD));
    public static final Block BIRCH_FENCE = register("birch_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.BIRCH_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava().sound(SoundType.WOOD));
    public static final Block JUNGLE_FENCE = register("jungle_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.JUNGLE_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava().sound(SoundType.WOOD));
    public static final Block ACACIA_FENCE = register("acacia_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.ACACIA_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava().sound(SoundType.WOOD));
    public static final Block CHERRY_FENCE = register("cherry_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.CHERRY_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava().sound(SoundType.CHERRY_WOOD));
    public static final Block DARK_OAK_FENCE = register("dark_oak_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.DARK_OAK_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava().sound(SoundType.WOOD));
    public static final Block PALE_OAK_FENCE = register("pale_oak_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava().sound(SoundType.WOOD));
    public static final Block MANGROVE_FENCE = register("mangrove_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.MANGROVE_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava().sound(SoundType.WOOD));
    public static final Block BAMBOO_FENCE = register("bamboo_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.BAMBOO_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.BAMBOO_WOOD).ignitedByLava());
    public static final Block SPRUCE_DOOR = register("spruce_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.SPRUCE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.SPRUCE_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block BIRCH_DOOR = register("birch_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.BIRCH, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.BIRCH_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block JUNGLE_DOOR = register("jungle_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.JUNGLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.JUNGLE_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block ACACIA_DOOR = register("acacia_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.ACACIA, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.ACACIA_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block CHERRY_DOOR = register("cherry_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.CHERRY, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.CHERRY_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block DARK_OAK_DOOR = register("dark_oak_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.DARK_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.DARK_OAK_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block PALE_OAK_DOOR = register("pale_oak_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.PALE_OAK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block MANGROVE_DOOR = register("mangrove_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.MANGROVE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.MANGROVE_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block BAMBOO_DOOR = register("bamboo_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.BAMBOO, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.BAMBOO_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block END_ROD = register("end_rod", EndRodBlock::new, BlockBehaviour.Properties.of().forceSolidOff().instabreak().lightLevel((blockstate) -> {
        return 14;
    }).sound(SoundType.WOOD).noOcclusion());
    public static final Block CHORUS_PLANT = register("chorus_plant", ChorusPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).forceSolidOff().strength(0.4F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block CHORUS_FLOWER = register("chorus_flower", (blockbehaviour_properties) -> {
        return new ChorusFlowerBlock(Blocks.CHORUS_PLANT, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).forceSolidOff().randomTicks().strength(0.4F).sound(SoundType.WOOD).noOcclusion().isValidSpawn(Blocks::never).pushReaction(PushReaction.DESTROY).isRedstoneConductor(Blocks::never));
    public static final Block PURPUR_BLOCK = register("purpur_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block PURPUR_PILLAR = register("purpur_pillar", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block PURPUR_STAIRS = registerLegacyStair("purpur_stairs", Blocks.PURPUR_BLOCK);
    public static final Block END_STONE_BRICKS = register("end_stone_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 9.0F));
    public static final Block TORCHFLOWER_CROP = register("torchflower_crop", TorchflowerCropBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
    public static final Block PITCHER_CROP = register("pitcher_crop", PitcherCropBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
    public static final Block PITCHER_PLANT = register("pitcher_plant", DoublePlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.CROP).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block BEETROOTS = register("beetroots", BeetrootBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
    public static final Block DIRT_PATH = register("dirt_path", DirtPathBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.65F).sound(SoundType.GRASS).isViewBlocking(Blocks::always).isSuffocating(Blocks::always));
    public static final Block END_GATEWAY = register("end_gateway", EndGatewayBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).noCollision().lightLevel((blockstate) -> {
        return 15;
    }).strength(-1.0F, 3600000.0F).noLootTable().pushReaction(PushReaction.BLOCK));
    public static final Block REPEATING_COMMAND_BLOCK = register("repeating_command_block", (blockbehaviour_properties) -> {
        return new CommandBlock(false, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(-1.0F, 3600000.0F).noLootTable());
    public static final Block CHAIN_COMMAND_BLOCK = register("chain_command_block", (blockbehaviour_properties) -> {
        return new CommandBlock(true, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).requiresCorrectToolForDrops().strength(-1.0F, 3600000.0F).noLootTable());
    public static final Block FROSTED_ICE = register("frosted_ice", FrostedIceBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.ICE).friction(0.98F).strength(0.5F).sound(SoundType.GLASS).noOcclusion().isValidSpawn((blockstate, blockgetter, blockpos, entitytype) -> {
        return entitytype == EntityType.POLAR_BEAR;
    }).isRedstoneConductor(Blocks::never));
    public static final Block MAGMA_BLOCK = register("magma_block", MagmaBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().lightLevel((blockstate) -> {
        return 3;
    }).strength(0.5F).isValidSpawn((blockstate, blockgetter, blockpos, entitytype) -> {
        return entitytype.fireImmune();
    }).hasPostProcess(Blocks::always).emissiveRendering(Blocks::always));
    public static final Block NETHER_WART_BLOCK = register("nether_wart_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.0F).sound(SoundType.WART_BLOCK));
    public static final Block RED_NETHER_BRICKS = register("red_nether_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.NETHER_BRICKS));
    public static final Block BONE_BLOCK = register("bone_block", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.XYLOPHONE).requiresCorrectToolForDrops().strength(2.0F).sound(SoundType.BONE_BLOCK));
    public static final Block STRUCTURE_VOID = register("structure_void", StructureVoidBlock::new, BlockBehaviour.Properties.of().replaceable().noCollision().noLootTable().noTerrainParticles().pushReaction(PushReaction.DESTROY));
    public static final Block OBSERVER = register("observer", ObserverBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).strength(3.0F).requiresCorrectToolForDrops().isRedstoneConductor(Blocks::never));
    public static final Block SHULKER_BOX = register("shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock((DyeColor) null, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_PURPLE));
    public static final Block WHITE_SHULKER_BOX = register("white_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.WHITE, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.SNOW));
    public static final Block ORANGE_SHULKER_BOX = register("orange_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.ORANGE, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_ORANGE));
    public static final Block MAGENTA_SHULKER_BOX = register("magenta_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.MAGENTA, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_MAGENTA));
    public static final Block LIGHT_BLUE_SHULKER_BOX = register("light_blue_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.LIGHT_BLUE, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_LIGHT_BLUE));
    public static final Block YELLOW_SHULKER_BOX = register("yellow_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.YELLOW, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_YELLOW));
    public static final Block LIME_SHULKER_BOX = register("lime_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.LIME, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_LIGHT_GREEN));
    public static final Block PINK_SHULKER_BOX = register("pink_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.PINK, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_PINK));
    public static final Block GRAY_SHULKER_BOX = register("gray_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.GRAY, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_GRAY));
    public static final Block LIGHT_GRAY_SHULKER_BOX = register("light_gray_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.LIGHT_GRAY, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_LIGHT_GRAY));
    public static final Block CYAN_SHULKER_BOX = register("cyan_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.CYAN, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_CYAN));
    public static final Block PURPLE_SHULKER_BOX = register("purple_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.PURPLE, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.TERRACOTTA_PURPLE));
    public static final Block BLUE_SHULKER_BOX = register("blue_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.BLUE, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_BLUE));
    public static final Block BROWN_SHULKER_BOX = register("brown_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.BROWN, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_BROWN));
    public static final Block GREEN_SHULKER_BOX = register("green_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.GREEN, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_GREEN));
    public static final Block RED_SHULKER_BOX = register("red_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.RED, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_RED));
    public static final Block BLACK_SHULKER_BOX = register("black_shulker_box", (blockbehaviour_properties) -> {
        return new ShulkerBoxBlock(DyeColor.BLACK, blockbehaviour_properties);
    }, shulkerBoxProperties(MapColor.COLOR_BLACK));
    public static final Block WHITE_GLAZED_TERRACOTTA = register("white_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.WHITE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block ORANGE_GLAZED_TERRACOTTA = register("orange_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block MAGENTA_GLAZED_TERRACOTTA = register("magenta_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.MAGENTA).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block LIGHT_BLUE_GLAZED_TERRACOTTA = register("light_blue_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.LIGHT_BLUE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block YELLOW_GLAZED_TERRACOTTA = register("yellow_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.YELLOW).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block LIME_GLAZED_TERRACOTTA = register("lime_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.LIME).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block PINK_GLAZED_TERRACOTTA = register("pink_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.PINK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block GRAY_GLAZED_TERRACOTTA = register("gray_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block LIGHT_GRAY_GLAZED_TERRACOTTA = register("light_gray_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.LIGHT_GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block CYAN_GLAZED_TERRACOTTA = register("cyan_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.CYAN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block PURPLE_GLAZED_TERRACOTTA = register("purple_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.PURPLE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block BLUE_GLAZED_TERRACOTTA = register("blue_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.BLUE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block BROWN_GLAZED_TERRACOTTA = register("brown_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.BROWN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block GREEN_GLAZED_TERRACOTTA = register("green_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.GREEN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block RED_GLAZED_TERRACOTTA = register("red_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.RED).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block BLACK_GLAZED_TERRACOTTA = register("black_glazed_terracotta", GlazedTerracottaBlock::new, BlockBehaviour.Properties.of().mapColor(DyeColor.BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.4F).pushReaction(PushReaction.PUSH_ONLY));
    public static final Block WHITE_CONCRETE = register("white_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.WHITE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block ORANGE_CONCRETE = register("orange_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block MAGENTA_CONCRETE = register("magenta_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.MAGENTA).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block LIGHT_BLUE_CONCRETE = register("light_blue_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.LIGHT_BLUE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block YELLOW_CONCRETE = register("yellow_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.YELLOW).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block LIME_CONCRETE = register("lime_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.LIME).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block PINK_CONCRETE = register("pink_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.PINK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block GRAY_CONCRETE = register("gray_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block LIGHT_GRAY_CONCRETE = register("light_gray_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.LIGHT_GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block CYAN_CONCRETE = register("cyan_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.CYAN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block PURPLE_CONCRETE = register("purple_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.PURPLE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block BLUE_CONCRETE = register("blue_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.BLUE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block BROWN_CONCRETE = register("brown_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.BROWN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block GREEN_CONCRETE = register("green_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.GREEN).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block RED_CONCRETE = register("red_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.RED).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block BLACK_CONCRETE = register("black_concrete", BlockBehaviour.Properties.of().mapColor(DyeColor.BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F));
    public static final Block WHITE_CONCRETE_POWDER = register("white_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.WHITE_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.WHITE).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block ORANGE_CONCRETE_POWDER = register("orange_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.ORANGE_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.ORANGE).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block MAGENTA_CONCRETE_POWDER = register("magenta_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.MAGENTA_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.MAGENTA).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block LIGHT_BLUE_CONCRETE_POWDER = register("light_blue_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.LIGHT_BLUE_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.LIGHT_BLUE).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block YELLOW_CONCRETE_POWDER = register("yellow_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.YELLOW_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.YELLOW).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block LIME_CONCRETE_POWDER = register("lime_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.LIME_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.LIME).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block PINK_CONCRETE_POWDER = register("pink_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.PINK_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.PINK).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block GRAY_CONCRETE_POWDER = register("gray_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.GRAY_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.GRAY).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block LIGHT_GRAY_CONCRETE_POWDER = register("light_gray_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.LIGHT_GRAY_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.LIGHT_GRAY).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block CYAN_CONCRETE_POWDER = register("cyan_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.CYAN_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.CYAN).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block PURPLE_CONCRETE_POWDER = register("purple_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.PURPLE_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.PURPLE).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block BLUE_CONCRETE_POWDER = register("blue_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.BLUE_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.BLUE).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block BROWN_CONCRETE_POWDER = register("brown_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.BROWN_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.BROWN).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block GREEN_CONCRETE_POWDER = register("green_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.GREEN_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.GREEN).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block RED_CONCRETE_POWDER = register("red_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.RED_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.RED).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block BLACK_CONCRETE_POWDER = register("black_concrete_powder", (blockbehaviour_properties) -> {
        return new ConcretePowderBlock(Blocks.BLACK_CONCRETE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(DyeColor.BLACK).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final Block KELP = register("kelp", KelpBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollision().randomTicks().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block KELP_PLANT = register("kelp_plant", KelpPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block DRIED_KELP_BLOCK = register("dried_kelp_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.5F, 2.5F).sound(SoundType.GRASS));
    public static final Block TURTLE_EGG = register("turtle_egg", TurtleEggBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).forceSolidOn().strength(0.5F).sound(SoundType.METAL).randomTicks().noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block SNIFFER_EGG = register("sniffer_egg", SnifferEggBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5F).sound(SoundType.METAL).noOcclusion());
    public static final Block DRIED_GHAST = register("dried_ghast", DriedGhastBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instabreak().sound(SoundType.DRIED_GHAST).noOcclusion().randomTicks());
    public static final Block DEAD_TUBE_CORAL_BLOCK = register("dead_tube_coral_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block DEAD_BRAIN_CORAL_BLOCK = register("dead_brain_coral_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block DEAD_BUBBLE_CORAL_BLOCK = register("dead_bubble_coral_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block DEAD_FIRE_CORAL_BLOCK = register("dead_fire_coral_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block DEAD_HORN_CORAL_BLOCK = register("dead_horn_coral_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block TUBE_CORAL_BLOCK = register("tube_coral_block", (blockbehaviour_properties) -> {
        return new CoralBlock(Blocks.DEAD_TUBE_CORAL_BLOCK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.CORAL_BLOCK));
    public static final Block BRAIN_CORAL_BLOCK = register("brain_coral_block", (blockbehaviour_properties) -> {
        return new CoralBlock(Blocks.DEAD_BRAIN_CORAL_BLOCK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.CORAL_BLOCK));
    public static final Block BUBBLE_CORAL_BLOCK = register("bubble_coral_block", (blockbehaviour_properties) -> {
        return new CoralBlock(Blocks.DEAD_BUBBLE_CORAL_BLOCK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.CORAL_BLOCK));
    public static final Block FIRE_CORAL_BLOCK = register("fire_coral_block", (blockbehaviour_properties) -> {
        return new CoralBlock(Blocks.DEAD_FIRE_CORAL_BLOCK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.CORAL_BLOCK));
    public static final Block HORN_CORAL_BLOCK = register("horn_coral_block", (blockbehaviour_properties) -> {
        return new CoralBlock(Blocks.DEAD_HORN_CORAL_BLOCK, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.CORAL_BLOCK));
    public static final Block DEAD_TUBE_CORAL = register("dead_tube_coral", BaseCoralPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_BRAIN_CORAL = register("dead_brain_coral", BaseCoralPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_BUBBLE_CORAL = register("dead_bubble_coral", BaseCoralPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_FIRE_CORAL = register("dead_fire_coral", BaseCoralPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_HORN_CORAL = register("dead_horn_coral", BaseCoralPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block TUBE_CORAL = register("tube_coral", (blockbehaviour_properties) -> {
        return new CoralPlantBlock(Blocks.DEAD_TUBE_CORAL, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block BRAIN_CORAL = register("brain_coral", (blockbehaviour_properties) -> {
        return new CoralPlantBlock(Blocks.DEAD_BRAIN_CORAL, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block BUBBLE_CORAL = register("bubble_coral", (blockbehaviour_properties) -> {
        return new CoralPlantBlock(Blocks.DEAD_BUBBLE_CORAL, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block FIRE_CORAL = register("fire_coral", (blockbehaviour_properties) -> {
        return new CoralPlantBlock(Blocks.DEAD_FIRE_CORAL, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block HORN_CORAL = register("horn_coral", (blockbehaviour_properties) -> {
        return new CoralPlantBlock(Blocks.DEAD_HORN_CORAL, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block DEAD_TUBE_CORAL_FAN = register("dead_tube_coral_fan", BaseCoralFanBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_BRAIN_CORAL_FAN = register("dead_brain_coral_fan", BaseCoralFanBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_BUBBLE_CORAL_FAN = register("dead_bubble_coral_fan", BaseCoralFanBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_FIRE_CORAL_FAN = register("dead_fire_coral_fan", BaseCoralFanBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_HORN_CORAL_FAN = register("dead_horn_coral_fan", BaseCoralFanBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block TUBE_CORAL_FAN = register("tube_coral_fan", (blockbehaviour_properties) -> {
        return new CoralFanBlock(Blocks.DEAD_TUBE_CORAL_FAN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block BRAIN_CORAL_FAN = register("brain_coral_fan", (blockbehaviour_properties) -> {
        return new CoralFanBlock(Blocks.DEAD_BRAIN_CORAL_FAN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block BUBBLE_CORAL_FAN = register("bubble_coral_fan", (blockbehaviour_properties) -> {
        return new CoralFanBlock(Blocks.DEAD_BUBBLE_CORAL_FAN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block FIRE_CORAL_FAN = register("fire_coral_fan", (blockbehaviour_properties) -> {
        return new CoralFanBlock(Blocks.DEAD_FIRE_CORAL_FAN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block HORN_CORAL_FAN = register("horn_coral_fan", (blockbehaviour_properties) -> {
        return new CoralFanBlock(Blocks.DEAD_HORN_CORAL_FAN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block DEAD_TUBE_CORAL_WALL_FAN = register("dead_tube_coral_wall_fan", BaseCoralWallFanBlock::new, wallVariant(Blocks.DEAD_TUBE_CORAL_FAN, false).mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_BRAIN_CORAL_WALL_FAN = register("dead_brain_coral_wall_fan", BaseCoralWallFanBlock::new, wallVariant(Blocks.DEAD_BRAIN_CORAL_FAN, false).mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_BUBBLE_CORAL_WALL_FAN = register("dead_bubble_coral_wall_fan", BaseCoralWallFanBlock::new, wallVariant(Blocks.DEAD_BUBBLE_CORAL_FAN, false).mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_FIRE_CORAL_WALL_FAN = register("dead_fire_coral_wall_fan", BaseCoralWallFanBlock::new, wallVariant(Blocks.DEAD_FIRE_CORAL_FAN, false).mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block DEAD_HORN_CORAL_WALL_FAN = register("dead_horn_coral_wall_fan", BaseCoralWallFanBlock::new, wallVariant(Blocks.DEAD_HORN_CORAL_FAN, false).mapColor(MapColor.COLOR_GRAY).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().noCollision().instabreak());
    public static final Block TUBE_CORAL_WALL_FAN = register("tube_coral_wall_fan", (blockbehaviour_properties) -> {
        return new CoralWallFanBlock(Blocks.DEAD_TUBE_CORAL_WALL_FAN, blockbehaviour_properties);
    }, wallVariant(Blocks.TUBE_CORAL_FAN, false).mapColor(MapColor.COLOR_BLUE).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block BRAIN_CORAL_WALL_FAN = register("brain_coral_wall_fan", (blockbehaviour_properties) -> {
        return new CoralWallFanBlock(Blocks.DEAD_BRAIN_CORAL_WALL_FAN, blockbehaviour_properties);
    }, wallVariant(Blocks.BRAIN_CORAL_FAN, false).mapColor(MapColor.COLOR_PINK).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block BUBBLE_CORAL_WALL_FAN = register("bubble_coral_wall_fan", (blockbehaviour_properties) -> {
        return new CoralWallFanBlock(Blocks.DEAD_BUBBLE_CORAL_WALL_FAN, blockbehaviour_properties);
    }, wallVariant(Blocks.BUBBLE_CORAL_FAN, false).mapColor(MapColor.COLOR_PURPLE).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block FIRE_CORAL_WALL_FAN = register("fire_coral_wall_fan", (blockbehaviour_properties) -> {
        return new CoralWallFanBlock(Blocks.DEAD_FIRE_CORAL_WALL_FAN, blockbehaviour_properties);
    }, wallVariant(Blocks.FIRE_CORAL_FAN, false).mapColor(MapColor.COLOR_RED).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block HORN_CORAL_WALL_FAN = register("horn_coral_wall_fan", (blockbehaviour_properties) -> {
        return new CoralWallFanBlock(Blocks.DEAD_HORN_CORAL_WALL_FAN, blockbehaviour_properties);
    }, wallVariant(Blocks.HORN_CORAL_FAN, false).mapColor(MapColor.COLOR_YELLOW).noCollision().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY));
    public static final Block SEA_PICKLE = register("sea_pickle", SeaPickleBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).lightLevel((blockstate) -> {
        return SeaPickleBlock.isDead(blockstate) ? 0 : 3 + 3 * (Integer) blockstate.getValue(SeaPickleBlock.PICKLES);
    }).sound(SoundType.SLIME_BLOCK).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block BLUE_ICE = register("blue_ice", HalfTransparentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(2.8F).friction(0.989F).sound(SoundType.GLASS));
    public static final Block CONDUIT = register("conduit", ConduitBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).forceSolidOn().instrument(NoteBlockInstrument.HAT).strength(3.0F).lightLevel((blockstate) -> {
        return 15;
    }).noOcclusion());
    public static final Block BAMBOO_SAPLING = register("bamboo_sapling", BambooSaplingBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).forceSolidOn().randomTicks().instabreak().noCollision().strength(1.0F).sound(SoundType.BAMBOO_SAPLING).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block BAMBOO = register("bamboo", BambooStalkBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).forceSolidOn().randomTicks().instabreak().strength(1.0F).sound(SoundType.BAMBOO).noOcclusion().dynamicShape().offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY).isRedstoneConductor(Blocks::never));
    public static final Block POTTED_BAMBOO = register("potted_bamboo", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.BAMBOO, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block VOID_AIR = register("void_air", AirBlock::new, BlockBehaviour.Properties.of().replaceable().noCollision().noLootTable().air());
    public static final Block CAVE_AIR = register("cave_air", AirBlock::new, BlockBehaviour.Properties.of().replaceable().noCollision().noLootTable().air());
    public static final Block BUBBLE_COLUMN = register("bubble_column", BubbleColumnBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WATER).replaceable().noCollision().noLootTable().pushReaction(PushReaction.DESTROY).liquid().sound(SoundType.EMPTY));
    public static final Block POLISHED_GRANITE_STAIRS = registerLegacyStair("polished_granite_stairs", Blocks.POLISHED_GRANITE);
    public static final Block SMOOTH_RED_SANDSTONE_STAIRS = registerLegacyStair("smooth_red_sandstone_stairs", Blocks.SMOOTH_RED_SANDSTONE);
    public static final Block MOSSY_STONE_BRICK_STAIRS = registerLegacyStair("mossy_stone_brick_stairs", Blocks.MOSSY_STONE_BRICKS);
    public static final Block POLISHED_DIORITE_STAIRS = registerLegacyStair("polished_diorite_stairs", Blocks.POLISHED_DIORITE);
    public static final Block MOSSY_COBBLESTONE_STAIRS = registerLegacyStair("mossy_cobblestone_stairs", Blocks.MOSSY_COBBLESTONE);
    public static final Block END_STONE_BRICK_STAIRS = registerLegacyStair("end_stone_brick_stairs", Blocks.END_STONE_BRICKS);
    public static final Block STONE_STAIRS = registerLegacyStair("stone_stairs", Blocks.STONE);
    public static final Block SMOOTH_SANDSTONE_STAIRS = registerLegacyStair("smooth_sandstone_stairs", Blocks.SMOOTH_SANDSTONE);
    public static final Block SMOOTH_QUARTZ_STAIRS = registerLegacyStair("smooth_quartz_stairs", Blocks.SMOOTH_QUARTZ);
    public static final Block GRANITE_STAIRS = registerLegacyStair("granite_stairs", Blocks.GRANITE);
    public static final Block ANDESITE_STAIRS = registerLegacyStair("andesite_stairs", Blocks.ANDESITE);
    public static final Block RED_NETHER_BRICK_STAIRS = registerLegacyStair("red_nether_brick_stairs", Blocks.RED_NETHER_BRICKS);
    public static final Block POLISHED_ANDESITE_STAIRS = registerLegacyStair("polished_andesite_stairs", Blocks.POLISHED_ANDESITE);
    public static final Block DIORITE_STAIRS = registerLegacyStair("diorite_stairs", Blocks.DIORITE);
    public static final Block POLISHED_GRANITE_SLAB = register("polished_granite_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_GRANITE));
    public static final Block SMOOTH_RED_SANDSTONE_SLAB = register("smooth_red_sandstone_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.SMOOTH_RED_SANDSTONE));
    public static final Block MOSSY_STONE_BRICK_SLAB = register("mossy_stone_brick_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.MOSSY_STONE_BRICKS));
    public static final Block POLISHED_DIORITE_SLAB = register("polished_diorite_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_DIORITE));
    public static final Block MOSSY_COBBLESTONE_SLAB = register("mossy_cobblestone_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.MOSSY_COBBLESTONE));
    public static final Block END_STONE_BRICK_SLAB = register("end_stone_brick_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.END_STONE_BRICKS));
    public static final Block SMOOTH_SANDSTONE_SLAB = register("smooth_sandstone_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.SMOOTH_SANDSTONE));
    public static final Block SMOOTH_QUARTZ_SLAB = register("smooth_quartz_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.SMOOTH_QUARTZ));
    public static final Block GRANITE_SLAB = register("granite_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.GRANITE));
    public static final Block ANDESITE_SLAB = register("andesite_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.ANDESITE));
    public static final Block RED_NETHER_BRICK_SLAB = register("red_nether_brick_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.RED_NETHER_BRICKS));
    public static final Block POLISHED_ANDESITE_SLAB = register("polished_andesite_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_ANDESITE));
    public static final Block DIORITE_SLAB = register("diorite_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.DIORITE));
    public static final Block BRICK_WALL = register("brick_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.BRICKS).forceSolidOn());
    public static final Block PRISMARINE_WALL = register("prismarine_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.PRISMARINE).forceSolidOn());
    public static final Block RED_SANDSTONE_WALL = register("red_sandstone_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.RED_SANDSTONE).forceSolidOn());
    public static final Block MOSSY_STONE_BRICK_WALL = register("mossy_stone_brick_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.MOSSY_STONE_BRICKS).forceSolidOn());
    public static final Block GRANITE_WALL = register("granite_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.GRANITE).forceSolidOn());
    public static final Block STONE_BRICK_WALL = register("stone_brick_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.STONE_BRICKS).forceSolidOn());
    public static final Block MUD_BRICK_WALL = register("mud_brick_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.MUD_BRICKS).forceSolidOn());
    public static final Block NETHER_BRICK_WALL = register("nether_brick_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.NETHER_BRICKS).forceSolidOn());
    public static final Block ANDESITE_WALL = register("andesite_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.ANDESITE).forceSolidOn());
    public static final Block RED_NETHER_BRICK_WALL = register("red_nether_brick_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.RED_NETHER_BRICKS).forceSolidOn());
    public static final Block SANDSTONE_WALL = register("sandstone_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.SANDSTONE).forceSolidOn());
    public static final Block END_STONE_BRICK_WALL = register("end_stone_brick_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.END_STONE_BRICKS).forceSolidOn());
    public static final Block DIORITE_WALL = register("diorite_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.DIORITE).forceSolidOn());
    public static final Block SCAFFOLDING = register("scaffolding", ScaffoldingBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).noCollision().sound(SoundType.SCAFFOLDING).dynamicShape().isValidSpawn(Blocks::never).pushReaction(PushReaction.DESTROY).isRedstoneConductor(Blocks::never));
    public static final Block LOOM = register("loom", LoomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BARREL = register("barrel", BarrelBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block SMOKER = register("smoker", SmokerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.5F).lightLevel(litBlockEmission(13)));
    public static final Block BLAST_FURNACE = register("blast_furnace", BlastFurnaceBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.5F).lightLevel(litBlockEmission(13)));
    public static final Block CARTOGRAPHY_TABLE = register("cartography_table", CartographyTableBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block FLETCHING_TABLE = register("fletching_table", BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block GRINDSTONE = register("grindstone", GrindstoneBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.STONE).pushReaction(PushReaction.BLOCK));
    public static final Block LECTERN = register("lectern", LecternBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block SMITHING_TABLE = register("smithing_table", SmithingTableBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block STONECUTTER = register("stonecutter", StonecutterBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.5F));
    public static final Block BELL = register("bell", BellBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).forceSolidOn().strength(5.0F).sound(SoundType.ANVIL).pushReaction(PushReaction.DESTROY));
    public static final Block LANTERN = register("lantern", LanternBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).lightLevel((blockstate) -> {
        return 15;
    }).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block SOUL_LANTERN = register("soul_lantern", LanternBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).lightLevel((blockstate) -> {
        return 10;
    }).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final WeatheringCopperBlocks COPPER_LANTERN = WeatheringCopperBlocks.create("copper_lantern", Blocks::register, LanternBlock::new, WeatheringLanternBlock::new, (weatheringcopper_weatherstate) -> {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).lightLevel((blockstate) -> {
            return 15;
        }).noOcclusion().pushReaction(PushReaction.DESTROY);
    });
    public static final Block CAMPFIRE = register("campfire", (blockbehaviour_properties) -> {
        return new CampfireBlock(true, 1, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).lightLevel(litBlockEmission(15)).noOcclusion().ignitedByLava());
    public static final Block SOUL_CAMPFIRE = register("soul_campfire", (blockbehaviour_properties) -> {
        return new CampfireBlock(false, 2, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).lightLevel(litBlockEmission(10)).noOcclusion().ignitedByLava());
    public static final Block SWEET_BERRY_BUSH = register("sweet_berry_bush", SweetBerryBushBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).randomTicks().noCollision().sound(SoundType.SWEET_BERRY_BUSH).pushReaction(PushReaction.DESTROY));
    public static final Block WARPED_STEM = register("warped_stem", RotatedPillarBlock::new, netherStemProperties(MapColor.WARPED_STEM));
    public static final Block STRIPPED_WARPED_STEM = register("stripped_warped_stem", RotatedPillarBlock::new, netherStemProperties(MapColor.WARPED_STEM));
    public static final Block WARPED_HYPHAE = register("warped_hyphae", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_HYPHAE).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.STEM));
    public static final Block STRIPPED_WARPED_HYPHAE = register("stripped_warped_hyphae", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_HYPHAE).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.STEM));
    public static final Block WARPED_NYLIUM = register("warped_nylium", NyliumBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_NYLIUM).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.4F).sound(SoundType.NYLIUM).randomTicks());
    public static final Block WARPED_FUNGUS = register("warped_fungus", (blockbehaviour_properties) -> {
        return new FungusBlock(TreeFeatures.WARPED_FUNGUS_PLANTED, Blocks.WARPED_NYLIUM, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).instabreak().noCollision().sound(SoundType.FUNGUS).pushReaction(PushReaction.DESTROY));
    public static final Block WARPED_WART_BLOCK = register("warped_wart_block", BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_WART_BLOCK).strength(1.0F).sound(SoundType.WART_BLOCK));
    public static final Block WARPED_ROOTS = register("warped_roots", RootsBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).replaceable().noCollision().instabreak().sound(SoundType.ROOTS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block NETHER_SPROUTS = register("nether_sprouts", NetherSproutsBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).replaceable().noCollision().instabreak().sound(SoundType.NETHER_SPROUTS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block CRIMSON_STEM = register("crimson_stem", RotatedPillarBlock::new, netherStemProperties(MapColor.CRIMSON_STEM));
    public static final Block STRIPPED_CRIMSON_STEM = register("stripped_crimson_stem", RotatedPillarBlock::new, netherStemProperties(MapColor.CRIMSON_STEM));
    public static final Block CRIMSON_HYPHAE = register("crimson_hyphae", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_HYPHAE).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.STEM));
    public static final Block STRIPPED_CRIMSON_HYPHAE = register("stripped_crimson_hyphae", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_HYPHAE).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.STEM));
    public static final Block CRIMSON_NYLIUM = register("crimson_nylium", NyliumBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_NYLIUM).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.4F).sound(SoundType.NYLIUM).randomTicks());
    public static final Block CRIMSON_FUNGUS = register("crimson_fungus", (blockbehaviour_properties) -> {
        return new FungusBlock(TreeFeatures.CRIMSON_FUNGUS_PLANTED, Blocks.CRIMSON_NYLIUM, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instabreak().noCollision().sound(SoundType.FUNGUS).pushReaction(PushReaction.DESTROY));
    public static final Block SHROOMLIGHT = register("shroomlight", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.0F).sound(SoundType.SHROOMLIGHT).lightLevel((blockstate) -> {
        return 15;
    }));
    public static final Block WEEPING_VINES = register("weeping_vines", WeepingVinesBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).randomTicks().noCollision().instabreak().sound(SoundType.WEEPING_VINES).pushReaction(PushReaction.DESTROY));
    public static final Block WEEPING_VINES_PLANT = register("weeping_vines_plant", WeepingVinesPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).noCollision().instabreak().sound(SoundType.WEEPING_VINES).pushReaction(PushReaction.DESTROY));
    public static final Block TWISTING_VINES = register("twisting_vines", TwistingVinesBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).randomTicks().noCollision().instabreak().sound(SoundType.WEEPING_VINES).pushReaction(PushReaction.DESTROY));
    public static final Block TWISTING_VINES_PLANT = register("twisting_vines_plant", TwistingVinesPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).noCollision().instabreak().sound(SoundType.WEEPING_VINES).pushReaction(PushReaction.DESTROY));
    public static final Block CRIMSON_ROOTS = register("crimson_roots", RootsBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).replaceable().noCollision().instabreak().sound(SoundType.ROOTS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final Block CRIMSON_PLANKS = register("crimson_planks", BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_STEM).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.NETHER_WOOD));
    public static final Block WARPED_PLANKS = register("warped_planks", BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_STEM).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.NETHER_WOOD));
    public static final Block CRIMSON_SLAB = register("crimson_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.CRIMSON_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.NETHER_WOOD));
    public static final Block WARPED_SLAB = register("warped_slab", SlabBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.WARPED_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.NETHER_WOOD));
    public static final Block CRIMSON_PRESSURE_PLATE = register("crimson_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.CRIMSON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.CRIMSON_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).pushReaction(PushReaction.DESTROY));
    public static final Block WARPED_PRESSURE_PLATE = register("warped_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.WARPED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.WARPED_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).pushReaction(PushReaction.DESTROY));
    public static final Block CRIMSON_FENCE = register("crimson_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.CRIMSON_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.NETHER_WOOD));
    public static final Block WARPED_FENCE = register("warped_fence", FenceBlock::new, BlockBehaviour.Properties.of().mapColor(Blocks.WARPED_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.NETHER_WOOD));
    public static final Block CRIMSON_TRAPDOOR = register("crimson_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.CRIMSON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.CRIMSON_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never));
    public static final Block WARPED_TRAPDOOR = register("warped_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.WARPED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.WARPED_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never));
    public static final Block CRIMSON_FENCE_GATE = register("crimson_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.CRIMSON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.CRIMSON_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F));
    public static final Block WARPED_FENCE_GATE = register("warped_fence_gate", (blockbehaviour_properties) -> {
        return new FenceGateBlock(WoodType.WARPED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.WARPED_PLANKS.defaultMapColor()).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F));
    public static final Block CRIMSON_STAIRS = registerLegacyStair("crimson_stairs", Blocks.CRIMSON_PLANKS);
    public static final Block WARPED_STAIRS = registerLegacyStair("warped_stairs", Blocks.WARPED_PLANKS);
    public static final Block CRIMSON_BUTTON = register("crimson_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.CRIMSON, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block WARPED_BUTTON = register("warped_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.WARPED, 30, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block CRIMSON_DOOR = register("crimson_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.CRIMSON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.CRIMSON_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block WARPED_DOOR = register("warped_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.WARPED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.WARPED_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block CRIMSON_SIGN = register("crimson_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.CRIMSON, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.CRIMSON_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).forceSolidOn().noCollision().strength(1.0F));
    public static final Block WARPED_SIGN = register("warped_sign", (blockbehaviour_properties) -> {
        return new StandingSignBlock(WoodType.WARPED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.WARPED_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).forceSolidOn().noCollision().strength(1.0F));
    public static final Block CRIMSON_WALL_SIGN = register("crimson_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.CRIMSON, blockbehaviour_properties);
    }, wallVariant(Blocks.CRIMSON_SIGN, true).mapColor(Blocks.CRIMSON_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).forceSolidOn().noCollision().strength(1.0F));
    public static final Block WARPED_WALL_SIGN = register("warped_wall_sign", (blockbehaviour_properties) -> {
        return new WallSignBlock(WoodType.WARPED, blockbehaviour_properties);
    }, wallVariant(Blocks.WARPED_SIGN, true).mapColor(Blocks.WARPED_PLANKS.defaultMapColor()).instrument(NoteBlockInstrument.BASS).forceSolidOn().noCollision().strength(1.0F));
    public static final Block STRUCTURE_BLOCK = register("structure_block", StructureBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).requiresCorrectToolForDrops().strength(-1.0F, 3600000.0F).noLootTable());
    public static final Block JIGSAW = register("jigsaw", JigsawBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).requiresCorrectToolForDrops().strength(-1.0F, 3600000.0F).noLootTable());
    public static final Block TEST_BLOCK = register("test_block", TestBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(-1.0F, 3600000.0F).noLootTable());
    public static final Block TEST_INSTANCE_BLOCK = register("test_instance_block", TestInstanceBlock::new, BlockBehaviour.Properties.of().noOcclusion().strength(-1.0F, 3600000.0F).noLootTable().isViewBlocking(Blocks::never));
    public static final Block COMPOSTER = register("composter", ComposterBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(0.6F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block TARGET = register("target", TargetBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(0.5F).sound(SoundType.GRASS));
    public static final Block BEE_NEST = register("bee_nest", BeehiveBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BASS).strength(0.3F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block BEEHIVE = register("beehive", BeehiveBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(0.6F).sound(SoundType.WOOD).ignitedByLava());
    public static final Block HONEY_BLOCK = register("honey_block", HoneyBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).speedFactor(0.4F).jumpFactor(0.5F).noOcclusion().sound(SoundType.HONEY_BLOCK));
    public static final Block HONEYCOMB_BLOCK = register("honeycomb_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.6F).sound(SoundType.CORAL_BLOCK));
    public static final Block NETHERITE_BLOCK = register("netherite_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(50.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK));
    public static final Block ANCIENT_DEBRIS = register("ancient_debris", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(30.0F, 1200.0F).sound(SoundType.ANCIENT_DEBRIS));
    public static final Block CRYING_OBSIDIAN = register("crying_obsidian", CryingObsidianBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(50.0F, 1200.0F).lightLevel((blockstate) -> {
        return 10;
    }));
    public static final Block RESPAWN_ANCHOR = register("respawn_anchor", RespawnAnchorBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(50.0F, 1200.0F).lightLevel((blockstate) -> {
        return RespawnAnchorBlock.getScaledChargeLevel(blockstate, 15);
    }));
    public static final Block POTTED_CRIMSON_FUNGUS = register("potted_crimson_fungus", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.CRIMSON_FUNGUS, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_WARPED_FUNGUS = register("potted_warped_fungus", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.WARPED_FUNGUS, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_CRIMSON_ROOTS = register("potted_crimson_roots", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.CRIMSON_ROOTS, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_WARPED_ROOTS = register("potted_warped_roots", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.WARPED_ROOTS, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block LODESTONE = register("lodestone", BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5F).sound(SoundType.LODESTONE).pushReaction(PushReaction.BLOCK));
    public static final Block BLACKSTONE = register("blackstone", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block BLACKSTONE_STAIRS = registerLegacyStair("blackstone_stairs", Blocks.BLACKSTONE);
    public static final Block BLACKSTONE_WALL = register("blackstone_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.BLACKSTONE).forceSolidOn());
    public static final Block BLACKSTONE_SLAB = register("blackstone_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.BLACKSTONE).strength(2.0F, 6.0F));
    public static final Block POLISHED_BLACKSTONE = register("polished_blackstone", BlockBehaviour.Properties.ofLegacyCopy(Blocks.BLACKSTONE).strength(2.0F, 6.0F));
    public static final Block POLISHED_BLACKSTONE_BRICKS = register("polished_blackstone_bricks", BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_BLACKSTONE).strength(1.5F, 6.0F));
    public static final Block CRACKED_POLISHED_BLACKSTONE_BRICKS = register("cracked_polished_blackstone_bricks", BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_BLACKSTONE_BRICKS));
    public static final Block CHISELED_POLISHED_BLACKSTONE = register("chiseled_polished_blackstone", BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_BLACKSTONE).strength(1.5F, 6.0F));
    public static final Block POLISHED_BLACKSTONE_BRICK_SLAB = register("polished_blackstone_brick_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_BLACKSTONE_BRICKS).strength(2.0F, 6.0F));
    public static final Block POLISHED_BLACKSTONE_BRICK_STAIRS = registerLegacyStair("polished_blackstone_brick_stairs", Blocks.POLISHED_BLACKSTONE_BRICKS);
    public static final Block POLISHED_BLACKSTONE_BRICK_WALL = register("polished_blackstone_brick_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_BLACKSTONE_BRICKS).forceSolidOn());
    public static final Block GILDED_BLACKSTONE = register("gilded_blackstone", BlockBehaviour.Properties.ofLegacyCopy(Blocks.BLACKSTONE).sound(SoundType.GILDED_BLACKSTONE));
    public static final Block POLISHED_BLACKSTONE_STAIRS = registerLegacyStair("polished_blackstone_stairs", Blocks.POLISHED_BLACKSTONE);
    public static final Block POLISHED_BLACKSTONE_SLAB = register("polished_blackstone_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_BLACKSTONE));
    public static final Block POLISHED_BLACKSTONE_PRESSURE_PLATE = register("polished_blackstone_pressure_plate", (blockbehaviour_properties) -> {
        return new PressurePlateBlock(BlockSetType.POLISHED_BLACKSTONE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).noCollision().strength(0.5F).pushReaction(PushReaction.DESTROY));
    public static final Block POLISHED_BLACKSTONE_BUTTON = register("polished_blackstone_button", (blockbehaviour_properties) -> {
        return new ButtonBlock(BlockSetType.STONE, 20, blockbehaviour_properties);
    }, buttonProperties());
    public static final Block POLISHED_BLACKSTONE_WALL = register("polished_blackstone_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_BLACKSTONE).forceSolidOn());
    public static final Block CHISELED_NETHER_BRICKS = register("chiseled_nether_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.NETHER_BRICKS));
    public static final Block CRACKED_NETHER_BRICKS = register("cracked_nether_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.NETHER_BRICKS));
    public static final Block QUARTZ_BRICKS = register("quartz_bricks", BlockBehaviour.Properties.ofLegacyCopy(Blocks.QUARTZ_BLOCK));
    public static final Block CANDLE = register("candle", CandleBlock::new, candleProperties(MapColor.SAND));
    public static final Block WHITE_CANDLE = register("white_candle", CandleBlock::new, candleProperties(MapColor.WOOL));
    public static final Block ORANGE_CANDLE = register("orange_candle", CandleBlock::new, candleProperties(MapColor.COLOR_ORANGE));
    public static final Block MAGENTA_CANDLE = register("magenta_candle", CandleBlock::new, candleProperties(MapColor.COLOR_MAGENTA));
    public static final Block LIGHT_BLUE_CANDLE = register("light_blue_candle", CandleBlock::new, candleProperties(MapColor.COLOR_LIGHT_BLUE));
    public static final Block YELLOW_CANDLE = register("yellow_candle", CandleBlock::new, candleProperties(MapColor.COLOR_YELLOW));
    public static final Block LIME_CANDLE = register("lime_candle", CandleBlock::new, candleProperties(MapColor.COLOR_LIGHT_GREEN));
    public static final Block PINK_CANDLE = register("pink_candle", CandleBlock::new, candleProperties(MapColor.COLOR_PINK));
    public static final Block GRAY_CANDLE = register("gray_candle", CandleBlock::new, candleProperties(MapColor.COLOR_GRAY));
    public static final Block LIGHT_GRAY_CANDLE = register("light_gray_candle", CandleBlock::new, candleProperties(MapColor.COLOR_LIGHT_GRAY));
    public static final Block CYAN_CANDLE = register("cyan_candle", CandleBlock::new, candleProperties(MapColor.COLOR_CYAN));
    public static final Block PURPLE_CANDLE = register("purple_candle", CandleBlock::new, candleProperties(MapColor.COLOR_PURPLE));
    public static final Block BLUE_CANDLE = register("blue_candle", CandleBlock::new, candleProperties(MapColor.COLOR_BLUE));
    public static final Block BROWN_CANDLE = register("brown_candle", CandleBlock::new, candleProperties(MapColor.COLOR_BROWN));
    public static final Block GREEN_CANDLE = register("green_candle", CandleBlock::new, candleProperties(MapColor.COLOR_GREEN));
    public static final Block RED_CANDLE = register("red_candle", CandleBlock::new, candleProperties(MapColor.COLOR_RED));
    public static final Block BLACK_CANDLE = register("black_candle", CandleBlock::new, candleProperties(MapColor.COLOR_BLACK));
    public static final Block CANDLE_CAKE = register("candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CAKE).lightLevel(litBlockEmission(3)));
    public static final Block WHITE_CANDLE_CAKE = register("white_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.WHITE_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block ORANGE_CANDLE_CAKE = register("orange_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.ORANGE_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block MAGENTA_CANDLE_CAKE = register("magenta_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.MAGENTA_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block LIGHT_BLUE_CANDLE_CAKE = register("light_blue_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.LIGHT_BLUE_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block YELLOW_CANDLE_CAKE = register("yellow_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.YELLOW_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block LIME_CANDLE_CAKE = register("lime_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.LIME_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block PINK_CANDLE_CAKE = register("pink_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.PINK_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block GRAY_CANDLE_CAKE = register("gray_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.GRAY_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block LIGHT_GRAY_CANDLE_CAKE = register("light_gray_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.LIGHT_GRAY_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block CYAN_CANDLE_CAKE = register("cyan_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.CYAN_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block PURPLE_CANDLE_CAKE = register("purple_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.PURPLE_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block BLUE_CANDLE_CAKE = register("blue_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.BLUE_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block BROWN_CANDLE_CAKE = register("brown_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.BROWN_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block GREEN_CANDLE_CAKE = register("green_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.GREEN_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block RED_CANDLE_CAKE = register("red_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.RED_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block BLACK_CANDLE_CAKE = register("black_candle_cake", (blockbehaviour_properties) -> {
        return new CandleCakeBlock(Blocks.BLACK_CANDLE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.CANDLE_CAKE));
    public static final Block AMETHYST_BLOCK = register("amethyst_block", AmethystBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops());
    public static final Block BUDDING_AMETHYST = register("budding_amethyst", BuddingAmethystBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).randomTicks().strength(1.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops().pushReaction(PushReaction.DESTROY));
    public static final Block AMETHYST_CLUSTER = register("amethyst_cluster", (blockbehaviour_properties) -> {
        return new AmethystClusterBlock(7.0F, 10.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER).strength(1.5F).lightLevel((blockstate) -> {
        return 5;
    }).pushReaction(PushReaction.DESTROY));
    public static final Block LARGE_AMETHYST_BUD = register("large_amethyst_bud", (blockbehaviour_properties) -> {
        return new AmethystClusterBlock(5.0F, 10.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.AMETHYST_CLUSTER).sound(SoundType.MEDIUM_AMETHYST_BUD).lightLevel((blockstate) -> {
        return 4;
    }));
    public static final Block MEDIUM_AMETHYST_BUD = register("medium_amethyst_bud", (blockbehaviour_properties) -> {
        return new AmethystClusterBlock(4.0F, 10.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.AMETHYST_CLUSTER).sound(SoundType.LARGE_AMETHYST_BUD).lightLevel((blockstate) -> {
        return 2;
    }));
    public static final Block SMALL_AMETHYST_BUD = register("small_amethyst_bud", (blockbehaviour_properties) -> {
        return new AmethystClusterBlock(3.0F, 8.0F, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.AMETHYST_CLUSTER).sound(SoundType.SMALL_AMETHYST_BUD).lightLevel((blockstate) -> {
        return 1;
    }));
    public static final Block TUFF = register("tuff", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.TUFF).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final Block TUFF_SLAB = register("tuff_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.TUFF));
    public static final Block TUFF_STAIRS = register("tuff_stairs", (blockbehaviour_properties) -> {
        return new StairBlock(Blocks.TUFF.defaultBlockState(), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.TUFF));
    public static final Block TUFF_WALL = register("tuff_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.TUFF).forceSolidOn());
    public static final Block POLISHED_TUFF = register("polished_tuff", BlockBehaviour.Properties.ofLegacyCopy(Blocks.TUFF).sound(SoundType.POLISHED_TUFF));
    public static final Block POLISHED_TUFF_SLAB = register("polished_tuff_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_TUFF));
    public static final Block POLISHED_TUFF_STAIRS = register("polished_tuff_stairs", (blockbehaviour_properties) -> {
        return new StairBlock(Blocks.POLISHED_TUFF.defaultBlockState(), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_TUFF));
    public static final Block POLISHED_TUFF_WALL = register("polished_tuff_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_TUFF).forceSolidOn());
    public static final Block CHISELED_TUFF = register("chiseled_tuff", BlockBehaviour.Properties.ofLegacyCopy(Blocks.TUFF));
    public static final Block TUFF_BRICKS = register("tuff_bricks", BlockBehaviour.Properties.ofLegacyCopy(Blocks.TUFF).sound(SoundType.TUFF_BRICKS));
    public static final Block TUFF_BRICK_SLAB = register("tuff_brick_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.TUFF_BRICKS));
    public static final Block TUFF_BRICK_STAIRS = register("tuff_brick_stairs", (blockbehaviour_properties) -> {
        return new StairBlock(Blocks.TUFF_BRICKS.defaultBlockState(), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.TUFF_BRICKS));
    public static final Block TUFF_BRICK_WALL = register("tuff_brick_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.TUFF_BRICKS).forceSolidOn());
    public static final Block CHISELED_TUFF_BRICKS = register("chiseled_tuff_bricks", BlockBehaviour.Properties.ofLegacyCopy(Blocks.TUFF_BRICKS));
    public static final Block CALCITE = register("calcite", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.CALCITE).requiresCorrectToolForDrops().strength(0.75F));
    public static final Block TINTED_GLASS = register("tinted_glass", TintedGlassBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).mapColor(MapColor.COLOR_GRAY).noOcclusion().isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never).isViewBlocking(Blocks::never));
    public static final Block POWDER_SNOW = register("powder_snow", PowderSnowBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.25F).sound(SoundType.POWDER_SNOW).dynamicShape().noOcclusion().isRedstoneConductor(Blocks::never));
    public static final Block SCULK_SENSOR = register("sculk_sensor", SculkSensorBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.5F).sound(SoundType.SCULK_SENSOR).lightLevel((blockstate) -> {
        return 1;
    }).emissiveRendering((blockstate, blockgetter, blockpos) -> {
        return SculkSensorBlock.getPhase(blockstate) == SculkSensorPhase.ACTIVE;
    }));
    public static final Block CALIBRATED_SCULK_SENSOR = register("calibrated_sculk_sensor", CalibratedSculkSensorBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.SCULK_SENSOR));
    public static final Block SCULK = register("sculk", SculkBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.2F).sound(SoundType.SCULK));
    public static final Block SCULK_VEIN = register("sculk_vein", SculkVeinBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).forceSolidOn().noCollision().strength(0.2F).sound(SoundType.SCULK_VEIN).pushReaction(PushReaction.DESTROY));
    public static final Block SCULK_CATALYST = register("sculk_catalyst", SculkCatalystBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 3.0F).sound(SoundType.SCULK_CATALYST).lightLevel((blockstate) -> {
        return 6;
    }));
    public static final Block SCULK_SHRIEKER = register("sculk_shrieker", SculkShriekerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 3.0F).sound(SoundType.SCULK_SHRIEKER));
    public static final Block COPPER_BLOCK = register("copper_block", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.COPPER));
    public static final Block EXPOSED_COPPER = register("exposed_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).mapColor(MapColor.TERRACOTTA_LIGHT_GRAY));
    public static final Block WEATHERED_COPPER = register("weathered_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).mapColor(MapColor.WARPED_STEM));
    public static final Block OXIDIZED_COPPER = register("oxidized_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).mapColor(MapColor.WARPED_NYLIUM));
    public static final Block COPPER_ORE = register("copper_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(ConstantInt.of(0), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.IRON_ORE));
    public static final Block DEEPSLATE_COPPER_ORE = register("deepslate_copper_ore", (blockbehaviour_properties) -> {
        return new DropExperienceBlock(ConstantInt.of(0), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofLegacyCopy(Blocks.COPPER_ORE).mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final Block OXIDIZED_CUT_COPPER = register("oxidized_cut_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_COPPER));
    public static final Block WEATHERED_CUT_COPPER = register("weathered_cut_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER));
    public static final Block EXPOSED_CUT_COPPER = register("exposed_cut_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER));
    public static final Block CUT_COPPER = register("cut_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK));
    public static final Block OXIDIZED_CHISELED_COPPER = register("oxidized_chiseled_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_COPPER));
    public static final Block WEATHERED_CHISELED_COPPER = register("weathered_chiseled_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER));
    public static final Block EXPOSED_CHISELED_COPPER = register("exposed_chiseled_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER));
    public static final Block CHISELED_COPPER = register("chiseled_copper", (blockbehaviour_properties) -> {
        return new WeatheringCopperFullBlock(WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK));
    public static final Block WAXED_OXIDIZED_CHISELED_COPPER = register("waxed_oxidized_chiseled_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_CHISELED_COPPER));
    public static final Block WAXED_WEATHERED_CHISELED_COPPER = register("waxed_weathered_chiseled_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_CHISELED_COPPER));
    public static final Block WAXED_EXPOSED_CHISELED_COPPER = register("waxed_exposed_chiseled_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_CHISELED_COPPER));
    public static final Block WAXED_CHISELED_COPPER = register("waxed_chiseled_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_COPPER));
    public static final Block OXIDIZED_CUT_COPPER_STAIRS = register("oxidized_cut_copper_stairs", (blockbehaviour_properties) -> {
        return new WeatheringCopperStairBlock(WeatheringCopper.WeatherState.OXIDIZED, Blocks.OXIDIZED_CUT_COPPER.defaultBlockState(), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_CUT_COPPER));
    public static final Block WEATHERED_CUT_COPPER_STAIRS = register("weathered_cut_copper_stairs", (blockbehaviour_properties) -> {
        return new WeatheringCopperStairBlock(WeatheringCopper.WeatherState.WEATHERED, Blocks.WEATHERED_CUT_COPPER.defaultBlockState(), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER));
    public static final Block EXPOSED_CUT_COPPER_STAIRS = register("exposed_cut_copper_stairs", (blockbehaviour_properties) -> {
        return new WeatheringCopperStairBlock(WeatheringCopper.WeatherState.EXPOSED, Blocks.EXPOSED_CUT_COPPER.defaultBlockState(), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER));
    public static final Block CUT_COPPER_STAIRS = register("cut_copper_stairs", (blockbehaviour_properties) -> {
        return new WeatheringCopperStairBlock(WeatheringCopper.WeatherState.UNAFFECTED, Blocks.CUT_COPPER.defaultBlockState(), blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK));
    public static final Block OXIDIZED_CUT_COPPER_SLAB = register("oxidized_cut_copper_slab", (blockbehaviour_properties) -> {
        return new WeatheringCopperSlabBlock(WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_CUT_COPPER));
    public static final Block WEATHERED_CUT_COPPER_SLAB = register("weathered_cut_copper_slab", (blockbehaviour_properties) -> {
        return new WeatheringCopperSlabBlock(WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_CUT_COPPER));
    public static final Block EXPOSED_CUT_COPPER_SLAB = register("exposed_cut_copper_slab", (blockbehaviour_properties) -> {
        return new WeatheringCopperSlabBlock(WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_CUT_COPPER));
    public static final Block CUT_COPPER_SLAB = register("cut_copper_slab", (blockbehaviour_properties) -> {
        return new WeatheringCopperSlabBlock(WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.CUT_COPPER));
    public static final Block WAXED_COPPER_BLOCK = register("waxed_copper_block", BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK));
    public static final Block WAXED_WEATHERED_COPPER = register("waxed_weathered_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER));
    public static final Block WAXED_EXPOSED_COPPER = register("waxed_exposed_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER));
    public static final Block WAXED_OXIDIZED_COPPER = register("waxed_oxidized_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_COPPER));
    public static final Block WAXED_OXIDIZED_CUT_COPPER = register("waxed_oxidized_cut_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_COPPER));
    public static final Block WAXED_WEATHERED_CUT_COPPER = register("waxed_weathered_cut_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER));
    public static final Block WAXED_EXPOSED_CUT_COPPER = register("waxed_exposed_cut_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER));
    public static final Block WAXED_CUT_COPPER = register("waxed_cut_copper", BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK));
    public static final Block WAXED_OXIDIZED_CUT_COPPER_STAIRS = registerStair("waxed_oxidized_cut_copper_stairs", Blocks.WAXED_OXIDIZED_CUT_COPPER);
    public static final Block WAXED_WEATHERED_CUT_COPPER_STAIRS = registerStair("waxed_weathered_cut_copper_stairs", Blocks.WAXED_WEATHERED_CUT_COPPER);
    public static final Block WAXED_EXPOSED_CUT_COPPER_STAIRS = registerStair("waxed_exposed_cut_copper_stairs", Blocks.WAXED_EXPOSED_CUT_COPPER);
    public static final Block WAXED_CUT_COPPER_STAIRS = registerStair("waxed_cut_copper_stairs", Blocks.WAXED_CUT_COPPER);
    public static final Block WAXED_OXIDIZED_CUT_COPPER_SLAB = register("waxed_oxidized_cut_copper_slab", SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WAXED_OXIDIZED_CUT_COPPER).requiresCorrectToolForDrops());
    public static final Block WAXED_WEATHERED_CUT_COPPER_SLAB = register("waxed_weathered_cut_copper_slab", SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WAXED_WEATHERED_CUT_COPPER).requiresCorrectToolForDrops());
    public static final Block WAXED_EXPOSED_CUT_COPPER_SLAB = register("waxed_exposed_cut_copper_slab", SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WAXED_EXPOSED_CUT_COPPER).requiresCorrectToolForDrops());
    public static final Block WAXED_CUT_COPPER_SLAB = register("waxed_cut_copper_slab", SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WAXED_CUT_COPPER).requiresCorrectToolForDrops());
    public static final Block COPPER_DOOR = register("copper_door", (blockbehaviour_properties) -> {
        return new WeatheringCopperDoorBlock(BlockSetType.COPPER, WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.COPPER_BLOCK.defaultMapColor()).strength(3.0F, 6.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block EXPOSED_COPPER_DOOR = register("exposed_copper_door", (blockbehaviour_properties) -> {
        return new WeatheringCopperDoorBlock(BlockSetType.COPPER, WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_DOOR).mapColor(Blocks.EXPOSED_COPPER.defaultMapColor()));
    public static final Block OXIDIZED_COPPER_DOOR = register("oxidized_copper_door", (blockbehaviour_properties) -> {
        return new WeatheringCopperDoorBlock(BlockSetType.COPPER, WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_DOOR).mapColor(Blocks.OXIDIZED_COPPER.defaultMapColor()));
    public static final Block WEATHERED_COPPER_DOOR = register("weathered_copper_door", (blockbehaviour_properties) -> {
        return new WeatheringCopperDoorBlock(BlockSetType.COPPER, WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_DOOR).mapColor(Blocks.WEATHERED_COPPER.defaultMapColor()));
    public static final Block WAXED_COPPER_DOOR = register("waxed_copper_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.COPPER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_DOOR));
    public static final Block WAXED_EXPOSED_COPPER_DOOR = register("waxed_exposed_copper_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.COPPER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER_DOOR));
    public static final Block WAXED_OXIDIZED_COPPER_DOOR = register("waxed_oxidized_copper_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.COPPER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_COPPER_DOOR));
    public static final Block WAXED_WEATHERED_COPPER_DOOR = register("waxed_weathered_copper_door", (blockbehaviour_properties) -> {
        return new DoorBlock(BlockSetType.COPPER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER_DOOR));
    public static final Block COPPER_TRAPDOOR = register("copper_trapdoor", (blockbehaviour_properties) -> {
        return new WeatheringCopperTrapDoorBlock(BlockSetType.COPPER, WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.COPPER_BLOCK.defaultMapColor()).strength(3.0F, 6.0F).requiresCorrectToolForDrops().noOcclusion().isValidSpawn(Blocks::never));
    public static final Block EXPOSED_COPPER_TRAPDOOR = register("exposed_copper_trapdoor", (blockbehaviour_properties) -> {
        return new WeatheringCopperTrapDoorBlock(BlockSetType.COPPER, WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_TRAPDOOR).mapColor(Blocks.EXPOSED_COPPER.defaultMapColor()));
    public static final Block OXIDIZED_COPPER_TRAPDOOR = register("oxidized_copper_trapdoor", (blockbehaviour_properties) -> {
        return new WeatheringCopperTrapDoorBlock(BlockSetType.COPPER, WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_TRAPDOOR).mapColor(Blocks.OXIDIZED_COPPER.defaultMapColor()));
    public static final Block WEATHERED_COPPER_TRAPDOOR = register("weathered_copper_trapdoor", (blockbehaviour_properties) -> {
        return new WeatheringCopperTrapDoorBlock(BlockSetType.COPPER, WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_TRAPDOOR).mapColor(Blocks.WEATHERED_COPPER.defaultMapColor()));
    public static final Block WAXED_COPPER_TRAPDOOR = register("waxed_copper_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.COPPER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_TRAPDOOR));
    public static final Block WAXED_EXPOSED_COPPER_TRAPDOOR = register("waxed_exposed_copper_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.COPPER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER_TRAPDOOR));
    public static final Block WAXED_OXIDIZED_COPPER_TRAPDOOR = register("waxed_oxidized_copper_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.COPPER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_COPPER_TRAPDOOR));
    public static final Block WAXED_WEATHERED_COPPER_TRAPDOOR = register("waxed_weathered_copper_trapdoor", (blockbehaviour_properties) -> {
        return new TrapDoorBlock(BlockSetType.COPPER, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER_TRAPDOOR));
    public static final Block COPPER_GRATE = register("copper_grate", (blockbehaviour_properties) -> {
        return new WeatheringCopperGrateBlock(WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.COPPER_GRATE).mapColor(MapColor.COLOR_ORANGE).noOcclusion().requiresCorrectToolForDrops().isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never).isViewBlocking(Blocks::never));
    public static final Block EXPOSED_COPPER_GRATE = register("exposed_copper_grate", (blockbehaviour_properties) -> {
        return new WeatheringCopperGrateBlock(WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_GRATE).mapColor(MapColor.TERRACOTTA_LIGHT_GRAY));
    public static final Block WEATHERED_COPPER_GRATE = register("weathered_copper_grate", (blockbehaviour_properties) -> {
        return new WeatheringCopperGrateBlock(WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_GRATE).mapColor(MapColor.WARPED_STEM));
    public static final Block OXIDIZED_COPPER_GRATE = register("oxidized_copper_grate", (blockbehaviour_properties) -> {
        return new WeatheringCopperGrateBlock(WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_GRATE).mapColor(MapColor.WARPED_NYLIUM));
    public static final Block WAXED_COPPER_GRATE = register("waxed_copper_grate", WaterloggedTransparentBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_GRATE));
    public static final Block WAXED_EXPOSED_COPPER_GRATE = register("waxed_exposed_copper_grate", WaterloggedTransparentBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER_GRATE));
    public static final Block WAXED_WEATHERED_COPPER_GRATE = register("waxed_weathered_copper_grate", WaterloggedTransparentBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER_GRATE));
    public static final Block WAXED_OXIDIZED_COPPER_GRATE = register("waxed_oxidized_copper_grate", WaterloggedTransparentBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_COPPER_GRATE));
    public static final Block COPPER_BULB = register("copper_bulb", (blockbehaviour_properties) -> {
        return new WeatheringCopperBulbBlock(WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.COPPER_BLOCK.defaultMapColor()).strength(3.0F, 6.0F).sound(SoundType.COPPER_BULB).requiresCorrectToolForDrops().isRedstoneConductor(Blocks::never).lightLevel(litBlockEmission(15)));
    public static final Block EXPOSED_COPPER_BULB = register("exposed_copper_bulb", (blockbehaviour_properties) -> {
        return new WeatheringCopperBulbBlock(WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BULB).mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).lightLevel(litBlockEmission(12)));
    public static final Block WEATHERED_COPPER_BULB = register("weathered_copper_bulb", (blockbehaviour_properties) -> {
        return new WeatheringCopperBulbBlock(WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BULB).mapColor(MapColor.WARPED_STEM).lightLevel(litBlockEmission(8)));
    public static final Block OXIDIZED_COPPER_BULB = register("oxidized_copper_bulb", (blockbehaviour_properties) -> {
        return new WeatheringCopperBulbBlock(WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BULB).mapColor(MapColor.WARPED_NYLIUM).lightLevel(litBlockEmission(4)));
    public static final Block WAXED_COPPER_BULB = register("waxed_copper_bulb", CopperBulbBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BULB));
    public static final Block WAXED_EXPOSED_COPPER_BULB = register("waxed_exposed_copper_bulb", CopperBulbBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER_BULB));
    public static final Block WAXED_WEATHERED_COPPER_BULB = register("waxed_weathered_copper_bulb", CopperBulbBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER_BULB));
    public static final Block WAXED_OXIDIZED_COPPER_BULB = register("waxed_oxidized_copper_bulb", CopperBulbBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_COPPER_BULB));
    public static final Block COPPER_CHEST = register("copper_chest", (blockbehaviour_properties) -> {
        return new WeatheringCopperChestBlock(WeatheringCopper.WeatherState.UNAFFECTED, SoundEvents.COPPER_CHEST_OPEN, SoundEvents.COPPER_CHEST_CLOSE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.COPPER_BLOCK.defaultMapColor()).strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops());
    public static final Block EXPOSED_COPPER_CHEST = register("exposed_copper_chest", (blockbehaviour_properties) -> {
        return new WeatheringCopperChestBlock(WeatheringCopper.WeatherState.EXPOSED, SoundEvents.COPPER_CHEST_OPEN, SoundEvents.COPPER_CHEST_CLOSE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_CHEST).mapColor(MapColor.TERRACOTTA_LIGHT_GRAY));
    public static final Block WEATHERED_COPPER_CHEST = register("weathered_copper_chest", (blockbehaviour_properties) -> {
        return new WeatheringCopperChestBlock(WeatheringCopper.WeatherState.WEATHERED, SoundEvents.COPPER_CHEST_WEATHERED_OPEN, SoundEvents.COPPER_CHEST_WEATHERED_CLOSE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_CHEST).mapColor(MapColor.WARPED_STEM));
    public static final Block OXIDIZED_COPPER_CHEST = register("oxidized_copper_chest", (blockbehaviour_properties) -> {
        return new WeatheringCopperChestBlock(WeatheringCopper.WeatherState.OXIDIZED, SoundEvents.COPPER_CHEST_OXIDIZED_OPEN, SoundEvents.COPPER_CHEST_OXIDIZED_CLOSE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_CHEST).mapColor(MapColor.WARPED_NYLIUM));
    public static final Block WAXED_COPPER_CHEST = register("waxed_copper_chest", (blockbehaviour_properties) -> {
        return new CopperChestBlock(WeatheringCopper.WeatherState.UNAFFECTED, SoundEvents.COPPER_CHEST_OPEN, SoundEvents.COPPER_CHEST_CLOSE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_CHEST));
    public static final Block WAXED_EXPOSED_COPPER_CHEST = register("waxed_exposed_copper_chest", (blockbehaviour_properties) -> {
        return new CopperChestBlock(WeatheringCopper.WeatherState.EXPOSED, SoundEvents.COPPER_CHEST_OPEN, SoundEvents.COPPER_CHEST_CLOSE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER_CHEST));
    public static final Block WAXED_WEATHERED_COPPER_CHEST = register("waxed_weathered_copper_chest", (blockbehaviour_properties) -> {
        return new CopperChestBlock(WeatheringCopper.WeatherState.WEATHERED, SoundEvents.COPPER_CHEST_WEATHERED_OPEN, SoundEvents.COPPER_CHEST_WEATHERED_CLOSE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER_CHEST));
    public static final Block WAXED_OXIDIZED_COPPER_CHEST = register("waxed_oxidized_copper_chest", (blockbehaviour_properties) -> {
        return new CopperChestBlock(WeatheringCopper.WeatherState.OXIDIZED, SoundEvents.COPPER_CHEST_OXIDIZED_OPEN, SoundEvents.COPPER_CHEST_OXIDIZED_CLOSE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_COPPER_CHEST));
    public static final Block COPPER_GOLEM_STATUE = register("copper_golem_statue", (blockbehaviour_properties) -> {
        return new WeatheringCopperGolemStatueBlock(WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.COPPER_BLOCK.defaultMapColor()).strength(3.0F, 6.0F).sound(SoundType.COPPER_GOLEM_STATUE).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final Block EXPOSED_COPPER_GOLEM_STATUE = register("exposed_copper_golem_statue", (blockbehaviour_properties) -> {
        return new WeatheringCopperGolemStatueBlock(WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_GOLEM_STATUE).mapColor(MapColor.TERRACOTTA_LIGHT_GRAY));
    public static final Block WEATHERED_COPPER_GOLEM_STATUE = register("weathered_copper_golem_statue", (blockbehaviour_properties) -> {
        return new WeatheringCopperGolemStatueBlock(WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_GOLEM_STATUE).mapColor(MapColor.WARPED_STEM));
    public static final Block OXIDIZED_COPPER_GOLEM_STATUE = register("oxidized_copper_golem_statue", (blockbehaviour_properties) -> {
        return new WeatheringCopperGolemStatueBlock(WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_GOLEM_STATUE).mapColor(MapColor.WARPED_NYLIUM));
    public static final Block WAXED_COPPER_GOLEM_STATUE = register("waxed_copper_golem_statue", (blockbehaviour_properties) -> {
        return new CopperGolemStatueBlock(WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_GOLEM_STATUE));
    public static final Block WAXED_EXPOSED_COPPER_GOLEM_STATUE = register("waxed_exposed_copper_golem_statue", (blockbehaviour_properties) -> {
        return new CopperGolemStatueBlock(WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_COPPER_GOLEM_STATUE));
    public static final Block WAXED_WEATHERED_COPPER_GOLEM_STATUE = register("waxed_weathered_copper_golem_statue", (blockbehaviour_properties) -> {
        return new CopperGolemStatueBlock(WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_COPPER_GOLEM_STATUE));
    public static final Block WAXED_OXIDIZED_COPPER_GOLEM_STATUE = register("waxed_oxidized_copper_golem_statue", (blockbehaviour_properties) -> {
        return new CopperGolemStatueBlock(WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_COPPER_GOLEM_STATUE));
    public static final Block LIGHTNING_ROD = register("lightning_rod", (blockbehaviour_properties) -> {
        return new WeatheringLightningRodBlock(WeatheringCopper.WeatherState.UNAFFECTED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).forceSolidOn().requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.COPPER).noOcclusion());
    public static final Block EXPOSED_LIGHTNING_ROD = register("exposed_lightning_rod", (blockbehaviour_properties) -> {
        return new WeatheringLightningRodBlock(WeatheringCopper.WeatherState.EXPOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHTNING_ROD).mapColor(MapColor.TERRACOTTA_LIGHT_GRAY));
    public static final Block WEATHERED_LIGHTNING_ROD = register("weathered_lightning_rod", (blockbehaviour_properties) -> {
        return new WeatheringLightningRodBlock(WeatheringCopper.WeatherState.WEATHERED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHTNING_ROD).mapColor(MapColor.WARPED_STEM));
    public static final Block OXIDIZED_LIGHTNING_ROD = register("oxidized_lightning_rod", (blockbehaviour_properties) -> {
        return new WeatheringLightningRodBlock(WeatheringCopper.WeatherState.OXIDIZED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHTNING_ROD).mapColor(MapColor.WARPED_NYLIUM));
    public static final Block WAXED_LIGHTNING_ROD = register("waxed_lightning_rod", LightningRodBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHTNING_ROD));
    public static final Block WAXED_EXPOSED_LIGHTNING_ROD = register("waxed_exposed_lightning_rod", LightningRodBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.EXPOSED_LIGHTNING_ROD));
    public static final Block WAXED_WEATHERED_LIGHTNING_ROD = register("waxed_weathered_lightning_rod", LightningRodBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WEATHERED_LIGHTNING_ROD));
    public static final Block WAXED_OXIDIZED_LIGHTNING_ROD = register("waxed_oxidized_lightning_rod", LightningRodBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OXIDIZED_LIGHTNING_ROD));
    public static final Block POINTED_DRIPSTONE = register("pointed_dripstone", PointedDripstoneBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).noOcclusion().sound(SoundType.POINTED_DRIPSTONE).randomTicks().strength(1.5F, 3.0F).dynamicShape().offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY).isRedstoneConductor(Blocks::never).noOcclusion());
    public static final Block DRIPSTONE_BLOCK = register("dripstone_block", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.DRIPSTONE_BLOCK).requiresCorrectToolForDrops().strength(1.5F, 1.0F));
    public static final Block CAVE_VINES = register("cave_vines", CaveVinesBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).randomTicks().noCollision().lightLevel(CaveVines.emission(14)).instabreak().sound(SoundType.CAVE_VINES).pushReaction(PushReaction.DESTROY));
    public static final Block CAVE_VINES_PLANT = register("cave_vines_plant", CaveVinesPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().lightLevel(CaveVines.emission(14)).instabreak().sound(SoundType.CAVE_VINES).pushReaction(PushReaction.DESTROY));
    public static final Block SPORE_BLOSSOM = register("spore_blossom", SporeBlossomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).instabreak().noCollision().sound(SoundType.SPORE_BLOSSOM).pushReaction(PushReaction.DESTROY));
    public static final Block AZALEA = register("azalea", AzaleaBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).forceSolidOff().instabreak().sound(SoundType.AZALEA).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block FLOWERING_AZALEA = register("flowering_azalea", AzaleaBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).forceSolidOff().instabreak().sound(SoundType.FLOWERING_AZALEA).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Block MOSS_CARPET = register("moss_carpet", CarpetBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.1F).sound(SoundType.MOSS_CARPET).pushReaction(PushReaction.DESTROY));
    public static final Block PINK_PETALS = register("pink_petals", FlowerBedBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().sound(SoundType.PINK_PETALS).pushReaction(PushReaction.DESTROY));
    public static final Block WILDFLOWERS = register("wildflowers", FlowerBedBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().sound(SoundType.PINK_PETALS).pushReaction(PushReaction.DESTROY));
    public static final Block LEAF_LITTER = register("leaf_litter", LeafLitterBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).replaceable().noCollision().sound(SoundType.LEAF_LITTER).pushReaction(PushReaction.DESTROY));
    public static final Block MOSS_BLOCK = register("moss_block", (blockbehaviour_properties) -> {
        return new BonemealableFeaturePlacerBlock(CaveFeatures.MOSS_PATCH_BONEMEAL, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.1F).sound(SoundType.MOSS).pushReaction(PushReaction.DESTROY));
    public static final Block BIG_DRIPLEAF = register("big_dripleaf", BigDripleafBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).forceSolidOff().strength(0.1F).sound(SoundType.BIG_DRIPLEAF).pushReaction(PushReaction.DESTROY));
    public static final Block BIG_DRIPLEAF_STEM = register("big_dripleaf_stem", BigDripleafStemBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().strength(0.1F).sound(SoundType.BIG_DRIPLEAF).pushReaction(PushReaction.DESTROY));
    public static final Block SMALL_DRIPLEAF = register("small_dripleaf", SmallDripleafBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.SMALL_DRIPLEAF).offsetType(BlockBehaviour.OffsetType.XYZ).pushReaction(PushReaction.DESTROY));
    public static final Block HANGING_ROOTS = register("hanging_roots", HangingRootsBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).replaceable().noCollision().instabreak().sound(SoundType.HANGING_ROOTS).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY));
    public static final Block ROOTED_DIRT = register("rooted_dirt", RootedDirtBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.ROOTED_DIRT));
    public static final Block MUD = register("mud", MudBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.DIRT).mapColor(MapColor.TERRACOTTA_CYAN).isValidSpawn(Blocks::always).isRedstoneConductor(Blocks::always).isViewBlocking(Blocks::always).isSuffocating(Blocks::always).sound(SoundType.MUD));
    public static final Block DEEPSLATE = register("deepslate", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.DEEPSLATE));
    public static final Block COBBLED_DEEPSLATE = register("cobbled_deepslate", BlockBehaviour.Properties.ofLegacyCopy(Blocks.DEEPSLATE).strength(3.5F, 6.0F));
    public static final Block COBBLED_DEEPSLATE_STAIRS = registerLegacyStair("cobbled_deepslate_stairs", Blocks.COBBLED_DEEPSLATE);
    public static final Block COBBLED_DEEPSLATE_SLAB = register("cobbled_deepslate_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.COBBLED_DEEPSLATE));
    public static final Block COBBLED_DEEPSLATE_WALL = register("cobbled_deepslate_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.COBBLED_DEEPSLATE).forceSolidOn());
    public static final Block POLISHED_DEEPSLATE = register("polished_deepslate", BlockBehaviour.Properties.ofLegacyCopy(Blocks.COBBLED_DEEPSLATE).sound(SoundType.POLISHED_DEEPSLATE));
    public static final Block POLISHED_DEEPSLATE_STAIRS = registerLegacyStair("polished_deepslate_stairs", Blocks.POLISHED_DEEPSLATE);
    public static final Block POLISHED_DEEPSLATE_SLAB = register("polished_deepslate_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_DEEPSLATE));
    public static final Block POLISHED_DEEPSLATE_WALL = register("polished_deepslate_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.POLISHED_DEEPSLATE).forceSolidOn());
    public static final Block DEEPSLATE_TILES = register("deepslate_tiles", BlockBehaviour.Properties.ofLegacyCopy(Blocks.COBBLED_DEEPSLATE).sound(SoundType.DEEPSLATE_TILES));
    public static final Block DEEPSLATE_TILE_STAIRS = registerLegacyStair("deepslate_tile_stairs", Blocks.DEEPSLATE_TILES);
    public static final Block DEEPSLATE_TILE_SLAB = register("deepslate_tile_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.DEEPSLATE_TILES));
    public static final Block DEEPSLATE_TILE_WALL = register("deepslate_tile_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.DEEPSLATE_TILES).forceSolidOn());
    public static final Block DEEPSLATE_BRICKS = register("deepslate_bricks", BlockBehaviour.Properties.ofLegacyCopy(Blocks.COBBLED_DEEPSLATE).sound(SoundType.DEEPSLATE_BRICKS));
    public static final Block DEEPSLATE_BRICK_STAIRS = registerLegacyStair("deepslate_brick_stairs", Blocks.DEEPSLATE_BRICKS);
    public static final Block DEEPSLATE_BRICK_SLAB = register("deepslate_brick_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.DEEPSLATE_BRICKS));
    public static final Block DEEPSLATE_BRICK_WALL = register("deepslate_brick_wall", WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(Blocks.DEEPSLATE_BRICKS).forceSolidOn());
    public static final Block CHISELED_DEEPSLATE = register("chiseled_deepslate", BlockBehaviour.Properties.ofLegacyCopy(Blocks.COBBLED_DEEPSLATE).sound(SoundType.DEEPSLATE_BRICKS));
    public static final Block CRACKED_DEEPSLATE_BRICKS = register("cracked_deepslate_bricks", BlockBehaviour.Properties.ofLegacyCopy(Blocks.DEEPSLATE_BRICKS));
    public static final Block CRACKED_DEEPSLATE_TILES = register("cracked_deepslate_tiles", BlockBehaviour.Properties.ofLegacyCopy(Blocks.DEEPSLATE_TILES));
    public static final Block INFESTED_DEEPSLATE = register("infested_deepslate", (blockbehaviour_properties) -> {
        return new InfestedRotatedPillarBlock(Blocks.DEEPSLATE, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).sound(SoundType.DEEPSLATE));
    public static final Block SMOOTH_BASALT = register("smooth_basalt", BlockBehaviour.Properties.ofLegacyCopy(Blocks.BASALT));
    public static final Block RAW_IRON_BLOCK = register("raw_iron_block", BlockBehaviour.Properties.of().mapColor(MapColor.RAW_IRON).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(5.0F, 6.0F));
    public static final Block RAW_COPPER_BLOCK = register("raw_copper_block", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(5.0F, 6.0F));
    public static final Block RAW_GOLD_BLOCK = register("raw_gold_block", BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(5.0F, 6.0F));
    public static final Block POTTED_AZALEA = register("potted_azalea_bush", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.AZALEA, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block POTTED_FLOWERING_AZALEA = register("potted_flowering_azalea_bush", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.FLOWERING_AZALEA, blockbehaviour_properties);
    }, flowerPotProperties());
    public static final Block OCHRE_FROGLIGHT = register("ochre_froglight", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.3F).lightLevel((blockstate) -> {
        return 15;
    }).sound(SoundType.FROGLIGHT));
    public static final Block VERDANT_FROGLIGHT = register("verdant_froglight", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.GLOW_LICHEN).strength(0.3F).lightLevel((blockstate) -> {
        return 15;
    }).sound(SoundType.FROGLIGHT));
    public static final Block PEARLESCENT_FROGLIGHT = register("pearlescent_froglight", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.3F).lightLevel((blockstate) -> {
        return 15;
    }).sound(SoundType.FROGLIGHT));
    public static final Block FROGSPAWN = register("frogspawn", FrogspawnBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WATER).instabreak().noOcclusion().noCollision().sound(SoundType.FROGSPAWN).pushReaction(PushReaction.DESTROY));
    public static final Block REINFORCED_DEEPSLATE = register("reinforced_deepslate", BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.DEEPSLATE).strength(55.0F, 1200.0F));
    public static final Block DECORATED_POT = register("decorated_pot", DecoratedPotBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).strength(0.0F, 0.0F).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final Block CRAFTER = register("crafter", CrafterBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 3.5F));
    public static final Block TRIAL_SPAWNER = register("trial_spawner", TrialSpawnerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).lightLevel((blockstate) -> {
        return ((TrialSpawnerState) blockstate.getValue(TrialSpawnerBlock.STATE)).lightLevel();
    }).strength(50.0F).sound(SoundType.TRIAL_SPAWNER).isViewBlocking(Blocks::never).noOcclusion());
    public static final Block VAULT = register("vault", VaultBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).noOcclusion().sound(SoundType.VAULT).lightLevel((blockstate) -> {
        return ((VaultState) blockstate.getValue(VaultBlock.STATE)).lightLevel();
    }).strength(50.0F).isViewBlocking(Blocks::never));
    public static final Block HEAVY_CORE = register("heavy_core", HeavyCoreBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).instrument(NoteBlockInstrument.SNARE).sound(SoundType.HEAVY_CORE).strength(10.0F).pushReaction(PushReaction.NORMAL).explosionResistance(1200.0F));
    public static final Block PALE_MOSS_BLOCK = register("pale_moss_block", (blockbehaviour_properties) -> {
        return new BonemealableFeaturePlacerBlock(VegetationFeatures.PALE_MOSS_PATCH_BONEMEAL, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().ignitedByLava().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.1F).sound(SoundType.MOSS).pushReaction(PushReaction.DESTROY));
    public static final Block PALE_MOSS_CARPET = register("pale_moss_carpet", MossyCarpetBlock::new, BlockBehaviour.Properties.of().ignitedByLava().mapColor(Blocks.PALE_MOSS_BLOCK.defaultMapColor()).strength(0.1F).sound(SoundType.MOSS_CARPET).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final Block PALE_HANGING_MOSS = register("pale_hanging_moss", HangingMossBlock::new, BlockBehaviour.Properties.of().ignitedByLava().mapColor(Blocks.PALE_MOSS_BLOCK.defaultMapColor()).noCollision().sound(SoundType.MOSS_CARPET).pushReaction(PushReaction.DESTROY));
    public static final Block OPEN_EYEBLOSSOM = register("open_eyeblossom", (blockbehaviour_properties) -> {
        return new EyeblossomBlock(EyeblossomBlock.Type.OPEN, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.CREAKING_HEART.defaultMapColor()).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY).randomTicks());
    public static final Block CLOSED_EYEBLOSSOM = register("closed_eyeblossom", (blockbehaviour_properties) -> {
        return new EyeblossomBlock(EyeblossomBlock.Type.CLOSED, blockbehaviour_properties);
    }, BlockBehaviour.Properties.of().mapColor(Blocks.PALE_OAK_LEAVES.defaultMapColor()).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY).randomTicks());
    public static final Block POTTED_OPEN_EYEBLOSSOM = register("potted_open_eyeblossom", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.OPEN_EYEBLOSSOM, blockbehaviour_properties);
    }, flowerPotProperties().randomTicks());
    public static final Block POTTED_CLOSED_EYEBLOSSOM = register("potted_closed_eyeblossom", (blockbehaviour_properties) -> {
        return new FlowerPotBlock(Blocks.CLOSED_EYEBLOSSOM, blockbehaviour_properties);
    }, flowerPotProperties().randomTicks());
    public static final Block FIREFLY_BUSH = register("firefly_bush", FireflyBushBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).ignitedByLava().lightLevel((blockstate) -> {
        return 2;
    }).noCollision().instabreak().sound(SoundType.SWEET_BERRY_BUSH).pushReaction(PushReaction.DESTROY));

    public Blocks() {}

    private static ToIntFunction<BlockState> litBlockEmission(int lightEmission) {
        return (blockstate) -> {
            return (Boolean) blockstate.getValue(BlockStateProperties.LIT) ? lightEmission : 0;
        };
    }

    private static Function<BlockState, MapColor> waterloggedMapColor(MapColor mapColor) {
        return (blockstate) -> {
            return (Boolean) blockstate.getValue(BlockStateProperties.WATERLOGGED) ? MapColor.WATER : mapColor;
        };
    }

    private static Boolean never(BlockState state, BlockGetter blockGetter, BlockPos blockPos, EntityType<?> entityType) {
        return false;
    }

    private static Boolean always(BlockState state, BlockGetter blockGetter, BlockPos blockPos, EntityType<?> entityType) {
        return true;
    }

    private static Boolean ocelotOrParrot(BlockState state, BlockGetter blockGetter, BlockPos blockPos, EntityType<?> entityType) {
        return entityType == EntityType.OCELOT || entityType == EntityType.PARROT;
    }

    private static Block registerBed(String id, DyeColor color) {
        return register(id, (blockbehaviour_properties) -> {
            return new BedBlock(color, blockbehaviour_properties);
        }, BlockBehaviour.Properties.of().mapColor((blockstate) -> {
            return blockstate.getValue(BedBlock.PART) == BedPart.FOOT ? color.getMapColor() : MapColor.WOOL;
        }).sound(SoundType.WOOD).strength(0.2F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
    }

    private static BlockBehaviour.Properties logProperties(MapColor topColor, MapColor sideColor, SoundType soundType) {
        return BlockBehaviour.Properties.of().mapColor((blockstate) -> {
            return blockstate.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y ? topColor : sideColor;
        }).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(soundType).ignitedByLava();
    }

    private static BlockBehaviour.Properties netherStemProperties(MapColor mapColor) {
        return BlockBehaviour.Properties.of().mapColor((blockstate) -> {
            return mapColor;
        }).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.STEM);
    }

    private static boolean always(BlockState state, BlockGetter blockGetter, BlockPos blockPos) {
        return true;
    }

    private static boolean never(BlockState state, BlockGetter blockGetter, BlockPos blockPos) {
        return false;
    }

    private static Block registerStainedGlass(String id, DyeColor color) {
        return register(id, (blockbehaviour_properties) -> {
            return new StainedGlassBlock(color, blockbehaviour_properties);
        }, BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion().isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never).isViewBlocking(Blocks::never));
    }

    private static BlockBehaviour.Properties leavesProperties(SoundType soundType) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(soundType).noOcclusion().isValidSpawn(Blocks::ocelotOrParrot).isSuffocating(Blocks::never).isViewBlocking(Blocks::never).ignitedByLava().pushReaction(PushReaction.DESTROY).isRedstoneConductor(Blocks::never);
    }

    private static BlockBehaviour.Properties shulkerBoxProperties(MapColor mapColor) {
        return BlockBehaviour.Properties.of().mapColor(mapColor).forceSolidOn().strength(2.0F).dynamicShape().noOcclusion().isSuffocating(Blocks.NOT_CLOSED_SHULKER).isViewBlocking(Blocks.NOT_CLOSED_SHULKER).pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties pistonProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F).isRedstoneConductor(Blocks::never).isSuffocating(Blocks.NOT_EXTENDED_PISTON).isViewBlocking(Blocks.NOT_EXTENDED_PISTON).pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties buttonProperties() {
        return BlockBehaviour.Properties.of().noCollision().strength(0.5F).pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties flowerPotProperties() {
        return BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties candleProperties(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).noOcclusion().strength(0.1F).sound(SoundType.CANDLE).lightLevel(CandleBlock.LIGHT_EMISSION).pushReaction(PushReaction.DESTROY);
    }

    /** @deprecated */
    @Deprecated
    private static Block registerLegacyStair(String id, Block base) {
        return register(id, (blockbehaviour_properties) -> {
            return new StairBlock(base.defaultBlockState(), blockbehaviour_properties);
        }, BlockBehaviour.Properties.ofLegacyCopy(base));
    }

    private static Block registerStair(String id, Block base) {
        return register(id, (blockbehaviour_properties) -> {
            return new StairBlock(base.defaultBlockState(), blockbehaviour_properties);
        }, BlockBehaviour.Properties.ofFullCopy(base));
    }

    private static BlockBehaviour.Properties wallVariant(Block standingBlock, boolean copyName) {
        BlockBehaviour.Properties blockbehaviour_properties = BlockBehaviour.Properties.of().overrideLootTable(standingBlock.getLootTable());

        if (copyName) {
            blockbehaviour_properties = blockbehaviour_properties.overrideDescription(standingBlock.getDescriptionId());
        }

        return blockbehaviour_properties;
    }

    private static Block register(ResourceKey<Block> id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        Block block = (Block) factory.apply(properties.setId(id));

        return (Block) Registry.register(BuiltInRegistries.BLOCK, id, block);
    }

    private static Block register(ResourceKey<Block> id, BlockBehaviour.Properties properties) {
        return register(id, Block::new, properties);
    }

    private static ResourceKey<Block> vanillaBlockId(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(name));
    }

    private static Block register(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        return register(vanillaBlockId(id), factory, properties);
    }

    private static Block register(String id, BlockBehaviour.Properties properties) {
        return register(id, Block::new, properties);
    }

    static {
        for (Block block : BuiltInRegistries.BLOCK) {
            UnmodifiableIterator unmodifiableiterator = block.getStateDefinition().getPossibleStates().iterator();

            while (unmodifiableiterator.hasNext()) {
                BlockState blockstate = (BlockState) unmodifiableiterator.next();

                Block.BLOCK_STATE_REGISTRY.add(blockstate);
                blockstate.initCache();
            }
        }

    }
}
