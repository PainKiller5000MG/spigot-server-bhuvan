package net.minecraft.gametest.framework;

public class GlobalTestReporter {

    private static TestReporter DELEGATE = new LogTestReporter();

    public GlobalTestReporter() {}

    public static void replaceWith(TestReporter testReporter) {
        GlobalTestReporter.DELEGATE = testReporter;
    }

    public static void onTestFailed(GameTestInfo testInfo) {
        GlobalTestReporter.DELEGATE.onTestFailed(testInfo);
    }

    public static void onTestSuccess(GameTestInfo testInfo) {
        GlobalTestReporter.DELEGATE.onTestSuccess(testInfo);
    }

    public static void finish() {
        GlobalTestReporter.DELEGATE.finish();
    }
}
