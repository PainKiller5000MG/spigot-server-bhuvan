package net.minecraft.server.bossevents;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

public class CustomBossEvent extends ServerBossEvent {

    private static final int DEFAULT_MAX = 100;
    private final Identifier id;
    private final Set<UUID> players = Sets.newHashSet();
    private int value;
    private int max = 100;

    public CustomBossEvent(Identifier id, Component name) {
        super(name, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        this.id = id;
        this.setProgress(0.0F);
    }

    public Identifier getTextId() {
        return this.id;
    }

    @Override
    public void addPlayer(ServerPlayer player) {
        super.addPlayer(player);
        this.players.add(player.getUUID());
    }

    public void addOfflinePlayer(UUID player) {
        this.players.add(player);
    }

    @Override
    public void removePlayer(ServerPlayer player) {
        super.removePlayer(player);
        this.players.remove(player.getUUID());
    }

    @Override
    public void removeAllPlayers() {
        super.removeAllPlayers();
        this.players.clear();
    }

    public int getValue() {
        return this.value;
    }

    public int getMax() {
        return this.max;
    }

    public void setValue(int value) {
        this.value = value;
        this.setProgress(Mth.clamp((float) value / (float) this.max, 0.0F, 1.0F));
    }

    public void setMax(int max) {
        this.max = max;
        this.setProgress(Mth.clamp((float) this.value / (float) max, 0.0F, 1.0F));
    }

    public final Component getDisplayName() {
        return ComponentUtils.wrapInSquareBrackets(this.getName()).withStyle((style) -> {
            return style.withColor(this.getColor().getFormatting()).withHoverEvent(new HoverEvent.ShowText(Component.literal(this.getTextId().toString()))).withInsertion(this.getTextId().toString());
        });
    }

    public boolean setPlayers(Collection<ServerPlayer> players) {
        Set<UUID> set = Sets.newHashSet();
        Set<ServerPlayer> set1 = Sets.newHashSet();

        for (UUID uuid : this.players) {
            boolean flag = false;

            for (ServerPlayer serverplayer : players) {
                if (serverplayer.getUUID().equals(uuid)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                set.add(uuid);
            }
        }

        for (ServerPlayer serverplayer1 : players) {
            boolean flag1 = false;

            for (UUID uuid1 : this.players) {
                if (serverplayer1.getUUID().equals(uuid1)) {
                    flag1 = true;
                    break;
                }
            }

            if (!flag1) {
                set1.add(serverplayer1);
            }
        }

        for (UUID uuid2 : set) {
            for (ServerPlayer serverplayer2 : this.getPlayers()) {
                if (serverplayer2.getUUID().equals(uuid2)) {
                    this.removePlayer(serverplayer2);
                    break;
                }
            }

            this.players.remove(uuid2);
        }

        for (ServerPlayer serverplayer3 : set1) {
            this.addPlayer(serverplayer3);
        }

        return !set.isEmpty() || !set1.isEmpty();
    }

    public static CustomBossEvent load(Identifier id, CustomBossEvent.Packed packed) {
        CustomBossEvent custombossevent = new CustomBossEvent(id, packed.name);

        custombossevent.setVisible(packed.visible);
        custombossevent.setValue(packed.value);
        custombossevent.setMax(packed.max);
        custombossevent.setColor(packed.color);
        custombossevent.setOverlay(packed.overlay);
        custombossevent.setDarkenScreen(packed.darkenScreen);
        custombossevent.setPlayBossMusic(packed.playBossMusic);
        custombossevent.setCreateWorldFog(packed.createWorldFog);
        Set set = packed.players;

        Objects.requireNonNull(custombossevent);
        set.forEach(custombossevent::addOfflinePlayer);
        return custombossevent;
    }

    public CustomBossEvent.Packed pack() {
        return new CustomBossEvent.Packed(this.getName(), this.isVisible(), this.getValue(), this.getMax(), this.getColor(), this.getOverlay(), this.shouldDarkenScreen(), this.shouldPlayBossMusic(), this.shouldCreateWorldFog(), Set.copyOf(this.players));
    }

    public void onPlayerConnect(ServerPlayer player) {
        if (this.players.contains(player.getUUID())) {
            this.addPlayer(player);
        }

    }

    public void onPlayerDisconnect(ServerPlayer player) {
        super.removePlayer(player);
    }

    public static record Packed(Component name, boolean visible, int value, int max, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay, boolean darkenScreen, boolean playBossMusic, boolean createWorldFog, Set<UUID> players) {

        public static final Codec<CustomBossEvent.Packed> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(ComponentSerialization.CODEC.fieldOf("Name").forGetter(CustomBossEvent.Packed::name), Codec.BOOL.optionalFieldOf("Visible", false).forGetter(CustomBossEvent.Packed::visible), Codec.INT.optionalFieldOf("Value", 0).forGetter(CustomBossEvent.Packed::value), Codec.INT.optionalFieldOf("Max", 100).forGetter(CustomBossEvent.Packed::max), BossEvent.BossBarColor.CODEC.optionalFieldOf("Color", BossEvent.BossBarColor.WHITE).forGetter(CustomBossEvent.Packed::color), BossEvent.BossBarOverlay.CODEC.optionalFieldOf("Overlay", BossEvent.BossBarOverlay.PROGRESS).forGetter(CustomBossEvent.Packed::overlay), Codec.BOOL.optionalFieldOf("DarkenScreen", false).forGetter(CustomBossEvent.Packed::darkenScreen), Codec.BOOL.optionalFieldOf("PlayBossMusic", false).forGetter(CustomBossEvent.Packed::playBossMusic), Codec.BOOL.optionalFieldOf("CreateWorldFog", false).forGetter(CustomBossEvent.Packed::createWorldFog), UUIDUtil.CODEC_SET.optionalFieldOf("Players", Set.of()).forGetter(CustomBossEvent.Packed::players)).apply(instance, CustomBossEvent.Packed::new);
        });
    }
}
