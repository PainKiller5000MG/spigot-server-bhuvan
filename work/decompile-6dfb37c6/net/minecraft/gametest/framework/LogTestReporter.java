package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public class LogTestReporter implements TestReporter {

    private static final Logger LOGGER = LogUtils.getLogger();

    public LogTestReporter() {}

    @Override
    public void onTestFailed(GameTestInfo testInfo) {
        String s = testInfo.getTestBlockPos().toShortString();

        if (testInfo.isRequired()) {
            LogTestReporter.LOGGER.error("{} failed at {}! {}", new Object[]{testInfo.id(), s, Util.describeError(testInfo.getError())});
        } else {
            LogTestReporter.LOGGER.warn("(optional) {} failed at {}. {}", new Object[]{testInfo.id(), s, Util.describeError(testInfo.getError())});
        }

    }

    @Override
    public void onTestSuccess(GameTestInfo testInfo) {}
}
