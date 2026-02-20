package net.minecraft.gametest.framework;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TestBlock;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.TestBlockMode;

public class BlockBasedTestInstance extends GameTestInstance {

    public static final MapCodec<BlockBasedTestInstance> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(TestData.CODEC.forGetter(GameTestInstance::info)).apply(instance, BlockBasedTestInstance::new);
    });

    public BlockBasedTestInstance(TestData<Holder<TestEnvironmentDefinition>> testData) {
        super(testData);
    }

    @Override
    public void run(GameTestHelper helper) {
        BlockPos blockpos = this.findStartBlock(helper);
        TestBlockEntity testblockentity = (TestBlockEntity) helper.getBlockEntity(blockpos, TestBlockEntity.class);

        testblockentity.trigger();
        helper.onEachTick(() -> {
            List<BlockPos> list = this.findTestBlocks(helper, TestBlockMode.ACCEPT);

            if (list.isEmpty()) {
                helper.fail((Component) Component.translatable("test_block.error.missing", TestBlockMode.ACCEPT.getDisplayName()));
            }

            boolean flag = list.stream().map((blockpos1) -> {
                return (TestBlockEntity) helper.getBlockEntity(blockpos1, TestBlockEntity.class);
            }).anyMatch(TestBlockEntity::hasTriggered);

            if (flag) {
                helper.succeed();
            } else {
                this.forAllTriggeredTestBlocks(helper, TestBlockMode.FAIL, (testblockentity1) -> {
                    helper.fail((Component) Component.literal(testblockentity1.getMessage()));
                });
                this.forAllTriggeredTestBlocks(helper, TestBlockMode.LOG, TestBlockEntity::trigger);
            }

        });
    }

    private void forAllTriggeredTestBlocks(GameTestHelper helper, TestBlockMode mode, Consumer<TestBlockEntity> action) {
        for (BlockPos blockpos : this.findTestBlocks(helper, mode)) {
            TestBlockEntity testblockentity = (TestBlockEntity) helper.getBlockEntity(blockpos, TestBlockEntity.class);

            if (testblockentity.hasTriggered()) {
                action.accept(testblockentity);
                testblockentity.reset();
            }
        }

    }

    private BlockPos findStartBlock(GameTestHelper helper) {
        List<BlockPos> list = this.findTestBlocks(helper, TestBlockMode.START);

        if (list.isEmpty()) {
            helper.fail((Component) Component.translatable("test_block.error.missing", TestBlockMode.START.getDisplayName()));
        }

        if (list.size() != 1) {
            helper.fail((Component) Component.translatable("test_block.error.too_many", TestBlockMode.START.getDisplayName()));
        }

        return (BlockPos) list.getFirst();
    }

    private List<BlockPos> findTestBlocks(GameTestHelper helper, TestBlockMode mode) {
        List<BlockPos> list = new ArrayList();

        helper.forEveryBlockInStructure((blockpos) -> {
            BlockState blockstate = helper.getBlockState(blockpos);

            if (blockstate.is(Blocks.TEST_BLOCK) && blockstate.getValue(TestBlock.MODE) == mode) {
                list.add(blockpos.immutable());
            }

        });
        return list;
    }

    @Override
    public MapCodec<BlockBasedTestInstance> codec() {
        return BlockBasedTestInstance.CODEC;
    }

    @Override
    protected MutableComponent typeDescription() {
        return Component.translatable("test_instance.type.block_based");
    }
}
