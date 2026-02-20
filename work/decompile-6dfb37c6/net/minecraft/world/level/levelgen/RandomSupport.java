package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public final class RandomSupport {

    public static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    public static final long SILVER_RATIO_64 = 7640891576956012809L;
    private static final HashFunction MD5_128 = Hashing.md5();
    private static final AtomicLong SEED_UNIQUIFIER = new AtomicLong(8682522807148012L);

    public RandomSupport() {}

    @VisibleForTesting
    public static long mixStafford13(long z) {
        z = (z ^ z >>> 30) * -4658895280553007687L;
        z = (z ^ z >>> 27) * -7723592293110705685L;
        return z ^ z >>> 31;
    }

    public static RandomSupport.Seed128bit upgradeSeedTo128bitUnmixed(long legacySeed) {
        long j = legacySeed ^ 7640891576956012809L;
        long k = j + -7046029254386353131L;

        return new RandomSupport.Seed128bit(j, k);
    }

    public static RandomSupport.Seed128bit upgradeSeedTo128bit(long legacySeed) {
        return upgradeSeedTo128bitUnmixed(legacySeed).mixed();
    }

    public static RandomSupport.Seed128bit seedFromHashOf(String input) {
        byte[] abyte = RandomSupport.MD5_128.hashString(input, StandardCharsets.UTF_8).asBytes();
        long i = Longs.fromBytes(abyte[0], abyte[1], abyte[2], abyte[3], abyte[4], abyte[5], abyte[6], abyte[7]);
        long j = Longs.fromBytes(abyte[8], abyte[9], abyte[10], abyte[11], abyte[12], abyte[13], abyte[14], abyte[15]);

        return new RandomSupport.Seed128bit(i, j);
    }

    public static long generateUniqueSeed() {
        return RandomSupport.SEED_UNIQUIFIER.updateAndGet((i) -> {
            return i * 1181783497276652981L;
        }) ^ System.nanoTime();
    }

    public static record Seed128bit(long seedLo, long seedHi) {

        public RandomSupport.Seed128bit xor(long lo, long hi) {
            return new RandomSupport.Seed128bit(this.seedLo ^ lo, this.seedHi ^ hi);
        }

        public RandomSupport.Seed128bit xor(RandomSupport.Seed128bit other) {
            return this.xor(other.seedLo, other.seedHi);
        }

        public RandomSupport.Seed128bit mixed() {
            return new RandomSupport.Seed128bit(RandomSupport.mixStafford13(this.seedLo), RandomSupport.mixStafford13(this.seedHi));
        }
    }
}
