package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

public class MapItem extends Item {

    public static final int IMAGE_WIDTH = 128;
    public static final int IMAGE_HEIGHT = 128;

    public MapItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemStack create(ServerLevel level, int originX, int originZ, byte scale, boolean trackPosition, boolean unlimitedTracking) {
        ItemStack itemstack = new ItemStack(Items.FILLED_MAP);
        MapId mapid = createNewSavedData(level, originX, originZ, scale, trackPosition, unlimitedTracking, level.dimension());

        itemstack.set(DataComponents.MAP_ID, mapid);
        return itemstack;
    }

    public static @Nullable MapItemSavedData getSavedData(@Nullable MapId id, Level level) {
        return id == null ? null : level.getMapData(id);
    }

    public static @Nullable MapItemSavedData getSavedData(ItemStack itemStack, Level level) {
        MapId mapid = (MapId) itemStack.get(DataComponents.MAP_ID);

        return getSavedData(mapid, level);
    }

    public static MapId createNewSavedData(ServerLevel level, int xSpawn, int zSpawn, int scale, boolean trackingPosition, boolean unlimitedTracking, ResourceKey<Level> dimension) {
        MapItemSavedData mapitemsaveddata = MapItemSavedData.createFresh((double) xSpawn, (double) zSpawn, (byte) scale, trackingPosition, unlimitedTracking, dimension);
        MapId mapid = level.getFreeMapId();

        level.setMapData(mapid, mapitemsaveddata);
        return mapid;
    }

