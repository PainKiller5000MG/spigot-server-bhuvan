package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.gametest.framework.FailedTestTracker;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.FileUtil;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

public class TestInstanceBlockEntity extends BlockEntity implements BoundingBoxRenderable, BeaconBeamOwner {

    private static final Component INVALID_TEST_NAME = Component.translatable("test_instance_block.invalid_test");
    private static final List<BeaconBeamOwner.Section> BEAM_CLEARED = List.of();
    private static final List<BeaconBeamOwner.Section> BEAM_RUNNING = List.of(new BeaconBeamOwner.Section(ARGB.color(128, 128, 128)));
    private static final List<BeaconBeamOwner.Section> BEAM_SUCCESS = List.of(new BeaconBeamOwner.Section(ARGB.color(0, 255, 0)));
    private static final List<BeaconBeamOwner.Section> BEAM_REQUIRED_FAILED = List.of(new BeaconBeamOwner.Section(ARGB.color(255, 0, 0)));
    private static final List<BeaconBeamOwner.Section> BEAM_OPTIONAL_FAILED = List.of(new BeaconBeamOwner.Section(ARGB.color(255, 128, 0)));
    private static final Vec3i STRUCTURE_OFFSET = new Vec3i(0, 1, 1);
    private TestInstanceBlockEntity.Data data;
    private final List<TestInstanceBlockEntity.ErrorMarker> errorMarkers = new ArrayList();

