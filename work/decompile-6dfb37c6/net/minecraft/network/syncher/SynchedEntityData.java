package net.minecraft.network.syncher;

import com.mojang.logging.LogUtils;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.ClassTreeIdRegistry;
import org.apache.commons.lang3.ObjectUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SynchedEntityData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ID_VALUE = 254;
    private static final ClassTreeIdRegistry ID_REGISTRY = new ClassTreeIdRegistry();
    private final SyncedDataHolder entity;
    private final SynchedEntityData.DataItem<?>[] itemsById;
    private boolean isDirty;

    private SynchedEntityData(SyncedDataHolder entity, SynchedEntityData.DataItem<?>[] itemsById) {
        this.entity = entity;
        this.itemsById = itemsById;
    }

    public static <T> EntityDataAccessor<T> defineId(Class<? extends SyncedDataHolder> clazz, EntityDataSerializer<T> type) {
        if (SynchedEntityData.LOGGER.isDebugEnabled()) {
            try {
                Class<?> oclass1 = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());

                if (!oclass1.equals(clazz)) {
                    SynchedEntityData.LOGGER.debug("defineId called for: {} from {}", new Object[]{clazz, oclass1, new RuntimeException()});
                }
            } catch (ClassNotFoundException classnotfoundexception) {
                ;
            }
        }

        int i = SynchedEntityData.ID_REGISTRY.define(clazz);

        if (i > 254) {
            throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is 254)");
        } else {
            return type.createAccessor(i);
        }
    }

    private <T> SynchedEntityData.DataItem<T> getItem(EntityDataAccessor<T> accessor) {
        return this.itemsById[accessor.id()];
    }

    public <T> T get(EntityDataAccessor<T> accessor) {
        return (T) this.getItem(accessor).getValue();
    }

    public <T> void set(EntityDataAccessor<T> accessor, T value) {
        this.set(accessor, value, false);
    }

    public <T> void set(EntityDataAccessor<T> accessor, T value, boolean forceDirty) {
        SynchedEntityData.DataItem<T> synchedentitydata_dataitem = this.<T>getItem(accessor);

        if (forceDirty || ObjectUtils.notEqual(value, synchedentitydata_dataitem.getValue())) {
            synchedentitydata_dataitem.setValue(value);
            this.entity.onSyncedDataUpdated(accessor);
            synchedentitydata_dataitem.setDirty(true);
            this.isDirty = true;
        }

    }

    public boolean isDirty() {
        return this.isDirty;
    }

    public @Nullable List<SynchedEntityData.DataValue<?>> packDirty() {
        if (!this.isDirty) {
            return null;
        } else {
            this.isDirty = false;
            List<SynchedEntityData.DataValue<?>> list = new ArrayList();

            for (SynchedEntityData.DataItem<?> synchedentitydata_dataitem : this.itemsById) {
                if (synchedentitydata_dataitem.isDirty()) {
                    synchedentitydata_dataitem.setDirty(false);
                    list.add(synchedentitydata_dataitem.value());
                }
            }

            return list;
        }
    }

    public @Nullable List<SynchedEntityData.DataValue<?>> getNonDefaultValues() {
        List<SynchedEntityData.DataValue<?>> list = null;

        for (SynchedEntityData.DataItem<?> synchedentitydata_dataitem : this.itemsById) {
            if (!synchedentitydata_dataitem.isSetToDefault()) {
                if (list == null) {
                    list = new ArrayList();
                }

                list.add(synchedentitydata_dataitem.value());
            }
        }

        return list;
    }

    public void assignValues(List<SynchedEntityData.DataValue<?>> items) {
        for (SynchedEntityData.DataValue<?> synchedentitydata_datavalue : items) {
            SynchedEntityData.DataItem<?> synchedentitydata_dataitem = this.itemsById[synchedentitydata_datavalue.id];

            this.assignValue(synchedentitydata_dataitem, synchedentitydata_datavalue);
            this.entity.onSyncedDataUpdated(synchedentitydata_dataitem.getAccessor());
        }

        this.entity.onSyncedDataUpdated(items);
    }

    private <T> void assignValue(SynchedEntityData.DataItem<T> dataItem, SynchedEntityData.DataValue<?> item) {
        if (!Objects.equals(item.serializer(), dataItem.accessor.serializer())) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Invalid entity data item type for field %d on entity %s: old=%s(%s), new=%s(%s)", dataItem.accessor.id(), this.entity, dataItem.value, dataItem.value.getClass(), item.value, item.value.getClass()));
        } else {
            dataItem.setValue(item.value);
        }
    }

    public static record DataValue<T>(int id, EntityDataSerializer<T> serializer, T value) {

        public static <T> SynchedEntityData.DataValue<T> create(EntityDataAccessor<T> accessor, T value) {
            EntityDataSerializer<T> entitydataserializer = accessor.serializer();

            return new SynchedEntityData.DataValue<T>(accessor.id(), entitydataserializer, entitydataserializer.copy(value));
        }

        public void write(RegistryFriendlyByteBuf output) {
            int i = EntityDataSerializers.getSerializedId(this.serializer);

            if (i < 0) {
                throw new EncoderException("Unknown serializer type " + String.valueOf(this.serializer));
            } else {
                output.writeByte(this.id);
                output.writeVarInt(i);
                this.serializer.codec().encode(output, this.value);
            }
        }

        public static SynchedEntityData.DataValue<?> read(RegistryFriendlyByteBuf input, int id) {
            int j = input.readVarInt();
            EntityDataSerializer<?> entitydataserializer = EntityDataSerializers.getSerializer(j);

            if (entitydataserializer == null) {
                throw new DecoderException("Unknown serializer type " + j);
            } else {
                return read(input, id, entitydataserializer);
            }
        }

        private static <T> SynchedEntityData.DataValue<T> read(RegistryFriendlyByteBuf input, int id, EntityDataSerializer<T> serializer) {
            return new SynchedEntityData.DataValue<T>(id, serializer, serializer.codec().decode(input));
        }
    }

    public static class DataItem<T> {

        private final EntityDataAccessor<T> accessor;
        private T value;
        private final T initialValue;
        private boolean dirty;

        public DataItem(EntityDataAccessor<T> accessor, T initialValue) {
            this.accessor = accessor;
            this.initialValue = initialValue;
            this.value = initialValue;
        }

        public EntityDataAccessor<T> getAccessor() {
            return this.accessor;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public T getValue() {
            return this.value;
        }

        public boolean isDirty() {
            return this.dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean isSetToDefault() {
            return this.initialValue.equals(this.value);
        }

        public SynchedEntityData.DataValue<T> value() {
            return SynchedEntityData.DataValue.<T>create(this.accessor, this.value);
        }
    }

    public static class Builder {

        private final SyncedDataHolder entity;
        private final @Nullable SynchedEntityData.DataItem<?>[] itemsById;

        public Builder(SyncedDataHolder entity) {
            this.entity = entity;
            this.itemsById = new SynchedEntityData.DataItem[SynchedEntityData.ID_REGISTRY.getCount(entity.getClass())];
        }

        public <T> SynchedEntityData.Builder define(EntityDataAccessor<T> accessor, T value) {
            int i = accessor.id();

            if (i > this.itemsById.length) {
                throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is " + this.itemsById.length + ")");
            } else if (this.itemsById[i] != null) {
                throw new IllegalArgumentException("Duplicate id value for " + i + "!");
            } else if (EntityDataSerializers.getSerializedId(accessor.serializer()) < 0) {
                String s = String.valueOf(accessor.serializer());

                throw new IllegalArgumentException("Unregistered serializer " + s + " for " + i + "!");
            } else {
                this.itemsById[accessor.id()] = new SynchedEntityData.DataItem(accessor, value);
                return this;
            }
        }

        public SynchedEntityData build() {
            for (int i = 0; i < this.itemsById.length; ++i) {
                if (this.itemsById[i] == null) {
                    String s = String.valueOf(this.entity.getClass());

                    throw new IllegalStateException("Entity " + s + " has not defined synched data value " + i);
                }
            }

            return new SynchedEntityData(this.entity, this.itemsById);
        }
    }
}
