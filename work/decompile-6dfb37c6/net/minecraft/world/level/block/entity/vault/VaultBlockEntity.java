package net.minecraft.world.level.block.entity.vault;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class VaultBlockEntity extends BlockEntity {

    private final VaultServerData serverData = new VaultServerData();
    private final VaultSharedData sharedData = new VaultSharedData();
    private final VaultClientData clientData = new VaultClientData();
    private VaultConfig config;

    public VaultBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.VAULT, worldPosition, blockState);
        this.config = VaultConfig.DEFAULT;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return (CompoundTag) Util.make(new CompoundTag(), (compoundtag) -> {
            compoundtag.store("shared_data", VaultSharedData.CODEC, registries.createSerializationContext(NbtOps.INSTANCE), this.sharedData);
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("config", VaultConfig.CODEC, this.config);
        output.store("shared_data", VaultSharedData.CODEC, this.sharedData);
        output.store("server_data", VaultServerData.CODEC, this.serverData);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Optional optional = input.read("server_data", VaultServerData.CODEC);
        VaultServerData vaultserverdata = this.serverData;

        Objects.requireNonNull(this.serverData);
        optional.ifPresent(vaultserverdata::set);
        this.config = (VaultConfig) input.read("config", VaultConfig.CODEC).orElse(VaultConfig.DEFAULT);
        optional = input.read("shared_data", VaultSharedData.CODEC);
        VaultSharedData vaultshareddata = this.sharedData;

        Objects.requireNonNull(this.sharedData);
        optional.ifPresent(vaultshareddata::set);
    }

    public @Nullable VaultServerData getServerData() {
        return this.level != null && !this.level.isClientSide() ? this.serverData : null;
    }

    public VaultSharedData getSharedData() {
        return this.sharedData;
    }

    public VaultClientData getClientData() {
        return this.clientData;
    }

    public VaultConfig getConfig() {
        return this.config;
    }

    @VisibleForTesting
    public void setConfig(VaultConfig config) {
        this.config = config;
    }

    public static final class Server {

        private static final int UNLOCKING_DELAY_TICKS = 14;
        private static final int DISPLAY_CYCLE_TICK_RATE = 20;
        private static final int INSERT_FAIL_SOUND_BUFFER_TICKS = 15;

        public Server() {}

        public static void tick(ServerLevel serverLevel, BlockPos pos, BlockState blockState, VaultConfig config, VaultServerData serverData, VaultSharedData sharedData) {
            VaultState vaultstate = (VaultState) blockState.getValue(VaultBlock.STATE);

            if (shouldCycleDisplayItem(serverLevel.getGameTime(), vaultstate)) {
                cycleDisplayItemFromLootTable(serverLevel, vaultstate, config, sharedData, pos);
            }

            BlockState blockstate1 = blockState;

            if (serverLevel.getGameTime() >= serverData.stateUpdatingResumesAt()) {
                blockstate1 = (BlockState) blockState.setValue(VaultBlock.STATE, vaultstate.tickAndGetNext(serverLevel, pos, config, serverData, sharedData));
                if (blockState != blockstate1) {
                    setVaultState(serverLevel, pos, blockState, blockstate1, config, sharedData);
                }
            }

            if (serverData.isDirty || sharedData.isDirty) {
                VaultBlockEntity.setChanged(serverLevel, pos, blockState);
                if (sharedData.isDirty) {
                    serverLevel.sendBlockUpdated(pos, blockState, blockstate1, 2);
                }

                serverData.isDirty = false;
                sharedData.isDirty = false;
            }

        }

        public static void tryInsertKey(ServerLevel serverLevel, BlockPos pos, BlockState blockState, VaultConfig config, VaultServerData serverData, VaultSharedData sharedData, Player player, ItemStack stackToInsert) {
            VaultState vaultstate = (VaultState) blockState.getValue(VaultBlock.STATE);

            if (canEjectReward(config, vaultstate)) {
                if (!isValidToInsert(config, stackToInsert)) {
                    playInsertFailSound(serverLevel, serverData, pos, SoundEvents.VAULT_INSERT_ITEM_FAIL);
                } else if (serverData.hasRewardedPlayer(player)) {
                    playInsertFailSound(serverLevel, serverData, pos, SoundEvents.VAULT_REJECT_REWARDED_PLAYER);
                } else {
                    List<ItemStack> list = resolveItemsToEject(serverLevel, config, pos, player, stackToInsert);

                    if (!list.isEmpty()) {
                        player.awardStat(Stats.ITEM_USED.get(stackToInsert.getItem()));
                        stackToInsert.consume(config.keyItem().getCount(), player);
                        unlock(serverLevel, blockState, pos, config, serverData, sharedData, list);
                        serverData.addToRewardedPlayers(player);
                        sharedData.updateConnectedPlayersWithinRange(serverLevel, pos, serverData, config, config.deactivationRange());
                    }
                }
            }
        }

        static void setVaultState(ServerLevel serverLevel, BlockPos pos, BlockState currentBlockState, BlockState newBlockState, VaultConfig config, VaultSharedData sharedData) {
            VaultState vaultstate = (VaultState) currentBlockState.getValue(VaultBlock.STATE);
            VaultState vaultstate1 = (VaultState) newBlockState.getValue(VaultBlock.STATE);

            serverLevel.setBlock(pos, newBlockState, 3);
            vaultstate.onTransition(serverLevel, pos, vaultstate1, config, sharedData, (Boolean) newBlockState.getValue(VaultBlock.OMINOUS));
        }

        static void cycleDisplayItemFromLootTable(ServerLevel serverLevel, VaultState vaultState, VaultConfig config, VaultSharedData sharedData, BlockPos pos) {
            if (!canEjectReward(config, vaultState)) {
                sharedData.setDisplayItem(ItemStack.EMPTY);
            } else {
                ItemStack itemstack = getRandomDisplayItemFromLootTable(serverLevel, pos, (ResourceKey) config.overrideLootTableToDisplay().orElse(config.lootTable()));

                sharedData.setDisplayItem(itemstack);
            }
        }

        private static ItemStack getRandomDisplayItemFromLootTable(ServerLevel serverLevel, BlockPos pos, ResourceKey<LootTable> lootTableId) {
            LootTable loottable = serverLevel.getServer().reloadableRegistries().getLootTable(lootTableId);
            LootParams lootparams = (new LootParams.Builder(serverLevel)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).create(LootContextParamSets.VAULT);
            List<ItemStack> list = loottable.getRandomItems(lootparams, serverLevel.getRandom());

            return list.isEmpty() ? ItemStack.EMPTY : (ItemStack) Util.getRandom(list, serverLevel.getRandom());
        }

        private static void unlock(ServerLevel serverLevel, BlockState blockState, BlockPos pos, VaultConfig config, VaultServerData serverData, VaultSharedData sharedData, List<ItemStack> itemsToEject) {
            serverData.setItemsToEject(itemsToEject);
            sharedData.setDisplayItem(serverData.getNextItemToEject());
            serverData.pauseStateUpdatingUntil(serverLevel.getGameTime() + 14L);
            setVaultState(serverLevel, pos, blockState, (BlockState) blockState.setValue(VaultBlock.STATE, VaultState.UNLOCKING), config, sharedData);
        }

        private static List<ItemStack> resolveItemsToEject(ServerLevel serverLevel, VaultConfig config, BlockPos pos, Player player, ItemStack insertedStack) {
            LootTable loottable = serverLevel.getServer().reloadableRegistries().getLootTable(config.lootTable());
            LootParams lootparams = (new LootParams.Builder(serverLevel)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player).withParameter(LootContextParams.TOOL, insertedStack).create(LootContextParamSets.VAULT);

            return loottable.getRandomItems(lootparams);
        }

        private static boolean canEjectReward(VaultConfig config, VaultState vaultState) {
            return !config.keyItem().isEmpty() && vaultState != VaultState.INACTIVE;
        }

        private static boolean isValidToInsert(VaultConfig config, ItemStack stackToInsert) {
            return ItemStack.isSameItemSameComponents(stackToInsert, config.keyItem()) && stackToInsert.getCount() >= config.keyItem().getCount();
        }

        private static boolean shouldCycleDisplayItem(long gameTime, VaultState vaultState) {
            return gameTime % 20L == 0L && vaultState == VaultState.ACTIVE;
        }

        private static void playInsertFailSound(ServerLevel serverLevel, VaultServerData serverData, BlockPos pos, SoundEvent sound) {
            if (serverLevel.getGameTime() >= serverData.getLastInsertFailTimestamp() + 15L) {
                serverLevel.playSound((Entity) null, pos, sound, SoundSource.BLOCKS);
                serverData.setLastInsertFailTimestamp(serverLevel.getGameTime());
            }

        }
    }

    public static final class Client {

        private static final int PARTICLE_TICK_RATE = 20;
        private static final float IDLE_PARTICLE_CHANCE = 0.5F;
        private static final float AMBIENT_SOUND_CHANCE = 0.02F;
        private static final int ACTIVATION_PARTICLE_COUNT = 20;
        private static final int DEACTIVATION_PARTICLE_COUNT = 20;

        public Client() {}

        public static void tick(Level clientLevel, BlockPos pos, BlockState blockState, VaultClientData clientData, VaultSharedData sharedData) {
            clientData.updateDisplayItemSpin();
            if (clientLevel.getGameTime() % 20L == 0L) {
                emitConnectionParticlesForNearbyPlayers(clientLevel, pos, blockState, sharedData);
            }

            emitIdleParticles(clientLevel, pos, sharedData, (Boolean) blockState.getValue(VaultBlock.OMINOUS) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME);
            playIdleSounds(clientLevel, pos, sharedData);
        }

        public static void emitActivationParticles(Level clientLevel, BlockPos pos, BlockState blockState, VaultSharedData sharedData, ParticleOptions flameParticle) {
            emitConnectionParticlesForNearbyPlayers(clientLevel, pos, blockState, sharedData);
            RandomSource randomsource = clientLevel.random;

            for (int i = 0; i < 20; ++i) {
                Vec3 vec3 = randomPosInsideCage(pos, randomsource);

                clientLevel.addParticle(ParticleTypes.SMOKE, vec3.x(), vec3.y(), vec3.z(), 0.0D, 0.0D, 0.0D);
                clientLevel.addParticle(flameParticle, vec3.x(), vec3.y(), vec3.z(), 0.0D, 0.0D, 0.0D);
            }

        }

        public static void emitDeactivationParticles(Level clientLevel, BlockPos pos, ParticleOptions flameParticle) {
            RandomSource randomsource = clientLevel.random;

            for (int i = 0; i < 20; ++i) {
                Vec3 vec3 = randomPosCenterOfCage(pos, randomsource);
                Vec3 vec31 = new Vec3(randomsource.nextGaussian() * 0.02D, randomsource.nextGaussian() * 0.02D, randomsource.nextGaussian() * 0.02D);

                clientLevel.addParticle(flameParticle, vec3.x(), vec3.y(), vec3.z(), vec31.x(), vec31.y(), vec31.z());
            }

        }

        private static void emitIdleParticles(Level clientLevel, BlockPos pos, VaultSharedData sharedData, ParticleOptions flameParticle) {
            RandomSource randomsource = clientLevel.getRandom();

            if (randomsource.nextFloat() <= 0.5F) {
                Vec3 vec3 = randomPosInsideCage(pos, randomsource);

                clientLevel.addParticle(ParticleTypes.SMOKE, vec3.x(), vec3.y(), vec3.z(), 0.0D, 0.0D, 0.0D);
                if (shouldDisplayActiveEffects(sharedData)) {
                    clientLevel.addParticle(flameParticle, vec3.x(), vec3.y(), vec3.z(), 0.0D, 0.0D, 0.0D);
                }
            }

        }

        private static void emitConnectionParticlesForPlayer(Level level, Vec3 flyTowards, Player player) {
            RandomSource randomsource = level.random;
            Vec3 vec31 = flyTowards.vectorTo(player.position().add(0.0D, (double) (player.getBbHeight() / 2.0F), 0.0D));
            int i = Mth.nextInt(randomsource, 2, 5);

            for (int j = 0; j < i; ++j) {
                Vec3 vec32 = vec31.offsetRandom(randomsource, 1.0F);

                level.addParticle(ParticleTypes.VAULT_CONNECTION, flyTowards.x(), flyTowards.y(), flyTowards.z(), vec32.x(), vec32.y(), vec32.z());
            }

        }

        private static void emitConnectionParticlesForNearbyPlayers(Level level, BlockPos pos, BlockState blockState, VaultSharedData sharedData) {
            Set<UUID> set = sharedData.getConnectedPlayers();

            if (!set.isEmpty()) {
                Vec3 vec3 = keyholePos(pos, (Direction) blockState.getValue(VaultBlock.FACING));

                for (UUID uuid : set) {
                    Player player = level.getPlayerByUUID(uuid);

                    if (player != null && isWithinConnectionRange(pos, sharedData, player)) {
                        emitConnectionParticlesForPlayer(level, vec3, player);
                    }
                }

            }
        }

        private static boolean isWithinConnectionRange(BlockPos vaultPos, VaultSharedData sharedData, Player player) {
            return player.blockPosition().distSqr(vaultPos) <= Mth.square(sharedData.connectedParticlesRange());
        }

        private static void playIdleSounds(Level clientLevel, BlockPos pos, VaultSharedData sharedData) {
            if (shouldDisplayActiveEffects(sharedData)) {
                RandomSource randomsource = clientLevel.getRandom();

                if (randomsource.nextFloat() <= 0.02F) {
                    clientLevel.playLocalSound(pos, SoundEvents.VAULT_AMBIENT, SoundSource.BLOCKS, randomsource.nextFloat() * 0.25F + 0.75F, randomsource.nextFloat() + 0.5F, false);
                }

            }
        }

        public static boolean shouldDisplayActiveEffects(VaultSharedData sharedData) {
            return sharedData.hasDisplayItem();
        }

        private static Vec3 randomPosCenterOfCage(BlockPos blockPos, RandomSource random) {
            return Vec3.atLowerCornerOf(blockPos).add(Mth.nextDouble(random, 0.4D, 0.6D), Mth.nextDouble(random, 0.4D, 0.6D), Mth.nextDouble(random, 0.4D, 0.6D));
        }

        private static Vec3 randomPosInsideCage(BlockPos blockPos, RandomSource random) {
            return Vec3.atLowerCornerOf(blockPos).add(Mth.nextDouble(random, 0.1D, 0.9D), Mth.nextDouble(random, 0.25D, 0.75D), Mth.nextDouble(random, 0.1D, 0.9D));
        }

        private static Vec3 keyholePos(BlockPos blockPos, Direction blockFacing) {
            return Vec3.atBottomCenterOf(blockPos).add((double) blockFacing.getStepX() * 0.5D, 1.75D, (double) blockFacing.getStepZ() * 0.5D);
        }
    }
}
