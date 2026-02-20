package net.minecraft.gametest.framework;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class GameTestHelper {

    private final GameTestInfo testInfo;
    private boolean finalCheckAdded;

    public GameTestHelper(GameTestInfo testInfo) {
        this.testInfo = testInfo;
    }

    public GameTestAssertException assertionException(Component description) {
        return new GameTestAssertException(description, this.testInfo.getTick());
    }

    public GameTestAssertException assertionException(String descriptionId, Object... arguments) {
        return this.assertionException(Component.translatableEscape(descriptionId, arguments));
    }

    public GameTestAssertPosException assertionException(BlockPos pos, Component description) {
        return new GameTestAssertPosException(description, this.absolutePos(pos), pos, this.testInfo.getTick());
    }

    public GameTestAssertPosException assertionException(BlockPos pos, String descriptionId, Object... arguments) {
        return this.assertionException(pos, (Component) Component.translatableEscape(descriptionId, arguments));
    }

    public ServerLevel getLevel() {
        return this.testInfo.getLevel();
    }

    public BlockState getBlockState(BlockPos pos) {
        return this.getLevel().getBlockState(this.absolutePos(pos));
    }

    public <T extends BlockEntity> T getBlockEntity(BlockPos pos, Class<T> type) {
        BlockEntity blockentity = this.getLevel().getBlockEntity(this.absolutePos(pos));

        if (blockentity == null) {
            throw this.assertionException(pos, "test.error.missing_block_entity");
        } else if (type.isInstance(blockentity)) {
            return (T) (type.cast(blockentity));
        } else {
            throw this.assertionException(pos, "test.error.wrong_block_entity", blockentity.getType().builtInRegistryHolder().getRegisteredName());
        }
    }

    public void killAllEntities() {
        this.killAllEntitiesOfClass(Entity.class);
    }

    public void killAllEntitiesOfClass(Class<? extends Entity> baseClass) {
        AABB aabb = this.getBounds();
        List<? extends Entity> list = this.getLevel().<Entity>getEntitiesOfClass(baseClass, aabb.inflate(1.0D), (entity) -> {
            return !(entity instanceof Player);
        });

        list.forEach((entity) -> {
            entity.kill(this.getLevel());
        });
    }

    public ItemEntity spawnItem(Item item, Vec3 pos) {
        ServerLevel serverlevel = this.getLevel();
        Vec3 vec31 = this.absoluteVec(pos);
        ItemEntity itementity = new ItemEntity(serverlevel, vec31.x, vec31.y, vec31.z, new ItemStack(item, 1));

        itementity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        serverlevel.addFreshEntity(itementity);
        return itementity;
    }

    public ItemEntity spawnItem(Item item, float x, float y, float z) {
        return this.spawnItem(item, new Vec3((double) x, (double) y, (double) z));
    }

    public ItemEntity spawnItem(Item item, BlockPos pos) {
        return this.spawnItem(item, (float) pos.getX(), (float) pos.getY(), (float) pos.getZ());
    }

    public <E extends Entity> E spawn(EntityType<E> entityType, BlockPos pos) {
        return (E) this.spawn(entityType, Vec3.atBottomCenterOf(pos));
    }

    public <E extends Entity> List<E> spawn(EntityType<E> entityType, BlockPos pos, int amount) {
        return this.spawn(entityType, Vec3.atBottomCenterOf(pos), amount);
    }

    public <E extends Entity> List<E> spawn(EntityType<E> entityType, Vec3 pos, int amount) {
        List<E> list = new ArrayList();

        for (int j = 0; j < amount; ++j) {
            list.add(this.spawn(entityType, pos));
        }

        return list;
    }

    public <E extends Entity> E spawn(EntityType<E> entityType, Vec3 pos) {
        return (E) this.spawn(entityType, pos, (EntitySpawnReason) null);
    }

    public <E extends Entity> E spawn(EntityType<E> entityType, Vec3 pos, @Nullable EntitySpawnReason spawnReason) {
        ServerLevel serverlevel = this.getLevel();
        E e0 = entityType.create(serverlevel, EntitySpawnReason.STRUCTURE);

        if (e0 == null) {
            throw this.assertionException(BlockPos.containing(pos), "test.error.spawn_failure", entityType.builtInRegistryHolder().getRegisteredName());
        } else {
            if (e0 instanceof Mob) {
                Mob mob = (Mob) e0;

                mob.setPersistenceRequired();
            }

            Vec3 vec31 = this.absoluteVec(pos);
            float f = e0.rotate(this.getTestRotation());

            e0.snapTo(vec31.x, vec31.y, vec31.z, f, e0.getXRot());
            e0.setYBodyRot(f);
            e0.setYHeadRot(f);
            if (spawnReason != null && e0 instanceof Mob) {
                Mob mob1 = (Mob) e0;

                mob1.finalizeSpawn(this.getLevel(), this.getLevel().getCurrentDifficultyAt(mob1.blockPosition()), spawnReason, (SpawnGroupData) null);
            }

            serverlevel.addFreshEntityWithPassengers(e0);
            return e0;
        }
    }

    public <E extends Mob> E spawn(EntityType<E> entityType, int x, int y, int z, EntitySpawnReason entitySpawnReason) {
        return (E) (this.spawn(entityType, new Vec3((double) x, (double) y, (double) z), entitySpawnReason));
    }

    public void hurt(Entity entity, DamageSource source, float damage) {
        entity.hurtServer(this.getLevel(), source, damage);
    }

    public void kill(Entity entity) {
        entity.kill(this.getLevel());
    }

    public <E extends Entity> E findOneEntity(EntityType<E> entityType) {
        return (E) this.findClosestEntity(entityType, 0, 0, 0, (double) Integer.MAX_VALUE);
    }

    public <E extends Entity> E findClosestEntity(EntityType<E> entityType, int x, int y, int z, double distance) {
        List<E> list = this.<E>findEntities(entityType, x, y, z, distance);

        if (list.isEmpty()) {
            throw this.assertionException("test.error.expected_entity_around", entityType.getDescription(), x, y, z);
        } else if (list.size() > 1) {
            throw this.assertionException("test.error.too_many_entities", entityType.toShortString(), x, y, z, list.size());
        } else {
            Vec3 vec3 = this.absoluteVec(new Vec3((double) x, (double) y, (double) z));

            list.sort((entity, entity1) -> {
                double d1 = entity.position().distanceTo(vec3);
                double d2 = entity1.position().distanceTo(vec3);

                return Double.compare(d1, d2);
            });
            return (E) (list.get(0));
        }
    }

    public <E extends Entity> List<E> findEntities(EntityType<E> entityType, int x, int y, int z, double distance) {
        return this.<E>findEntities(entityType, Vec3.atBottomCenterOf(new BlockPos(x, y, z)), distance);
    }

    public <E extends Entity> List<E> findEntities(EntityType<E> entityType, Vec3 pos, double distance) {
        ServerLevel serverlevel = this.getLevel();
        Vec3 vec31 = this.absoluteVec(pos);
        AABB aabb = this.testInfo.getStructureBounds();
        AABB aabb1 = new AABB(vec31.add(-distance, -distance, -distance), vec31.add(distance, distance, distance));

        return serverlevel.getEntities(entityType, aabb, (entity) -> {
            return entity.getBoundingBox().intersects(aabb1) && entity.isAlive();
        });
    }

    public <E extends Entity> E spawn(EntityType<E> entityType, int x, int y, int z) {
        return (E) this.spawn(entityType, new BlockPos(x, y, z));
    }

    public <E extends Entity> E spawn(EntityType<E> entityType, float x, float y, float z) {
        return (E) this.spawn(entityType, new Vec3((double) x, (double) y, (double) z));
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> entityType, BlockPos pos) {
        E e0 = (E) (this.spawn(entityType, pos));

        e0.removeFreeWill();
        return e0;
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> entityType, int x, int y, int z) {
        return (E) this.spawnWithNoFreeWill(entityType, new BlockPos(x, y, z));
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> entityType, Vec3 pos) {
        E e0 = (E) (this.spawn(entityType, pos));

        e0.removeFreeWill();
        return e0;
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> entityType, float x, float y, float z) {
        return (E) this.spawnWithNoFreeWill(entityType, new Vec3((double) x, (double) y, (double) z));
    }

    public void moveTo(Mob mob, float x, float y, float z) {
        Vec3 vec3 = this.absoluteVec(new Vec3((double) x, (double) y, (double) z));

        mob.snapTo(vec3.x, vec3.y, vec3.z, mob.getYRot(), mob.getXRot());
    }

    public GameTestSequence walkTo(Mob mob, BlockPos targetPos, float speedModifier) {
        return this.startSequence().thenExecuteAfter(2, () -> {
            Path path = mob.getNavigation().createPath(this.absolutePos(targetPos), 0);

            mob.getNavigation().moveTo(path, (double) speedModifier);
        });
    }

    public void pressButton(int x, int y, int z) {
        this.pressButton(new BlockPos(x, y, z));
    }

    public void pressButton(BlockPos buttonPos) {
        this.assertBlockTag(BlockTags.BUTTONS, buttonPos);
        BlockPos blockpos1 = this.absolutePos(buttonPos);
        BlockState blockstate = this.getLevel().getBlockState(blockpos1);
        ButtonBlock buttonblock = (ButtonBlock) blockstate.getBlock();

        buttonblock.press(blockstate, this.getLevel(), blockpos1, (Player) null);
    }

    public void useBlock(BlockPos relativePos) {
        this.useBlock(relativePos, this.makeMockPlayer(GameType.CREATIVE));
    }

    public void useBlock(BlockPos relativePos, Player player) {
        BlockPos blockpos1 = this.absolutePos(relativePos);

        this.useBlock(relativePos, player, new BlockHitResult(Vec3.atCenterOf(blockpos1), Direction.NORTH, blockpos1, true));
    }

    public void useBlock(BlockPos relativePos, Player player, BlockHitResult hitResult) {
        BlockPos blockpos1 = this.absolutePos(relativePos);
        BlockState blockstate = this.getLevel().getBlockState(blockpos1);
        InteractionHand interactionhand = InteractionHand.MAIN_HAND;
        InteractionResult interactionresult = blockstate.useItemOn(player.getItemInHand(interactionhand), this.getLevel(), player, interactionhand, hitResult);

        if (!interactionresult.consumesAction()) {
            if (!(interactionresult instanceof InteractionResult.TryEmptyHandInteraction) || !blockstate.useWithoutItem(this.getLevel(), player, hitResult).consumesAction()) {
                UseOnContext useoncontext = new UseOnContext(player, interactionhand, hitResult);

                player.getItemInHand(interactionhand).useOn(useoncontext);
            }
        }
    }

    public LivingEntity makeAboutToDrown(LivingEntity entity) {
        entity.setAirSupply(0);
        entity.setHealth(0.25F);
        return entity;
    }

    public LivingEntity withLowHealth(LivingEntity entity) {
        entity.setHealth(0.25F);
        return entity;
    }

    public Player makeMockPlayer(final GameType gameType) {
        return new Player(this.getLevel(), new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public GameType gameMode() {
                return gameType;
            }

            @Override
            public boolean isClientAuthoritative() {
                return false;
            }
        };
    }

    /** @deprecated */
    @Deprecated(forRemoval = true)
    public ServerPlayer makeMockServerPlayerInLevel() {
        CommonListenerCookie commonlistenercookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer serverplayer = new ServerPlayer(this.getLevel().getServer(), this.getLevel(), commonlistenercookie.gameProfile(), commonlistenercookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);

        new EmbeddedChannel(new ChannelHandler[]{connection});
        this.getLevel().getServer().getPlayerList().placeNewPlayer(connection, serverplayer, commonlistenercookie);
        return serverplayer;
    }

    public void pullLever(int x, int y, int z) {
        this.pullLever(new BlockPos(x, y, z));
    }

    public void pullLever(BlockPos leverPos) {
        this.assertBlockPresent(Blocks.LEVER, leverPos);
        BlockPos blockpos1 = this.absolutePos(leverPos);
        BlockState blockstate = this.getLevel().getBlockState(blockpos1);
        LeverBlock leverblock = (LeverBlock) blockstate.getBlock();

        leverblock.pull(blockstate, this.getLevel(), blockpos1, (Player) null);
    }

    public void pulseRedstone(BlockPos pos, long duration) {
        this.setBlock(pos, Blocks.REDSTONE_BLOCK);
        this.runAfterDelay(duration, () -> {
            this.setBlock(pos, Blocks.AIR);
        });
    }

    public void destroyBlock(BlockPos pos) {
        this.getLevel().destroyBlock(this.absolutePos(pos), false, (Entity) null);
    }

    public void setBlock(int x, int y, int z, Block block) {
        this.setBlock(new BlockPos(x, y, z), block);
    }

    public void setBlock(int x, int y, int z, BlockState state) {
        this.setBlock(new BlockPos(x, y, z), state);
    }

    public void setBlock(BlockPos blockPos, Block block) {
        this.setBlock(blockPos, block.defaultBlockState());
    }

    public void setBlock(BlockPos blockPos, BlockState state) {
        this.getLevel().setBlock(this.absolutePos(blockPos), state, 3);
    }

    public void setBlock(BlockPos blockPos, Block block, Direction direction) {
        this.setBlock(blockPos, block.defaultBlockState(), direction);
    }

    public void setBlock(BlockPos blockPos, BlockState blockState, Direction direction) {
        BlockState blockstate1 = blockState;

        if (blockState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            blockstate1 = (BlockState) blockState.setValue(HorizontalDirectionalBlock.FACING, direction);
        }

        if (blockState.hasProperty(BlockStateProperties.FACING)) {
            blockstate1 = (BlockState) blockState.setValue(BlockStateProperties.FACING, direction);
        }

        this.getLevel().setBlock(this.absolutePos(blockPos), blockstate1, 3);
    }

    public void assertBlockPresent(Block blockType, int x, int y, int z) {
        this.assertBlockPresent(blockType, new BlockPos(x, y, z));
    }

    public void assertBlockPresent(Block blockType, BlockPos pos) {
        BlockState blockstate = this.getBlockState(pos);

        this.assertBlock(pos, (block1) -> {
            return blockstate.is(blockType);
        }, (block1) -> {
            return Component.translatable("test.error.expected_block", blockType.getName(), block1.getName());
        });
    }

    public void assertBlockNotPresent(Block blockType, int x, int y, int z) {
        this.assertBlockNotPresent(blockType, new BlockPos(x, y, z));
    }

    public void assertBlockNotPresent(Block blockType, BlockPos pos) {
        this.assertBlock(pos, (block1) -> {
            return !this.getBlockState(pos).is(blockType);
        }, (block1) -> {
            return Component.translatable("test.error.unexpected_block", blockType.getName());
        });
    }

    public void assertBlockTag(TagKey<Block> tag, BlockPos pos) {
        this.assertBlockState(pos, (blockstate) -> {
            return blockstate.is(tag);
        }, (blockstate) -> {
            return Component.translatable("test.error.expected_block_tag", Component.translationArg(tag.location()), blockstate.getBlock().getName());
        });
    }

    public void succeedWhenBlockPresent(Block block, int x, int y, int z) {
        this.succeedWhenBlockPresent(block, new BlockPos(x, y, z));
    }

    public void succeedWhenBlockPresent(Block block, BlockPos pos) {
        this.succeedWhen(() -> {
            this.assertBlockPresent(block, pos);
        });
    }

    public void assertBlock(BlockPos pos, Predicate<Block> predicate, Function<Block, Component> errorMessage) {
        this.assertBlockState(pos, (blockstate) -> {
            return predicate.test(blockstate.getBlock());
        }, (blockstate) -> {
            return (Component) errorMessage.apply(blockstate.getBlock());
        });
    }

    public <T extends Comparable<T>> void assertBlockProperty(BlockPos pos, Property<T> property, T value) {
        BlockState blockstate = this.getBlockState(pos);
        boolean flag = blockstate.hasProperty(property);

        if (!flag) {
            throw this.assertionException(pos, "test.error.block_property_missing", property.getName(), value);
        } else if (!blockstate.getValue(property).equals(value)) {
            throw this.assertionException(pos, "test.error.block_property_mismatch", property.getName(), value, blockstate.getValue(property));
        }
    }

    public <T extends Comparable<T>> void assertBlockProperty(BlockPos pos, Property<T> property, Predicate<T> predicate, Component errorMessage) {
        this.assertBlockState(pos, (blockstate) -> {
            if (!blockstate.hasProperty(property)) {
                return false;
            } else {
                T t0 = (T) blockstate.getValue(property);

                return predicate.test(t0);
            }
        }, (blockstate) -> {
            return errorMessage;
        });
    }

    public void assertBlockState(BlockPos pos, BlockState expected) {
        BlockState blockstate1 = this.getBlockState(pos);

        if (!blockstate1.equals(expected)) {
            throw this.assertionException(pos, "test.error.state_not_equal", expected, blockstate1);
        }
    }

    public void assertBlockState(BlockPos pos, Predicate<BlockState> predicate, Function<BlockState, Component> errorMessage) {
        BlockState blockstate = this.getBlockState(pos);

        if (!predicate.test(blockstate)) {
            throw this.assertionException(pos, (Component) errorMessage.apply(blockstate));
        }
    }

    public <T extends BlockEntity> void assertBlockEntityData(BlockPos pos, Class<T> type, Predicate<T> predicate, Supplier<Component> errorMessage) {
        T t0 = this.getBlockEntity(pos, type);

        if (!predicate.test(t0)) {
            throw this.assertionException(pos, (Component) errorMessage.get());
        }
    }

    public void assertRedstoneSignal(BlockPos pos, Direction direction, IntPredicate levelPredicate, Supplier<Component> errorMessage) {
        BlockPos blockpos1 = this.absolutePos(pos);
        ServerLevel serverlevel = this.getLevel();
        BlockState blockstate = serverlevel.getBlockState(blockpos1);
        int i = blockstate.getSignal(serverlevel, blockpos1, direction);

        if (!levelPredicate.test(i)) {
            throw this.assertionException(pos, (Component) errorMessage.get());
        }
    }

    public void assertEntityPresent(EntityType<?> entityType) {
        if (!this.getLevel().hasEntities(entityType, this.getBounds(), Entity::isAlive)) {
            throw this.assertionException("test.error.expected_entity_in_test", entityType.getDescription());
        }
    }

    public void assertEntityPresent(EntityType<?> entityType, int x, int y, int z) {
        this.assertEntityPresent(entityType, new BlockPos(x, y, z));
    }

    public void assertEntityPresent(EntityType<?> entityType, BlockPos pos) {
        BlockPos blockpos1 = this.absolutePos(pos);

        if (!this.getLevel().hasEntities(entityType, new AABB(blockpos1), Entity::isAlive)) {
            throw this.assertionException(pos, "test.error.expected_entity", entityType.getDescription());
        }
    }

    public void assertEntityPresent(EntityType<?> entityType, AABB relativeAABB) {
        AABB aabb1 = this.absoluteAABB(relativeAABB);

        if (!this.getLevel().hasEntities(entityType, aabb1, Entity::isAlive)) {
            throw this.assertionException(BlockPos.containing(relativeAABB.getCenter()), "test.error.expected_entity", entityType.getDescription());
        }
    }

    public void assertEntityPresent(EntityType<?> entityType, AABB relativeAABB, Component message) {
        AABB aabb1 = this.absoluteAABB(relativeAABB);

        if (!this.getLevel().hasEntities(entityType, aabb1, Entity::isAlive)) {
            throw this.assertionException(BlockPos.containing(relativeAABB.getCenter()), message);
        }
    }

    public void assertEntitiesPresent(EntityType<?> entityType, int expectedEntities) {
        List<? extends Entity> list = this.getLevel().getEntities(entityType, this.getBounds(), Entity::isAlive);

        if (list.size() != expectedEntities) {
            throw this.assertionException("test.error.expected_entity_count", expectedEntities, entityType.getDescription(), list.size());
        }
    }

    public void assertEntitiesPresent(EntityType<?> entityType, BlockPos pos, int numOfExpectedEntities, double distance) {
        this.absolutePos(pos);
        List<? extends Entity> list = this.<Entity>getEntities(entityType, pos, distance);

        if (list.size() != numOfExpectedEntities) {
            throw this.assertionException(pos, "test.error.expected_entity_count", numOfExpectedEntities, entityType.getDescription(), list.size());
        }
    }

    public void assertEntityPresent(EntityType<?> entityType, BlockPos pos, double distance) {
        List<? extends Entity> list = this.<Entity>getEntities(entityType, pos, distance);

        if (list.isEmpty()) {
            this.absolutePos(pos);
            throw this.assertionException(pos, "test.error.expected_entity", entityType.getDescription());
        }
    }

    public <T extends Entity> List<T> getEntities(EntityType<T> entityType, BlockPos pos, double distance) {
        BlockPos blockpos1 = this.absolutePos(pos);

        return this.getLevel().getEntities(entityType, (new AABB(blockpos1)).inflate(distance), Entity::isAlive);
    }

    public <T extends Entity> List<T> getEntities(EntityType<T> entityType) {
        return this.getLevel().getEntities(entityType, this.getBounds(), Entity::isAlive);
    }

    public void assertEntityInstancePresent(Entity entity, int x, int y, int z) {
        this.assertEntityInstancePresent(entity, new BlockPos(x, y, z));
    }

    public void assertEntityInstancePresent(Entity entity, BlockPos pos) {
        BlockPos blockpos1 = this.absolutePos(pos);
        List<? extends Entity> list = this.getLevel().getEntities(entity.getType(), new AABB(blockpos1), Entity::isAlive);

        list.stream().filter((entity1) -> {
            return entity1 == entity;
        }).findFirst().orElseThrow(() -> {
            return this.assertionException(pos, "test.error.expected_entity", entity.getType().getDescription());
        });
    }

    public void assertItemEntityCountIs(Item itemType, BlockPos pos, double distance, int count) {
        BlockPos blockpos1 = this.absolutePos(pos);
        List<ItemEntity> list = this.getLevel().getEntities(EntityType.ITEM, (new AABB(blockpos1)).inflate(distance), Entity::isAlive);
        int j = 0;

        for (ItemEntity itementity : list) {
            ItemStack itemstack = itementity.getItem();

            if (itemstack.is(itemType)) {
                j += itemstack.getCount();
            }
        }

        if (j != count) {
            throw this.assertionException(pos, "test.error.expected_items_count", count, itemType.getName(), j);
        }
    }

    public void assertItemEntityPresent(Item itemType, BlockPos pos, double distance) {
        BlockPos blockpos1 = this.absolutePos(pos);
        Predicate<ItemEntity> predicate = (itementity) -> {
            return itementity.isAlive() && itementity.getItem().is(itemType);
        };

        if (!this.getLevel().hasEntities(EntityType.ITEM, (new AABB(blockpos1)).inflate(distance), predicate)) {
            throw this.assertionException(pos, "test.error.expected_item", itemType.getName());
        }
    }

    public void assertItemEntityNotPresent(Item itemType, BlockPos pos, double distance) {
        BlockPos blockpos1 = this.absolutePos(pos);
        Predicate<ItemEntity> predicate = (itementity) -> {
            return itementity.isAlive() && itementity.getItem().is(itemType);
        };

        if (this.getLevel().hasEntities(EntityType.ITEM, (new AABB(blockpos1)).inflate(distance), predicate)) {
            throw this.assertionException(pos, "test.error.unexpected_item", itemType.getName());
        }
    }

    public void assertItemEntityPresent(Item itemType) {
        Predicate<ItemEntity> predicate = (itementity) -> {
            return itementity.isAlive() && itementity.getItem().is(itemType);
        };

        if (!this.getLevel().hasEntities(EntityType.ITEM, this.getBounds(), predicate)) {
            throw this.assertionException("test.error.expected_item", itemType.getName());
        }
    }

    public void assertItemEntityNotPresent(Item itemType) {
        Predicate<ItemEntity> predicate = (itementity) -> {
            return itementity.isAlive() && itementity.getItem().is(itemType);
        };

        if (this.getLevel().hasEntities(EntityType.ITEM, this.getBounds(), predicate)) {
            throw this.assertionException("test.error.unexpected_item", itemType.getName());
        }
    }

    public void assertEntityNotPresent(EntityType<?> entityType) {
        List<? extends Entity> list = this.getLevel().getEntities(entityType, this.getBounds(), Entity::isAlive);

        if (!list.isEmpty()) {
            throw this.assertionException(((Entity) list.getFirst()).blockPosition(), "test.error.unexpected_entity", entityType.getDescription());
        }
    }

    public void assertEntityNotPresent(EntityType<?> entityType, int x, int y, int z) {
        this.assertEntityNotPresent(entityType, new BlockPos(x, y, z));
    }

    public void assertEntityNotPresent(EntityType<?> entityType, BlockPos pos) {
        BlockPos blockpos1 = this.absolutePos(pos);

        if (this.getLevel().hasEntities(entityType, new AABB(blockpos1), Entity::isAlive)) {
            throw this.assertionException(pos, "test.error.unexpected_entity", entityType.getDescription());
        }
    }

    public void assertEntityNotPresent(EntityType<?> entityType, AABB relativeAABB) {
        AABB aabb1 = this.absoluteAABB(relativeAABB);
        List<? extends Entity> list = this.getLevel().getEntities(entityType, aabb1, Entity::isAlive);

        if (!list.isEmpty()) {
            throw this.assertionException(((Entity) list.getFirst()).blockPosition(), "test.error.unexpected_entity", entityType.getDescription());
        }
    }

    public void assertEntityTouching(EntityType<?> entityType, double x, double y, double z) {
        Vec3 vec3 = new Vec3(x, y, z);
        Vec3 vec31 = this.absoluteVec(vec3);
        Predicate<? super Entity> predicate = (entity) -> {
            return entity.getBoundingBox().intersects(vec31, vec31);
        };

        if (!this.getLevel().hasEntities(entityType, this.getBounds(), predicate)) {
            throw this.assertionException("test.error.expected_entity_touching", entityType.getDescription(), vec31.x(), vec31.y(), vec31.z(), x, y, z);
        }
    }

    public void assertEntityNotTouching(EntityType<?> entityType, double x, double y, double z) {
        Vec3 vec3 = new Vec3(x, y, z);
        Vec3 vec31 = this.absoluteVec(vec3);
        Predicate<? super Entity> predicate = (entity) -> {
            return !entity.getBoundingBox().intersects(vec31, vec31);
        };

        if (!this.getLevel().hasEntities(entityType, this.getBounds(), predicate)) {
            throw this.assertionException("test.error.expected_entity_not_touching", entityType.getDescription(), vec31.x(), vec31.y(), vec31.z(), x, y, z);
        }
    }

    public <E extends Entity, T> void assertEntityData(BlockPos pos, EntityType<E> entityType, Predicate<E> test) {
        BlockPos blockpos1 = this.absolutePos(pos);
        List<E> list = this.getLevel().getEntities(entityType, new AABB(blockpos1), Entity::isAlive);

        if (list.isEmpty()) {
            throw this.assertionException(pos, "test.error.expected_entity", entityType.getDescription());
        } else {
            for (E e0 : list) {
                if (!test.test(e0)) {
                    throw this.assertionException(e0.blockPosition(), "test.error.expected_entity_data_predicate", e0.getName());
                }
            }

        }
    }

    public <E extends Entity, T> void assertEntityData(BlockPos pos, EntityType<E> entityType, Function<? super E, T> dataAccessor, @Nullable T data) {
        this.assertEntityData(new AABB(pos), entityType, dataAccessor, data);
    }

    public <E extends Entity, T> void assertEntityData(AABB box, EntityType<E> entityType, Function<? super E, T> dataAccessor, @Nullable T data) {
        List<E> list = this.getLevel().getEntities(entityType, this.absoluteAABB(box), Entity::isAlive);

        if (list.isEmpty()) {
            throw this.assertionException(BlockPos.containing(box.getBottomCenter()), "test.error.expected_entity", entityType.getDescription());
        } else {
            for (E e0 : list) {
                T t1 = (T) dataAccessor.apply(e0);

                if (!Objects.equals(t1, data)) {
                    throw this.assertionException(BlockPos.containing(box.getBottomCenter()), "test.error.expected_entity_data", data, t1);
                }
            }

        }
    }

    public <E extends LivingEntity> void assertEntityIsHolding(BlockPos pos, EntityType<E> entityType, Item item) {
        BlockPos blockpos1 = this.absolutePos(pos);
        List<E> list = this.getLevel().getEntities(entityType, new AABB(blockpos1), Entity::isAlive);

        if (list.isEmpty()) {
            throw this.assertionException(pos, "test.error.expected_entity", entityType.getDescription());
        } else {
            for (E e0 : list) {
                if (e0.isHolding(item)) {
                    return;
                }
            }

            throw this.assertionException(pos, "test.error.expected_entity_holding", item.getName());
        }
    }

    public <E extends Entity & InventoryCarrier> void assertEntityInventoryContains(BlockPos pos, EntityType<E> entityType, Item item) {
        BlockPos blockpos1 = this.absolutePos(pos);
        List<E> list = this.getLevel().getEntities(entityType, new AABB(blockpos1), (object) -> {
            return ((Entity) object).isAlive();
        });

        if (list.isEmpty()) {
            throw this.assertionException(pos, "test.error.expected_entity", entityType.getDescription());
        } else {
            for (E e0 : list) {
                if (((InventoryCarrier) e0).getInventory().hasAnyMatching((itemstack) -> {
                    return itemstack.is(item);
                })) {
                    return;
                }
            }

            throw this.assertionException(pos, "test.error.expected_entity_having", item.getName());
        }
    }

    public void assertContainerEmpty(BlockPos pos) {
        BaseContainerBlockEntity basecontainerblockentity = (BaseContainerBlockEntity) this.getBlockEntity(pos, BaseContainerBlockEntity.class);

        if (!basecontainerblockentity.isEmpty()) {
            throw this.assertionException(pos, "test.error.expected_empty_container");
        }
    }

    public void assertContainerContainsSingle(BlockPos pos, Item item) {
        BaseContainerBlockEntity basecontainerblockentity = (BaseContainerBlockEntity) this.getBlockEntity(pos, BaseContainerBlockEntity.class);

        if (basecontainerblockentity.countItem(item) != 1) {
            throw this.assertionException(pos, "test.error.expected_container_contents_single", item.getName());
        }
    }

    public void assertContainerContains(BlockPos pos, Item item) {
        BaseContainerBlockEntity basecontainerblockentity = (BaseContainerBlockEntity) this.getBlockEntity(pos, BaseContainerBlockEntity.class);

        if (basecontainerblockentity.countItem(item) == 0) {
            throw this.assertionException(pos, "test.error.expected_container_contents", item.getName());
        }
    }

    public void assertSameBlockStates(BoundingBox sourceBoundingBox, BlockPos targetBoundingBoxCorner) {
        BlockPos.betweenClosedStream(sourceBoundingBox).forEach((blockpos1) -> {
            BlockPos blockpos2 = targetBoundingBoxCorner.offset(blockpos1.getX() - sourceBoundingBox.minX(), blockpos1.getY() - sourceBoundingBox.minY(), blockpos1.getZ() - sourceBoundingBox.minZ());

            this.assertSameBlockState(blockpos1, blockpos2);
        });
    }

    public void assertSameBlockState(BlockPos sourcePos, BlockPos targetPos) {
        BlockState blockstate = this.getBlockState(sourcePos);
        BlockState blockstate1 = this.getBlockState(targetPos);

        if (blockstate != blockstate1) {
            throw this.assertionException(sourcePos, "test.error.state_not_equal", blockstate1, blockstate);
        }
    }

    public void assertAtTickTimeContainerContains(long time, BlockPos pos, Item item) {
        this.runAtTickTime(time, () -> {
            this.assertContainerContainsSingle(pos, item);
        });
    }

    public void assertAtTickTimeContainerEmpty(long time, BlockPos pos) {
        this.runAtTickTime(time, () -> {
            this.assertContainerEmpty(pos);
        });
    }

    public <E extends Entity, T> void succeedWhenEntityData(BlockPos pos, EntityType<E> entityType, Function<E, T> dataAccessor, T data) {
        this.succeedWhen(() -> {
            this.assertEntityData(pos, entityType, dataAccessor, data);
        });
    }

    public <E extends Entity> void assertEntityProperty(E entity, Predicate<E> test, Component description) {
        if (!test.test(entity)) {
            throw this.assertionException(entity.blockPosition(), "test.error.entity_property", entity.getName(), description);
        }
    }

    public <E extends Entity, T> void assertEntityProperty(E entity, Function<E, T> test, T expected, Component description) {
        T t1 = (T) test.apply(entity);

        if (!t1.equals(expected)) {
            throw this.assertionException(entity.blockPosition(), "test.error.entity_property_details", entity.getName(), description, t1, expected);
        }
    }

    public void assertLivingEntityHasMobEffect(LivingEntity entity, Holder<MobEffect> mobEffect, int amplifier) {
        MobEffectInstance mobeffectinstance = entity.getEffect(mobEffect);

        if (mobeffectinstance == null || mobeffectinstance.getAmplifier() != amplifier) {
            throw this.assertionException("test.error.expected_entity_effect", entity.getName(), PotionContents.getPotionDescription(mobEffect, amplifier));
        }
    }

    public void succeedWhenEntityPresent(EntityType<?> entityType, int x, int y, int z) {
        this.succeedWhenEntityPresent(entityType, new BlockPos(x, y, z));
    }

    public void succeedWhenEntityPresent(EntityType<?> entityType, BlockPos pos) {
        this.succeedWhen(() -> {
            this.assertEntityPresent(entityType, pos);
        });
    }

    public void succeedWhenEntityNotPresent(EntityType<?> entityType, int x, int y, int z) {
        this.succeedWhenEntityNotPresent(entityType, new BlockPos(x, y, z));
    }

    public void succeedWhenEntityNotPresent(EntityType<?> entityType, BlockPos pos) {
        this.succeedWhen(() -> {
            this.assertEntityNotPresent(entityType, pos);
        });
    }

    public void succeed() {
        this.testInfo.succeed();
    }

    private void ensureSingleFinalCheck() {
        if (this.finalCheckAdded) {
            throw new IllegalStateException("This test already has final clause");
        } else {
            this.finalCheckAdded = true;
        }
    }

    public void succeedIf(Runnable asserter) {
        this.ensureSingleFinalCheck();
        this.testInfo.createSequence().thenWaitUntil(0L, asserter).thenSucceed();
    }

    public void succeedWhen(Runnable asserter) {
        this.ensureSingleFinalCheck();
        this.testInfo.createSequence().thenWaitUntil(asserter).thenSucceed();
    }

    public void succeedOnTickWhen(int tick, Runnable asserter) {
        this.ensureSingleFinalCheck();
        this.testInfo.createSequence().thenWaitUntil((long) tick, asserter).thenSucceed();
    }

    public void runAtTickTime(long time, Runnable asserter) {
        this.testInfo.setRunAtTickTime(time, asserter);
    }

    public void runAfterDelay(long ticksToDelay, Runnable whatToRun) {
        this.runAtTickTime((long) this.testInfo.getTick() + ticksToDelay, whatToRun);
    }

    public void randomTick(BlockPos pos) {
        BlockPos blockpos1 = this.absolutePos(pos);
        ServerLevel serverlevel = this.getLevel();

        serverlevel.getBlockState(blockpos1).randomTick(serverlevel, blockpos1, serverlevel.random);
    }

    public void tickBlock(BlockPos pos) {
        BlockPos blockpos1 = this.absolutePos(pos);
        ServerLevel serverlevel = this.getLevel();

        serverlevel.getBlockState(blockpos1).tick(serverlevel, blockpos1, serverlevel.random);
    }

    public void tickPrecipitation(BlockPos pos) {
        BlockPos blockpos1 = this.absolutePos(pos);
        ServerLevel serverlevel = this.getLevel();

        serverlevel.tickPrecipitation(blockpos1);
    }

    public void tickPrecipitation() {
        AABB aabb = this.getRelativeBounds();
        int i = (int) Math.floor(aabb.maxX);
        int j = (int) Math.floor(aabb.maxZ);
        int k = (int) Math.floor(aabb.maxY);

        for (int l = (int) Math.floor(aabb.minX); l < i; ++l) {
            for (int i1 = (int) Math.floor(aabb.minZ); i1 < j; ++i1) {
                this.tickPrecipitation(new BlockPos(l, k, i1));
            }
        }

    }

    public int getHeight(Heightmap.Types heightmap, int x, int z) {
        BlockPos blockpos = this.absolutePos(new BlockPos(x, 0, z));

        return this.relativePos(this.getLevel().getHeightmapPos(heightmap, blockpos)).getY();
    }

    public void fail(Component message, BlockPos pos) {
        throw this.assertionException(pos, message);
    }

    public void fail(Component message, Entity entity) {
        throw this.assertionException(entity.blockPosition(), message);
    }

    public void fail(Component message) {
        throw this.assertionException(message);
    }

    public void fail(String message) {
        throw this.assertionException(Component.literal(message));
    }

    public void failIf(Runnable asserter) {
        this.testInfo.createSequence().thenWaitUntil(asserter).thenFail(() -> {
            return this.assertionException("test.error.fail");
        });
    }

    public void failIfEver(Runnable asserter) {
        LongStream.range((long) this.testInfo.getTick(), (long) this.testInfo.getTimeoutTicks()).forEach((i) -> {
            GameTestInfo gametestinfo = this.testInfo;

            Objects.requireNonNull(asserter);
            gametestinfo.setRunAtTickTime(i, asserter::run);
        });
    }

    public GameTestSequence startSequence() {
        return this.testInfo.createSequence();
    }

    public BlockPos absolutePos(BlockPos relativePos) {
        BlockPos blockpos1 = this.testInfo.getTestOrigin();
        BlockPos blockpos2 = blockpos1.offset(relativePos);

        return StructureTemplate.transform(blockpos2, Mirror.NONE, this.testInfo.getRotation(), blockpos1);
    }

    public BlockPos relativePos(BlockPos absolutePos) {
        BlockPos blockpos1 = this.testInfo.getTestOrigin();
        Rotation rotation = this.testInfo.getRotation().getRotated(Rotation.CLOCKWISE_180);
        BlockPos blockpos2 = StructureTemplate.transform(absolutePos, Mirror.NONE, rotation, blockpos1);

        return blockpos2.subtract(blockpos1);
    }

    public AABB absoluteAABB(AABB relativeAABB) {
        Vec3 vec3 = this.absoluteVec(relativeAABB.getMinPosition());
        Vec3 vec31 = this.absoluteVec(relativeAABB.getMaxPosition());

        return new AABB(vec3, vec31);
    }

    public AABB relativeAABB(AABB absoluteAABB) {
        Vec3 vec3 = this.relativeVec(absoluteAABB.getMinPosition());
        Vec3 vec31 = this.relativeVec(absoluteAABB.getMaxPosition());

        return new AABB(vec3, vec31);
    }

    public Vec3 absoluteVec(Vec3 relativeVec) {
        Vec3 vec31 = Vec3.atLowerCornerOf(this.testInfo.getTestOrigin());

        return StructureTemplate.transform(vec31.add(relativeVec), Mirror.NONE, this.testInfo.getRotation(), this.testInfo.getTestOrigin());
    }

    public Vec3 relativeVec(Vec3 absoluteVec) {
        Vec3 vec31 = Vec3.atLowerCornerOf(this.testInfo.getTestOrigin());

        return StructureTemplate.transform(absoluteVec.subtract(vec31), Mirror.NONE, this.testInfo.getRotation(), this.testInfo.getTestOrigin());
    }

    public Rotation getTestRotation() {
        return this.testInfo.getRotation();
    }

    public Direction getTestDirection() {
        return this.testInfo.getRotation().rotate(Direction.SOUTH);
    }

    public Direction getAbsoluteDirection(Direction direction) {
        return this.getTestRotation().rotate(direction);
    }

    public void assertTrue(boolean condition, Component errorMessage) {
        if (!condition) {
            throw this.assertionException(errorMessage);
        }
    }

    public void assertTrue(boolean condition, String errorMessage) {
        this.assertTrue(condition, (Component) Component.literal(errorMessage));
    }

    public <N> void assertValueEqual(N value, N expected, String valueName) {
        this.assertValueEqual(value, expected, (Component) Component.literal(valueName));
    }

    public <N> void assertValueEqual(N value, N expected, Component valueName) {
        if (!value.equals(expected)) {
            throw this.assertionException("test.error.value_not_equal", valueName, value, expected);
        }
    }

    public void assertFalse(boolean condition, Component errorMessage) {
        this.assertTrue(!condition, errorMessage);
    }

    public void assertFalse(boolean condition, String errorMessage) {
        this.assertFalse(condition, (Component) Component.literal(errorMessage));
    }

    public long getTick() {
        return (long) this.testInfo.getTick();
    }

    public AABB getBounds() {
        return this.testInfo.getStructureBounds();
    }

    public AABB getRelativeBounds() {
        AABB aabb = this.testInfo.getStructureBounds();
        Rotation rotation = this.testInfo.getRotation();

        switch (rotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                return new AABB(0.0D, 0.0D, 0.0D, aabb.getZsize(), aabb.getYsize(), aabb.getXsize());
            default:
                return new AABB(0.0D, 0.0D, 0.0D, aabb.getXsize(), aabb.getYsize(), aabb.getZsize());
        }
    }

    public void forEveryBlockInStructure(Consumer<BlockPos> forBlock) {
        AABB aabb = this.getRelativeBounds().contract(1.0D, 1.0D, 1.0D);

        BlockPos.MutableBlockPos.betweenClosedStream(aabb).forEach(forBlock);
    }

    public void onEachTick(Runnable action) {
        LongStream.range((long) this.testInfo.getTick(), (long) this.testInfo.getTimeoutTicks()).forEach((i) -> {
            GameTestInfo gametestinfo = this.testInfo;

            Objects.requireNonNull(action);
            gametestinfo.setRunAtTickTime(i, action::run);
        });
    }

    public void placeAt(Player player, ItemStack blockStack, BlockPos pos, Direction face) {
        BlockPos blockpos1 = this.absolutePos(pos.relative(face));
        BlockHitResult blockhitresult = new BlockHitResult(Vec3.atCenterOf(blockpos1), face, blockpos1, false);
        UseOnContext useoncontext = new UseOnContext(player, InteractionHand.MAIN_HAND, blockhitresult);

        blockStack.useOn(useoncontext);
    }

    public void setBiome(ResourceKey<Biome> biome) {
        AABB aabb = this.getBounds();
        BlockPos blockpos = BlockPos.containing(aabb.minX, aabb.minY, aabb.minZ);
        BlockPos blockpos1 = BlockPos.containing(aabb.maxX, aabb.maxY, aabb.maxZ);
        Either<Integer, CommandSyntaxException> either = FillBiomeCommand.fill(this.getLevel(), blockpos, blockpos1, this.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome));

        if (either.right().isPresent()) {
            throw this.assertionException("test.error.set_biome");
        }
    }
}
