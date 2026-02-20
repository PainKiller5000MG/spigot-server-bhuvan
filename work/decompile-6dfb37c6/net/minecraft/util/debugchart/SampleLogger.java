package net.minecraft.util.debugchart;

public interface SampleLogger {

    void logFullSample(long[] sample);

    void logSample(long sample);

    void logPartialSample(long sample, int dimension);
}
