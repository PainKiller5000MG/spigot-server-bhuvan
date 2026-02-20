package net.minecraft.advancements;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.criterion.CriterionValidator;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public record Advancement(Optional<Identifier> parent, Optional<DisplayInfo> display, AdvancementRewards rewards, Map<String, Criterion<?>> criteria, AdvancementRequirements requirements, boolean sendsTelemetryEvent, Optional<Component> name) {

    private static final Codec<Map<String, Criterion<?>>> CRITERIA_CODEC = Codec.unboundedMap(Codec.STRING, Criterion.CODEC).validate((map) -> {
        return map.isEmpty() ? DataResult.error(() -> {
            return "Advancement criteria cannot be empty";
        }) : DataResult.success(map);
    });
    public static final Codec<Advancement> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Identifier.CODEC.optionalFieldOf("parent").forGetter(Advancement::parent), DisplayInfo.CODEC.optionalFieldOf("display").forGetter(Advancement::display), AdvancementRewards.CODEC.optionalFieldOf("rewards", AdvancementRewards.EMPTY).forGetter(Advancement::rewards), Advancement.CRITERIA_CODEC.fieldOf("criteria").forGetter(Advancement::criteria), AdvancementRequirements.CODEC.optionalFieldOf("requirements").forGetter((advancement) -> {
            return Optional.of(advancement.requirements());
        }), Codec.BOOL.optionalFieldOf("sends_telemetry_event", false).forGetter(Advancement::sendsTelemetryEvent)).apply(instance, (optional, optional1, advancementrewards, map, optional2, obool) -> {
            AdvancementRequirements advancementrequirements = (AdvancementRequirements) optional2.orElseGet(() -> {
                return AdvancementRequirements.allOf(map.keySet());
            });

            return new Advancement(optional, optional1, advancementrewards, map, advancementrequirements, obool);
        });
    }).validate(Advancement::validate);
    public static final StreamCodec<RegistryFriendlyByteBuf, Advancement> STREAM_CODEC = StreamCodec.<RegistryFriendlyByteBuf, Advancement>ofMember(Advancement::write, Advancement::read);

    public Advancement(Optional<Identifier> parent, Optional<DisplayInfo> display, AdvancementRewards rewards, Map<String, Criterion<?>> criteria, AdvancementRequirements requirements, boolean sendsTelemetryEvent) {
        this(parent, display, rewards, Map.copyOf(criteria), requirements, sendsTelemetryEvent, display.map(Advancement::decorateName));
    }

    private static DataResult<Advancement> validate(Advancement advancement) {
        return advancement.requirements().validate(advancement.criteria().keySet()).map((advancementrequirements) -> {
            return advancement;
        });
    }

    private static Component decorateName(DisplayInfo display) {
        Component component = display.getTitle();
        ChatFormatting chatformatting = display.getType().getChatColor();
        Component component1 = ComponentUtils.mergeStyles(component.copy(), Style.EMPTY.withColor(chatformatting)).append("\n").append(display.getDescription());
        Component component2 = component.copy().withStyle((style) -> {
            return style.withHoverEvent(new HoverEvent.ShowText(component1));
        });

        return ComponentUtils.wrapInSquareBrackets(component2).withStyle(chatformatting);
    }

    public static Component name(AdvancementHolder holder) {
        return (Component) holder.value().name().orElseGet(() -> {
            return Component.literal(holder.id().toString());
        });
    }

    private void write(RegistryFriendlyByteBuf output) {
        output.writeOptional(this.parent, FriendlyByteBuf::writeIdentifier);
        DisplayInfo.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(output, this.display);
        this.requirements.write(output);
        output.writeBoolean(this.sendsTelemetryEvent);
    }

    private static Advancement read(RegistryFriendlyByteBuf input) {
        return new Advancement(input.readOptional(FriendlyByteBuf::readIdentifier), (Optional) DisplayInfo.STREAM_CODEC.apply(ByteBufCodecs::optional).decode(input), AdvancementRewards.EMPTY, Map.of(), new AdvancementRequirements(input), input.readBoolean());
    }

    public boolean isRoot() {
        return this.parent.isEmpty();
    }

    public void validate(ProblemReporter reporter, HolderGetter.Provider lootData) {
        this.criteria.forEach((s, criterion) -> {
            CriterionValidator criterionvalidator = new CriterionValidator(reporter.forChild(new ProblemReporter.RootFieldPathElement(s)), lootData);

            criterion.triggerInstance().validate(criterionvalidator);
        });
    }

    public static class Builder {

        private Optional<Identifier> parent = Optional.empty();
        private Optional<DisplayInfo> display = Optional.empty();
        private AdvancementRewards rewards;
        private final ImmutableMap.Builder<String, Criterion<?>> criteria;
        private Optional<AdvancementRequirements> requirements;
        private AdvancementRequirements.Strategy requirementsStrategy;
        private boolean sendsTelemetryEvent;

        public Builder() {
            this.rewards = AdvancementRewards.EMPTY;
            this.criteria = ImmutableMap.builder();
            this.requirements = Optional.empty();
            this.requirementsStrategy = AdvancementRequirements.Strategy.AND;
        }

        public static Advancement.Builder advancement() {
            return (new Advancement.Builder()).sendsTelemetryEvent();
        }

        public static Advancement.Builder recipeAdvancement() {
            return new Advancement.Builder();
        }

        public Advancement.Builder parent(AdvancementHolder parent) {
            this.parent = Optional.of(parent.id());
            return this;
        }

        /** @deprecated */
        @Deprecated(forRemoval = true)
        public Advancement.Builder parent(Identifier parent) {
            this.parent = Optional.of(parent);
            return this;
        }

        public Advancement.Builder display(ItemStack icon, Component title, Component description, @Nullable Identifier background, AdvancementType frame, boolean showToast, boolean announceChat, boolean hidden) {
            return this.display(new DisplayInfo(icon, title, description, Optional.ofNullable(background).map(ClientAsset.ResourceTexture::new), frame, showToast, announceChat, hidden));
        }

        public Advancement.Builder display(ItemLike icon, Component title, Component description, @Nullable Identifier background, AdvancementType frame, boolean showToast, boolean announceChat, boolean hidden) {
            return this.display(new DisplayInfo(new ItemStack(icon.asItem()), title, description, Optional.ofNullable(background).map(ClientAsset.ResourceTexture::new), frame, showToast, announceChat, hidden));
        }

        public Advancement.Builder display(DisplayInfo display) {
            this.display = Optional.of(display);
            return this;
        }

        public Advancement.Builder rewards(AdvancementRewards.Builder rewards) {
            return this.rewards(rewards.build());
        }

        public Advancement.Builder rewards(AdvancementRewards rewards) {
            this.rewards = rewards;
            return this;
        }

        public Advancement.Builder addCriterion(String name, Criterion<?> criterion) {
            this.criteria.put(name, criterion);
            return this;
        }

        public Advancement.Builder requirements(AdvancementRequirements.Strategy strategy) {
            this.requirementsStrategy = strategy;
            return this;
        }

        public Advancement.Builder requirements(AdvancementRequirements requirements) {
            this.requirements = Optional.of(requirements);
            return this;
        }

        public Advancement.Builder sendsTelemetryEvent() {
            this.sendsTelemetryEvent = true;
            return this;
        }

        public AdvancementHolder build(Identifier id) {
            Map<String, Criterion<?>> map = this.criteria.buildOrThrow();
            AdvancementRequirements advancementrequirements = (AdvancementRequirements) this.requirements.orElseGet(() -> {
                return this.requirementsStrategy.create(map.keySet());
            });

            return new AdvancementHolder(id, new Advancement(this.parent, this.display, this.rewards, map, advancementrequirements, this.sendsTelemetryEvent));
        }

        public AdvancementHolder save(Consumer<AdvancementHolder> output, String name) {
            AdvancementHolder advancementholder = this.build(Identifier.parse(name));

            output.accept(advancementholder);
            return advancementholder;
        }
    }
}
