package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface HoverEvent {

    Codec<HoverEvent> CODEC = HoverEvent.Action.CODEC.dispatch("action", HoverEvent::action, (hoverevent_action) -> {
        return hoverevent_action.codec;
    });

    HoverEvent.Action action();

    public static record ShowText(Component value) implements HoverEvent {

        public static final MapCodec<HoverEvent.ShowText> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(ComponentSerialization.CODEC.fieldOf("value").forGetter(HoverEvent.ShowText::value)).apply(instance, HoverEvent.ShowText::new);
        });

        @Override
        public HoverEvent.Action action() {
            return HoverEvent.Action.SHOW_TEXT;
        }
    }

    public static record ShowItem(ItemStack item) implements HoverEvent {

        public static final MapCodec<HoverEvent.ShowItem> CODEC = ItemStack.MAP_CODEC.xmap(HoverEvent.ShowItem::new, HoverEvent.ShowItem::item);

        public ShowItem(ItemStack item) {
            item = item.copy();
            this.item = item;
        }

        @Override
        public HoverEvent.Action action() {
            return HoverEvent.Action.SHOW_ITEM;
        }

        public boolean equals(Object obj) {
            boolean flag;

            if (obj instanceof HoverEvent.ShowItem hoverevent_showitem) {
                if (ItemStack.matches(this.item, hoverevent_showitem.item)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }

        public int hashCode() {
            return ItemStack.hashItemAndComponents(this.item);
        }
    }

    public static record ShowEntity(HoverEvent.EntityTooltipInfo entity) implements HoverEvent {

        public static final MapCodec<HoverEvent.ShowEntity> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(HoverEvent.EntityTooltipInfo.CODEC.forGetter(HoverEvent.ShowEntity::entity)).apply(instance, HoverEvent.ShowEntity::new);
        });

        @Override
        public HoverEvent.Action action() {
            return HoverEvent.Action.SHOW_ENTITY;
        }
    }

    public static class EntityTooltipInfo {

        public static final MapCodec<HoverEvent.EntityTooltipInfo> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("id").forGetter((hoverevent_entitytooltipinfo) -> {
                return hoverevent_entitytooltipinfo.type;
            }), UUIDUtil.LENIENT_CODEC.fieldOf("uuid").forGetter((hoverevent_entitytooltipinfo) -> {
                return hoverevent_entitytooltipinfo.uuid;
            }), ComponentSerialization.CODEC.optionalFieldOf("name").forGetter((hoverevent_entitytooltipinfo) -> {
                return hoverevent_entitytooltipinfo.name;
            })).apply(instance, HoverEvent.EntityTooltipInfo::new);
        });
        public final EntityType<?> type;
        public final UUID uuid;
        public final Optional<Component> name;
        private @Nullable List<Component> linesCache;

        public EntityTooltipInfo(EntityType<?> type, UUID uuid, @Nullable Component name) {
            this(type, uuid, Optional.ofNullable(name));
        }

        public EntityTooltipInfo(EntityType<?> type, UUID uuid, Optional<Component> name) {
            this.type = type;
            this.uuid = uuid;
            this.name = name;
        }

        public List<Component> getTooltipLines() {
            if (this.linesCache == null) {
                this.linesCache = new ArrayList();
                Optional optional = this.name;
                List list = this.linesCache;

                Objects.requireNonNull(this.linesCache);
                optional.ifPresent(list::add);
                this.linesCache.add(Component.translatable("gui.entity_tooltip.type", this.type.getDescription()));
                this.linesCache.add(Component.literal(this.uuid.toString()));
            }

            return this.linesCache;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                HoverEvent.EntityTooltipInfo hoverevent_entitytooltipinfo = (HoverEvent.EntityTooltipInfo) o;

                return this.type.equals(hoverevent_entitytooltipinfo.type) && this.uuid.equals(hoverevent_entitytooltipinfo.uuid) && this.name.equals(hoverevent_entitytooltipinfo.name);
            } else {
                return false;
            }
        }

        public int hashCode() {
            int i = this.type.hashCode();

            i = 31 * i + this.uuid.hashCode();
            i = 31 * i + this.name.hashCode();
            return i;
        }
    }

    public static enum Action implements StringRepresentable {

        SHOW_TEXT("show_text", true, HoverEvent.ShowText.CODEC), SHOW_ITEM("show_item", true, HoverEvent.ShowItem.CODEC), SHOW_ENTITY("show_entity", true, HoverEvent.ShowEntity.CODEC);

        public static final Codec<HoverEvent.Action> UNSAFE_CODEC = StringRepresentable.<HoverEvent.Action>fromValues(HoverEvent.Action::values);
        public static final Codec<HoverEvent.Action> CODEC = HoverEvent.Action.UNSAFE_CODEC.validate(HoverEvent.Action::filterForSerialization);
        private final String name;
        private final boolean allowFromServer;
        private final MapCodec<? extends HoverEvent> codec;

        private Action(String name, boolean allowFromServer, MapCodec<? extends HoverEvent> codec) {
            this.name = name;
            this.allowFromServer = allowFromServer;
            this.codec = codec;
        }

        public boolean isAllowedFromServer() {
            return this.allowFromServer;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public String toString() {
            return "<action " + this.name + ">";
        }

        private static DataResult<HoverEvent.Action> filterForSerialization(HoverEvent.Action action) {
            return !action.isAllowedFromServer() ? DataResult.error(() -> {
                return "Action not allowed: " + String.valueOf(action);
            }) : DataResult.success(action, Lifecycle.stable());
        }
    }
}