    public void update(Level level, Entity player, MapItemSavedData data) {
        if (level.dimension() == data.dimension && player instanceof Player) {
            int i = 1 << data.scale;
            int j = data.centerX;
            int k = data.centerZ;
            int l = Mth.floor(player.getX() - (double) j) / i + 64;
            int i1 = Mth.floor(player.getZ() - (double) k) / i + 64;
            int j1 = 128 / i;

            if (level.dimensionType().hasCeiling()) {
                j1 /= 2;
            }

            MapItemSavedData.HoldingPlayer mapitemsaveddata_holdingplayer = data.getHoldingPlayer((Player) player);

            ++mapitemsaveddata_holdingplayer.step;
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos blockpos_mutableblockpos1 = new BlockPos.MutableBlockPos();
            boolean flag = false;

            for (int k1 = l - j1 + 1; k1 < l + j1; ++k1) {
                if ((k1 & 15) == (mapitemsaveddata_holdingplayer.step & 15) || flag) {
                    flag = false;
                    double d0 = 0.0D;

                    for (int l1 = i1 - j1 - 1; l1 < i1 + j1; ++l1) {
                        if (k1 >= 0 && l1 >= -1 && k1 < 128 && l1 < 128) {
                            int i2 = Mth.square(k1 - l) + Mth.square(l1 - i1);
                            boolean flag1 = i2 > (j1 - 2) * (j1 - 2);
                            int j2 = (j / i + k1 - 64) * i;
                            int k2 = (k / i + l1 - 64) * i;
                            Multiset<MapColor> multiset = LinkedHashMultiset.create();
                            LevelChunk levelchunk = level.getChunk(SectionPos.blockToSectionCoord(j2), SectionPos.blockToSectionCoord(k2));

                            if (!levelchunk.isEmpty()) {
                                int l2 = 0;
                                double d1 = 0.0D;

                                if (level.dimensionType().hasCeiling()) {
                                    int i3 = j2 + k2 * 231871;

                                    i3 = i3 * i3 * 31287121 + i3 * 11;
                                    if ((i3 >> 20 & 1) == 0) {
                                        multiset.add(Blocks.DIRT.defaultBlockState().getMapColor(level, BlockPos.ZERO), 10);
                                    } else {
                                        multiset.add(Blocks.STONE.defaultBlockState().getMapColor(level, BlockPos.ZERO), 100);
                                    }

                                    d1 = 100.0D;
                                } else {
                                    for (int j3 = 0; j3 < i; ++j3) {
                                        for (int k3 = 0; k3 < i; ++k3) {
                                            blockpos_mutableblockpos.set(j2 + j3, 0, k2 + k3);
                                            int l3 = levelchunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockpos_mutableblockpos.getX(), blockpos_mutableblockpos.getZ()) + 1;
                                            BlockState blockstate;

                                            if (l3 <= level.getMinY()) {
                                                blockstate = Blocks.BEDROCK.defaultBlockState();
                                            } else {
                                                do {
                                                    --l3;
                                                    blockpos_mutableblockpos.setY(l3);
                                                    blockstate = levelchunk.getBlockState(blockpos_mutableblockpos);
                                                } while (blockstate.getMapColor(level, blockpos_mutableblockpos) == MapColor.NONE && l3 > level.getMinY());

                                                if (l3 > level.getMinY() && !blockstate.getFluidState().isEmpty()) {
                                                    int i4 = l3 - 1;

                                                    blockpos_mutableblockpos1.set(blockpos_mutableblockpos);

                                                    BlockState blockstate1;

                                                    do {
                                                        blockpos_mutableblockpos1.setY(i4--);
                                                        blockstate1 = levelchunk.getBlockState(blockpos_mutableblockpos1);
                                                        ++l2;
                                                    } while (i4 > level.getMinY() && !blockstate1.getFluidState().isEmpty());

                                                    blockstate = this.getCorrectStateForFluidBlock(level, blockstate, blockpos_mutableblockpos);
                                                }
                                            }

                                            data.checkBanners(level, blockpos_mutableblockpos.getX(), blockpos_mutableblockpos.getZ());
                                            d1 += (double) l3 / (double) (i * i);
                                            multiset.add(blockstate.getMapColor(level, blockpos_mutableblockpos));
                                        }
                                    }
                                }

                                l2 /= i * i;
                                MapColor mapcolor = (MapColor) Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.NONE);
                                MapColor.Brightness mapcolor_brightness;

                                if (mapcolor == MapColor.WATER) {
                                    double d2 = (double) l2 * 0.1D + (double) (k1 + l1 & 1) * 0.2D;

                                    if (d2 < 0.5D) {
                                        mapcolor_brightness = MapColor.Brightness.HIGH;
                                    } else if (d2 > 0.9D) {
                                        mapcolor_brightness = MapColor.Brightness.LOW;
                                    } else {
                                        mapcolor_brightness = MapColor.Brightness.NORMAL;
                                    }
                                } else {
                                    double d3 = (d1 - d0) * 4.0D / (double) (i + 4) + ((double) (k1 + l1 & 1) - 0.5D) * 0.4D;

                                    if (d3 > 0.6D) {
                                        mapcolor_brightness = MapColor.Brightness.HIGH;
                                    } else if (d3 < -0.6D) {
                                        mapcolor_brightness = MapColor.Brightness.LOW;
                                    } else {
                                        mapcolor_brightness = MapColor.Brightness.NORMAL;
                                    }
                                }

                                d0 = d1;
                                if (l1 >= 0 && i2 < j1 * j1 && (!flag1 || (k1 + l1 & 1) != 0)) {
                                    flag |= data.updateColor(k1, l1, mapcolor.getPackedId(mapcolor_brightness));
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    private BlockState getCorrectStateForFluidBlock(Level level, BlockState state, BlockPos pos) {
        FluidState fluidstate = state.getFluidState();

        return !fluidstate.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluidstate.createLegacyBlock() : state;
    }

    private static boolean isBiomeWatery(boolean[] isBiomeWatery, int x, int z) {
        return isBiomeWatery[z * 128 + x];
    }

    public static void renderBiomePreviewMap(ServerLevel level, ItemStack mapItemStack) {
        MapItemSavedData mapitemsaveddata = getSavedData(mapItemStack, level);

        if (mapitemsaveddata != null) {
            if (level.dimension() == mapitemsaveddata.dimension) {
                int i = 1 << mapitemsaveddata.scale;
                int j = mapitemsaveddata.centerX;
                int k = mapitemsaveddata.centerZ;
                boolean[] aboolean = new boolean[16384];
                int l = j / i - 64;
                int i1 = k / i - 64;
                BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

                for (int j1 = 0; j1 < 128; ++j1) {
                    for (int k1 = 0; k1 < 128; ++k1) {
                        Holder<Biome> holder = level.getBiome(blockpos_mutableblockpos.set((l + k1) * i, 0, (i1 + j1) * i));

                        aboolean[j1 * 128 + k1] = holder.is(BiomeTags.WATER_ON_MAP_OUTLINES);
                    }
                }

                for (int l1 = 1; l1 < 127; ++l1) {
                    for (int i2 = 1; i2 < 127; ++i2) {
                        int j2 = 0;

                        for (int k2 = -1; k2 < 2; ++k2) {
                            for (int l2 = -1; l2 < 2; ++l2) {
                                if ((k2 != 0 || l2 != 0) && isBiomeWatery(aboolean, l1 + k2, i2 + l2)) {
                                    ++j2;
                                }
                            }
                        }

                        MapColor.Brightness mapcolor_brightness = MapColor.Brightness.LOWEST;
                        MapColor mapcolor = MapColor.NONE;

                        if (isBiomeWatery(aboolean, l1, i2)) {
                            mapcolor = MapColor.COLOR_ORANGE;
                            if (j2 > 7 && i2 % 2 == 0) {
                                switch ((l1 + (int) (Mth.sin((double) ((float) i2 + 0.0F)) * 7.0F)) / 8 % 5) {
                                    case 0:
                                    case 4:
                                        mapcolor_brightness = MapColor.Brightness.LOW;
                                        break;
                                    case 1:
                                    case 3:
                                        mapcolor_brightness = MapColor.Brightness.NORMAL;
                                        break;
                                    case 2:
                                        mapcolor_brightness = MapColor.Brightness.HIGH;
                                }
                            } else if (j2 > 7) {
                                mapcolor = MapColor.NONE;
                            } else if (j2 > 5) {
                                mapcolor_brightness = MapColor.Brightness.NORMAL;
                            } else if (j2 > 3) {
                                mapcolor_brightness = MapColor.Brightness.LOW;
                            } else if (j2 > 1) {
                                mapcolor_brightness = MapColor.Brightness.LOW;
                            }
                        } else if (j2 > 0) {
                            mapcolor = MapColor.COLOR_BROWN;
                            if (j2 > 3) {
                                mapcolor_brightness = MapColor.Brightness.NORMAL;
                            } else {
                                mapcolor_brightness = MapColor.Brightness.LOWEST;
                            }
                        }

                        if (mapcolor != MapColor.NONE) {
                            mapitemsaveddata.setColor(l1, i2, mapcolor.getPackedId(mapcolor_brightness));
                        }
                    }
                }

            }
        }
    }

    @Override
    public void inventoryTick(ItemStack itemStack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        MapItemSavedData mapitemsaveddata = getSavedData(itemStack, level);

        if (mapitemsaveddata != null) {
            if (owner instanceof Player) {
                Player player = (Player) owner;

                mapitemsaveddata.tickCarriedBy(player, itemStack);
            }

            if (!mapitemsaveddata.locked && slot != null && slot.getType() == EquipmentSlot.Type.HAND) {
                this.update(level, owner, mapitemsaveddata);
            }

        }
    }

    @Override
    public void onCraftedPostProcess(ItemStack itemStack, Level level) {
        MapPostProcessing mappostprocessing = (MapPostProcessing) itemStack.remove(DataComponents.MAP_POST_PROCESSING);

        if (mappostprocessing != null) {
            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                switch (mappostprocessing) {
                    case LOCK:
                        lockMap(itemStack, serverlevel);
                        break;
                    case SCALE:
                        scaleMap(itemStack, serverlevel);
                }
            }

        }
    }

    private static void scaleMap(ItemStack itemStack, ServerLevel level) {
        MapItemSavedData mapitemsaveddata = getSavedData(itemStack, level);

        if (mapitemsaveddata != null) {
            MapId mapid = level.getFreeMapId();

            level.setMapData(mapid, mapitemsaveddata.scaled());
            itemStack.set(DataComponents.MAP_ID, mapid);
        }

    }

    private static void lockMap(ItemStack map, ServerLevel level) {
        MapItemSavedData mapitemsaveddata = getSavedData(map, level);

        if (mapitemsaveddata != null) {
            MapId mapid = level.getFreeMapId();
            MapItemSavedData mapitemsaveddata1 = mapitemsaveddata.locked();

            level.setMapData(mapid, mapitemsaveddata1);
            map.set(DataComponents.MAP_ID, mapid);
        }

    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos());

        if (blockstate.is(BlockTags.BANNERS)) {
            if (!context.getLevel().isClientSide()) {
                MapItemSavedData mapitemsaveddata = getSavedData(context.getItemInHand(), context.getLevel());

                if (mapitemsaveddata != null && !mapitemsaveddata.toggleBanner(context.getLevel(), context.getClickedPos())) {
                    return InteractionResult.FAIL;
                }
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.useOn(context);
        }
    }
}
