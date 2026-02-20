package net.minecraft.world.level.saveddata;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.util.datafix.DataFixTypes;

public record SavedDataType<T extends SavedData>(String id, Supplier<T> constructor, Codec<T> codec, DataFixTypes dataFixType) {

    public boolean equals(Object obj) {
        boolean flag;

        if (obj instanceof SavedDataType<?> saveddatatype) {
            if (this.id.equals(saveddatatype.id)) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    public int hashCode() {
        return this.id.hashCode();
    }

    public String toString() {
        return "SavedDataType[" + this.id + "]";
    }
}
