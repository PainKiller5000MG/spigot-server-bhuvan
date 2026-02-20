package net.minecraft.util.datafix;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.DataFixerBuilder.Result;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.fixes.AbstractArrowPickupFix;
import net.minecraft.util.datafix.fixes.AddFieldFix;
import net.minecraft.util.datafix.fixes.AddFlagIfNotPresentFix;
import net.minecraft.util.datafix.fixes.AddNewChoices;
import net.minecraft.util.datafix.fixes.AdvancementsFix;
import net.minecraft.util.datafix.fixes.AdvancementsRenameFix;
import net.minecraft.util.datafix.fixes.AreaEffectCloudDurationScaleFix;
import net.minecraft.util.datafix.fixes.AreaEffectCloudPotionFix;
import net.minecraft.util.datafix.fixes.AttributeIdPrefixFix;
import net.minecraft.util.datafix.fixes.AttributeModifierIdFix;
import net.minecraft.util.datafix.fixes.AttributesRenameLegacy;
import net.minecraft.util.datafix.fixes.BannerEntityCustomNameToOverrideComponentFix;
import net.minecraft.util.datafix.fixes.BannerPatternFormatFix;
import net.minecraft.util.datafix.fixes.BedItemColorFix;
import net.minecraft.util.datafix.fixes.BeehiveFieldRenameFix;
import net.minecraft.util.datafix.fixes.BiomeFix;
import net.minecraft.util.datafix.fixes.BitStorageAlignFix;
import net.minecraft.util.datafix.fixes.BlendingDataFix;
import net.minecraft.util.datafix.fixes.BlendingDataRemoveFromNetherEndFix;
import net.minecraft.util.datafix.fixes.BlockEntityBannerColorFix;
import net.minecraft.util.datafix.fixes.BlockEntityBlockStateFix;
import net.minecraft.util.datafix.fixes.BlockEntityCustomNameToComponentFix;
import net.minecraft.util.datafix.fixes.BlockEntityFurnaceBurnTimeFix;
import net.minecraft.util.datafix.fixes.BlockEntityIdFix;
import net.minecraft.util.datafix.fixes.BlockEntityJukeboxFix;
import net.minecraft.util.datafix.fixes.BlockEntityKeepPacked;
import net.minecraft.util.datafix.fixes.BlockEntityRenameFix;
import net.minecraft.util.datafix.fixes.BlockEntityShulkerBoxColorFix;
import net.minecraft.util.datafix.fixes.BlockEntitySignDoubleSidedEditableTextFix;
import net.minecraft.util.datafix.fixes.BlockEntityUUIDFix;
import net.minecraft.util.datafix.fixes.BlockNameFlatteningFix;
import net.minecraft.util.datafix.fixes.BlockPosFormatAndRenamesFix;
import net.minecraft.util.datafix.fixes.BlockPropertyRenameAndFix;
import net.minecraft.util.datafix.fixes.BlockRenameFix;
import net.minecraft.util.datafix.fixes.BlockStateStructureTemplateFix;
import net.minecraft.util.datafix.fixes.BoatSplitFix;
import net.minecraft.util.datafix.fixes.CarvingStepRemoveFix;
import net.minecraft.util.datafix.fixes.CatTypeFix;
import net.minecraft.util.datafix.fixes.CauldronRenameFix;
import net.minecraft.util.datafix.fixes.CavesAndCliffsRenames;
import net.minecraft.util.datafix.fixes.ChestedHorsesInventoryZeroIndexingFix;
import net.minecraft.util.datafix.fixes.ChunkBedBlockEntityInjecterFix;
import net.minecraft.util.datafix.fixes.ChunkBiomeFix;
import net.minecraft.util.datafix.fixes.ChunkDeleteIgnoredLightDataFix;
import net.minecraft.util.datafix.fixes.ChunkDeleteLightFix;
import net.minecraft.util.datafix.fixes.ChunkHeightAndBiomeFix;
import net.minecraft.util.datafix.fixes.ChunkLightRemoveFix;
import net.minecraft.util.datafix.fixes.ChunkPalettedStorageFix;
import net.minecraft.util.datafix.fixes.ChunkProtoTickListFix;
import net.minecraft.util.datafix.fixes.ChunkRenamesFix;
import net.minecraft.util.datafix.fixes.ChunkStatusFix;
import net.minecraft.util.datafix.fixes.ChunkStatusFix2;
import net.minecraft.util.datafix.fixes.ChunkStructuresTemplateRenameFix;
import net.minecraft.util.datafix.fixes.ChunkTicketUnpackPosFix;
import net.minecraft.util.datafix.fixes.ChunkToProtochunkFix;
import net.minecraft.util.datafix.fixes.ColorlessShulkerEntityFix;
import net.minecraft.util.datafix.fixes.ContainerBlockEntityLockPredicateFix;
import net.minecraft.util.datafix.fixes.CopperGolemWeatherStateFix;
import net.minecraft.util.datafix.fixes.CriteriaRenameFix;
import net.minecraft.util.datafix.fixes.CustomModelDataExpandFix;
import net.minecraft.util.datafix.fixes.DebugProfileOverlayReferenceFix;
import net.minecraft.util.datafix.fixes.DecoratedPotFieldRenameFix;
import net.minecraft.util.datafix.fixes.DropChancesFormatFix;
import net.minecraft.util.datafix.fixes.DropInvalidSignDataFix;
import net.minecraft.util.datafix.fixes.DyeItemRenameFix;
import net.minecraft.util.datafix.fixes.EffectDurationFix;
import net.minecraft.util.datafix.fixes.EmptyItemInHotbarFix;
import net.minecraft.util.datafix.fixes.EmptyItemInVillagerTradeFix;
import net.minecraft.util.datafix.fixes.EntityArmorStandSilentFix;
import net.minecraft.util.datafix.fixes.EntityAttributeBaseValueFix;
import net.minecraft.util.datafix.fixes.EntityBlockStateFix;
import net.minecraft.util.datafix.fixes.EntityBrushableBlockFieldsRenameFix;
import net.minecraft.util.datafix.fixes.EntityCatSplitFix;
import net.minecraft.util.datafix.fixes.EntityCodSalmonFix;
import net.minecraft.util.datafix.fixes.EntityCustomNameToComponentFix;
import net.minecraft.util.datafix.fixes.EntityElderGuardianSplitFix;
import net.minecraft.util.datafix.fixes.EntityEquipmentToArmorAndHandFix;
import net.minecraft.util.datafix.fixes.EntityFallDistanceFloatToDoubleFix;
import net.minecraft.util.datafix.fixes.EntityFieldsRenameFix;
import net.minecraft.util.datafix.fixes.EntityGoatMissingStateFix;
import net.minecraft.util.datafix.fixes.EntityHealthFix;
import net.minecraft.util.datafix.fixes.EntityHorseSaddleFix;
import net.minecraft.util.datafix.fixes.EntityHorseSplitFix;
import net.minecraft.util.datafix.fixes.EntityIdFix;
import net.minecraft.util.datafix.fixes.EntityItemFrameDirectionFix;
import net.minecraft.util.datafix.fixes.EntityMinecartIdentifiersFix;
import net.minecraft.util.datafix.fixes.EntityPaintingItemFrameDirectionFix;
import net.minecraft.util.datafix.fixes.EntityPaintingMotiveFix;
import net.minecraft.util.datafix.fixes.EntityProjectileOwnerFix;
import net.minecraft.util.datafix.fixes.EntityPufferfishRenameFix;
import net.minecraft.util.datafix.fixes.EntityRavagerRenameFix;
import net.minecraft.util.datafix.fixes.EntityRedundantChanceTagsFix;
import net.minecraft.util.datafix.fixes.EntityRidingToPassengersFix;
import net.minecraft.util.datafix.fixes.EntitySalmonSizeFix;
import net.minecraft.util.datafix.fixes.EntityShulkerColorFix;
import net.minecraft.util.datafix.fixes.EntityShulkerRotationFix;
import net.minecraft.util.datafix.fixes.EntitySkeletonSplitFix;
import net.minecraft.util.datafix.fixes.EntitySpawnerItemVariantComponentFix;
import net.minecraft.util.datafix.fixes.EntityStringUuidFix;
import net.minecraft.util.datafix.fixes.EntityTheRenameningFix;
import net.minecraft.util.datafix.fixes.EntityTippedArrowFix;
import net.minecraft.util.datafix.fixes.EntityUUIDFix;
import net.minecraft.util.datafix.fixes.EntityVariantFix;
import net.minecraft.util.datafix.fixes.EntityWolfColorFix;
import net.minecraft.util.datafix.fixes.EntityZombieSplitFix;
import net.minecraft.util.datafix.fixes.EntityZombieVillagerTypeFix;
import net.minecraft.util.datafix.fixes.EntityZombifiedPiglinRenameFix;
import net.minecraft.util.datafix.fixes.EquipmentFormatFix;
import net.minecraft.util.datafix.fixes.EquippableAssetRenameFix;
import net.minecraft.util.datafix.fixes.FeatureFlagRemoveFix;
import net.minecraft.util.datafix.fixes.FilteredBooksFix;
import net.minecraft.util.datafix.fixes.FilteredSignsFix;
import net.minecraft.util.datafix.fixes.FireResistantToDamageResistantComponentFix;
import net.minecraft.util.datafix.fixes.FixProjectileStoredItem;
import net.minecraft.util.datafix.fixes.FixWolfHealth;
import net.minecraft.util.datafix.fixes.FoodToConsumableFix;
import net.minecraft.util.datafix.fixes.ForcePoiRebuild;
import net.minecraft.util.datafix.fixes.ForcedChunkToTicketFix;
import net.minecraft.util.datafix.fixes.FurnaceRecipeFix;
import net.minecraft.util.datafix.fixes.GameRuleRegistryFix;
import net.minecraft.util.datafix.fixes.GoatHornIdFix;
import net.minecraft.util.datafix.fixes.GossipUUIDFix;
import net.minecraft.util.datafix.fixes.HeightmapRenamingFix;
import net.minecraft.util.datafix.fixes.HorseBodyArmorItemFix;
import net.minecraft.util.datafix.fixes.IglooMetadataRemovalFix;
import net.minecraft.util.datafix.fixes.InlineBlockPosFormatFix;
import net.minecraft.util.datafix.fixes.InvalidBlockEntityLockFix;
import net.minecraft.util.datafix.fixes.InvalidLockComponentFix;
import net.minecraft.util.datafix.fixes.ItemBannerColorFix;
import net.minecraft.util.datafix.fixes.ItemCustomNameToComponentFix;
import net.minecraft.util.datafix.fixes.ItemIdFix;
import net.minecraft.util.datafix.fixes.ItemLoreFix;
import net.minecraft.util.datafix.fixes.ItemPotionFix;
import net.minecraft.util.datafix.fixes.ItemRenameFix;
import net.minecraft.util.datafix.fixes.ItemShulkerBoxColorFix;
import net.minecraft.util.datafix.fixes.ItemSpawnEggFix;
import net.minecraft.util.datafix.fixes.ItemStackComponentizationFix;
import net.minecraft.util.datafix.fixes.ItemStackCustomNameToOverrideComponentFix;
import net.minecraft.util.datafix.fixes.ItemStackEnchantmentNamesFix;
import net.minecraft.util.datafix.fixes.ItemStackMapIdFix;
import net.minecraft.util.datafix.fixes.ItemStackSpawnEggFix;
import net.minecraft.util.datafix.fixes.ItemStackTheFlatteningFix;
import net.minecraft.util.datafix.fixes.ItemStackUUIDFix;
import net.minecraft.util.datafix.fixes.ItemWaterPotionFix;
import net.minecraft.util.datafix.fixes.JigsawPropertiesFix;
import net.minecraft.util.datafix.fixes.JigsawRotationFix;
import net.minecraft.util.datafix.fixes.JukeboxTicksSinceSongStartedFix;
import net.minecraft.util.datafix.fixes.LeavesFix;
import net.minecraft.util.datafix.fixes.LegacyDimensionIdFix;
import net.minecraft.util.datafix.fixes.LegacyDragonFightFix;
import net.minecraft.util.datafix.fixes.LegacyHoverEventFix;
import net.minecraft.util.datafix.fixes.LegacyWorldBorderFix;
import net.minecraft.util.datafix.fixes.LevelDataGeneratorOptionsFix;
import net.minecraft.util.datafix.fixes.LevelFlatGeneratorInfoFix;
import net.minecraft.util.datafix.fixes.LevelLegacyWorldGenSettingsFix;
import net.minecraft.util.datafix.fixes.LevelUUIDFix;
import net.minecraft.util.datafix.fixes.LockComponentPredicateFix;
import net.minecraft.util.datafix.fixes.LodestoneCompassComponentFix;
import net.minecraft.util.datafix.fixes.MapBannerBlockPosFormatFix;
import net.minecraft.util.datafix.fixes.MapIdFix;
import net.minecraft.util.datafix.fixes.MemoryExpiryDataFix;
import net.minecraft.util.datafix.fixes.MissingDimensionFix;
import net.minecraft.util.datafix.fixes.MobEffectIdFix;
import net.minecraft.util.datafix.fixes.MobSpawnerEntityIdentifiersFix;
import net.minecraft.util.datafix.fixes.NamedEntityConvertUncheckedFix;
import net.minecraft.util.datafix.fixes.NamedEntityWriteReadFix;
import net.minecraft.util.datafix.fixes.NamespacedTypeRenameFix;
import net.minecraft.util.datafix.fixes.NewVillageFix;
import net.minecraft.util.datafix.fixes.ObjectiveRenderTypeFix;
import net.minecraft.util.datafix.fixes.OminousBannerBlockEntityRenameFix;
import net.minecraft.util.datafix.fixes.OminousBannerRarityFix;
import net.minecraft.util.datafix.fixes.OminousBannerRenameFix;
import net.minecraft.util.datafix.fixes.OptionsAccessibilityOnboardFix;
import net.minecraft.util.datafix.fixes.OptionsAddTextBackgroundFix;
import net.minecraft.util.datafix.fixes.OptionsAmbientOcclusionFix;
import net.minecraft.util.datafix.fixes.OptionsFancyGraphicsToGraphicsModeFix;
import net.minecraft.util.datafix.fixes.OptionsForceVBOFix;
import net.minecraft.util.datafix.fixes.OptionsGraphicsModeSplitFix;
import net.minecraft.util.datafix.fixes.OptionsKeyLwjgl3Fix;
import net.minecraft.util.datafix.fixes.OptionsKeyTranslationFix;
import net.minecraft.util.datafix.fixes.OptionsLowerCaseLanguageFix;
import net.minecraft.util.datafix.fixes.OptionsMenuBlurrinessFix;
import net.minecraft.util.datafix.fixes.OptionsMusicToastFix;
import net.minecraft.util.datafix.fixes.OptionsProgrammerArtFix;
import net.minecraft.util.datafix.fixes.OptionsRenameFieldFix;
import net.minecraft.util.datafix.fixes.OptionsSetGraphicsPresetToCustomFix;
import net.minecraft.util.datafix.fixes.OverreachingTickFix;
import net.minecraft.util.datafix.fixes.ParticleUnflatteningFix;
import net.minecraft.util.datafix.fixes.PlayerEquipmentFix;
import net.minecraft.util.datafix.fixes.PlayerHeadBlockProfileFix;
import net.minecraft.util.datafix.fixes.PlayerRespawnDataFix;
import net.minecraft.util.datafix.fixes.PlayerUUIDFix;
import net.minecraft.util.datafix.fixes.PoiTypeRemoveFix;
import net.minecraft.util.datafix.fixes.PoiTypeRenameFix;
import net.minecraft.util.datafix.fixes.PrimedTntBlockStateFixer;
import net.minecraft.util.datafix.fixes.ProjectileStoredWeaponFix;
import net.minecraft.util.datafix.fixes.RaidRenamesDataFix;
import net.minecraft.util.datafix.fixes.RandomSequenceSettingsFix;
import net.minecraft.util.datafix.fixes.RecipesFix;
import net.minecraft.util.datafix.fixes.RecipesRenameningFix;
import net.minecraft.util.datafix.fixes.RedstoneWireConnectionsFix;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.util.datafix.fixes.RemapChunkStatusFix;
import net.minecraft.util.datafix.fixes.RemoveBlockEntityTagFix;
import net.minecraft.util.datafix.fixes.RemoveEmptyItemInBrushableBlockFix;
import net.minecraft.util.datafix.fixes.RemoveGolemGossipFix;
import net.minecraft.util.datafix.fixes.RenameEnchantmentsFix;
import net.minecraft.util.datafix.fixes.RenamedCoralFansFix;
import net.minecraft.util.datafix.fixes.RenamedCoralFix;
import net.minecraft.util.datafix.fixes.ReorganizePoi;
import net.minecraft.util.datafix.fixes.SaddleEquipmentSlotFix;
import net.minecraft.util.datafix.fixes.SavedDataFeaturePoolElementFix;
import net.minecraft.util.datafix.fixes.SavedDataUUIDFix;
import net.minecraft.util.datafix.fixes.ScoreboardDisplayNameFix;
import net.minecraft.util.datafix.fixes.ScoreboardDisplaySlotFix;
import net.minecraft.util.datafix.fixes.SignTextStrictJsonFix;
import net.minecraft.util.datafix.fixes.SpawnerDataFix;
import net.minecraft.util.datafix.fixes.StatsCounterFix;
import net.minecraft.util.datafix.fixes.StatsRenameFix;
import net.minecraft.util.datafix.fixes.StriderGravityFix;
import net.minecraft.util.datafix.fixes.StructureReferenceCountFix;
import net.minecraft.util.datafix.fixes.StructureSettingsFlattenFix;
import net.minecraft.util.datafix.fixes.StructuresBecomeConfiguredFix;
import net.minecraft.util.datafix.fixes.TextComponentHoverAndClickEventFix;
import net.minecraft.util.datafix.fixes.TextComponentStringifiedFlagsFix;
import net.minecraft.util.datafix.fixes.ThrownPotionSplitFix;
import net.minecraft.util.datafix.fixes.TippedArrowPotionToItemFix;
import net.minecraft.util.datafix.fixes.TooltipDisplayComponentFix;
import net.minecraft.util.datafix.fixes.TrappedChestBlockEntityFix;
import net.minecraft.util.datafix.fixes.TrialSpawnerConfigFix;
import net.minecraft.util.datafix.fixes.TrialSpawnerConfigInRegistryFix;
import net.minecraft.util.datafix.fixes.TridentAnimationFix;
import net.minecraft.util.datafix.fixes.UnflattenTextComponentFix;
import net.minecraft.util.datafix.fixes.VariantRenameFix;
import net.minecraft.util.datafix.fixes.VillagerDataFix;
import net.minecraft.util.datafix.fixes.VillagerFollowRangeFix;
import net.minecraft.util.datafix.fixes.VillagerRebuildLevelAndXpFix;
import net.minecraft.util.datafix.fixes.VillagerSetCanPickUpLootFix;
import net.minecraft.util.datafix.fixes.VillagerTradeFix;
import net.minecraft.util.datafix.fixes.WallPropertyFix;
import net.minecraft.util.datafix.fixes.WeaponSmithChestLootTableFix;
import net.minecraft.util.datafix.fixes.WorldBorderWarningTimeFix;
import net.minecraft.util.datafix.fixes.WorldGenSettingsDisallowOldCustomWorldsFix;
import net.minecraft.util.datafix.fixes.WorldGenSettingsFix;
import net.minecraft.util.datafix.fixes.WorldGenSettingsHeightAndBiomeFix;
import net.minecraft.util.datafix.fixes.WorldSpawnDataFix;
import net.minecraft.util.datafix.fixes.WriteAndReadFix;
import net.minecraft.util.datafix.fixes.WrittenBookPagesStrictJsonFix;
import net.minecraft.util.datafix.fixes.ZombieVillagerRebuildXpFix;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import net.minecraft.util.datafix.schemas.V100;
import net.minecraft.util.datafix.schemas.V102;
import net.minecraft.util.datafix.schemas.V1022;
import net.minecraft.util.datafix.schemas.V106;
import net.minecraft.util.datafix.schemas.V107;
import net.minecraft.util.datafix.schemas.V1125;
import net.minecraft.util.datafix.schemas.V135;
import net.minecraft.util.datafix.schemas.V143;
import net.minecraft.util.datafix.schemas.V1451;
import net.minecraft.util.datafix.schemas.V1451_1;
import net.minecraft.util.datafix.schemas.V1451_2;
import net.minecraft.util.datafix.schemas.V1451_3;
import net.minecraft.util.datafix.schemas.V1451_4;
import net.minecraft.util.datafix.schemas.V1451_5;
import net.minecraft.util.datafix.schemas.V1451_6;
import net.minecraft.util.datafix.schemas.V1458;
import net.minecraft.util.datafix.schemas.V1460;
import net.minecraft.util.datafix.schemas.V1466;
import net.minecraft.util.datafix.schemas.V1470;
import net.minecraft.util.datafix.schemas.V1481;
import net.minecraft.util.datafix.schemas.V1483;
import net.minecraft.util.datafix.schemas.V1486;
import net.minecraft.util.datafix.schemas.V1488;
import net.minecraft.util.datafix.schemas.V1510;
import net.minecraft.util.datafix.schemas.V1800;
import net.minecraft.util.datafix.schemas.V1801;
import net.minecraft.util.datafix.schemas.V1904;
import net.minecraft.util.datafix.schemas.V1906;
import net.minecraft.util.datafix.schemas.V1909;
import net.minecraft.util.datafix.schemas.V1920;
import net.minecraft.util.datafix.schemas.V1928;
import net.minecraft.util.datafix.schemas.V1929;
import net.minecraft.util.datafix.schemas.V1931;
import net.minecraft.util.datafix.schemas.V2100;
import net.minecraft.util.datafix.schemas.V2501;
import net.minecraft.util.datafix.schemas.V2502;
import net.minecraft.util.datafix.schemas.V2505;
import net.minecraft.util.datafix.schemas.V2509;
import net.minecraft.util.datafix.schemas.V2511_1;
import net.minecraft.util.datafix.schemas.V2519;
import net.minecraft.util.datafix.schemas.V2522;
import net.minecraft.util.datafix.schemas.V2551;
import net.minecraft.util.datafix.schemas.V2568;
import net.minecraft.util.datafix.schemas.V2571;
import net.minecraft.util.datafix.schemas.V2684;
import net.minecraft.util.datafix.schemas.V2686;
import net.minecraft.util.datafix.schemas.V2688;
import net.minecraft.util.datafix.schemas.V2704;
import net.minecraft.util.datafix.schemas.V2707;
import net.minecraft.util.datafix.schemas.V2831;
import net.minecraft.util.datafix.schemas.V2832;
import net.minecraft.util.datafix.schemas.V2842;
import net.minecraft.util.datafix.schemas.V3076;
import net.minecraft.util.datafix.schemas.V3078;
import net.minecraft.util.datafix.schemas.V3081;
import net.minecraft.util.datafix.schemas.V3082;
import net.minecraft.util.datafix.schemas.V3083;
import net.minecraft.util.datafix.schemas.V3202;
import net.minecraft.util.datafix.schemas.V3203;
import net.minecraft.util.datafix.schemas.V3204;
import net.minecraft.util.datafix.schemas.V3325;
import net.minecraft.util.datafix.schemas.V3326;
import net.minecraft.util.datafix.schemas.V3327;
import net.minecraft.util.datafix.schemas.V3328;
import net.minecraft.util.datafix.schemas.V3438;
import net.minecraft.util.datafix.schemas.V3439;
import net.minecraft.util.datafix.schemas.V3439_1;
import net.minecraft.util.datafix.schemas.V3448;
import net.minecraft.util.datafix.schemas.V3682;
import net.minecraft.util.datafix.schemas.V3683;
import net.minecraft.util.datafix.schemas.V3685;
import net.minecraft.util.datafix.schemas.V3689;
import net.minecraft.util.datafix.schemas.V3799;
import net.minecraft.util.datafix.schemas.V3807;
import net.minecraft.util.datafix.schemas.V3808;
import net.minecraft.util.datafix.schemas.V3808_1;
import net.minecraft.util.datafix.schemas.V3808_2;
import net.minecraft.util.datafix.schemas.V3813;
import net.minecraft.util.datafix.schemas.V3816;
import net.minecraft.util.datafix.schemas.V3818;
import net.minecraft.util.datafix.schemas.V3818_3;
import net.minecraft.util.datafix.schemas.V3818_4;
import net.minecraft.util.datafix.schemas.V3818_5;
import net.minecraft.util.datafix.schemas.V3825;
import net.minecraft.util.datafix.schemas.V3938;
import net.minecraft.util.datafix.schemas.V4059;
import net.minecraft.util.datafix.schemas.V4067;
import net.minecraft.util.datafix.schemas.V4070;
import net.minecraft.util.datafix.schemas.V4071;
import net.minecraft.util.datafix.schemas.V4290;
import net.minecraft.util.datafix.schemas.V4292;
import net.minecraft.util.datafix.schemas.V4300;
import net.minecraft.util.datafix.schemas.V4301;
import net.minecraft.util.datafix.schemas.V4302;
import net.minecraft.util.datafix.schemas.V4306;
import net.minecraft.util.datafix.schemas.V4307;
import net.minecraft.util.datafix.schemas.V4312;
import net.minecraft.util.datafix.schemas.V4420;
import net.minecraft.util.datafix.schemas.V4421;
import net.minecraft.util.datafix.schemas.V4531;
import net.minecraft.util.datafix.schemas.V4532;
import net.minecraft.util.datafix.schemas.V4533;
import net.minecraft.util.datafix.schemas.V4543;
import net.minecraft.util.datafix.schemas.V4648;
import net.minecraft.util.datafix.schemas.V4656;
import net.minecraft.util.datafix.schemas.V501;
import net.minecraft.util.datafix.schemas.V700;
import net.minecraft.util.datafix.schemas.V701;
import net.minecraft.util.datafix.schemas.V702;
import net.minecraft.util.datafix.schemas.V703;
import net.minecraft.util.datafix.schemas.V704;
import net.minecraft.util.datafix.schemas.V705;
import net.minecraft.util.datafix.schemas.V808;
import net.minecraft.util.datafix.schemas.V99;

