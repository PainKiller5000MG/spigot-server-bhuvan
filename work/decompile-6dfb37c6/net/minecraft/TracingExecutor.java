package net.minecraft;

import com.mojang.jtracy.TracyClient;
import com.mojang.jtracy.Zone;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public record TracingExecutor(ExecutorService service) implements Executor {

    public Executor forName(String name) {
        return (Executor) (SharedConstants.IS_RUNNING_IN_IDE ? (runnable) -> {
            this.service.execute(() -> {
                Thread thread = Thread.currentThread();
                String s1 = thread.getName();

                thread.setName(name);

                try {
                    Zone zone = TracyClient.beginZone(name, SharedConstants.IS_RUNNING_IN_IDE);

                    try {
                        runnable.run();
                    } catch (Throwable throwable) {
                        if (zone != null) {
                            try {
                                zone.close();
                            } catch (Throwable throwable1) {
                                throwable.addSuppressed(throwable1);
                            }
                        }

                        throw throwable;
                    }

                    if (zone != null) {
                        zone.close();
                    }
                } finally {
                    thread.setName(s1);
                }

            });
        } : (TracyClient.isAvailable() ? (runnable) -> {
            this.service.execute(() -> {
                try (Zone zone = TracyClient.beginZone(name, SharedConstants.IS_RUNNING_IN_IDE)) {
                    runnable.run();
                }

            });
        } : this.service));
    }

    public void execute(Runnable command) {
        this.service.execute(wrapUnnamed(command));
    }

    public void shutdownAndAwait(long timeout, TimeUnit unit) {
        this.service.shutdown();

        boolean flag;

        try {
            flag = this.service.awaitTermination(timeout, unit);
        } catch (InterruptedException interruptedexception) {
            flag = false;
        }

        if (!flag) {
            this.service.shutdownNow();
        }

    }

    private static Runnable wrapUnnamed(Runnable command) {
        return !TracyClient.isAvailable() ? command : () -> {
            try (Zone zone = TracyClient.beginZone("task", SharedConstants.IS_RUNNING_IN_IDE)) {
                command.run();
            }

        };
    }
}
