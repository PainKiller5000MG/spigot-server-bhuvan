package net.minecraft.advancements;

import java.time.Instant;
import net.minecraft.network.FriendlyByteBuf;
import org.jspecify.annotations.Nullable;

public class CriterionProgress {

    private @Nullable Instant obtained;

    public CriterionProgress() {}

    public CriterionProgress(Instant obtained) {
        this.obtained = obtained;
    }

    public boolean isDone() {
        return this.obtained != null;
    }

    public void grant() {
        this.obtained = Instant.now();
    }

    public void revoke() {
        this.obtained = null;
    }

    public @Nullable Instant getObtained() {
        return this.obtained;
    }

    public String toString() {
        Object object = this.obtained == null ? "false" : this.obtained;

        return "CriterionProgress{obtained=" + String.valueOf(object) + "}";
    }

    public void serializeToNetwork(FriendlyByteBuf output) {
        output.writeNullable(this.obtained, FriendlyByteBuf::writeInstant);
    }

    public static CriterionProgress fromNetwork(FriendlyByteBuf input) {
        CriterionProgress criterionprogress = new CriterionProgress();

        criterionprogress.obtained = (Instant) input.readNullable(FriendlyByteBuf::readInstant);
        return criterionprogress;
    }
}
