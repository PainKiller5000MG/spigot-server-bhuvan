package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class EnchantingTableBlockEntity extends BlockEntity implements Nameable {

    private static final Component DEFAULT_NAME = Component.translatable("container.enchant");
    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    public float tRot;
    private static final RandomSource RANDOM = RandomSource.create();
    private @Nullable Component name;

    public EnchantingTableBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.ENCHANTING_TABLE, worldPosition, blockState);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("CustomName", ComponentSerialization.CODEC, this.name);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.name = parseCustomNameSafe(input, "CustomName");
    }

    public static void bookAnimationTick(Level level, BlockPos worldPosition, BlockState state, EnchantingTableBlockEntity entity) {
        entity.oOpen = entity.open;
        entity.oRot = entity.rot;
        Player player = level.getNearestPlayer((double) worldPosition.getX() + 0.5D, (double) worldPosition.getY() + 0.5D, (double) worldPosition.getZ() + 0.5D, 3.0D, false);

        if (player != null) {
            double d0 = player.getX() - ((double) worldPosition.getX() + 0.5D);
            double d1 = player.getZ() - ((double) worldPosition.getZ() + 0.5D);

            entity.tRot = (float) Mth.atan2(d1, d0);
            entity.open += 0.1F;
            if (entity.open < 0.5F || EnchantingTableBlockEntity.RANDOM.nextInt(40) == 0) {
                float f = entity.flipT;

                do {
                    entity.flipT += (float) (EnchantingTableBlockEntity.RANDOM.nextInt(4) - EnchantingTableBlockEntity.RANDOM.nextInt(4));
                } while (f == entity.flipT);
            }
        } else {
            entity.tRot += 0.02F;
            entity.open -= 0.1F;
        }

        while (entity.rot >= (float) Math.PI) {
            entity.rot -= ((float) Math.PI * 2F);
        }

        while (entity.rot < -(float) Math.PI) {
            entity.rot += ((float) Math.PI * 2F);
        }

        while (entity.tRot >= (float) Math.PI) {
            entity.tRot -= ((float) Math.PI * 2F);
        }

        while (entity.tRot < -(float) Math.PI) {
            entity.tRot += ((float) Math.PI * 2F);
        }

        float f1;

        for (f1 = entity.tRot - entity.rot; f1 >= (float) Math.PI; f1 -= ((float) Math.PI * 2F)) {
            ;
        }

        while (f1 < -(float) Math.PI) {
            f1 += ((float) Math.PI * 2F);
        }

        entity.rot += f1 * 0.4F;
        entity.open = Mth.clamp(entity.open, 0.0F, 1.0F);
        ++entity.time;
        entity.oFlip = entity.flip;
        float f2 = (entity.flipT - entity.flip) * 0.4F;
        float f3 = 0.2F;

        f2 = Mth.clamp(f2, -0.2F, 0.2F);
        entity.flipA += (f2 - entity.flipA) * 0.9F;
        entity.flip += entity.flipA;
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : EnchantingTableBlockEntity.DEFAULT_NAME;
    }

    public void setCustomName(@Nullable Component name) {
        this.name = name;
    }

    @Override
    public @Nullable Component getCustomName() {
        return this.name;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.name = (Component) components.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.name);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("CustomName");
    }
}