public class DataFixers {

    private static final BiFunction<Integer, Schema, Schema> SAME = Schema::new;
    private static final BiFunction<Integer, Schema, Schema> SAME_NAMESPACED = NamespacedSchema::new;
    private static final Result DATA_FIXER = createFixerUpper();
    public static final int BLENDING_VERSION = 4295;

    private DataFixers() {}

    public static DataFixer getDataFixer() {
        return DataFixers.DATA_FIXER.fixer();
    }

    private static Result createFixerUpper() {
        DataFixerBuilder datafixerbuilder = new DataFixerBuilder(SharedConstants.getCurrentVersion().dataVersion().version());

        addFixers(datafixerbuilder);
        return datafixerbuilder.build();
    }

    public static CompletableFuture<?> optimize(Set<TypeReference> typesToOptimize) {
        if (typesToOptimize.isEmpty()) {
            return CompletableFuture.completedFuture((Object) null);
        } else {
            Executor executor = Executors.newSingleThreadExecutor((new ThreadFactoryBuilder()).setNameFormat("Datafixer Bootstrap").setDaemon(true).setPriority(1).build());

            return DataFixers.DATA_FIXER.optimize(typesToOptimize, executor);
        }
    }

    private static void addFixers(DataFixerBuilder fixerUpper) {
        fixerUpper.addSchema(99, V99::new);
        Schema schema = fixerUpper.addSchema(100, V100::new);

        fixerUpper.addFixer(new EntityEquipmentToArmorAndHandFix(schema));
        Schema schema1 = fixerUpper.addSchema(101, DataFixers.SAME);

        fixerUpper.addFixer(new VillagerSetCanPickUpLootFix(schema1));
        Schema schema2 = fixerUpper.addSchema(102, V102::new);

        fixerUpper.addFixer(new ItemIdFix(schema2, true));
        fixerUpper.addFixer(new ItemPotionFix(schema2, false));
        Schema schema3 = fixerUpper.addSchema(105, DataFixers.SAME);

        fixerUpper.addFixer(new ItemSpawnEggFix(schema3, true));
        Schema schema4 = fixerUpper.addSchema(106, V106::new);

        fixerUpper.addFixer(new MobSpawnerEntityIdentifiersFix(schema4, true));
        Schema schema5 = fixerUpper.addSchema(107, V107::new);

        fixerUpper.addFixer(new EntityMinecartIdentifiersFix(schema5));
        Schema schema6 = fixerUpper.addSchema(108, DataFixers.SAME);

        fixerUpper.addFixer(new EntityStringUuidFix(schema6, true));
        Schema schema7 = fixerUpper.addSchema(109, DataFixers.SAME);

        fixerUpper.addFixer(new EntityHealthFix(schema7, true));
        Schema schema8 = fixerUpper.addSchema(110, DataFixers.SAME);

        fixerUpper.addFixer(new EntityHorseSaddleFix(schema8, true));
        Schema schema9 = fixerUpper.addSchema(111, DataFixers.SAME);

        fixerUpper.addFixer(new EntityPaintingItemFrameDirectionFix(schema9, true));
        Schema schema10 = fixerUpper.addSchema(113, DataFixers.SAME);

        fixerUpper.addFixer(new EntityRedundantChanceTagsFix(schema10, true));
        Schema schema11 = fixerUpper.addSchema(135, V135::new);

        fixerUpper.addFixer(new EntityRidingToPassengersFix(schema11, true));
        Schema schema12 = fixerUpper.addSchema(143, V143::new);

        fixerUpper.addFixer(new EntityTippedArrowFix(schema12, true));
        Schema schema13 = fixerUpper.addSchema(147, DataFixers.SAME);

        fixerUpper.addFixer(new EntityArmorStandSilentFix(schema13, true));
        Schema schema14 = fixerUpper.addSchema(165, DataFixers.SAME);

        fixerUpper.addFixer(new SignTextStrictJsonFix(schema14));
        fixerUpper.addFixer(new WrittenBookPagesStrictJsonFix(schema14));
        Schema schema15 = fixerUpper.addSchema(501, V501::new);

        fixerUpper.addFixer(new AddNewChoices(schema15, "Add 1.10 entities fix", References.ENTITY));
        Schema schema16 = fixerUpper.addSchema(502, DataFixers.SAME);

        fixerUpper.addFixer(ItemRenameFix.create(schema16, "cooked_fished item renamer", (s) -> {
            return Objects.equals(NamespacedSchema.ensureNamespaced(s), "minecraft:cooked_fished") ? "minecraft:cooked_fish" : s;
        }));
        fixerUpper.addFixer(new EntityZombieVillagerTypeFix(schema16, false));
        Schema schema17 = fixerUpper.addSchema(505, DataFixers.SAME);

        fixerUpper.addFixer(new OptionsForceVBOFix(schema17, false));
        Schema schema18 = fixerUpper.addSchema(700, V700::new);

        fixerUpper.addFixer(new EntityElderGuardianSplitFix(schema18, true));
        Schema schema19 = fixerUpper.addSchema(701, V701::new);

        fixerUpper.addFixer(new EntitySkeletonSplitFix(schema19, true));
        Schema schema20 = fixerUpper.addSchema(702, V702::new);

        fixerUpper.addFixer(new EntityZombieSplitFix(schema20));
        Schema schema21 = fixerUpper.addSchema(703, V703::new);

        fixerUpper.addFixer(new EntityHorseSplitFix(schema21, true));
        Schema schema22 = fixerUpper.addSchema(704, V704::new);

        fixerUpper.addFixer(new BlockEntityIdFix(schema22, true));
        Schema schema23 = fixerUpper.addSchema(705, V705::new);

        fixerUpper.addFixer(new EntityIdFix(schema23, true));
        Schema schema24 = fixerUpper.addSchema(804, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ItemBannerColorFix(schema24, true));
        Schema schema25 = fixerUpper.addSchema(806, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ItemWaterPotionFix(schema25, false));
        Schema schema26 = fixerUpper.addSchema(808, V808::new);

        fixerUpper.addFixer(new AddNewChoices(schema26, "added shulker box", References.BLOCK_ENTITY));
        Schema schema27 = fixerUpper.addSchema(808, 1, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntityShulkerColorFix(schema27, false));
        Schema schema28 = fixerUpper.addSchema(813, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ItemShulkerBoxColorFix(schema28, false));
        fixerUpper.addFixer(new BlockEntityShulkerBoxColorFix(schema28, false));
        Schema schema29 = fixerUpper.addSchema(816, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsLowerCaseLanguageFix(schema29, false));
        Schema schema30 = fixerUpper.addSchema(820, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(ItemRenameFix.create(schema30, "totem item renamer", createRenamer("minecraft:totem", "minecraft:totem_of_undying")));
        Schema schema31 = fixerUpper.addSchema(1022, V1022::new);

        fixerUpper.addFixer(new WriteAndReadFix(schema31, "added shoulder entities to players", References.PLAYER));
        Schema schema32 = fixerUpper.addSchema(1125, V1125::new);

        fixerUpper.addFixer(new ChunkBedBlockEntityInjecterFix(schema32, true));
        fixerUpper.addFixer(new BedItemColorFix(schema32, false));
        Schema schema33 = fixerUpper.addSchema(1344, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsKeyLwjgl3Fix(schema33, false));
        Schema schema34 = fixerUpper.addSchema(1446, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsKeyTranslationFix(schema34, false));
        Schema schema35 = fixerUpper.addSchema(1450, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new BlockStateStructureTemplateFix(schema35, false));
        Schema schema36 = fixerUpper.addSchema(1451, V1451::new);

        fixerUpper.addFixer(new AddNewChoices(schema36, "AddTrappedChestFix", References.BLOCK_ENTITY));
        Schema schema37 = fixerUpper.addSchema(1451, 1, V1451_1::new);

        fixerUpper.addFixer(new ChunkPalettedStorageFix(schema37, true));
        Schema schema38 = fixerUpper.addSchema(1451, 2, V1451_2::new);

        fixerUpper.addFixer(new BlockEntityBlockStateFix(schema38, true));
        Schema schema39 = fixerUpper.addSchema(1451, 3, V1451_3::new);

        fixerUpper.addFixer(new EntityBlockStateFix(schema39, true));
        fixerUpper.addFixer(new ItemStackMapIdFix(schema39, false));
        Schema schema40 = fixerUpper.addSchema(1451, 4, V1451_4::new);

        fixerUpper.addFixer(new BlockNameFlatteningFix(schema40, true));
        fixerUpper.addFixer(new ItemStackTheFlatteningFix(schema40, false));
        Schema schema41 = fixerUpper.addSchema(1451, 5, V1451_5::new);

        fixerUpper.addFixer(new RemoveBlockEntityTagFix(schema41, Set.of("minecraft:noteblock", "minecraft:flower_pot")));
        fixerUpper.addFixer(new ItemStackSpawnEggFix(schema41, false, "minecraft:spawn_egg"));
        fixerUpper.addFixer(new EntityWolfColorFix(schema41, false));
        fixerUpper.addFixer(new BlockEntityBannerColorFix(schema41, false));
        fixerUpper.addFixer(new LevelFlatGeneratorInfoFix(schema41, false));
        Schema schema42 = fixerUpper.addSchema(1451, 6, V1451_6::new);

        fixerUpper.addFixer(new StatsCounterFix(schema42, true));
        fixerUpper.addFixer(new BlockEntityJukeboxFix(schema42, false));
        Schema schema43 = fixerUpper.addSchema(1451, 7, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new VillagerTradeFix(schema43));
        Schema schema44 = fixerUpper.addSchema(1456, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntityItemFrameDirectionFix(schema44, false));
        Schema schema45 = fixerUpper.addSchema(1458, V1458::new);

        fixerUpper.addFixer(new EntityCustomNameToComponentFix(schema45));
        fixerUpper.addFixer(new ItemCustomNameToComponentFix(schema45));
        fixerUpper.addFixer(new BlockEntityCustomNameToComponentFix(schema45));
        Schema schema46 = fixerUpper.addSchema(1460, V1460::new);

        fixerUpper.addFixer(new EntityPaintingMotiveFix(schema46, false));
        Schema schema47 = fixerUpper.addSchema(1466, V1466::new);

        fixerUpper.addFixer(new AddNewChoices(schema47, "Add DUMMY block entity", References.BLOCK_ENTITY));
        fixerUpper.addFixer(new ChunkToProtochunkFix(schema47, true));
        Schema schema48 = fixerUpper.addSchema(1470, V1470::new);

        fixerUpper.addFixer(new AddNewChoices(schema48, "Add 1.13 entities fix", References.ENTITY));
        Schema schema49 = fixerUpper.addSchema(1474, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ColorlessShulkerEntityFix(schema49, false));
        fixerUpper.addFixer(BlockRenameFix.create(schema49, "Colorless shulker block fixer", (s) -> {
            return Objects.equals(NamespacedSchema.ensureNamespaced(s), "minecraft:purple_shulker_box") ? "minecraft:shulker_box" : s;
        }));
        fixerUpper.addFixer(ItemRenameFix.create(schema49, "Colorless shulker item fixer", (s) -> {
            return Objects.equals(NamespacedSchema.ensureNamespaced(s), "minecraft:purple_shulker_box") ? "minecraft:shulker_box" : s;
        }));
        Schema schema50 = fixerUpper.addSchema(1475, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(BlockRenameFix.create(schema50, "Flowing fixer", createRenamer(ImmutableMap.of("minecraft:flowing_water", "minecraft:water", "minecraft:flowing_lava", "minecraft:lava"))));
        Schema schema51 = fixerUpper.addSchema(1480, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(BlockRenameFix.create(schema51, "Rename coral blocks", createRenamer(RenamedCoralFix.RENAMED_IDS)));
        fixerUpper.addFixer(ItemRenameFix.create(schema51, "Rename coral items", createRenamer(RenamedCoralFix.RENAMED_IDS)));
        Schema schema52 = fixerUpper.addSchema(1481, V1481::new);

        fixerUpper.addFixer(new AddNewChoices(schema52, "Add conduit", References.BLOCK_ENTITY));
        Schema schema53 = fixerUpper.addSchema(1483, V1483::new);

        fixerUpper.addFixer(new EntityPufferfishRenameFix(schema53, true));
        fixerUpper.addFixer(ItemRenameFix.create(schema53, "Rename pufferfish egg item", createRenamer(EntityPufferfishRenameFix.RENAMED_IDS)));
        Schema schema54 = fixerUpper.addSchema(1484, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(ItemRenameFix.create(schema54, "Rename seagrass items", createRenamer(ImmutableMap.of("minecraft:sea_grass", "minecraft:seagrass", "minecraft:tall_sea_grass", "minecraft:tall_seagrass"))));
        fixerUpper.addFixer(BlockRenameFix.create(schema54, "Rename seagrass blocks", createRenamer(ImmutableMap.of("minecraft:sea_grass", "minecraft:seagrass", "minecraft:tall_sea_grass", "minecraft:tall_seagrass"))));
        fixerUpper.addFixer(new HeightmapRenamingFix(schema54, false));
        Schema schema55 = fixerUpper.addSchema(1486, V1486::new);

        fixerUpper.addFixer(new EntityCodSalmonFix(schema55, true));
        fixerUpper.addFixer(ItemRenameFix.create(schema55, "Rename cod/salmon egg items", createRenamer(EntityCodSalmonFix.RENAMED_EGG_IDS)));
        Schema schema56 = fixerUpper.addSchema(1487, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(ItemRenameFix.create(schema56, "Rename prismarine_brick(s)_* blocks", createRenamer(ImmutableMap.of("minecraft:prismarine_bricks_slab", "minecraft:prismarine_brick_slab", "minecraft:prismarine_bricks_stairs", "minecraft:prismarine_brick_stairs"))));
        fixerUpper.addFixer(BlockRenameFix.create(schema56, "Rename prismarine_brick(s)_* items", createRenamer(ImmutableMap.of("minecraft:prismarine_bricks_slab", "minecraft:prismarine_brick_slab", "minecraft:prismarine_bricks_stairs", "minecraft:prismarine_brick_stairs"))));
        Schema schema57 = fixerUpper.addSchema(1488, V1488::new);

        fixerUpper.addFixer(BlockRenameFix.create(schema57, "Rename kelp/kelptop", createRenamer(ImmutableMap.of("minecraft:kelp_top", "minecraft:kelp", "minecraft:kelp", "minecraft:kelp_plant"))));
        fixerUpper.addFixer(ItemRenameFix.create(schema57, "Rename kelptop", createRenamer("minecraft:kelp_top", "minecraft:kelp")));
        fixerUpper.addFixer(new NamedEntityWriteReadFix(schema57, true, "Command block block entity custom name fix", References.BLOCK_ENTITY, "minecraft:command_block") {
            @Override
            protected <T> Dynamic<T> fix(Dynamic<T> input) {
                return BlockEntityCustomNameToComponentFix.<T>fixTagCustomName(input);
            }
        });
        fixerUpper.addFixer(new DataFix(schema57, false) {
            protected TypeRewriteRule makeRule() {
                Type<?> type = this.getInputSchema().getType(References.ENTITY);
                OpticFinder<String> opticfinder = DSL.fieldFinder("id", NamespacedSchema.namespacedString());
                OpticFinder<?> opticfinder1 = type.findField("CustomName");
                OpticFinder<Pair<String, String>> opticfinder2 = DSL.typeFinder(this.getInputSchema().getType(References.TEXT_COMPONENT));

                return this.fixTypeEverywhereTyped("Command block minecart custom name fix", type, (typed) -> {
                    String s = (String) typed.getOptional(opticfinder).orElse("");

                    return !"minecraft:commandblock_minecart".equals(s) ? typed : typed.updateTyped(opticfinder1, (typed1) -> {
                        return typed1.update(opticfinder2, (pair) -> {
                            return pair.mapSecond(LegacyComponentDataFixUtils::createTextComponentJson);
                        });
                    });
                });
            }
        });
        fixerUpper.addFixer(new IglooMetadataRemovalFix(schema57, false));
        Schema schema58 = fixerUpper.addSchema(1490, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(BlockRenameFix.create(schema58, "Rename melon_block", createRenamer("minecraft:melon_block", "minecraft:melon")));
        fixerUpper.addFixer(ItemRenameFix.create(schema58, "Rename melon_block/melon/speckled_melon", createRenamer(ImmutableMap.of("minecraft:melon_block", "minecraft:melon", "minecraft:melon", "minecraft:melon_slice", "minecraft:speckled_melon", "minecraft:glistering_melon_slice"))));
        Schema schema59 = fixerUpper.addSchema(1492, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ChunkStructuresTemplateRenameFix(schema59, false));
        Schema schema60 = fixerUpper.addSchema(1494, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ItemStackEnchantmentNamesFix(schema60, false));
        Schema schema61 = fixerUpper.addSchema(1496, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new LeavesFix(schema61, false));
        Schema schema62 = fixerUpper.addSchema(1500, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new BlockEntityKeepPacked(schema62, false));
        Schema schema63 = fixerUpper.addSchema(1501, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AdvancementsFix(schema63, false));
        Schema schema64 = fixerUpper.addSchema(1502, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new NamespacedTypeRenameFix(schema64, "Recipes fix", References.RECIPE, createRenamer(RecipesFix.RECIPES)));
        Schema schema65 = fixerUpper.addSchema(1506, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new LevelDataGeneratorOptionsFix(schema65, false));
        Schema schema66 = fixerUpper.addSchema(1510, V1510::new);

        fixerUpper.addFixer(BlockRenameFix.create(schema66, "Block renamening fix", createRenamer(EntityTheRenameningFix.RENAMED_BLOCKS)));
        fixerUpper.addFixer(ItemRenameFix.create(schema66, "Item renamening fix", createRenamer(EntityTheRenameningFix.RENAMED_ITEMS)));
        fixerUpper.addFixer(new NamespacedTypeRenameFix(schema66, "Recipes renamening fix", References.RECIPE, createRenamer(RecipesRenameningFix.RECIPES)));
        fixerUpper.addFixer(new EntityTheRenameningFix(schema66, true));
        fixerUpper.addFixer(new StatsRenameFix(schema66, "SwimStatsRenameFix", ImmutableMap.of("minecraft:swim_one_cm", "minecraft:walk_on_water_one_cm", "minecraft:dive_one_cm", "minecraft:walk_under_water_one_cm")));
        Schema schema67 = fixerUpper.addSchema(1514, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ScoreboardDisplayNameFix(schema67, "ObjectiveDisplayNameFix", References.OBJECTIVE));
        fixerUpper.addFixer(new ScoreboardDisplayNameFix(schema67, "TeamDisplayNameFix", References.TEAM));
        fixerUpper.addFixer(new ObjectiveRenderTypeFix(schema67));
        Schema schema68 = fixerUpper.addSchema(1515, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(BlockRenameFix.create(schema68, "Rename coral fan blocks", createRenamer(RenamedCoralFansFix.RENAMED_IDS)));
        Schema schema69 = fixerUpper.addSchema(1624, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new TrappedChestBlockEntityFix(schema69, false));
        Schema schema70 = fixerUpper.addSchema(1800, V1800::new);

        fixerUpper.addFixer(new AddNewChoices(schema70, "Added 1.14 mobs fix", References.ENTITY));
        fixerUpper.addFixer(ItemRenameFix.create(schema70, "Rename dye items", createRenamer(DyeItemRenameFix.RENAMED_IDS)));
        Schema schema71 = fixerUpper.addSchema(1801, V1801::new);

        fixerUpper.addFixer(new AddNewChoices(schema71, "Added Illager Beast", References.ENTITY));
        Schema schema72 = fixerUpper.addSchema(1802, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(BlockRenameFix.create(schema72, "Rename sign blocks & stone slabs", createRenamer(ImmutableMap.of("minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:sign", "minecraft:oak_sign", "minecraft:wall_sign", "minecraft:oak_wall_sign"))));
        fixerUpper.addFixer(ItemRenameFix.create(schema72, "Rename sign item & stone slabs", createRenamer(ImmutableMap.of("minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:sign", "minecraft:oak_sign"))));
        Schema schema73 = fixerUpper.addSchema(1803, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ItemLoreFix(schema73));
        Schema schema74 = fixerUpper.addSchema(1904, V1904::new);

        fixerUpper.addFixer(new AddNewChoices(schema74, "Added Cats", References.ENTITY));
        fixerUpper.addFixer(new EntityCatSplitFix(schema74, false));
        Schema schema75 = fixerUpper.addSchema(1905, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ChunkStatusFix(schema75, false));
        Schema schema76 = fixerUpper.addSchema(1906, V1906::new);

        fixerUpper.addFixer(new AddNewChoices(schema76, "Add POI Blocks", References.BLOCK_ENTITY));
        Schema schema77 = fixerUpper.addSchema(1909, V1909::new);

        fixerUpper.addFixer(new AddNewChoices(schema77, "Add jigsaw", References.BLOCK_ENTITY));
        Schema schema78 = fixerUpper.addSchema(1911, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ChunkStatusFix2(schema78, false));
        Schema schema79 = fixerUpper.addSchema(1914, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new WeaponSmithChestLootTableFix(schema79, false));
        Schema schema80 = fixerUpper.addSchema(1917, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new CatTypeFix(schema80, false));
        Schema schema81 = fixerUpper.addSchema(1918, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new VillagerDataFix(schema81, "minecraft:villager"));
        fixerUpper.addFixer(new VillagerDataFix(schema81, "minecraft:zombie_villager"));
        Schema schema82 = fixerUpper.addSchema(1920, V1920::new);

        fixerUpper.addFixer(new NewVillageFix(schema82, false));
        fixerUpper.addFixer(new AddNewChoices(schema82, "Add campfire", References.BLOCK_ENTITY));
        Schema schema83 = fixerUpper.addSchema(1925, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new MapIdFix(schema83));
        Schema schema84 = fixerUpper.addSchema(1928, V1928::new);

        fixerUpper.addFixer(new EntityRavagerRenameFix(schema84, true));
        fixerUpper.addFixer(ItemRenameFix.create(schema84, "Rename ravager egg item", createRenamer(EntityRavagerRenameFix.RENAMED_IDS)));
        Schema schema85 = fixerUpper.addSchema(1929, V1929::new);

        fixerUpper.addFixer(new AddNewChoices(schema85, "Add Wandering Trader and Trader Llama", References.ENTITY));
        Schema schema86 = fixerUpper.addSchema(1931, V1931::new);

        fixerUpper.addFixer(new AddNewChoices(schema86, "Added Fox", References.ENTITY));
        Schema schema87 = fixerUpper.addSchema(1936, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsAddTextBackgroundFix(schema87, false));
        Schema schema88 = fixerUpper.addSchema(1946, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ReorganizePoi(schema88, false));
        Schema schema89 = fixerUpper.addSchema(1948, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OminousBannerRenameFix(schema89));
        Schema schema90 = fixerUpper.addSchema(1953, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OminousBannerBlockEntityRenameFix(schema90, false));
        Schema schema91 = fixerUpper.addSchema(1955, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new VillagerRebuildLevelAndXpFix(schema91, false));
        fixerUpper.addFixer(new ZombieVillagerRebuildXpFix(schema91, false));
        Schema schema92 = fixerUpper.addSchema(1961, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ChunkLightRemoveFix(schema92, false));
        Schema schema93 = fixerUpper.addSchema(1963, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new RemoveGolemGossipFix(schema93, false));
        Schema schema94 = fixerUpper.addSchema(2100, V2100::new);

        fixerUpper.addFixer(new AddNewChoices(schema94, "Added Bee and Bee Stinger", References.ENTITY));
        fixerUpper.addFixer(new AddNewChoices(schema94, "Add beehive", References.BLOCK_ENTITY));
        fixerUpper.addFixer(new NamespacedTypeRenameFix(schema94, "Rename sugar recipe", References.RECIPE, createRenamer("minecraft:sugar", "minecraft:sugar_from_sugar_cane")));
        fixerUpper.addFixer(new AdvancementsRenameFix(schema94, false, "Rename sugar recipe advancement", createRenamer("minecraft:recipes/misc/sugar", "minecraft:recipes/misc/sugar_from_sugar_cane")));
        Schema schema95 = fixerUpper.addSchema(2202, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ChunkBiomeFix(schema95, false));
        Schema schema96 = fixerUpper.addSchema(2209, DataFixers.SAME_NAMESPACED);
        UnaryOperator<String> unaryoperator = createRenamer("minecraft:bee_hive", "minecraft:beehive");

        fixerUpper.addFixer(ItemRenameFix.create(schema96, "Rename bee_hive item to beehive", unaryoperator));
        fixerUpper.addFixer(new PoiTypeRenameFix(schema96, "Rename bee_hive poi to beehive", unaryoperator));
        fixerUpper.addFixer(BlockRenameFix.create(schema96, "Rename bee_hive block to beehive", unaryoperator));
        Schema schema97 = fixerUpper.addSchema(2211, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new StructureReferenceCountFix(schema97, false));
        Schema schema98 = fixerUpper.addSchema(2218, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ForcePoiRebuild(schema98, false));
        Schema schema99 = fixerUpper.addSchema(2501, V2501::new);

        fixerUpper.addFixer(new FurnaceRecipeFix(schema99, true));
        Schema schema100 = fixerUpper.addSchema(2502, V2502::new);

        fixerUpper.addFixer(new AddNewChoices(schema100, "Added Hoglin", References.ENTITY));
        Schema schema101 = fixerUpper.addSchema(2503, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new WallPropertyFix(schema101, false));
        fixerUpper.addFixer(new AdvancementsRenameFix(schema101, false, "Composter category change", createRenamer("minecraft:recipes/misc/composter", "minecraft:recipes/decorations/composter")));
        Schema schema102 = fixerUpper.addSchema(2505, V2505::new);

        fixerUpper.addFixer(new AddNewChoices(schema102, "Added Piglin", References.ENTITY));
        fixerUpper.addFixer(new MemoryExpiryDataFix(schema102, "minecraft:villager"));
        Schema schema103 = fixerUpper.addSchema(2508, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(ItemRenameFix.create(schema103, "Renamed fungi items to fungus", createRenamer(ImmutableMap.of("minecraft:warped_fungi", "minecraft:warped_fungus", "minecraft:crimson_fungi", "minecraft:crimson_fungus"))));
        fixerUpper.addFixer(BlockRenameFix.create(schema103, "Renamed fungi blocks to fungus", createRenamer(ImmutableMap.of("minecraft:warped_fungi", "minecraft:warped_fungus", "minecraft:crimson_fungi", "minecraft:crimson_fungus"))));
        Schema schema104 = fixerUpper.addSchema(2509, V2509::new);

        fixerUpper.addFixer(new EntityZombifiedPiglinRenameFix(schema104));
        fixerUpper.addFixer(ItemRenameFix.create(schema104, "Rename zombie pigman egg item", createRenamer(EntityZombifiedPiglinRenameFix.RENAMED_IDS)));
        Schema schema105 = fixerUpper.addSchema(2511, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntityProjectileOwnerFix(schema105));
        Schema schema106 = fixerUpper.addSchema(2511, 1, V2511_1::new);

        fixerUpper.addFixer(new NamedEntityConvertUncheckedFix(schema106, "SplashPotionItemFieldRenameFix", References.ENTITY, "minecraft:potion"));
        Schema schema107 = fixerUpper.addSchema(2514, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntityUUIDFix(schema107));
        fixerUpper.addFixer(new BlockEntityUUIDFix(schema107));
        fixerUpper.addFixer(new PlayerUUIDFix(schema107));
        fixerUpper.addFixer(new LevelUUIDFix(schema107));
        fixerUpper.addFixer(new SavedDataUUIDFix(schema107));
        fixerUpper.addFixer(new ItemStackUUIDFix(schema107));
        Schema schema108 = fixerUpper.addSchema(2516, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new GossipUUIDFix(schema108, "minecraft:villager"));
        fixerUpper.addFixer(new GossipUUIDFix(schema108, "minecraft:zombie_villager"));
        Schema schema109 = fixerUpper.addSchema(2518, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new JigsawPropertiesFix(schema109, false));
        fixerUpper.addFixer(new JigsawRotationFix(schema109));
        Schema schema110 = fixerUpper.addSchema(2519, V2519::new);

        fixerUpper.addFixer(new AddNewChoices(schema110, "Added Strider", References.ENTITY));
        Schema schema111 = fixerUpper.addSchema(2522, V2522::new);

        fixerUpper.addFixer(new AddNewChoices(schema111, "Added Zoglin", References.ENTITY));
        Schema schema112 = fixerUpper.addSchema(2523, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AttributesRenameLegacy(schema112, "Attribute renames", createRenamerNoNamespace(ImmutableMap.builder().put("generic.maxHealth", "minecraft:generic.max_health").put("Max Health", "minecraft:generic.max_health").put("zombie.spawnReinforcements", "minecraft:zombie.spawn_reinforcements").put("Spawn Reinforcements Chance", "minecraft:zombie.spawn_reinforcements").put("horse.jumpStrength", "minecraft:horse.jump_strength").put("Jump Strength", "minecraft:horse.jump_strength").put("generic.followRange", "minecraft:generic.follow_range").put("Follow Range", "minecraft:generic.follow_range").put("generic.knockbackResistance", "minecraft:generic.knockback_resistance").put("Knockback Resistance", "minecraft:generic.knockback_resistance").put("generic.movementSpeed", "minecraft:generic.movement_speed").put("Movement Speed", "minecraft:generic.movement_speed").put("generic.flyingSpeed", "minecraft:generic.flying_speed").put("Flying Speed", "minecraft:generic.flying_speed").put("generic.attackDamage", "minecraft:generic.attack_damage").put("generic.attackKnockback", "minecraft:generic.attack_knockback").put("generic.attackSpeed", "minecraft:generic.attack_speed").put("generic.armorToughness", "minecraft:generic.armor_toughness").build())));
        Schema schema113 = fixerUpper.addSchema(2527, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new BitStorageAlignFix(schema113));
        Schema schema114 = fixerUpper.addSchema(2528, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(ItemRenameFix.create(schema114, "Rename soul fire torch and soul fire lantern", createRenamer(ImmutableMap.of("minecraft:soul_fire_torch", "minecraft:soul_torch", "minecraft:soul_fire_lantern", "minecraft:soul_lantern"))));
        fixerUpper.addFixer(BlockRenameFix.create(schema114, "Rename soul fire torch and soul fire lantern", createRenamer(ImmutableMap.of("minecraft:soul_fire_torch", "minecraft:soul_torch", "minecraft:soul_fire_wall_torch", "minecraft:soul_wall_torch", "minecraft:soul_fire_lantern", "minecraft:soul_lantern"))));
        Schema schema115 = fixerUpper.addSchema(2529, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new StriderGravityFix(schema115, false));
        Schema schema116 = fixerUpper.addSchema(2531, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new RedstoneWireConnectionsFix(schema116));
        Schema schema117 = fixerUpper.addSchema(2533, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new VillagerFollowRangeFix(schema117));
        Schema schema118 = fixerUpper.addSchema(2535, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntityShulkerRotationFix(schema118));
        Schema schema119 = fixerUpper.addSchema(2537, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new LegacyDimensionIdFix(schema119));
        Schema schema120 = fixerUpper.addSchema(2538, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new LevelLegacyWorldGenSettingsFix(schema120));
        Schema schema121 = fixerUpper.addSchema(2550, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new WorldGenSettingsFix(schema121));
        Schema schema122 = fixerUpper.addSchema(2551, V2551::new);

        fixerUpper.addFixer(new WriteAndReadFix(schema122, "add types to WorldGenData", References.WORLD_GEN_SETTINGS));
        Schema schema123 = fixerUpper.addSchema(2552, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new NamespacedTypeRenameFix(schema123, "Nether biome rename", References.BIOME, createRenamer("minecraft:nether", "minecraft:nether_wastes")));
        Schema schema124 = fixerUpper.addSchema(2553, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new NamespacedTypeRenameFix(schema124, "Biomes fix", References.BIOME, createRenamer(BiomeFix.BIOMES)));
        Schema schema125 = fixerUpper.addSchema(2556, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsFancyGraphicsToGraphicsModeFix(schema125));
        Schema schema126 = fixerUpper.addSchema(2558, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new MissingDimensionFix(schema126, false));
        fixerUpper.addFixer(new OptionsRenameFieldFix(schema126, false, "Rename swapHands setting", "key_key.swapHands", "key_key.swapOffhand"));
        Schema schema127 = fixerUpper.addSchema(2568, V2568::new);

        fixerUpper.addFixer(new AddNewChoices(schema127, "Added Piglin Brute", References.ENTITY));
        Schema schema128 = fixerUpper.addSchema(2571, V2571::new);

        fixerUpper.addFixer(new AddNewChoices(schema128, "Added Goat", References.ENTITY));
        Schema schema129 = fixerUpper.addSchema(2679, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new CauldronRenameFix(schema129, false));
        Schema schema130 = fixerUpper.addSchema(2680, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(ItemRenameFix.create(schema130, "Renamed grass path item to dirt path", createRenamer("minecraft:grass_path", "minecraft:dirt_path")));
        fixerUpper.addFixer(BlockRenameFix.create(schema130, "Renamed grass path block to dirt path", createRenamer("minecraft:grass_path", "minecraft:dirt_path")));
        Schema schema131 = fixerUpper.addSchema(2684, V2684::new);

        fixerUpper.addFixer(new AddNewChoices(schema131, "Added Sculk Sensor", References.BLOCK_ENTITY));
        Schema schema132 = fixerUpper.addSchema(2686, V2686::new);

        fixerUpper.addFixer(new AddNewChoices(schema132, "Added Axolotl", References.ENTITY));
        Schema schema133 = fixerUpper.addSchema(2688, V2688::new);

        fixerUpper.addFixer(new AddNewChoices(schema133, "Added Glow Squid", References.ENTITY));
        fixerUpper.addFixer(new AddNewChoices(schema133, "Added Glow Item Frame", References.ENTITY));
        Schema schema134 = fixerUpper.addSchema(2690, DataFixers.SAME_NAMESPACED);
        ImmutableMap<String, String> immutablemap = ImmutableMap.builder().put("minecraft:weathered_copper_block", "minecraft:oxidized_copper_block").put("minecraft:semi_weathered_copper_block", "minecraft:weathered_copper_block").put("minecraft:lightly_weathered_copper_block", "minecraft:exposed_copper_block").put("minecraft:weathered_cut_copper", "minecraft:oxidized_cut_copper").put("minecraft:semi_weathered_cut_copper", "minecraft:weathered_cut_copper").put("minecraft:lightly_weathered_cut_copper", "minecraft:exposed_cut_copper").put("minecraft:weathered_cut_copper_stairs", "minecraft:oxidized_cut_copper_stairs").put("minecraft:semi_weathered_cut_copper_stairs", "minecraft:weathered_cut_copper_stairs").put("minecraft:lightly_weathered_cut_copper_stairs", "minecraft:exposed_cut_copper_stairs").put("minecraft:weathered_cut_copper_slab", "minecraft:oxidized_cut_copper_slab").put("minecraft:semi_weathered_cut_copper_slab", "minecraft:weathered_cut_copper_slab").put("minecraft:lightly_weathered_cut_copper_slab", "minecraft:exposed_cut_copper_slab").put("minecraft:waxed_semi_weathered_copper", "minecraft:waxed_weathered_copper").put("minecraft:waxed_lightly_weathered_copper", "minecraft:waxed_exposed_copper").put("minecraft:waxed_semi_weathered_cut_copper", "minecraft:waxed_weathered_cut_copper").put("minecraft:waxed_lightly_weathered_cut_copper", "minecraft:waxed_exposed_cut_copper").put("minecraft:waxed_semi_weathered_cut_copper_stairs", "minecraft:waxed_weathered_cut_copper_stairs").put("minecraft:waxed_lightly_weathered_cut_copper_stairs", "minecraft:waxed_exposed_cut_copper_stairs").put("minecraft:waxed_semi_weathered_cut_copper_slab", "minecraft:waxed_weathered_cut_copper_slab").put("minecraft:waxed_lightly_weathered_cut_copper_slab", "minecraft:waxed_exposed_cut_copper_slab").build();

        fixerUpper.addFixer(ItemRenameFix.create(schema134, "Renamed copper block items to new oxidized terms", createRenamer(immutablemap)));
        fixerUpper.addFixer(BlockRenameFix.create(schema134, "Renamed copper blocks to new oxidized terms", createRenamer(immutablemap)));
        Schema schema135 = fixerUpper.addSchema(2691, DataFixers.SAME_NAMESPACED);
        ImmutableMap<String, String> immutablemap1 = ImmutableMap.builder().put("minecraft:waxed_copper", "minecraft:waxed_copper_block").put("minecraft:oxidized_copper_block", "minecraft:oxidized_copper").put("minecraft:weathered_copper_block", "minecraft:weathered_copper").put("minecraft:exposed_copper_block", "minecraft:exposed_copper").build();

        fixerUpper.addFixer(ItemRenameFix.create(schema135, "Rename copper item suffixes", createRenamer(immutablemap1)));
        fixerUpper.addFixer(BlockRenameFix.create(schema135, "Rename copper blocks suffixes", createRenamer(immutablemap1)));
        Schema schema136 = fixerUpper.addSchema(2693, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AddFlagIfNotPresentFix(schema136, References.WORLD_GEN_SETTINGS, "has_increased_height_already", false));
        Schema schema137 = fixerUpper.addSchema(2696, DataFixers.SAME_NAMESPACED);
        ImmutableMap<String, String> immutablemap2 = ImmutableMap.builder().put("minecraft:grimstone", "minecraft:deepslate").put("minecraft:grimstone_slab", "minecraft:cobbled_deepslate_slab").put("minecraft:grimstone_stairs", "minecraft:cobbled_deepslate_stairs").put("minecraft:grimstone_wall", "minecraft:cobbled_deepslate_wall").put("minecraft:polished_grimstone", "minecraft:polished_deepslate").put("minecraft:polished_grimstone_slab", "minecraft:polished_deepslate_slab").put("minecraft:polished_grimstone_stairs", "minecraft:polished_deepslate_stairs").put("minecraft:polished_grimstone_wall", "minecraft:polished_deepslate_wall").put("minecraft:grimstone_tiles", "minecraft:deepslate_tiles").put("minecraft:grimstone_tile_slab", "minecraft:deepslate_tile_slab").put("minecraft:grimstone_tile_stairs", "minecraft:deepslate_tile_stairs").put("minecraft:grimstone_tile_wall", "minecraft:deepslate_tile_wall").put("minecraft:grimstone_bricks", "minecraft:deepslate_bricks").put("minecraft:grimstone_brick_slab", "minecraft:deepslate_brick_slab").put("minecraft:grimstone_brick_stairs", "minecraft:deepslate_brick_stairs").put("minecraft:grimstone_brick_wall", "minecraft:deepslate_brick_wall").put("minecraft:chiseled_grimstone", "minecraft:chiseled_deepslate").build();

        fixerUpper.addFixer(ItemRenameFix.create(schema137, "Renamed grimstone block items to deepslate", createRenamer(immutablemap2)));
        fixerUpper.addFixer(BlockRenameFix.create(schema137, "Renamed grimstone blocks to deepslate", createRenamer(immutablemap2)));
        Schema schema138 = fixerUpper.addSchema(2700, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(BlockRenameFix.create(schema138, "Renamed cave vines blocks", createRenamer(ImmutableMap.of("minecraft:cave_vines_head", "minecraft:cave_vines", "minecraft:cave_vines_body", "minecraft:cave_vines_plant"))));
        Schema schema139 = fixerUpper.addSchema(2701, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new SavedDataFeaturePoolElementFix(schema139));
        Schema schema140 = fixerUpper.addSchema(2702, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AbstractArrowPickupFix(schema140));
        Schema schema141 = fixerUpper.addSchema(2704, V2704::new);

        fixerUpper.addFixer(new AddNewChoices(schema141, "Added Goat", References.ENTITY));
        Schema schema142 = fixerUpper.addSchema(2707, V2707::new);

        fixerUpper.addFixer(new AddNewChoices(schema142, "Added Marker", References.ENTITY));
        fixerUpper.addFixer(new AddFlagIfNotPresentFix(schema142, References.WORLD_GEN_SETTINGS, "has_increased_height_already", true));
        Schema schema143 = fixerUpper.addSchema(2710, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new StatsRenameFix(schema143, "Renamed play_one_minute stat to play_time", ImmutableMap.of("minecraft:play_one_minute", "minecraft:play_time")));
        Schema schema144 = fixerUpper.addSchema(2717, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(ItemRenameFix.create(schema144, "Rename azalea_leaves_flowers", createRenamer(ImmutableMap.of("minecraft:azalea_leaves_flowers", "minecraft:flowering_azalea_leaves"))));
        fixerUpper.addFixer(BlockRenameFix.create(schema144, "Rename azalea_leaves_flowers items", createRenamer(ImmutableMap.of("minecraft:azalea_leaves_flowers", "minecraft:flowering_azalea_leaves"))));
        Schema schema145 = fixerUpper.addSchema(2825, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AddFlagIfNotPresentFix(schema145, References.WORLD_GEN_SETTINGS, "has_increased_height_already", false));
        Schema schema146 = fixerUpper.addSchema(2831, V2831::new);

        fixerUpper.addFixer(new SpawnerDataFix(schema146));
        Schema schema147 = fixerUpper.addSchema(2832, V2832::new);

        fixerUpper.addFixer(new WorldGenSettingsHeightAndBiomeFix(schema147));
        fixerUpper.addFixer(new ChunkHeightAndBiomeFix(schema147));
        Schema schema148 = fixerUpper.addSchema(2833, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new WorldGenSettingsDisallowOldCustomWorldsFix(schema148));
        Schema schema149 = fixerUpper.addSchema(2838, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new NamespacedTypeRenameFix(schema149, "Caves and Cliffs biome renames", References.BIOME, createRenamer(CavesAndCliffsRenames.RENAMES)));
        Schema schema150 = fixerUpper.addSchema(2841, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ChunkProtoTickListFix(schema150));
        Schema schema151 = fixerUpper.addSchema(2842, V2842::new);

        fixerUpper.addFixer(new ChunkRenamesFix(schema151));
        Schema schema152 = fixerUpper.addSchema(2843, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OverreachingTickFix(schema152));
        fixerUpper.addFixer(new NamespacedTypeRenameFix(schema152, "Remove Deep Warm Ocean", References.BIOME, createRenamer("minecraft:deep_warm_ocean", "minecraft:warm_ocean")));
        Schema schema153 = fixerUpper.addSchema(2846, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AdvancementsRenameFix(schema153, false, "Rename some C&C part 2 advancements", createRenamer(ImmutableMap.of("minecraft:husbandry/play_jukebox_in_meadows", "minecraft:adventure/play_jukebox_in_meadows", "minecraft:adventure/caves_and_cliff", "minecraft:adventure/fall_from_world_height", "minecraft:adventure/ride_strider_in_overworld_lava", "minecraft:nether/ride_strider_in_overworld_lava"))));
        Schema schema154 = fixerUpper.addSchema(2852, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new WorldGenSettingsDisallowOldCustomWorldsFix(schema154));
        Schema schema155 = fixerUpper.addSchema(2967, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new StructureSettingsFlattenFix(schema155));
        Schema schema156 = fixerUpper.addSchema(2970, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new StructuresBecomeConfiguredFix(schema156));
        Schema schema157 = fixerUpper.addSchema(3076, V3076::new);

        fixerUpper.addFixer(new AddNewChoices(schema157, "Added Sculk Catalyst", References.BLOCK_ENTITY));
        Schema schema158 = fixerUpper.addSchema(3077, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ChunkDeleteIgnoredLightDataFix(schema158));
        Schema schema159 = fixerUpper.addSchema(3078, V3078::new);

        fixerUpper.addFixer(new AddNewChoices(schema159, "Added Frog", References.ENTITY));
        fixerUpper.addFixer(new AddNewChoices(schema159, "Added Tadpole", References.ENTITY));
        fixerUpper.addFixer(new AddNewChoices(schema159, "Added Sculk Shrieker", References.BLOCK_ENTITY));
        Schema schema160 = fixerUpper.addSchema(3081, V3081::new);

        fixerUpper.addFixer(new AddNewChoices(schema160, "Added Warden", References.ENTITY));
        Schema schema161 = fixerUpper.addSchema(3082, V3082::new);

        fixerUpper.addFixer(new AddNewChoices(schema161, "Added Chest Boat", References.ENTITY));
        Schema schema162 = fixerUpper.addSchema(3083, V3083::new);

        fixerUpper.addFixer(new AddNewChoices(schema162, "Added Allay", References.ENTITY));
        Schema schema163 = fixerUpper.addSchema(3084, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new NamespacedTypeRenameFix(schema163, "game_event_renames_3084", References.GAME_EVENT_NAME, createRenamer(ImmutableMap.builder().put("minecraft:block_press", "minecraft:block_activate").put("minecraft:block_switch", "minecraft:block_activate").put("minecraft:block_unpress", "minecraft:block_deactivate").put("minecraft:block_unswitch", "minecraft:block_deactivate").put("minecraft:drinking_finish", "minecraft:drink").put("minecraft:elytra_free_fall", "minecraft:elytra_glide").put("minecraft:entity_damaged", "minecraft:entity_damage").put("minecraft:entity_dying", "minecraft:entity_die").put("minecraft:entity_killed", "minecraft:entity_die").put("minecraft:mob_interact", "minecraft:entity_interact").put("minecraft:ravager_roar", "minecraft:entity_roar").put("minecraft:ring_bell", "minecraft:block_change").put("minecraft:shulker_close", "minecraft:container_close").put("minecraft:shulker_open", "minecraft:container_open").put("minecraft:wolf_shaking", "minecraft:entity_shake").build())));
        Schema schema164 = fixerUpper.addSchema(3086, DataFixers.SAME_NAMESPACED);
        TypeReference typereference = References.ENTITY;
        Int2ObjectOpenHashMap int2objectopenhashmap = (Int2ObjectOpenHashMap) Util.make(new Int2ObjectOpenHashMap(), (int2objectopenhashmap1) -> {
            int2objectopenhashmap1.defaultReturnValue("minecraft:tabby");
            int2objectopenhashmap1.put(0, "minecraft:tabby");
            int2objectopenhashmap1.put(1, "minecraft:black");
            int2objectopenhashmap1.put(2, "minecraft:red");
            int2objectopenhashmap1.put(3, "minecraft:siamese");
            int2objectopenhashmap1.put(4, "minecraft:british");
            int2objectopenhashmap1.put(5, "minecraft:calico");
            int2objectopenhashmap1.put(6, "minecraft:persian");
            int2objectopenhashmap1.put(7, "minecraft:ragdoll");
            int2objectopenhashmap1.put(8, "minecraft:white");
            int2objectopenhashmap1.put(9, "minecraft:jellie");
            int2objectopenhashmap1.put(10, "minecraft:all_black");
        });

        Objects.requireNonNull(int2objectopenhashmap);
        fixerUpper.addFixer(new EntityVariantFix(schema164, "Change cat variant type", typereference, "minecraft:cat", "CatType", int2objectopenhashmap::get));
        ImmutableMap<String, String> immutablemap3 = ImmutableMap.builder().put("textures/entity/cat/tabby.png", "minecraft:tabby").put("textures/entity/cat/black.png", "minecraft:black").put("textures/entity/cat/red.png", "minecraft:red").put("textures/entity/cat/siamese.png", "minecraft:siamese").put("textures/entity/cat/british_shorthair.png", "minecraft:british").put("textures/entity/cat/calico.png", "minecraft:calico").put("textures/entity/cat/persian.png", "minecraft:persian").put("textures/entity/cat/ragdoll.png", "minecraft:ragdoll").put("textures/entity/cat/white.png", "minecraft:white").put("textures/entity/cat/jellie.png", "minecraft:jellie").put("textures/entity/cat/all_black.png", "minecraft:all_black").build();

        fixerUpper.addFixer(new CriteriaRenameFix(schema164, "Migrate cat variant advancement", "minecraft:husbandry/complete_catalogue", (s) -> {
            return (String) immutablemap3.getOrDefault(s, s);
        }));
        Schema schema165 = fixerUpper.addSchema(3087, DataFixers.SAME_NAMESPACED);

        typereference = References.ENTITY;
        int2objectopenhashmap = (Int2ObjectOpenHashMap) Util.make(new Int2ObjectOpenHashMap(), (int2objectopenhashmap1) -> {
            int2objectopenhashmap1.put(0, "minecraft:temperate");
            int2objectopenhashmap1.put(1, "minecraft:warm");
            int2objectopenhashmap1.put(2, "minecraft:cold");
        });
        Objects.requireNonNull(int2objectopenhashmap);
        fixerUpper.addFixer(new EntityVariantFix(schema165, "Change frog variant type", typereference, "minecraft:frog", "Variant", int2objectopenhashmap::get));
        Schema schema166 = fixerUpper.addSchema(3090, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntityFieldsRenameFix(schema166, "EntityPaintingFieldsRenameFix", "minecraft:painting", Map.of("Motive", "variant", "Facing", "facing")));
        Schema schema167 = fixerUpper.addSchema(3093, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntityGoatMissingStateFix(schema167));
        Schema schema168 = fixerUpper.addSchema(3094, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new GoatHornIdFix(schema168));
        Schema schema169 = fixerUpper.addSchema(3097, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new FilteredBooksFix(schema169));
        fixerUpper.addFixer(new FilteredSignsFix(schema169));
        Map<String, String> map = Map.of("minecraft:british", "minecraft:british_shorthair");

        fixerUpper.addFixer(new VariantRenameFix(schema169, "Rename british shorthair", References.ENTITY, "minecraft:cat", map));
        fixerUpper.addFixer(new CriteriaRenameFix(schema169, "Migrate cat variant advancement for british shorthair", "minecraft:husbandry/complete_catalogue", (s) -> {
            return (String) map.getOrDefault(s, s);
        }));
        Set set = Set.of("minecraft:unemployed", "minecraft:nitwit");

        Objects.requireNonNull(set);
        fixerUpper.addFixer(new PoiTypeRemoveFix(schema169, "Remove unpopulated villager PoI types", set::contains));
        Schema schema170 = fixerUpper.addSchema(3108, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new BlendingDataRemoveFromNetherEndFix(schema170));
        Schema schema171 = fixerUpper.addSchema(3201, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsProgrammerArtFix(schema171));
        Schema schema172 = fixerUpper.addSchema(3202, V3202::new);

        fixerUpper.addFixer(new AddNewChoices(schema172, "Added Hanging Sign", References.BLOCK_ENTITY));
        Schema schema173 = fixerUpper.addSchema(3203, V3203::new);

        fixerUpper.addFixer(new AddNewChoices(schema173, "Added Camel", References.ENTITY));
        Schema schema174 = fixerUpper.addSchema(3204, V3204::new);

        fixerUpper.addFixer(new AddNewChoices(schema174, "Added Chiseled Bookshelf", References.BLOCK_ENTITY));
        Schema schema175 = fixerUpper.addSchema(3209, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ItemStackSpawnEggFix(schema175, false, "minecraft:pig_spawn_egg"));
        Schema schema176 = fixerUpper.addSchema(3214, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsAmbientOcclusionFix(schema176));
        Schema schema177 = fixerUpper.addSchema(3319, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsAccessibilityOnboardFix(schema177));
        Schema schema178 = fixerUpper.addSchema(3322, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EffectDurationFix(schema178));
        Schema schema179 = fixerUpper.addSchema(3325, V3325::new);

        fixerUpper.addFixer(new AddNewChoices(schema179, "Added displays", References.ENTITY));
        Schema schema180 = fixerUpper.addSchema(3326, V3326::new);

        fixerUpper.addFixer(new AddNewChoices(schema180, "Added Sniffer", References.ENTITY));
        Schema schema181 = fixerUpper.addSchema(3327, V3327::new);

        fixerUpper.addFixer(new AddNewChoices(schema181, "Archaeology", References.BLOCK_ENTITY));
        Schema schema182 = fixerUpper.addSchema(3328, V3328::new);

        fixerUpper.addFixer(new AddNewChoices(schema182, "Added interaction", References.ENTITY));
        Schema schema183 = fixerUpper.addSchema(3438, V3438::new);

        fixerUpper.addFixer(BlockEntityRenameFix.create(schema183, "Rename Suspicious Sand to Brushable Block", createRenamer("minecraft:suspicious_sand", "minecraft:brushable_block")));
        fixerUpper.addFixer(new EntityBrushableBlockFieldsRenameFix(schema183));
        fixerUpper.addFixer(ItemRenameFix.create(schema183, "Pottery shard renaming", createRenamer(ImmutableMap.of("minecraft:pottery_shard_archer", "minecraft:archer_pottery_shard", "minecraft:pottery_shard_prize", "minecraft:prize_pottery_shard", "minecraft:pottery_shard_arms_up", "minecraft:arms_up_pottery_shard", "minecraft:pottery_shard_skull", "minecraft:skull_pottery_shard"))));
        fixerUpper.addFixer(new AddNewChoices(schema183, "Added calibrated sculk sensor", References.BLOCK_ENTITY));
        Schema schema184 = fixerUpper.addSchema(3439, V3439::new);

        fixerUpper.addFixer(new BlockEntitySignDoubleSidedEditableTextFix(schema184, "Updated sign text format for Signs", "minecraft:sign"));
        Schema schema185 = fixerUpper.addSchema(3439, 1, V3439_1::new);

        fixerUpper.addFixer(new BlockEntitySignDoubleSidedEditableTextFix(schema185, "Updated sign text format for Hanging Signs", "minecraft:hanging_sign"));
        Schema schema186 = fixerUpper.addSchema(3440, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new NamespacedTypeRenameFix(schema186, "Replace experimental 1.20 overworld", References.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, createRenamer("minecraft:overworld_update_1_20", "minecraft:overworld")));
        fixerUpper.addFixer(new FeatureFlagRemoveFix(schema186, "Remove 1.20 feature toggle", Set.of("minecraft:update_1_20")));
        Schema schema187 = fixerUpper.addSchema(3447, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(ItemRenameFix.create(schema187, "Pottery shard item renaming to Pottery sherd", createRenamer((Map) Stream.of("minecraft:angler_pottery_shard", "minecraft:archer_pottery_shard", "minecraft:arms_up_pottery_shard", "minecraft:blade_pottery_shard", "minecraft:brewer_pottery_shard", "minecraft:burn_pottery_shard", "minecraft:danger_pottery_shard", "minecraft:explorer_pottery_shard", "minecraft:friend_pottery_shard", "minecraft:heart_pottery_shard", "minecraft:heartbreak_pottery_shard", "minecraft:howl_pottery_shard", "minecraft:miner_pottery_shard", "minecraft:mourner_pottery_shard", "minecraft:plenty_pottery_shard", "minecraft:prize_pottery_shard", "minecraft:sheaf_pottery_shard", "minecraft:shelter_pottery_shard", "minecraft:skull_pottery_shard", "minecraft:snort_pottery_shard").collect(Collectors.toMap(Function.identity(), (s) -> {
            return s.replace("_pottery_shard", "_pottery_sherd");
        })))));
        Schema schema188 = fixerUpper.addSchema(3448, V3448::new);

        fixerUpper.addFixer(new DecoratedPotFieldRenameFix(schema188));
        Schema schema189 = fixerUpper.addSchema(3450, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new RemapChunkStatusFix(schema189, "Remove liquid_carvers and heightmap chunk statuses", createRenamer(Map.of("minecraft:liquid_carvers", "minecraft:carvers", "minecraft:heightmaps", "minecraft:spawn"))));
        Schema schema190 = fixerUpper.addSchema(3451, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ChunkDeleteLightFix(schema190));
        Schema schema191 = fixerUpper.addSchema(3459, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new LegacyDragonFightFix(schema191));
        Schema schema192 = fixerUpper.addSchema(3564, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new DropInvalidSignDataFix(schema192, "minecraft:sign"));
        Schema schema193 = fixerUpper.addSchema(3564, 1, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new DropInvalidSignDataFix(schema193, "minecraft:hanging_sign"));
        Schema schema194 = fixerUpper.addSchema(3565, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new RandomSequenceSettingsFix(schema194));
        Schema schema195 = fixerUpper.addSchema(3566, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ScoreboardDisplaySlotFix(schema195));
        Schema schema196 = fixerUpper.addSchema(3568, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new MobEffectIdFix(schema196));
        Schema schema197 = fixerUpper.addSchema(3682, V3682::new);

        fixerUpper.addFixer(new AddNewChoices(schema197, "Added Crafter", References.BLOCK_ENTITY));
        Schema schema198 = fixerUpper.addSchema(3683, V3683::new);

        fixerUpper.addFixer(new PrimedTntBlockStateFixer(schema198));
        Schema schema199 = fixerUpper.addSchema(3685, V3685::new);

        fixerUpper.addFixer(new FixProjectileStoredItem(schema199));
        Schema schema200 = fixerUpper.addSchema(3689, V3689::new);

        fixerUpper.addFixer(new AddNewChoices(schema200, "Added Breeze", References.ENTITY));
        fixerUpper.addFixer(new AddNewChoices(schema200, "Added Trial Spawner", References.BLOCK_ENTITY));
        Schema schema201 = fixerUpper.addSchema(3692, DataFixers.SAME_NAMESPACED);
        UnaryOperator<String> unaryoperator1 = createRenamer(Map.of("minecraft:grass", "minecraft:short_grass"));

        fixerUpper.addFixer(BlockRenameFix.create(schema201, "Rename grass block to short_grass", unaryoperator1));
        fixerUpper.addFixer(ItemRenameFix.create(schema201, "Rename grass item to short_grass", unaryoperator1));
        Schema schema202 = fixerUpper.addSchema(3799, V3799::new);

        fixerUpper.addFixer(new AddNewChoices(schema202, "Added Armadillo", References.ENTITY));
        Schema schema203 = fixerUpper.addSchema(3800, DataFixers.SAME_NAMESPACED);
        UnaryOperator<String> unaryoperator2 = createRenamer(Map.of("minecraft:scute", "minecraft:turtle_scute"));

        fixerUpper.addFixer(ItemRenameFix.create(schema203, "Rename scute item to turtle_scute", unaryoperator2));
        Schema schema204 = fixerUpper.addSchema(3803, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new RenameEnchantmentsFix(schema204, "Rename sweeping enchant to sweeping_edge", Map.of("minecraft:sweeping", "minecraft:sweeping_edge")));
        Schema schema205 = fixerUpper.addSchema(3807, V3807::new);

        fixerUpper.addFixer(new AddNewChoices(schema205, "Added Vault", References.BLOCK_ENTITY));
        Schema schema206 = fixerUpper.addSchema(3807, 1, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new MapBannerBlockPosFormatFix(schema206));
        Schema schema207 = fixerUpper.addSchema(3808, V3808::new);

        fixerUpper.addFixer(new HorseBodyArmorItemFix(schema207, "minecraft:horse", "ArmorItem", true));
        Schema schema208 = fixerUpper.addSchema(3808, 1, V3808_1::new);

        fixerUpper.addFixer(new HorseBodyArmorItemFix(schema208, "minecraft:llama", "DecorItem", false));
        Schema schema209 = fixerUpper.addSchema(3808, 2, V3808_2::new);

        fixerUpper.addFixer(new HorseBodyArmorItemFix(schema209, "minecraft:trader_llama", "DecorItem", false));
        Schema schema210 = fixerUpper.addSchema(3809, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ChestedHorsesInventoryZeroIndexingFix(schema210));
        Schema schema211 = fixerUpper.addSchema(3812, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new FixWolfHealth(schema211));
        Schema schema212 = fixerUpper.addSchema(3813, V3813::new);

        fixerUpper.addFixer(new BlockPosFormatAndRenamesFix(schema212));
        Schema schema213 = fixerUpper.addSchema(3814, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AttributesRenameLegacy(schema213, "Rename jump strength attribute", createRenamer("minecraft:horse.jump_strength", "minecraft:generic.jump_strength")));
        Schema schema214 = fixerUpper.addSchema(3816, V3816::new);

        fixerUpper.addFixer(new AddNewChoices(schema214, "Added Bogged", References.ENTITY));
        Schema schema215 = fixerUpper.addSchema(3818, V3818::new);

        fixerUpper.addFixer(new BeehiveFieldRenameFix(schema215));
        fixerUpper.addFixer(new EmptyItemInHotbarFix(schema215));
        Schema schema216 = fixerUpper.addSchema(3818, 1, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new BannerPatternFormatFix(schema216));
        Schema schema217 = fixerUpper.addSchema(3818, 2, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new TippedArrowPotionToItemFix(schema217));
        Schema schema218 = fixerUpper.addSchema(3818, 3, V3818_3::new);

        fixerUpper.addFixer(new WriteAndReadFix(schema218, "Inject data component types", References.DATA_COMPONENTS));
        Schema schema219 = fixerUpper.addSchema(3818, 4, V3818_4::new);

        fixerUpper.addFixer(new ParticleUnflatteningFix(schema219));
        Schema schema220 = fixerUpper.addSchema(3818, 5, V3818_5::new);

        fixerUpper.addFixer(new ItemStackComponentizationFix(schema220));
        Schema schema221 = fixerUpper.addSchema(3818, 6, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AreaEffectCloudPotionFix(schema221));
        Schema schema222 = fixerUpper.addSchema(3820, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new PlayerHeadBlockProfileFix(schema222));
        fixerUpper.addFixer(new LodestoneCompassComponentFix(schema222));
        Schema schema223 = fixerUpper.addSchema(3825, V3825::new);

        fixerUpper.addFixer(new ItemStackCustomNameToOverrideComponentFix(schema223));
        fixerUpper.addFixer(new BannerEntityCustomNameToOverrideComponentFix(schema223));
        fixerUpper.addFixer(new TrialSpawnerConfigFix(schema223));
        fixerUpper.addFixer(new AddNewChoices(schema223, "Added Ominous Item Spawner", References.ENTITY));
        Schema schema224 = fixerUpper.addSchema(3828, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EmptyItemInVillagerTradeFix(schema224));
        Schema schema225 = fixerUpper.addSchema(3833, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new RemoveEmptyItemInBrushableBlockFix(schema225));
        Schema schema226 = fixerUpper.addSchema(3938, V3938::new);

        fixerUpper.addFixer(new ProjectileStoredWeaponFix(schema226));
        Schema schema227 = fixerUpper.addSchema(3939, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new FeatureFlagRemoveFix(schema227, "Remove 1.21 feature toggle", Set.of("minecraft:update_1_21")));
        Schema schema228 = fixerUpper.addSchema(3943, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsMenuBlurrinessFix(schema228));
        Schema schema229 = fixerUpper.addSchema(3945, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AttributeModifierIdFix(schema229));
        fixerUpper.addFixer(new JukeboxTicksSinceSongStartedFix(schema229));
        Schema schema230 = fixerUpper.addSchema(4054, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OminousBannerRarityFix(schema230));
        Schema schema231 = fixerUpper.addSchema(4055, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AttributeIdPrefixFix(schema231));
        Schema schema232 = fixerUpper.addSchema(4057, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new CarvingStepRemoveFix(schema232));
        Schema schema233 = fixerUpper.addSchema(4059, V4059::new);

        fixerUpper.addFixer(new FoodToConsumableFix(schema233));
        Schema schema234 = fixerUpper.addSchema(4061, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new TrialSpawnerConfigInRegistryFix(schema234));
        Schema schema235 = fixerUpper.addSchema(4064, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new FireResistantToDamageResistantComponentFix(schema235));
        Schema schema236 = fixerUpper.addSchema(4067, V4067::new);

        fixerUpper.addFixer(new BoatSplitFix(schema236));
        fixerUpper.addFixer(new FeatureFlagRemoveFix(schema236, "Remove Bundle experimental feature flag", Set.of("minecraft:bundle")));
        Schema schema237 = fixerUpper.addSchema(4068, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new LockComponentPredicateFix(schema237));
        fixerUpper.addFixer(new ContainerBlockEntityLockPredicateFix(schema237));
        Schema schema238 = fixerUpper.addSchema(4070, V4070::new);

        fixerUpper.addFixer(new AddNewChoices(schema238, "Added Pale Oak Boat and Pale Oak Chest Boat", References.ENTITY));
        Schema schema239 = fixerUpper.addSchema(4071, V4071::new);

        fixerUpper.addFixer(new AddNewChoices(schema239, "Added Creaking", References.ENTITY));
        fixerUpper.addFixer(new AddNewChoices(schema239, "Added Creaking Heart", References.BLOCK_ENTITY));
        Schema schema240 = fixerUpper.addSchema(4081, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntitySalmonSizeFix(schema240));
        Schema schema241 = fixerUpper.addSchema(4173, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntityFieldsRenameFix(schema241, "Rename TNT Minecart fuse", "minecraft:tnt_minecart", Map.of("TNTFuse", "fuse")));
        Schema schema242 = fixerUpper.addSchema(4175, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EquippableAssetRenameFix(schema242));
        fixerUpper.addFixer(new CustomModelDataExpandFix(schema242));
        Schema schema243 = fixerUpper.addSchema(4176, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new InvalidBlockEntityLockFix(schema243));
        fixerUpper.addFixer(new InvalidLockComponentFix(schema243));
        Schema schema244 = fixerUpper.addSchema(4180, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new FeatureFlagRemoveFix(schema244, "Remove Winter Drop toggle", Set.of("minecraft:winter_drop")));
        Schema schema245 = fixerUpper.addSchema(4181, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new BlockEntityFurnaceBurnTimeFix(schema245, "minecraft:furnace"));
        fixerUpper.addFixer(new BlockEntityFurnaceBurnTimeFix(schema245, "minecraft:smoker"));
        fixerUpper.addFixer(new BlockEntityFurnaceBurnTimeFix(schema245, "minecraft:blast_furnace"));
        Schema schema246 = fixerUpper.addSchema(4187, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntityAttributeBaseValueFix(schema246, "Villager follow range fix undo", "minecraft:villager", "minecraft:follow_range", (d0) -> {
            return d0 == 48.0D ? 16.0D : d0;
        }));
        fixerUpper.addFixer(new EntityAttributeBaseValueFix(schema246, "Bee follow range fix", "minecraft:bee", "minecraft:follow_range", (d0) -> {
            return d0 == 48.0D ? 16.0D : d0;
        }));
        fixerUpper.addFixer(new EntityAttributeBaseValueFix(schema246, "Allay follow range fix", "minecraft:allay", "minecraft:follow_range", (d0) -> {
            return d0 == 48.0D ? 16.0D : d0;
        }));
        fixerUpper.addFixer(new EntityAttributeBaseValueFix(schema246, "Llama follow range fix", "minecraft:llama", "minecraft:follow_range", (d0) -> {
            return d0 == 40.0D ? 16.0D : d0;
        }));
        fixerUpper.addFixer(new EntityAttributeBaseValueFix(schema246, "Piglin Brute follow range fix", "minecraft:piglin_brute", "minecraft:follow_range", (d0) -> {
            return d0 == 16.0D ? 12.0D : d0;
        }));
        fixerUpper.addFixer(new EntityAttributeBaseValueFix(schema246, "Warden follow range fix", "minecraft:warden", "minecraft:follow_range", (d0) -> {
            return d0 == 16.0D ? 24.0D : d0;
        }));
        Schema schema247 = fixerUpper.addSchema(4290, V4290::new);

        fixerUpper.addFixer(new UnflattenTextComponentFix(schema247));
        Schema schema248 = fixerUpper.addSchema(4291, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new LegacyHoverEventFix(schema248));
        fixerUpper.addFixer(new TextComponentStringifiedFlagsFix(schema248));
        Schema schema249 = fixerUpper.addSchema(4292, V4292::new);

        fixerUpper.addFixer(new TextComponentHoverAndClickEventFix(schema249));
        Schema schema250 = fixerUpper.addSchema(4293, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new DropChancesFormatFix(schema250));
        Schema schema251 = fixerUpper.addSchema(4294, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new BlockPropertyRenameAndFix(schema251, "CreakingHeartBlockStateFix", "minecraft:creaking_heart", "active", "creaking_heart_state", (s) -> {
            return s.equals("true") ? "awake" : "uprooted";
        }));
        Schema schema252 = fixerUpper.addSchema(4295, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new BlendingDataFix(schema252));
        Schema schema253 = fixerUpper.addSchema(4296, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AreaEffectCloudDurationScaleFix(schema253));
        Schema schema254 = fixerUpper.addSchema(4297, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ForcedChunkToTicketFix(schema254));
        Schema schema255 = fixerUpper.addSchema(4299, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntitySpawnerItemVariantComponentFix(schema255));
        Schema schema256 = fixerUpper.addSchema(4300, V4300::new);

        fixerUpper.addFixer(new SaddleEquipmentSlotFix(schema256));
        Schema schema257 = fixerUpper.addSchema(4301, V4301::new);

        fixerUpper.addFixer(new EquipmentFormatFix(schema257));
        Schema schema258 = fixerUpper.addSchema(4302, V4302::new);

        fixerUpper.addFixer(new AddNewChoices(schema258, "Added Test and Test Instance Block Entities", References.BLOCK_ENTITY));
        Schema schema259 = fixerUpper.addSchema(4303, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new EntityFallDistanceFloatToDoubleFix(schema259, References.ENTITY));
        fixerUpper.addFixer(new EntityFallDistanceFloatToDoubleFix(schema259, References.PLAYER));
        Schema schema260 = fixerUpper.addSchema(4305, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new BlockPropertyRenameAndFix(schema260, "rename test block mode", "minecraft:test_block", "test_block_mode", "mode", (s) -> {
            return s;
        }));
        Schema schema261 = fixerUpper.addSchema(4306, V4306::new);

        fixerUpper.addFixer(new ThrownPotionSplitFix(schema261));
        Schema schema262 = fixerUpper.addSchema(4307, V4307::new);

        fixerUpper.addFixer(new TooltipDisplayComponentFix(schema262));
        Schema schema263 = fixerUpper.addSchema(4309, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new RaidRenamesDataFix(schema263));
        fixerUpper.addFixer(new ChunkTicketUnpackPosFix(schema263));
        Schema schema264 = fixerUpper.addSchema(4311, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new AdvancementsRenameFix(schema264, false, "Use lodestone category change", createRenamer("minecraft:nether/use_lodestone", "minecraft:adventure/use_lodestone")));
        Schema schema265 = fixerUpper.addSchema(4312, V4312::new);

        fixerUpper.addFixer(new PlayerEquipmentFix(schema265));
        Schema schema266 = fixerUpper.addSchema(4314, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new InlineBlockPosFormatFix(schema266));
        Schema schema267 = fixerUpper.addSchema(4420, V4420::new);

        fixerUpper.addFixer(new NamedEntityConvertUncheckedFix(schema267, "AreaEffectCloudCustomParticleFix", References.ENTITY, "minecraft:area_effect_cloud"));
        Schema schema268 = fixerUpper.addSchema(4421, V4421::new);

        fixerUpper.addFixer(new AddNewChoices(schema268, "Added Happy Ghast", References.ENTITY));
        Schema schema269 = fixerUpper.addSchema(4424, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new FeatureFlagRemoveFix(schema269, "Remove Locator Bar experimental feature flag", Set.of("minecraft:locator_bar")));
        fixerUpper.addFixer(new AddFieldFix(schema269, References.PLAYER, "style", (dynamic) -> {
            return dynamic.createString("minecraft:default");
        }, new String[]{"locator_bar_icon"}));
        fixerUpper.addFixer(new AddFieldFix(schema269, References.ENTITY, "style", (dynamic) -> {
            return dynamic.createString("minecraft:default");
        }, new String[]{"locator_bar_icon"}));
        Schema schema270 = fixerUpper.addSchema(4531, V4531::new);

        fixerUpper.addFixer(new AddNewChoices(schema270, "Added Copper Golem", References.ENTITY));
        Schema schema271 = fixerUpper.addSchema(4532, V4532::new);

        fixerUpper.addFixer(new AddNewChoices(schema271, "Added Copper Golem Statue Block Entity", References.BLOCK_ENTITY));
        Schema schema272 = fixerUpper.addSchema(4533, V4533::new);

        fixerUpper.addFixer(new AddNewChoices(schema272, "Added Shelf", References.BLOCK_ENTITY));
        Schema schema273 = fixerUpper.addSchema(4535, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new CopperGolemWeatherStateFix(schema273));
        Schema schema274 = fixerUpper.addSchema(4537, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new ChunkDeleteLightFix(schema274));
        Schema schema275 = fixerUpper.addSchema(4541, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(BlockRenameFix.create(schema275, "Rename chain to iron_chain", createRenamer("minecraft:chain", "minecraft:iron_chain")));
        fixerUpper.addFixer(ItemRenameFix.create(schema275, "Rename chain to iron_chain", createRenamer("minecraft:chain", "minecraft:iron_chain")));
        Schema schema276 = fixerUpper.addSchema(4543, V4543::new);

        fixerUpper.addFixer(new AddNewChoices(schema276, "Added Mannequin", References.ENTITY));
        Schema schema277 = fixerUpper.addSchema(4544, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new LegacyWorldBorderFix(schema277));
        Schema schema278 = fixerUpper.addSchema(4548, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new WorldSpawnDataFix(schema278));
        fixerUpper.addFixer(new PlayerRespawnDataFix(schema278));
        Schema schema279 = fixerUpper.addSchema(4648, V4648::new);

        fixerUpper.addFixer(new AddNewChoices(schema279, "Added Nautilus and Zombie Nautilus", References.ENTITY));
        Schema schema280 = fixerUpper.addSchema(4649, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new TridentAnimationFix(schema280));
        Schema schema281 = fixerUpper.addSchema(4650, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new DebugProfileOverlayReferenceFix(schema281));
        Schema schema282 = fixerUpper.addSchema(4651, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsGraphicsModeSplitFix(schema282, "cutoutLeaves", "false", "true", "true"));
        fixerUpper.addFixer(new OptionsGraphicsModeSplitFix(schema282, "weatherRadius", "5", "10", "10"));
        fixerUpper.addFixer(new OptionsGraphicsModeSplitFix(schema282, "vignette", "false", "true", "true"));
        fixerUpper.addFixer(new OptionsGraphicsModeSplitFix(schema282, "improvedTransparency", "false", "false", "true"));
        fixerUpper.addFixer(new OptionsSetGraphicsPresetToCustomFix(schema282));
        Schema schema283 = fixerUpper.addSchema(4656, V4656::new);

        fixerUpper.addFixer(new AddNewChoices(schema283, "Added Parched and Camel Husk", References.ENTITY));
        Schema schema284 = fixerUpper.addSchema(4657, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new WorldBorderWarningTimeFix(schema284));
        Schema schema285 = fixerUpper.addSchema(4658, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new GameRuleRegistryFix(schema285));
        Schema schema286 = fixerUpper.addSchema(4661, DataFixers.SAME_NAMESPACED);

        fixerUpper.addFixer(new OptionsMusicToastFix(schema286, false));
    }

    private static UnaryOperator<String> createRenamerNoNamespace(Map<String, String> map) {
        return (s) -> {
            return (String) map.getOrDefault(s, s);
        };
    }

    private static UnaryOperator<String> createRenamer(Map<String, String> map) {
        return (s) -> {
            return (String) map.getOrDefault(NamespacedSchema.ensureNamespaced(s), s);
        };
    }

    private static UnaryOperator<String> createRenamer(String from, String to) {
        return (s2) -> {
            return Objects.equals(NamespacedSchema.ensureNamespaced(s2), from) ? to : s2;
        };
    }
}
