package net.minecraft.gametest.framework;

import com.google.common.base.MoreObjects;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import org.apache.commons.lang3.exception.ExceptionUtils;

class ReportGameListener implements GameTestListener {

    private int attempts = 0;
    private int successes = 0;

    public ReportGameListener() {}

    @Override
    public void testStructureLoaded(GameTestInfo testInfo) {
        ++this.attempts;
    }

    private void handleRetry(GameTestInfo testInfo, GameTestRunner runner, boolean passed) {
        RetryOptions retryoptions = testInfo.retryOptions();
        String s = String.format(Locale.ROOT, "[Run: %4d, Ok: %4d, Fail: %4d", this.attempts, this.successes, this.attempts - this.successes);

        if (!retryoptions.unlimitedTries()) {
            s = s + String.format(Locale.ROOT, ", Left: %4d", retryoptions.numberOfTries() - this.attempts);
        }

        s = s + "]";
        String s1 = String.valueOf(testInfo.id());
        String s2 = s1 + " " + (passed ? "passed" : "failed") + "! " + testInfo.getRunTime() + "ms";
        String s3 = String.format(Locale.ROOT, "%-53s%s", s, s2);

        if (passed) {
            reportPassed(testInfo, s3);
        } else {
            say(testInfo.getLevel(), ChatFormatting.RED, s3);
        }

        if (retryoptions.hasTriesLeft(this.attempts, this.successes)) {
            runner.rerunTest(testInfo);
        }

    }

    @Override
    public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
        ++this.successes;
        if (testInfo.retryOptions().hasRetries()) {
            this.handleRetry(testInfo, runner, true);
        } else if (!testInfo.isFlaky()) {
            String s = String.valueOf(testInfo.id());

            reportPassed(testInfo, s + " passed! (" + testInfo.getRunTime() + "ms / " + testInfo.getTick() + "gameticks)");
        } else {
            if (this.successes >= testInfo.requiredSuccesses()) {
                String s1 = String.valueOf(testInfo);

                reportPassed(testInfo, s1 + " passed " + this.successes + " times of " + this.attempts + " attempts.");
            } else {
                ServerLevel serverlevel = testInfo.getLevel();
                ChatFormatting chatformatting = ChatFormatting.GREEN;
                String s2 = String.valueOf(testInfo);

                say(serverlevel, chatformatting, "Flaky test " + s2 + " succeeded, attempt: " + this.attempts + " successes: " + this.successes);
                runner.rerunTest(testInfo);
            }

        }
    }

    @Override
    public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
        if (!testInfo.isFlaky()) {
            reportFailure(testInfo, testInfo.getError());
            if (testInfo.retryOptions().hasRetries()) {
                this.handleRetry(testInfo, runner, false);
            }

        } else {
            GameTestInstance gametestinstance = testInfo.getTest();
            String s = String.valueOf(testInfo);
            String s1 = "Flaky test " + s + " failed, attempt: " + this.attempts + "/" + gametestinstance.maxAttempts();

            if (gametestinstance.requiredSuccesses() > 1) {
                s1 = s1 + ", successes: " + this.successes + " (" + gametestinstance.requiredSuccesses() + " required)";
            }

            say(testInfo.getLevel(), ChatFormatting.YELLOW, s1);
            if (testInfo.maxAttempts() - this.attempts + this.successes >= testInfo.requiredSuccesses()) {
                runner.rerunTest(testInfo);
            } else {
                reportFailure(testInfo, new ExhaustedAttemptsException(this.attempts, this.successes, testInfo));
            }

        }
    }

    @Override
    public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
        copy.addListener(this);
    }

    public static void reportPassed(GameTestInfo testInfo, String text) {
        getTestInstanceBlockEntity(testInfo).ifPresent((testinstanceblockentity) -> {
            testinstanceblockentity.setSuccess();
        });
        visualizePassedTest(testInfo, text);
    }

    private static void visualizePassedTest(GameTestInfo testInfo, String text) {
        say(testInfo.getLevel(), ChatFormatting.GREEN, text);
        GlobalTestReporter.onTestSuccess(testInfo);
    }

    protected static void reportFailure(GameTestInfo testInfo, Throwable error) {
        Component component;

        if (error instanceof GameTestAssertException gametestassertexception) {
            component = gametestassertexception.getDescription();
        } else {
            component = Component.literal(Util.describeError(error));
        }

        getTestInstanceBlockEntity(testInfo).ifPresent((testinstanceblockentity) -> {
            testinstanceblockentity.setErrorMessage(component);
        });
        visualizeFailedTest(testInfo, error);
    }

    protected static void visualizeFailedTest(GameTestInfo testInfo, Throwable error) {
        String s = error.getMessage();
        String s1 = s + (error.getCause() == null ? "" : " cause: " + Util.describeError(error.getCause()));

        s = testInfo.isRequired() ? "" : "(optional) ";
        String s2 = s + String.valueOf(testInfo.id()) + " failed! " + s1;

        say(testInfo.getLevel(), testInfo.isRequired() ? ChatFormatting.RED : ChatFormatting.YELLOW, s2);
        Throwable throwable1 = (Throwable) MoreObjects.firstNonNull(ExceptionUtils.getRootCause(error), error);

        if (throwable1 instanceof GameTestAssertPosException gametestassertposexception) {
            testInfo.getTestInstanceBlockEntity().markError(gametestassertposexception.getAbsolutePos(), gametestassertposexception.getMessageToShowAtBlock());
        }

        GlobalTestReporter.onTestFailed(testInfo);
    }

    private static Optional<TestInstanceBlockEntity> getTestInstanceBlockEntity(GameTestInfo testInfo) {
        ServerLevel serverlevel = testInfo.getLevel();
        Optional<BlockPos> optional = Optional.ofNullable(testInfo.getTestBlockPos());
        Optional<TestInstanceBlockEntity> optional1 = optional.flatMap((blockpos) -> {
            return serverlevel.getBlockEntity(blockpos, BlockEntityType.TEST_INSTANCE_BLOCK);
        });

        return optional1;
    }

    protected static void say(ServerLevel level, ChatFormatting format, String text) {
        level.getPlayers((serverplayer) -> {
            return true;
        }).forEach((serverplayer) -> {
            serverplayer.sendSystemMessage(Component.literal(text).withStyle(format));
        });
    }
}
