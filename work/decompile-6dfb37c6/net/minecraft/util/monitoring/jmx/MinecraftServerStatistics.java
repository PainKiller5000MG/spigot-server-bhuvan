package net.minecraft.util.monitoring.jmx;

import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class MinecraftServerStatistics implements DynamicMBean {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final MBeanInfo mBeanInfo;
    private final Map<String, MinecraftServerStatistics.AttributeDescription> attributeDescriptionByName;

    private MinecraftServerStatistics(MinecraftServer server) {
        this.attributeDescriptionByName = (Map) Stream.of(new MinecraftServerStatistics.AttributeDescription("tickTimes", this::getTickTimes, "Historical tick times (ms)", long[].class), new MinecraftServerStatistics.AttributeDescription("averageTickTime", this::getAverageTickTime, "Current average tick time (ms)", Long.TYPE)).collect(Collectors.toMap((minecraftserverstatistics_attributedescription) -> {
            return minecraftserverstatistics_attributedescription.name;
        }, Function.identity()));
        this.server = server;
        MBeanAttributeInfo[] ambeanattributeinfo = (MBeanAttributeInfo[]) this.attributeDescriptionByName.values().stream().map(MinecraftServerStatistics.AttributeDescription::asMBeanAttributeInfo).toArray((i) -> {
            return new MBeanAttributeInfo[i];
        });

        this.mBeanInfo = new MBeanInfo(MinecraftServerStatistics.class.getSimpleName(), "metrics for dedicated server", ambeanattributeinfo, (MBeanConstructorInfo[]) null, (MBeanOperationInfo[]) null, new MBeanNotificationInfo[0]);
    }

    public static void registerJmxMonitoring(MinecraftServer server) {
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(new MinecraftServerStatistics(server), new ObjectName("net.minecraft.server:type=Server"));
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException malformedobjectnameexception) {
            MinecraftServerStatistics.LOGGER.warn("Failed to initialise server as JMX bean", malformedobjectnameexception);
        }

    }

    private float getAverageTickTime() {
        return this.server.getCurrentSmoothedTickTime();
    }

    private long[] getTickTimes() {
        return this.server.getTickTimesNanos();
    }

    public @Nullable Object getAttribute(String attribute) {
        MinecraftServerStatistics.AttributeDescription minecraftserverstatistics_attributedescription = (MinecraftServerStatistics.AttributeDescription) this.attributeDescriptionByName.get(attribute);

        return minecraftserverstatistics_attributedescription == null ? null : minecraftserverstatistics_attributedescription.getter.get();
    }

    public void setAttribute(Attribute attribute) {}

    public AttributeList getAttributes(String[] attributes) {
        Stream stream = Arrays.stream(attributes);
        Map map = this.attributeDescriptionByName;

        Objects.requireNonNull(this.attributeDescriptionByName);
        List<Attribute> list = (List) stream.map(map::get).filter(Objects::nonNull).map((minecraftserverstatistics_attributedescription) -> {
            return new Attribute(minecraftserverstatistics_attributedescription.name, minecraftserverstatistics_attributedescription.getter.get());
        }).collect(Collectors.toList());

        return new AttributeList(list);
    }

    public AttributeList setAttributes(AttributeList attributes) {
        return new AttributeList();
    }

    public @Nullable Object invoke(String actionName, Object[] params, String[] signature) {
        return null;
    }

    public MBeanInfo getMBeanInfo() {
        return this.mBeanInfo;
    }

    private static final class AttributeDescription {

        private final String name;
        private final Supplier<Object> getter;
        private final String description;
        private final Class<?> type;

        private AttributeDescription(String name, Supplier<Object> getter, String description, Class<?> type) {
            this.name = name;
            this.getter = getter;
            this.description = description;
            this.type = type;
        }

        private MBeanAttributeInfo asMBeanAttributeInfo() {
            return new MBeanAttributeInfo(this.name, this.type.getSimpleName(), this.description, true, false, false);
        }
    }
}
