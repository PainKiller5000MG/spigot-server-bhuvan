package net.minecraft.world.entity.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Mannequin extends Avatar {

    protected static final EntityDataAccessor<ResolvableProfile> DATA_PROFILE = SynchedEntityData.<ResolvableProfile>defineId(Mannequin.class, EntityDataSerializers.RESOLVABLE_PROFILE);
    private static final EntityDataAccessor<Boolean> DATA_IMMOVABLE = SynchedEntityData.<Boolean>defineId(Mannequin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<Component>> DATA_DESCRIPTION = SynchedEntityData.<Optional<Component>>defineId(Mannequin.class, EntityDataSerializers.OPTIONAL_COMPONENT);
    private static final byte ALL_LAYERS = (byte) Arrays.stream(PlayerModelPart.values()).mapToInt(PlayerModelPart::getMask).reduce(0, (i, j) -> {
        return i | j;
    });
    public static final Set<Pose> VALID_POSES = Set.of(Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING, Pose.FALL_FLYING, Pose.SLEEPING);
    public static final Codec<Pose> POSE_CODEC = Pose.CODEC.validate((pose) -> {
        return Mannequin.VALID_POSES.contains(pose) ? DataResult.success(pose) : DataResult.error(() -> {
            return "Invalid pose: " + pose.getSerializedName();
        });
    });
    private static final Codec<Byte> LAYERS_CODEC = PlayerModelPart.CODEC.listOf().xmap((list) -> {
        return (byte) list.stream().mapToInt(PlayerModelPart::getMask).reduce(Mannequin.ALL_LAYERS, (i, j) -> {
            return i & ~j;
        });
    }, (obyte) -> {
        return Arrays.stream(PlayerModelPart.values()).filter((playermodelpart) -> {
            return (obyte & playermodelpart.getMask()) == 0;
        }).toList();
    });
    public static final ResolvableProfile DEFAULT_PROFILE = ResolvableProfile.Static.EMPTY;
    public static final Component DEFAULT_DESCRIPTION = Component.translatable("entity.minecraft.mannequin.label");
    protected static EntityType.EntityFactory<Mannequin> constructor = Mannequin::new;
    private static final String PROFILE_FIELD = "profile";
    private static final String HIDDEN_LAYERS_FIELD = "hidden_layers";
    private static final String MAIN_HAND_FIELD = "main_hand";
    private static final String POSE_FIELD = "pose";
    private static final String IMMOVABLE_FIELD = "immovable";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String HIDE_DESCRIPTION_FIELD = "hide_description";
    public Component description;
    public boolean hideDescription;

    public Mannequin(EntityType<Mannequin> type, Level level) {
        super(type, level);
        this.description = Mannequin.DEFAULT_DESCRIPTION;
        this.hideDescription = false;
        this.entityData.set(Mannequin.DATA_PLAYER_MODE_CUSTOMISATION, Mannequin.ALL_LAYERS);
    }

    protected Mannequin(Level level) {
        this(EntityType.MANNEQUIN, level);
    }

    public static @Nullable Mannequin create(EntityType<Mannequin> type, Level level) {
        return Mannequin.constructor.create(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Mannequin.DATA_PROFILE, Mannequin.DEFAULT_PROFILE);
        entityData.define(Mannequin.DATA_IMMOVABLE, false);
        entityData.define(Mannequin.DATA_DESCRIPTION, Optional.of(Mannequin.DEFAULT_DESCRIPTION));
    }

    public ResolvableProfile getProfile() {
        return (ResolvableProfile) this.entityData.get(Mannequin.DATA_PROFILE);
    }

    public void setProfile(ResolvableProfile profile) {
        this.entityData.set(Mannequin.DATA_PROFILE, profile);
    }

    public boolean getImmovable() {
        return (Boolean) this.entityData.get(Mannequin.DATA_IMMOVABLE);
    }

    public void setImmovable(boolean immovable) {
        this.entityData.set(Mannequin.DATA_IMMOVABLE, immovable);
    }

    public @Nullable Component getDescription() {
        return (Component) ((Optional) this.entityData.get(Mannequin.DATA_DESCRIPTION)).orElse((Object) null);
    }

    public void setDescription(Component description) {
        this.description = description;
        this.updateDescription();
    }

    public void setHideDescription(boolean hideDescription) {
        this.hideDescription = hideDescription;
        this.updateDescription();
    }

    private void updateDescription() {
        this.entityData.set(Mannequin.DATA_DESCRIPTION, this.hideDescription ? Optional.empty() : Optional.of(this.description));
    }

    @Override
    protected boolean isImmobile() {
        return this.getImmovable() || super.isImmobile();
    }

    @Override
    public boolean isEffectiveAi() {
        return !this.getImmovable() && super.isEffectiveAi();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("profile", ResolvableProfile.CODEC, this.getProfile());
        output.store("hidden_layers", Mannequin.LAYERS_CODEC, (Byte) this.entityData.get(Mannequin.DATA_PLAYER_MODE_CUSTOMISATION));
        output.store("main_hand", HumanoidArm.CODEC, this.getMainArm());
        output.store("pose", Mannequin.POSE_CODEC, this.getPose());
        output.putBoolean("immovable", this.getImmovable());
        Component component = this.getDescription();

        if (component != null) {
            if (!component.equals(Mannequin.DEFAULT_DESCRIPTION)) {
                output.store("description", ComponentSerialization.CODEC, component);
            }
        } else {
            output.putBoolean("hide_description", true);
        }

    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("profile", ResolvableProfile.CODEC).ifPresent(this::setProfile);
        this.entityData.set(Mannequin.DATA_PLAYER_MODE_CUSTOMISATION, (Byte) input.read("hidden_layers", Mannequin.LAYERS_CODEC).orElse(Mannequin.ALL_LAYERS));
        this.setMainArm((HumanoidArm) input.read("main_hand", HumanoidArm.CODEC).orElse(Mannequin.DEFAULT_MAIN_HAND));
        this.setPose((Pose) input.read("pose", Mannequin.POSE_CODEC).orElse(Pose.STANDING));
        this.setImmovable(input.getBooleanOr("immovable", false));
        this.setHideDescription(input.getBooleanOr("hide_description", false));
        this.setDescription((Component) input.read("description", ComponentSerialization.CODEC).orElse(Mannequin.DEFAULT_DESCRIPTION));
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.PROFILE ? castComponentValue(type, this.getProfile()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.PROFILE);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.PROFILE) {
            this.setProfile((ResolvableProfile) castComponentValue(DataComponents.PROFILE, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }
}
