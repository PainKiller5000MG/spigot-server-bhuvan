package net.minecraft.util.profiling;

import com.mojang.jtracy.Plot;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.util.profiling.metrics.MetricCategory;
import org.slf4j.Logger;

public class TracyZoneFiller implements ProfilerFiller {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(Set.of(Option.RETAIN_CLASS_REFERENCE), 5);
    private final List<com.mojang.jtracy.Zone> activeZones = new ArrayList();
    private final Map<String, TracyZoneFiller.PlotAndValue> plots = new HashMap();
    private final String name = Thread.currentThread().getName();

    public TracyZoneFiller() {}

    @Override
    public void startTick() {}

    @Override
    public void endTick() {
        for (TracyZoneFiller.PlotAndValue tracyzonefiller_plotandvalue : this.plots.values()) {
            tracyzonefiller_plotandvalue.set(0);
        }

    }

    @Override
    public void push(String name) {
        String s1 = "";
        String s2 = "";
        int i = 0;

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            Optional<StackFrame> optional = (Optional) TracyZoneFiller.STACK_WALKER.walk((stream) -> {
                return stream.filter((stackframe) -> {
                    return stackframe.getDeclaringClass() != TracyZoneFiller.class && stackframe.getDeclaringClass() != ProfilerFiller.CombinedProfileFiller.class;
                }).findFirst();
            });

            if (optional.isPresent()) {
                StackFrame stackframe = (StackFrame) optional.get();

                s1 = stackframe.getMethodName();
                s2 = stackframe.getFileName();
                i = stackframe.getLineNumber();
            }
        }

        com.mojang.jtracy.Zone com_mojang_jtracy_zone = TracyClient.beginZone(name, s1, s2, i);

        this.activeZones.add(com_mojang_jtracy_zone);
    }

    @Override
    public void push(Supplier<String> name) {
        this.push((String) name.get());
    }

    @Override
    public void pop() {
        if (this.activeZones.isEmpty()) {
            TracyZoneFiller.LOGGER.error("Tried to pop one too many times! Mismatched push() and pop()?");
        } else {
            com.mojang.jtracy.Zone com_mojang_jtracy_zone = (com.mojang.jtracy.Zone) this.activeZones.removeLast();

            com_mojang_jtracy_zone.close();
        }
    }

    @Override
    public void popPush(String name) {
        this.pop();
        this.push(name);
    }

    @Override
    public void popPush(Supplier<String> name) {
        this.pop();
        this.push((String) name.get());
    }

    @Override
    public void markForCharting(MetricCategory category) {}

    @Override
    public void incrementCounter(String name, int amount) {
        ((TracyZoneFiller.PlotAndValue) this.plots.computeIfAbsent(name, (s1) -> {
            return new TracyZoneFiller.PlotAndValue(this.name + " " + name);
        })).add(amount);
    }

    @Override
    public void incrementCounter(Supplier<String> name, int amount) {
        this.incrementCounter((String) name.get(), amount);
    }

    private com.mojang.jtracy.Zone activeZone() {
        return (com.mojang.jtracy.Zone) this.activeZones.getLast();
    }

    @Override
    public void addZoneText(String text) {
        this.activeZone().addText(text);
    }

    @Override
    public void addZoneValue(long value) {
        this.activeZone().addValue(value);
    }

    @Override
    public void setZoneColor(int color) {
        this.activeZone().setColor(color);
    }

    private static final class PlotAndValue {

        private final Plot plot;
        private int value;

        private PlotAndValue(String name) {
            this.plot = TracyClient.createPlot(name);
            this.value = 0;
        }

        void set(int value) {
            this.value = value;
            this.plot.setValue((double) value);
        }

        void add(int amount) {
            this.set(this.value + amount);
        }
    }
}