    public TestInstanceBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.TEST_INSTANCE_BLOCK, worldPosition, blockState);
        this.data = new TestInstanceBlockEntity.Data(Optional.empty(), Vec3i.ZERO, Rotation.NONE, false, TestInstanceBlockEntity.Status.CLEARED, Optional.empty());
    }

    public void set(TestInstanceBlockEntity.Data data) {
        this.data = data;
        this.setChanged();
    }

    public static Optional<Vec3i> getStructureSize(ServerLevel level, ResourceKey<GameTestInstance> testKey) {
        return getStructureTemplate(level, testKey).map(StructureTemplate::getSize);
    }

    public BoundingBox getStructureBoundingBox() {
        BlockPos blockpos = this.getStructurePos();
        BlockPos blockpos1 = blockpos.offset(this.getTransformedSize()).offset(-1, -1, -1);

        return BoundingBox.fromCorners(blockpos, blockpos1);
    }

    public AABB getStructureBounds() {
        return AABB.of(this.getStructureBoundingBox());
    }

    private static Optional<StructureTemplate> getStructureTemplate(ServerLevel level, ResourceKey<GameTestInstance> testKey) {
        return level.registryAccess().get(testKey).map((holder_reference) -> {
            return ((GameTestInstance) holder_reference.value()).structure();
        }).flatMap((identifier) -> {
            return level.getStructureManager().get(identifier);
        });
    }

    public Optional<ResourceKey<GameTestInstance>> test() {
        return this.data.test();
    }

    public Component getTestName() {
        return (Component) this.test().map((resourcekey) -> {
            return Component.literal(resourcekey.identifier().toString());
        }).orElse(TestInstanceBlockEntity.INVALID_TEST_NAME);
    }

    private Optional<Holder.Reference<GameTestInstance>> getTestHolder() {
        Optional optional = this.test();
        RegistryAccess registryaccess = this.level.registryAccess();

        Objects.requireNonNull(registryaccess);
        return optional.flatMap(registryaccess::get);
    }

    public boolean ignoreEntities() {
        return this.data.ignoreEntities();
    }

    public Vec3i getSize() {
        return this.data.size();
    }

    public Rotation getRotation() {
        return ((Rotation) this.getTestHolder().map(Holder::value).map(GameTestInstance::rotation).orElse(Rotation.NONE)).getRotated(this.data.rotation());
    }

    public Optional<Component> errorMessage() {
        return this.data.errorMessage();
    }

    public void setErrorMessage(Component errorMessage) {
        this.set(this.data.withError(errorMessage));
    }

    public void setSuccess() {
        this.set(this.data.withStatus(TestInstanceBlockEntity.Status.FINISHED));
    }

    public void setRunning() {
        this.set(this.data.withStatus(TestInstanceBlockEntity.Status.RUNNING));
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level instanceof ServerLevel) {
            this.level.sendBlockUpdated(this.getBlockPos(), Blocks.AIR.defaultBlockState(), this.getBlockState(), 3);
        }

    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        input.read("data", TestInstanceBlockEntity.Data.CODEC).ifPresent(this::set);
        this.errorMarkers.clear();
        this.errorMarkers.addAll((Collection) input.read("errors", TestInstanceBlockEntity.ErrorMarker.LIST_CODEC).orElse(List.of()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        output.store("data", TestInstanceBlockEntity.Data.CODEC, this.data);
        if (!this.errorMarkers.isEmpty()) {
            output.store("errors", TestInstanceBlockEntity.ErrorMarker.LIST_CODEC, this.errorMarkers);
        }

    }

    @Override
    public BoundingBoxRenderable.Mode renderMode() {
        return BoundingBoxRenderable.Mode.BOX;
    }

    public BlockPos getStructurePos() {
        return getStructurePos(this.getBlockPos());
    }

    public static BlockPos getStructurePos(BlockPos blockPos) {
        return blockPos.offset(TestInstanceBlockEntity.STRUCTURE_OFFSET);
    }

    @Override
    public BoundingBoxRenderable.RenderableBox getRenderableBox() {
        return new BoundingBoxRenderable.RenderableBox(new BlockPos(TestInstanceBlockEntity.STRUCTURE_OFFSET), this.getTransformedSize());
    }

    @Override
    public List<BeaconBeamOwner.Section> getBeamSections() {
        List list;

        switch (this.data.status().ordinal()) {
            case 0:
                list = TestInstanceBlockEntity.BEAM_CLEARED;
                break;
            case 1:
                list = TestInstanceBlockEntity.BEAM_RUNNING;
                break;
            case 2:
                list = this.errorMessage().isEmpty() ? TestInstanceBlockEntity.BEAM_SUCCESS : ((Boolean) this.getTestHolder().map(Holder::value).map(GameTestInstance::required).orElse(true) ? TestInstanceBlockEntity.BEAM_REQUIRED_FAILED : TestInstanceBlockEntity.BEAM_OPTIONAL_FAILED);
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return list;
    }

    private Vec3i getTransformedSize() {
        Vec3i vec3i = this.getSize();
        Rotation rotation = this.getRotation();
        boolean flag = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        int i = flag ? vec3i.getZ() : vec3i.getX();
        int j = flag ? vec3i.getX() : vec3i.getZ();

        return new Vec3i(i, vec3i.getY(), j);
    }

    public void resetTest(Consumer<Component> feedbackOutput) {
        this.removeBarriers();
        this.clearErrorMarkers();
        boolean flag = this.placeStructure();

        if (flag) {
            feedbackOutput.accept(Component.translatable("test_instance_block.reset_success", this.getTestName()).withStyle(ChatFormatting.GREEN));
        }

        this.set(this.data.withStatus(TestInstanceBlockEntity.Status.CLEARED));
    }

    public Optional<Identifier> saveTest(Consumer<Component> feedbackOutput) {
        Optional<Holder.Reference<GameTestInstance>> optional = this.getTestHolder();
        Optional<Identifier> optional1;

        if (optional.isPresent()) {
            optional1 = Optional.of(((GameTestInstance) ((Holder.Reference) optional.get()).value()).structure());
        } else {
            optional1 = this.test().map(ResourceKey::identifier);
        }

        if (optional1.isEmpty()) {
            BlockPos blockpos = this.getBlockPos();

            feedbackOutput.accept(Component.translatable("test_instance_block.error.unable_to_save", blockpos.getX(), blockpos.getY(), blockpos.getZ()).withStyle(ChatFormatting.RED));
            return optional1;
        } else {
            Level level = this.level;

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                StructureBlockEntity.saveStructure(serverlevel, (Identifier) optional1.get(), this.getStructurePos(), this.getSize(), this.ignoreEntities(), "", true, List.of(Blocks.AIR));
            }

            return optional1;
        }
    }

    public boolean exportTest(Consumer<Component> feedbackOutput) {
        Optional<Identifier> optional = this.saveTest(feedbackOutput);

        if (!optional.isEmpty()) {
            Level level = this.level;

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                return export(serverlevel, (Identifier) optional.get(), feedbackOutput);
            }
        }

        return false;
    }

    public static boolean export(ServerLevel level, Identifier structureId, Consumer<Component> feedbackOutput) {
        Path path = StructureUtils.testStructuresDir;
        Path path1 = level.getStructureManager().createAndValidatePathToGeneratedStructure(structureId, ".nbt");
        Path path2 = NbtToSnbt.convertStructure(CachedOutput.NO_CACHE, path1, structureId.getPath(), path.resolve(structureId.getNamespace()).resolve("structure"));

        if (path2 == null) {
            feedbackOutput.accept(Component.literal("Failed to export " + String.valueOf(path1)).withStyle(ChatFormatting.RED));
            return true;
        } else {
            try {
                FileUtil.createDirectoriesSafe(path2.getParent());
            } catch (IOException ioexception) {
                feedbackOutput.accept(Component.literal("Could not create folder " + String.valueOf(path2.getParent())).withStyle(ChatFormatting.RED));
                return true;
            }

            String s = String.valueOf(structureId);

            feedbackOutput.accept(Component.literal("Exported " + s + " to " + String.valueOf(path2.toAbsolutePath())));
            return false;
        }
    }

    public void runTest(Consumer<Component> feedbackOutput) {
        Level level = this.level;

        if (level instanceof ServerLevel serverlevel) {
            Optional optional = this.getTestHolder();
            BlockPos blockpos = this.getBlockPos();

            if (optional.isEmpty()) {
                feedbackOutput.accept(Component.translatable("test_instance_block.error.no_test", blockpos.getX(), blockpos.getY(), blockpos.getZ()).withStyle(ChatFormatting.RED));
            } else if (!this.placeStructure()) {
                feedbackOutput.accept(Component.translatable("test_instance_block.error.no_test_structure", blockpos.getX(), blockpos.getY(), blockpos.getZ()).withStyle(ChatFormatting.RED));
            } else {
                this.clearErrorMarkers();
                GameTestTicker.SINGLETON.clear();
                FailedTestTracker.forgetFailedTests();
                feedbackOutput.accept(Component.translatable("test_instance_block.starting", ((Holder.Reference) optional.get()).getRegisteredName()));
                GameTestInfo gametestinfo = new GameTestInfo((Holder.Reference) optional.get(), this.data.rotation(), serverlevel, RetryOptions.noRetries());

                gametestinfo.setTestBlockPos(blockpos);
                GameTestRunner gametestrunner = GameTestRunner.Builder.fromInfo(List.of(gametestinfo), serverlevel).build();

                TestCommand.trackAndStartRunner(serverlevel.getServer().createCommandSourceStack(), gametestrunner);
            }
        }
    }

    public boolean placeStructure() {
        Level level = this.level;

        if (level instanceof ServerLevel serverlevel) {
            Optional<StructureTemplate> optional = this.data.test().flatMap((resourcekey) -> {
                return getStructureTemplate(serverlevel, resourcekey);
            });

            if (optional.isPresent()) {
                this.placeStructure(serverlevel, (StructureTemplate) optional.get());
                return true;
            }
        }

        return false;
    }

    private void placeStructure(ServerLevel level, StructureTemplate template) {
        StructurePlaceSettings structureplacesettings = (new StructurePlaceSettings()).setRotation(this.getRotation()).setIgnoreEntities(this.data.ignoreEntities()).setKnownShape(true);
        BlockPos blockpos = this.getStartCorner();

        this.forceLoadChunks();
        StructureUtils.clearSpaceForStructure(this.getStructureBoundingBox(), level);
        this.removeEntities();
        template.placeInWorld(level, blockpos, blockpos, structureplacesettings, level.getRandom(), 818);
    }

    private void removeEntities() {
        this.level.getEntities((Entity) null, this.getStructureBounds()).stream().filter((entity) -> {
            return !(entity instanceof Player);
        }).forEach(Entity::discard);
    }

    private void forceLoadChunks() {
        Level level = this.level;

        if (level instanceof ServerLevel serverlevel) {
            this.getStructureBoundingBox().intersectingChunks().forEach((chunkpos) -> {
                serverlevel.setChunkForced(chunkpos.x, chunkpos.z, true);
            });
        }

    }

    public BlockPos getStartCorner() {
        Vec3i vec3i = this.getSize();
        Rotation rotation = this.getRotation();
        BlockPos blockpos = this.getStructurePos();
        BlockPos blockpos1;

        switch (rotation) {
            case NONE:
                blockpos1 = blockpos;
                break;
            case CLOCKWISE_90:
                blockpos1 = blockpos.offset(vec3i.getZ() - 1, 0, 0);
                break;
            case CLOCKWISE_180:
                blockpos1 = blockpos.offset(vec3i.getX() - 1, 0, vec3i.getZ() - 1);
                break;
            case COUNTERCLOCKWISE_90:
                blockpos1 = blockpos.offset(0, 0, vec3i.getX() - 1);
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return blockpos1;
    }

    public void encaseStructure() {
        this.processStructureBoundary((blockpos) -> {
            if (!this.level.getBlockState(blockpos).is(Blocks.TEST_INSTANCE_BLOCK)) {
                this.level.setBlockAndUpdate(blockpos, Blocks.BARRIER.defaultBlockState());
            }

        });
    }

    public void removeBarriers() {
        this.processStructureBoundary((blockpos) -> {
            if (this.level.getBlockState(blockpos).is(Blocks.BARRIER)) {
                this.level.setBlockAndUpdate(blockpos, Blocks.AIR.defaultBlockState());
            }

        });
    }

    public void processStructureBoundary(Consumer<BlockPos> action) {
        AABB aabb = this.getStructureBounds();
        boolean flag = !(Boolean) this.getTestHolder().map((holder_reference) -> {
            return ((GameTestInstance) holder_reference.value()).skyAccess();
        }).orElse(false);
        BlockPos blockpos = BlockPos.containing(aabb.minX, aabb.minY, aabb.minZ).offset(-1, -1, -1);
        BlockPos blockpos1 = BlockPos.containing(aabb.maxX, aabb.maxY, aabb.maxZ);

        BlockPos.betweenClosedStream(blockpos, blockpos1).forEach((blockpos2) -> {
            boolean flag1 = blockpos2.getX() == blockpos.getX() || blockpos2.getX() == blockpos1.getX() || blockpos2.getZ() == blockpos.getZ() || blockpos2.getZ() == blockpos1.getZ() || blockpos2.getY() == blockpos.getY();
            boolean flag2 = blockpos2.getY() == blockpos1.getY();

            if (flag1 || flag2 && flag) {
                action.accept(blockpos2);
            }

        });
    }

    public void markError(BlockPos pos, Component text) {
        this.errorMarkers.add(new TestInstanceBlockEntity.ErrorMarker(pos, text));
        this.setChanged();
    }

    public void clearErrorMarkers() {
        if (!this.errorMarkers.isEmpty()) {
            this.errorMarkers.clear();
            this.setChanged();
        }

    }

    public List<TestInstanceBlockEntity.ErrorMarker> getErrorMarkers() {
        return this.errorMarkers;
    }

    public static enum Status implements StringRepresentable {

        CLEARED("cleared", 0), RUNNING("running", 1), FINISHED("finished", 2);

        private static final IntFunction<TestInstanceBlockEntity.Status> ID_MAP = ByIdMap.<TestInstanceBlockEntity.Status>continuous((testinstanceblockentity_status) -> {
            return testinstanceblockentity_status.index;
        }, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final Codec<TestInstanceBlockEntity.Status> CODEC = StringRepresentable.<TestInstanceBlockEntity.Status>fromEnum(TestInstanceBlockEntity.Status::values);
        public static final StreamCodec<ByteBuf, TestInstanceBlockEntity.Status> STREAM_CODEC = ByteBufCodecs.idMapper(TestInstanceBlockEntity.Status::byIndex, (testinstanceblockentity_status) -> {
            return testinstanceblockentity_status.index;
        });
        private final String id;
        private final int index;

        private Status(String id, int index) {
            this.id = id;
            this.index = index;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }

        public static TestInstanceBlockEntity.Status byIndex(int index) {
            return (TestInstanceBlockEntity.Status) TestInstanceBlockEntity.Status.ID_MAP.apply(index);
        }
    }

    public static record Data(Optional<ResourceKey<GameTestInstance>> test, Vec3i size, Rotation rotation, boolean ignoreEntities, TestInstanceBlockEntity.Status status, Optional<Component> errorMessage) {

        public static final Codec<TestInstanceBlockEntity.Data> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(ResourceKey.codec(Registries.TEST_INSTANCE).optionalFieldOf("test").forGetter(TestInstanceBlockEntity.Data::test), Vec3i.CODEC.fieldOf("size").forGetter(TestInstanceBlockEntity.Data::size), Rotation.CODEC.fieldOf("rotation").forGetter(TestInstanceBlockEntity.Data::rotation), Codec.BOOL.fieldOf("ignore_entities").forGetter(TestInstanceBlockEntity.Data::ignoreEntities), TestInstanceBlockEntity.Status.CODEC.fieldOf("status").forGetter(TestInstanceBlockEntity.Data::status), ComponentSerialization.CODEC.optionalFieldOf("error_message").forGetter(TestInstanceBlockEntity.Data::errorMessage)).apply(instance, TestInstanceBlockEntity.Data::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, TestInstanceBlockEntity.Data> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.optional(ResourceKey.streamCodec(Registries.TEST_INSTANCE)), TestInstanceBlockEntity.Data::test, Vec3i.STREAM_CODEC, TestInstanceBlockEntity.Data::size, Rotation.STREAM_CODEC, TestInstanceBlockEntity.Data::rotation, ByteBufCodecs.BOOL, TestInstanceBlockEntity.Data::ignoreEntities, TestInstanceBlockEntity.Status.STREAM_CODEC, TestInstanceBlockEntity.Data::status, ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), TestInstanceBlockEntity.Data::errorMessage, TestInstanceBlockEntity.Data::new);

        public TestInstanceBlockEntity.Data withSize(Vec3i size) {
            return new TestInstanceBlockEntity.Data(this.test, size, this.rotation, this.ignoreEntities, this.status, this.errorMessage);
        }

        public TestInstanceBlockEntity.Data withStatus(TestInstanceBlockEntity.Status status) {
            return new TestInstanceBlockEntity.Data(this.test, this.size, this.rotation, this.ignoreEntities, status, Optional.empty());
        }

        public TestInstanceBlockEntity.Data withError(Component error) {
            return new TestInstanceBlockEntity.Data(this.test, this.size, this.rotation, this.ignoreEntities, TestInstanceBlockEntity.Status.FINISHED, Optional.of(error));
        }
    }

    public static record ErrorMarker(BlockPos pos, Component text) {

        public static final Codec<TestInstanceBlockEntity.ErrorMarker> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(BlockPos.CODEC.fieldOf("pos").forGetter(TestInstanceBlockEntity.ErrorMarker::pos), ComponentSerialization.CODEC.fieldOf("text").forGetter(TestInstanceBlockEntity.ErrorMarker::text)).apply(instance, TestInstanceBlockEntity.ErrorMarker::new);
        });
        public static final Codec<List<TestInstanceBlockEntity.ErrorMarker>> LIST_CODEC = TestInstanceBlockEntity.ErrorMarker.CODEC.listOf();
    }
}
