package net.minecraft.world.level.block.entity.vault;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public class VaultSharedData {

    static final String TAG_NAME = "shared_data";
    static Codec<VaultSharedData> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ItemStack.lenientOptionalFieldOf("display_item").forGetter((vaultshareddata) -> {
            return vaultshareddata.displayItem;
        }), UUIDUtil.CODEC_LINKED_SET.lenientOptionalFieldOf("connected_players", Set.of()).forGetter((vaultshareddata) -> {
            return vaultshareddata.connectedPlayers;
        }), Codec.DOUBLE.lenientOptionalFieldOf("connected_particles_range", VaultConfig.DEFAULT.deactivationRange()).forGetter((vaultshareddata) -> {
            return vaultshareddata.connectedParticlesRange;
        })).apply(instance, VaultSharedData::new);
    });
    private ItemStack displayItem;
    private Set<UUID> connectedPlayers;
    private double connectedParticlesRange;
    boolean isDirty;

    VaultSharedData(ItemStack displayItem, Set<UUID> connectedPlayers, double connectedParticlesRange) {
        this.displayItem = ItemStack.EMPTY;
        this.connectedPlayers = new ObjectLinkedOpenHashSet();
        this.connectedParticlesRange = VaultConfig.DEFAULT.deactivationRange();
        this.displayItem = displayItem;
        this.connectedPlayers.addAll(connectedPlayers);
        this.connectedParticlesRange = connectedParticlesRange;
    }

    VaultSharedData() {
        this.displayItem = ItemStack.EMPTY;
        this.connectedPlayers = new ObjectLinkedOpenHashSet();
        this.connectedParticlesRange = VaultConfig.DEFAULT.deactivationRange();
    }

    public ItemStack getDisplayItem() {
        return this.displayItem;
    }

    public boolean hasDisplayItem() {
        return !this.displayItem.isEmpty();
    }

    public void setDisplayItem(ItemStack stack) {
        if (!ItemStack.matches(this.displayItem, stack)) {
            this.displayItem = stack.copy();
            this.markDirty();
        }
    }

    boolean hasConnectedPlayers() {
        return !this.connectedPlayers.isEmpty();
    }

    Set<UUID> getConnectedPlayers() {
        return this.connectedPlayers;
    }

    double connectedParticlesRange() {
        return this.connectedParticlesRange;
    }

    void updateConnectedPlayersWithinRange(ServerLevel serverLevel, BlockPos pos, VaultServerData serverData, VaultConfig config, double limit) {
        Set<UUID> set = (Set) config.playerDetector().detect(serverLevel, config.entitySelector(), pos, limit, false).stream().filter((uuid) -> {
            return !serverData.getRewardedPlayers().contains(uuid);
        }).collect(Collectors.toSet());

        if (!this.connectedPlayers.equals(set)) {
            this.connectedPlayers = set;
            this.markDirty();
        }

    }

    private void markDirty() {
        this.isDirty = true;
    }

    void set(VaultSharedData from) {
        this.displayItem = from.displayItem;
        this.connectedPlayers = from.connectedPlayers;
        this.connectedParticlesRange = from.connectedParticlesRange;
    }
}
