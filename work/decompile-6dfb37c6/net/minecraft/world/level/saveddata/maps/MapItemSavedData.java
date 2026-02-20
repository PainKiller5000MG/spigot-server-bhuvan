package net.minecraft.world.level.saveddata.maps;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class MapItemSavedData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAP_SIZE = 128;
    private static final int HALF_MAP_SIZE = 64;
    public static final int MAX_SCALE = 4;
    public static final int TRACKED_DECORATION_LIMIT = 256;
    private static final String FRAME_PREFIX = "frame-";
    public static final Codec<MapItemSavedData> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter((mapitemsaveddata) -> {
            return mapitemsaveddata.dimension;
        }), Codec.INT.fieldOf("xCenter").forGetter((mapitemsaveddata) -> {
            return mapitemsaveddata.centerX;
        }), Codec.INT.fieldOf("zCenter").forGetter((mapitemsaveddata) -> {
            return mapitemsaveddata.centerZ;
        }), Codec.BYTE.optionalFieldOf("scale", (byte) 0).forGetter((mapitemsaveddata) -> {
            return mapitemsaveddata.scale;
        }), Codec.BYTE_BUFFER.fieldOf("colors").forGetter((mapitemsaveddata) -> {
            return ByteBuffer.wrap(mapitemsaveddata.colors);
        }), Codec.BOOL.optionalFieldOf("trackingPosition", true).forGetter((mapitemsaveddata) -> {
            return mapitemsaveddata.trackingPosition;
        }), Codec.BOOL.optionalFieldOf("unlimitedTracking", false).forGetter((mapitemsaveddata) -> {
            return mapitemsaveddata.unlimitedTracking;
        }), Codec.BOOL.optionalFieldOf("locked", false).forGetter((mapitemsaveddata) -> {
            return mapitemsaveddata.locked;
        }), MapBanner.CODEC.listOf().optionalFieldOf("banners", List.of()).forGetter((mapitemsaveddata) -> {
            return List.copyOf(mapitemsaveddata.bannerMarkers.values());
        }), MapFrame.CODEC.listOf().optionalFieldOf("frames", List.of()).forGetter((mapitemsaveddata) -> {
            return List.copyOf(mapitemsaveddata.frameMarkers.values());
        })).apply(instance, MapItemSavedData::new);
    });
    public int centerX;
    public int centerZ;
    public ResourceKey<Level> dimension;
    public boolean trackingPosition;
    public boolean unlimitedTracking;
    public byte scale;
    public byte[] colors;
    public boolean locked;
    public final List<MapItemSavedData.HoldingPlayer> carriedBy;
    public final Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers;
    private final Map<String, MapBanner> bannerMarkers;
    public final Map<String, MapDecoration> decorations;
    private final Map<String, MapFrame> frameMarkers;
    private int trackedDecorationCount;

    public static SavedDataType<MapItemSavedData> type(MapId id) {
        return new SavedDataType<MapItemSavedData>(id.key(), () -> {
            throw new IllegalStateException("Should never create an empty map saved data");
        }, MapItemSavedData.CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);
    }

    private MapItemSavedData(int centerX, int centerZ, byte scale, boolean trackingPosition, boolean unlimitedTracking, boolean locked, ResourceKey<Level> dimension) {
        this.colors = new byte[16384];
        this.carriedBy = Lists.newArrayList();
        this.carriedByPlayers = Maps.newHashMap();
        this.bannerMarkers = Maps.newHashMap();
        this.decorations = Maps.newLinkedHashMap();
        this.frameMarkers = Maps.newHashMap();
        this.scale = scale;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.dimension = dimension;
        this.trackingPosition = trackingPosition;
        this.unlimitedTracking = unlimitedTracking;
        this.locked = locked;
    }

    private MapItemSavedData(ResourceKey<Level> dimension, int centerX, int centerZ, byte scale, ByteBuffer colors, boolean trackingPosition, boolean unlimitedTracking, boolean locked, List<MapBanner> banners, List<MapFrame> frames) {
        this(centerX, centerZ, (byte) Mth.clamp(scale, 0, 4), trackingPosition, unlimitedTracking, locked, dimension);
        if (colors.array().length == 16384) {
            this.colors = colors.array();
        }

        for (MapBanner mapbanner : banners) {
            this.bannerMarkers.put(mapbanner.getId(), mapbanner);
            this.addDecoration(mapbanner.getDecoration(), (LevelAccessor) null, mapbanner.getId(), (double) mapbanner.pos().getX(), (double) mapbanner.pos().getZ(), 180.0D, (Component) mapbanner.name().orElse((Object) null));
        }

        for (MapFrame mapframe : frames) {
            this.frameMarkers.put(mapframe.getId(), mapframe);
            this.addDecoration(MapDecorationTypes.FRAME, (LevelAccessor) null, getFrameKey(mapframe.entityId()), (double) mapframe.pos().getX(), (double) mapframe.pos().getZ(), (double) mapframe.rotation(), (Component) null);
        }

    }

    public static MapItemSavedData createFresh(double originX, double originY, byte scale, boolean trackingPosition, boolean unlimitedTracking, ResourceKey<Level> dimension) {
        int i = 128 * (1 << scale);
        int j = Mth.floor((originX + 64.0D) / (double) i);
        int k = Mth.floor((originY + 64.0D) / (double) i);
        int l = j * i + i / 2 - 64;
        int i1 = k * i + i / 2 - 64;

        return new MapItemSavedData(l, i1, scale, trackingPosition, unlimitedTracking, false, dimension);
    }

    public static MapItemSavedData createForClient(byte scale, boolean isLocked, ResourceKey<Level> dimension) {
        return new MapItemSavedData(0, 0, scale, false, false, isLocked, dimension);
    }

    public MapItemSavedData locked() {
        MapItemSavedData mapitemsaveddata = new MapItemSavedData(this.centerX, this.centerZ, this.scale, this.trackingPosition, this.unlimitedTracking, true, this.dimension);

        mapitemsaveddata.bannerMarkers.putAll(this.bannerMarkers);
        mapitemsaveddata.decorations.putAll(this.decorations);
        mapitemsaveddata.trackedDecorationCount = this.trackedDecorationCount;
        System.arraycopy(this.colors, 0, mapitemsaveddata.colors, 0, this.colors.length);
        return mapitemsaveddata;
    }

    public MapItemSavedData scaled() {
        return createFresh((double) this.centerX, (double) this.centerZ, (byte) Mth.clamp(this.scale + 1, 0, 4), this.trackingPosition, this.unlimitedTracking, this.dimension);
    }

    private static Predicate<ItemStack> mapMatcher(ItemStack mapStack) {
        MapId mapid = (MapId) mapStack.get(DataComponents.MAP_ID);

        return (itemstack1) -> {
            return itemstack1 == mapStack ? true : itemstack1.is(mapStack.getItem()) && Objects.equals(mapid, itemstack1.get(DataComponents.MAP_ID));
        };
    }

    public void tickCarriedBy(Player tickingPlayer, ItemStack itemStack) {
        if (!this.carriedByPlayers.containsKey(tickingPlayer)) {
            MapItemSavedData.HoldingPlayer mapitemsaveddata_holdingplayer = new MapItemSavedData.HoldingPlayer(tickingPlayer);

            this.carriedByPlayers.put(tickingPlayer, mapitemsaveddata_holdingplayer);
            this.carriedBy.add(mapitemsaveddata_holdingplayer);
        }

        Predicate<ItemStack> predicate = mapMatcher(itemStack);

        if (!tickingPlayer.getInventory().contains(predicate)) {
            this.removeDecoration(tickingPlayer.getPlainTextName());
        }

        for (int i = 0; i < this.carriedBy.size(); ++i) {
            MapItemSavedData.HoldingPlayer mapitemsaveddata_holdingplayer1 = (MapItemSavedData.HoldingPlayer) this.carriedBy.get(i);
            Player player1 = mapitemsaveddata_holdingplayer1.player;
            String s = player1.getPlainTextName();

            if (!player1.isRemoved() && (player1.getInventory().contains(predicate) || itemStack.isFramed())) {
                if (!itemStack.isFramed() && player1.level().dimension() == this.dimension && this.trackingPosition) {
                    this.addDecoration(MapDecorationTypes.PLAYER, player1.level(), s, player1.getX(), player1.getZ(), (double) player1.getYRot(), (Component) null);
                }
            } else {
                this.carriedByPlayers.remove(player1);
                this.carriedBy.remove(mapitemsaveddata_holdingplayer1);
                this.removeDecoration(s);
            }

            if (!player1.equals(tickingPlayer) && hasMapInvisibilityItemEquipped(player1)) {
                this.removeDecoration(s);
            }
        }

        if (itemStack.isFramed() && this.trackingPosition) {
            ItemFrame itemframe = itemStack.getFrame();
            BlockPos blockpos = itemframe.getPos();
            MapFrame mapframe = (MapFrame) this.frameMarkers.get(MapFrame.frameId(blockpos));

            if (mapframe != null && itemframe.getId() != mapframe.entityId() && this.frameMarkers.containsKey(mapframe.getId())) {
                this.removeDecoration(getFrameKey(mapframe.entityId()));
            }

            MapFrame mapframe1 = new MapFrame(blockpos, itemframe.getDirection().get2DDataValue() * 90, itemframe.getId());

            this.addDecoration(MapDecorationTypes.FRAME, tickingPlayer.level(), getFrameKey(itemframe.getId()), (double) blockpos.getX(), (double) blockpos.getZ(), (double) (itemframe.getDirection().get2DDataValue() * 90), (Component) null);
            MapFrame mapframe2 = (MapFrame) this.frameMarkers.put(mapframe1.getId(), mapframe1);

            if (!mapframe1.equals(mapframe2)) {
                this.setDirty();
            }
        }

        MapDecorations mapdecorations = (MapDecorations) itemStack.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);

        if (!this.decorations.keySet().containsAll(mapdecorations.decorations().keySet())) {
            mapdecorations.decorations().forEach((s1, mapdecorations_entry) -> {
                if (!this.decorations.containsKey(s1)) {
                    this.addDecoration(mapdecorations_entry.type(), tickingPlayer.level(), s1, mapdecorations_entry.x(), mapdecorations_entry.z(), (double) mapdecorations_entry.rotation(), (Component) null);
                }

            });
        }

    }

    private static boolean hasMapInvisibilityItemEquipped(Player player) {
        for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
            if (equipmentslot != EquipmentSlot.MAINHAND && equipmentslot != EquipmentSlot.OFFHAND && player.getItemBySlot(equipmentslot).is(ItemTags.MAP_INVISIBILITY_EQUIPMENT)) {
                return true;
            }
        }

        return false;
    }

    private void removeDecoration(String string) {
        MapDecoration mapdecoration = (MapDecoration) this.decorations.remove(string);

        if (mapdecoration != null && ((MapDecorationType) mapdecoration.type().value()).trackCount()) {
            --this.trackedDecorationCount;
        }

        this.setDecorationsDirty();
    }

    public static void addTargetDecoration(ItemStack itemStack, BlockPos position, String key, Holder<MapDecorationType> decorationType) {
        MapDecorations.Entry mapdecorations_entry = new MapDecorations.Entry(decorationType, (double) position.getX(), (double) position.getZ(), 180.0F);

        itemStack.update(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY, (mapdecorations) -> {
            return mapdecorations.withDecoration(key, mapdecorations_entry);
        });
        if (((MapDecorationType) decorationType.value()).hasMapColor()) {
            itemStack.set(DataComponents.MAP_COLOR, new MapItemColor(((MapDecorationType) decorationType.value()).mapColor()));
        }

    }

    private void addDecoration(Holder<MapDecorationType> type, @Nullable LevelAccessor level, String key, double xPos, double zPos, double yRot, @Nullable Component name) {
        int i = 1 << this.scale;
        float f = (float) (xPos - (double) this.centerX) / (float) i;
        float f1 = (float) (zPos - (double) this.centerZ) / (float) i;
        MapItemSavedData.MapDecorationLocation mapitemsaveddata_mapdecorationlocation = this.calculateDecorationLocationAndType(type, level, yRot, f, f1);

        if (mapitemsaveddata_mapdecorationlocation == null) {
            this.removeDecoration(key);
        } else {
            MapDecoration mapdecoration = new MapDecoration(mapitemsaveddata_mapdecorationlocation.type(), mapitemsaveddata_mapdecorationlocation.x(), mapitemsaveddata_mapdecorationlocation.y(), mapitemsaveddata_mapdecorationlocation.rot(), Optional.ofNullable(name));
            MapDecoration mapdecoration1 = (MapDecoration) this.decorations.put(key, mapdecoration);

            if (!mapdecoration.equals(mapdecoration1)) {
                if (mapdecoration1 != null && ((MapDecorationType) mapdecoration1.type().value()).trackCount()) {
                    --this.trackedDecorationCount;
                }

                if (((MapDecorationType) mapitemsaveddata_mapdecorationlocation.type().value()).trackCount()) {
                    ++this.trackedDecorationCount;
                }

                this.setDecorationsDirty();
            }

        }
    }

    private MapItemSavedData.@Nullable MapDecorationLocation calculateDecorationLocationAndType(Holder<MapDecorationType> type, @Nullable LevelAccessor level, double yRot, float xDeltaFromCenter, float yDeltaFromCenter) {
        byte b0 = clampMapCoordinate(xDeltaFromCenter);
        byte b1 = clampMapCoordinate(yDeltaFromCenter);

        if (type.is(MapDecorationTypes.PLAYER)) {
            Pair<Holder<MapDecorationType>, Byte> pair = this.playerDecorationTypeAndRotation(type, level, yRot, xDeltaFromCenter, yDeltaFromCenter);

            return pair == null ? null : new MapItemSavedData.MapDecorationLocation((Holder) pair.getFirst(), b0, b1, (Byte) pair.getSecond());
        } else {
            return !isInsideMap(xDeltaFromCenter, yDeltaFromCenter) && !this.unlimitedTracking ? null : new MapItemSavedData.MapDecorationLocation(type, b0, b1, this.calculateRotation(level, yRot));
        }
    }

    private @Nullable Pair<Holder<MapDecorationType>, Byte> playerDecorationTypeAndRotation(Holder<MapDecorationType> type, @Nullable LevelAccessor level, double yRot, float xDeltaFromCenter, float yDeltaFromCenter) {
        if (isInsideMap(xDeltaFromCenter, yDeltaFromCenter)) {
            return Pair.of(type, this.calculateRotation(level, yRot));
        } else {
            Holder<MapDecorationType> holder1 = this.decorationTypeForPlayerOutsideMap(xDeltaFromCenter, yDeltaFromCenter);

            return holder1 == null ? null : Pair.of(holder1, (byte) 0);
        }
    }

    private byte calculateRotation(@Nullable LevelAccessor level, double yRot) {
        if (this.dimension == Level.NETHER && level != null) {
            int i = (int) (level.getGameTime() / 10L);

            return (byte) (i * i * 34187121 + i * 121 >> 15 & 15);
        } else {
            double d1 = yRot < 0.0D ? yRot - 8.0D : yRot + 8.0D;

            return (byte) ((int) (d1 * 16.0D / 360.0D));
        }
    }

    private static boolean isInsideMap(float xd, float yd) {
        int i = 63;

        return xd >= -63.0F && yd >= -63.0F && xd <= 63.0F && yd <= 63.0F;
    }

    private @Nullable Holder<MapDecorationType> decorationTypeForPlayerOutsideMap(float xDeltaFromCenter, float yDeltaFromCenter) {
        int i = 320;
        boolean flag = Math.abs(xDeltaFromCenter) < 320.0F && Math.abs(yDeltaFromCenter) < 320.0F;

        return flag ? MapDecorationTypes.PLAYER_OFF_MAP : (this.unlimitedTracking ? MapDecorationTypes.PLAYER_OFF_LIMITS : null);
    }

    private static byte clampMapCoordinate(float deltaFromCenter) {
        int i = 63;

        return deltaFromCenter <= -63.0F ? Byte.MIN_VALUE : (deltaFromCenter >= 63.0F ? 127 : (byte) ((int) ((double) (deltaFromCenter * 2.0F) + 0.5D)));
    }

    public @Nullable Packet<?> getUpdatePacket(MapId id, Player player) {
        MapItemSavedData.HoldingPlayer mapitemsaveddata_holdingplayer = (MapItemSavedData.HoldingPlayer) this.carriedByPlayers.get(player);

        return mapitemsaveddata_holdingplayer == null ? null : mapitemsaveddata_holdingplayer.nextUpdatePacket(id);
    }

    public void setColorsDirty(int x, int y) {
        this.setDirty();

        for (MapItemSavedData.HoldingPlayer mapitemsaveddata_holdingplayer : this.carriedBy) {
            mapitemsaveddata_holdingplayer.markColorsDirty(x, y);
        }

    }

    public void setDecorationsDirty() {
        this.carriedBy.forEach(MapItemSavedData.HoldingPlayer::markDecorationsDirty);
    }

    public MapItemSavedData.HoldingPlayer getHoldingPlayer(Player player) {
        MapItemSavedData.HoldingPlayer mapitemsaveddata_holdingplayer = (MapItemSavedData.HoldingPlayer) this.carriedByPlayers.get(player);

        if (mapitemsaveddata_holdingplayer == null) {
            mapitemsaveddata_holdingplayer = new MapItemSavedData.HoldingPlayer(player);
            this.carriedByPlayers.put(player, mapitemsaveddata_holdingplayer);
            this.carriedBy.add(mapitemsaveddata_holdingplayer);
        }

        return mapitemsaveddata_holdingplayer;
    }

    public boolean toggleBanner(LevelAccessor level, BlockPos pos) {
        double d0 = (double) pos.getX() + 0.5D;
        double d1 = (double) pos.getZ() + 0.5D;
        int i = 1 << this.scale;
        double d2 = (d0 - (double) this.centerX) / (double) i;
        double d3 = (d1 - (double) this.centerZ) / (double) i;
        int j = 63;

        if (d2 >= -63.0D && d3 >= -63.0D && d2 <= 63.0D && d3 <= 63.0D) {
            MapBanner mapbanner = MapBanner.fromWorld(level, pos);

            if (mapbanner == null) {
                return false;
            }

            if (this.bannerMarkers.remove(mapbanner.getId(), mapbanner)) {
                this.removeDecoration(mapbanner.getId());
                this.setDirty();
                return true;
            }

            if (!this.isTrackedCountOverLimit(256)) {
                this.bannerMarkers.put(mapbanner.getId(), mapbanner);
                this.addDecoration(mapbanner.getDecoration(), level, mapbanner.getId(), d0, d1, 180.0D, (Component) mapbanner.name().orElse((Object) null));
                this.setDirty();
                return true;
            }
        }

        return false;
    }

    public void checkBanners(BlockGetter level, int x, int z) {
        Iterator<MapBanner> iterator = this.bannerMarkers.values().iterator();

        while (iterator.hasNext()) {
            MapBanner mapbanner = (MapBanner) iterator.next();

            if (mapbanner.pos().getX() == x && mapbanner.pos().getZ() == z) {
                MapBanner mapbanner1 = MapBanner.fromWorld(level, mapbanner.pos());

                if (!mapbanner.equals(mapbanner1)) {
                    iterator.remove();
                    this.removeDecoration(mapbanner.getId());
                    this.setDirty();
                }
            }
        }

    }

    public Collection<MapBanner> getBanners() {
        return this.bannerMarkers.values();
    }

    public void removedFromFrame(BlockPos pos, int entityID) {
        this.removeDecoration(getFrameKey(entityID));
        this.frameMarkers.remove(MapFrame.frameId(pos));
        this.setDirty();
    }

    public boolean updateColor(int x, int y, byte newColor) {
        byte b1 = this.colors[x + y * 128];

        if (b1 != newColor) {
            this.setColor(x, y, newColor);
            return true;
        } else {
            return false;
        }
    }

    public void setColor(int x, int y, byte newColor) {
        this.colors[x + y * 128] = newColor;
        this.setColorsDirty(x, y);
    }

    public boolean isExplorationMap() {
        for (MapDecoration mapdecoration : this.decorations.values()) {
            if (((MapDecorationType) mapdecoration.type().value()).explorationMapElement()) {
                return true;
            }
        }

        return false;
    }

    public void addClientSideDecorations(List<MapDecoration> decorations) {
        this.decorations.clear();
        this.trackedDecorationCount = 0;

        for (int i = 0; i < decorations.size(); ++i) {
            MapDecoration mapdecoration = (MapDecoration) decorations.get(i);

            this.decorations.put("icon-" + i, mapdecoration);
            if (((MapDecorationType) mapdecoration.type().value()).trackCount()) {
                ++this.trackedDecorationCount;
            }
        }

    }

    public Iterable<MapDecoration> getDecorations() {
        return this.decorations.values();
    }

    public boolean isTrackedCountOverLimit(int limit) {
        return this.trackedDecorationCount >= limit;
    }

    private static String getFrameKey(int id) {
        return "frame-" + id;
    }

    public static record MapPatch(int startX, int startY, int width, int height, byte[] mapColors) {

        public static final StreamCodec<ByteBuf, Optional<MapItemSavedData.MapPatch>> STREAM_CODEC = StreamCodec.<ByteBuf, Optional<MapItemSavedData.MapPatch>>of(MapItemSavedData.MapPatch::write, MapItemSavedData.MapPatch::read);

        private static void write(ByteBuf output, Optional<MapItemSavedData.MapPatch> optional) {
            if (optional.isPresent()) {
                MapItemSavedData.MapPatch mapitemsaveddata_mappatch = (MapItemSavedData.MapPatch) optional.get();

                output.writeByte(mapitemsaveddata_mappatch.width);
                output.writeByte(mapitemsaveddata_mappatch.height);
                output.writeByte(mapitemsaveddata_mappatch.startX);
                output.writeByte(mapitemsaveddata_mappatch.startY);
                FriendlyByteBuf.writeByteArray(output, mapitemsaveddata_mappatch.mapColors);
            } else {
                output.writeByte(0);
            }

        }

        private static Optional<MapItemSavedData.MapPatch> read(ByteBuf input) {
            int i = input.readUnsignedByte();

            if (i > 0) {
                int j = input.readUnsignedByte();
                int k = input.readUnsignedByte();
                int l = input.readUnsignedByte();
                byte[] abyte = FriendlyByteBuf.readByteArray(input);

                return Optional.of(new MapItemSavedData.MapPatch(k, l, i, j, abyte));
            } else {
                return Optional.empty();
            }
        }

        public void applyToMap(MapItemSavedData map) {
            for (int i = 0; i < this.width; ++i) {
                for (int j = 0; j < this.height; ++j) {
                    map.setColor(this.startX + i, this.startY + j, this.mapColors[i + j * this.width]);
                }
            }

        }
    }

    public class HoldingPlayer {

        public final Player player;
        private boolean dirtyData = true;
        private int minDirtyX;
        private int minDirtyY;
        private int maxDirtyX = 127;
        private int maxDirtyY = 127;
        private boolean dirtyDecorations = true;
        private int tick;
        public int step;

        private HoldingPlayer(Player player) {
            this.player = player;
        }

        private MapItemSavedData.MapPatch createPatch() {
            int i = this.minDirtyX;
            int j = this.minDirtyY;
            int k = this.maxDirtyX + 1 - this.minDirtyX;
            int l = this.maxDirtyY + 1 - this.minDirtyY;
            byte[] abyte = new byte[k * l];

            for (int i1 = 0; i1 < k; ++i1) {
                for (int j1 = 0; j1 < l; ++j1) {
                    abyte[i1 + j1 * k] = MapItemSavedData.this.colors[i + i1 + (j + j1) * 128];
                }
            }

            return new MapItemSavedData.MapPatch(i, j, k, l, abyte);
        }

        private @Nullable Packet<?> nextUpdatePacket(MapId id) {
            MapItemSavedData.MapPatch mapitemsaveddata_mappatch;

            if (this.dirtyData) {
                this.dirtyData = false;
                mapitemsaveddata_mappatch = this.createPatch();
            } else {
                mapitemsaveddata_mappatch = null;
            }

            Collection<MapDecoration> collection;

            if (this.dirtyDecorations && this.tick++ % 5 == 0) {
                this.dirtyDecorations = false;
                collection = MapItemSavedData.this.decorations.values();
            } else {
                collection = null;
            }

            return collection == null && mapitemsaveddata_mappatch == null ? null : new ClientboundMapItemDataPacket(id, MapItemSavedData.this.scale, MapItemSavedData.this.locked, collection, mapitemsaveddata_mappatch);
        }

        private void markColorsDirty(int x, int y) {
            if (this.dirtyData) {
                this.minDirtyX = Math.min(this.minDirtyX, x);
                this.minDirtyY = Math.min(this.minDirtyY, y);
                this.maxDirtyX = Math.max(this.maxDirtyX, x);
                this.maxDirtyY = Math.max(this.maxDirtyY, y);
            } else {
                this.dirtyData = true;
                this.minDirtyX = x;
                this.minDirtyY = y;
                this.maxDirtyX = x;
                this.maxDirtyY = y;
            }

        }

        private void markDecorationsDirty() {
            this.dirtyDecorations = true;
        }
    }

    private static record MapDecorationLocation(Holder<MapDecorationType> type, byte x, byte y, byte rot) {

    }
}
