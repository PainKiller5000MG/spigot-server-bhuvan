package net.minecraft.util;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.jtracy.TracyClient;
import com.mojang.jtracy.Zone;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.CharPredicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.TracingExecutor;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Util {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_MAX_THREADS = 255;
    private static final int DEFAULT_SAFE_FILE_OPERATION_RETRIES = 10;
    private static final String MAX_THREADS_SYSTEM_PROPERTY = "max.bg.threads";
    private static final TracingExecutor BACKGROUND_EXECUTOR = makeExecutor("Main");
    private static final TracingExecutor IO_POOL = makeIoExecutor("IO-Worker-", false);
    private static final TracingExecutor DOWNLOAD_POOL = makeIoExecutor("Download-", true);
    private static final DateTimeFormatter FILENAME_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    public static final int LINEAR_LOOKUP_THRESHOLD = 8;
    private static final Set<String> ALLOWED_UNTRUSTED_LINK_PROTOCOLS = Set.of("http", "https");
    public static final long NANOS_PER_MILLI = 1000000L;
    public static TimeSource.NanoTimeSource timeSource = System::nanoTime;
    public static final Ticker TICKER = new Ticker() {
        public long read() {
            return Util.timeSource.getAsLong();
        }
    };
    public static final UUID NIL_UUID = new UUID(0L, 0L);
    public static final FileSystemProvider ZIP_FILE_SYSTEM_PROVIDER = (FileSystemProvider) FileSystemProvider.installedProviders().stream().filter((filesystemprovider) -> {
        return filesystemprovider.getScheme().equalsIgnoreCase("jar");
    }).findFirst().orElseThrow(() -> {
        return new IllegalStateException("No jar file system provider found");
    });
    private static Consumer<String> thePauser = (s) -> {
    };

    public Util() {}

    public static <K, V> Collector<Map.Entry<? extends K, ? extends V>, ?, Map<K, V>> toMap() {
        return Collectors.toMap(Entry::getKey, Entry::getValue);
    }

    public static <T> Collector<T, ?, List<T>> toMutableList() {
        return Collectors.toCollection(Lists::newArrayList);
    }

    public static <T extends Comparable<T>> String getPropertyName(Property<T> key, Object value) {
        return key.getName((Comparable) value);
    }

    public static String makeDescriptionId(String prefix, @Nullable Identifier location) {
        return location == null ? prefix + ".unregistered_sadface" : prefix + "." + location.getNamespace() + "." + location.getPath().replace('/', '.');
    }

    public static long getMillis() {
        return getNanos() / 1000000L;
    }

    public static long getNanos() {
        return Util.timeSource.getAsLong();
    }

    public static long getEpochMillis() {
        return Instant.now().toEpochMilli();
    }

    public static String getFilenameFormattedDateTime() {
        return Util.FILENAME_DATE_TIME_FORMATTER.format(ZonedDateTime.now());
    }

    private static TracingExecutor makeExecutor(String name) {
        int i = maxAllowedExecutorThreads();
        ExecutorService executorservice;

        if (i <= 0) {
            executorservice = MoreExecutors.newDirectExecutorService();
        } else {
            AtomicInteger atomicinteger = new AtomicInteger(1);

            executorservice = new ForkJoinPool(i, (forkjoinpool) -> {
                final String s1 = "Worker-" + name + "-" + atomicinteger.getAndIncrement();
                ForkJoinWorkerThread forkjoinworkerthread = new ForkJoinWorkerThread(forkjoinpool) {
                    protected void onStart() {
                        TracyClient.setThreadName(s1, name.hashCode());
                        super.onStart();
                    }

                    protected void onTermination(@Nullable Throwable exception) {
                        if (exception != null) {
                            Util.LOGGER.warn("{} died", this.getName(), exception);
                        } else {
                            Util.LOGGER.debug("{} shutdown", this.getName());
                        }

                        super.onTermination(exception);
                    }
                };

                forkjoinworkerthread.setName(s1);
                return forkjoinworkerthread;
            }, Util::onThreadException, true);
        }

        return new TracingExecutor(executorservice);
    }

    public static int maxAllowedExecutorThreads() {
        return Mth.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, getMaxThreads());
    }

    private static int getMaxThreads() {
        String s = System.getProperty("max.bg.threads");

        if (s != null) {
            try {
                int i = Integer.parseInt(s);

                if (i >= 1 && i <= 255) {
                    return i;
                }

                Util.LOGGER.error("Wrong {} property value '{}'. Should be an integer value between 1 and {}.", new Object[]{"max.bg.threads", s, 255});
            } catch (NumberFormatException numberformatexception) {
                Util.LOGGER.error("Could not parse {} property value '{}'. Should be an integer value between 1 and {}.", new Object[]{"max.bg.threads", s, 255});
            }
        }

        return 255;
    }

    public static TracingExecutor backgroundExecutor() {
        return Util.BACKGROUND_EXECUTOR;
    }

    public static TracingExecutor ioPool() {
        return Util.IO_POOL;
    }

    public static TracingExecutor nonCriticalIoPool() {
        return Util.DOWNLOAD_POOL;
    }

    public static void shutdownExecutors() {
        Util.BACKGROUND_EXECUTOR.shutdownAndAwait(3L, TimeUnit.SECONDS);
        Util.IO_POOL.shutdownAndAwait(3L, TimeUnit.SECONDS);
    }

    private static TracingExecutor makeIoExecutor(String prefix, boolean daemon) {
        AtomicInteger atomicinteger = new AtomicInteger(1);

        return new TracingExecutor(Executors.newCachedThreadPool((runnable) -> {
            Thread thread = new Thread(runnable);
            String s1 = prefix + atomicinteger.getAndIncrement();

            TracyClient.setThreadName(s1, prefix.hashCode());
            thread.setName(s1);
            thread.setDaemon(daemon);
            thread.setUncaughtExceptionHandler(Util::onThreadException);
            return thread;
        }));
    }

    public static void throwAsRuntime(Throwable throwable) {
        throw throwable instanceof RuntimeException ? (RuntimeException) throwable : new RuntimeException(throwable);
    }

    private static void onThreadException(Thread thread, Throwable throwable) {
        pauseInIde(throwable);
        if (throwable instanceof CompletionException) {
            throwable = throwable.getCause();
        }

        if (throwable instanceof ReportedException reportedexception) {
            Bootstrap.realStdoutPrintln(reportedexception.getReport().getFriendlyReport(ReportType.CRASH));
            System.exit(-1);
        }

        Util.LOGGER.error("Caught exception in thread {}", thread, throwable);
    }

    public static @Nullable Type<?> fetchChoiceType(TypeReference reference, String name) {
        return !SharedConstants.CHECK_DATA_FIXER_SCHEMA ? null : doFetchChoiceType(reference, name);
    }

    private static @Nullable Type<?> doFetchChoiceType(TypeReference reference, String name) {
        Type<?> type = null;

        try {
            type = DataFixers.getDataFixer().getSchema(DataFixUtils.makeKey(SharedConstants.getCurrentVersion().dataVersion().version())).getChoiceType(reference, name);
        } catch (IllegalArgumentException illegalargumentexception) {
            Util.LOGGER.error("No data fixer registered for {}", name);
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                throw illegalargumentexception;
            }
        }

        return type;
    }

    public static void runNamed(Runnable runnable, String name) {
        if (SharedConstants.IS_RUNNING_IN_IDE) {
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
        } else {
            try (Zone zone1 = TracyClient.beginZone(name, SharedConstants.IS_RUNNING_IN_IDE)) {
                runnable.run();
            }
        }

    }

    public static <T> String getRegisteredName(Registry<T> registry, T entry) {
        Identifier identifier = registry.getKey(entry);

        return identifier == null ? "[unregistered]" : identifier.toString();
    }

    public static <T> Predicate<T> allOf() {
        return (object) -> {
            return true;
        };
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> condition) {
        return condition;
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> condition1, Predicate<? super T> condition2) {
        return (object) -> {
            return condition1.test(object) && condition2.test(object);
        };
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> condition1, Predicate<? super T> condition2, Predicate<? super T> condition3) {
        return (object) -> {
            return condition1.test(object) && condition2.test(object) && condition3.test(object);
        };
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> condition1, Predicate<? super T> condition2, Predicate<? super T> condition3, Predicate<? super T> condition4) {
        return (object) -> {
            return condition1.test(object) && condition2.test(object) && condition3.test(object) && condition4.test(object);
        };
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> condition1, Predicate<? super T> condition2, Predicate<? super T> condition3, Predicate<? super T> condition4, Predicate<? super T> condition5) {
        return (object) -> {
            return condition1.test(object) && condition2.test(object) && condition3.test(object) && condition4.test(object) && condition5.test(object);
        };
    }

    @SafeVarargs
    public static <T> Predicate<T> allOf(Predicate<? super T>... conditions) {
        return (object) -> {
            for (Predicate<? super T> predicate : conditions) {
                if (!predicate.test(object)) {
                    return false;
                }
            }

            return true;
        };
    }

    public static <T> Predicate<T> allOf(List<? extends Predicate<? super T>> conditions) {
        Predicate predicate;

        switch (conditions.size()) {
            case 0:
                predicate = allOf();
                break;
            case 1:
                predicate = allOf((Predicate) conditions.get(0));
                break;
            case 2:
                predicate = allOf((Predicate) conditions.get(0), (Predicate) conditions.get(1));
                break;
            case 3:
                predicate = allOf((Predicate) conditions.get(0), (Predicate) conditions.get(1), (Predicate) conditions.get(2));
                break;
            case 4:
                predicate = allOf((Predicate) conditions.get(0), (Predicate) conditions.get(1), (Predicate) conditions.get(2), (Predicate) conditions.get(3));
                break;
            case 5:
                predicate = allOf((Predicate) conditions.get(0), (Predicate) conditions.get(1), (Predicate) conditions.get(2), (Predicate) conditions.get(3), (Predicate) conditions.get(4));
                break;
            default:
                Predicate<? super T>[] apredicate = (Predicate[]) conditions.toArray((i) -> {
                    return new Predicate[i];
                });

                predicate = allOf(apredicate);
        }

        return predicate;
    }

    public static <T> Predicate<T> anyOf() {
        return (object) -> {
            return false;
        };
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> condition1) {
        return condition1;
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> condition1, Predicate<? super T> condition2) {
        return (object) -> {
            return condition1.test(object) || condition2.test(object);
        };
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> condition1, Predicate<? super T> condition2, Predicate<? super T> condition3) {
        return (object) -> {
            return condition1.test(object) || condition2.test(object) || condition3.test(object);
        };
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> condition1, Predicate<? super T> condition2, Predicate<? super T> condition3, Predicate<? super T> condition4) {
        return (object) -> {
            return condition1.test(object) || condition2.test(object) || condition3.test(object) || condition4.test(object);
        };
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> condition1, Predicate<? super T> condition2, Predicate<? super T> condition3, Predicate<? super T> condition4, Predicate<? super T> condition5) {
        return (object) -> {
            return condition1.test(object) || condition2.test(object) || condition3.test(object) || condition4.test(object) || condition5.test(object);
        };
    }

    @SafeVarargs
    public static <T> Predicate<T> anyOf(Predicate<? super T>... conditions) {
        return (object) -> {
            for (Predicate<? super T> predicate : conditions) {
                if (predicate.test(object)) {
                    return true;
                }
            }

            return false;
        };
    }

    public static <T> Predicate<T> anyOf(List<? extends Predicate<? super T>> conditions) {
        Predicate predicate;

        switch (conditions.size()) {
            case 0:
                predicate = anyOf();
                break;
            case 1:
                predicate = anyOf((Predicate) conditions.get(0));
                break;
            case 2:
                predicate = anyOf((Predicate) conditions.get(0), (Predicate) conditions.get(1));
                break;
            case 3:
                predicate = anyOf((Predicate) conditions.get(0), (Predicate) conditions.get(1), (Predicate) conditions.get(2));
                break;
            case 4:
                predicate = anyOf((Predicate) conditions.get(0), (Predicate) conditions.get(1), (Predicate) conditions.get(2), (Predicate) conditions.get(3));
                break;
            case 5:
                predicate = anyOf((Predicate) conditions.get(0), (Predicate) conditions.get(1), (Predicate) conditions.get(2), (Predicate) conditions.get(3), (Predicate) conditions.get(4));
                break;
            default:
                Predicate<? super T>[] apredicate = (Predicate[]) conditions.toArray((i) -> {
                    return new Predicate[i];
                });

                predicate = anyOf(apredicate);
        }

        return predicate;
    }

    public static <T> boolean isSymmetrical(int width, int height, List<T> ingredients) {
        if (width == 1) {
            return true;
        } else {
            int k = width / 2;

            for (int l = 0; l < height; ++l) {
                for (int i1 = 0; i1 < k; ++i1) {
                    int j1 = width - 1 - i1;
                    T t0 = (T) ingredients.get(i1 + l * width);
                    T t1 = (T) ingredients.get(j1 + l * width);

                    if (!t0.equals(t1)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public static int growByHalf(int currentSize, int minimalNewSize) {
        return (int) Math.max(Math.min((long) currentSize + (long) (currentSize >> 1), 2147483639L), (long) minimalNewSize);
    }

    @SuppressForbidden(reason = "Intentional use of default locale for user-visible date")
    public static DateTimeFormatter localizedDateFormatter(FormatStyle formatStyle) {
        return DateTimeFormatter.ofLocalizedDateTime(formatStyle);
    }

    public static Util.OS getPlatform() {
        String s = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        return s.contains("win") ? Util.OS.WINDOWS : (s.contains("mac") ? Util.OS.OSX : (s.contains("solaris") ? Util.OS.SOLARIS : (s.contains("sunos") ? Util.OS.SOLARIS : (s.contains("linux") ? Util.OS.LINUX : (s.contains("unix") ? Util.OS.LINUX : Util.OS.UNKNOWN)))));
    }

    public static boolean isAarch64() {
        String s = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        return s.equals("aarch64");
    }

    public static URI parseAndValidateUntrustedUri(String uri) throws URISyntaxException {
        URI uri1 = new URI(uri);
        String s1 = uri1.getScheme();

        if (s1 == null) {
            throw new URISyntaxException(uri, "Missing protocol in URI: " + uri);
        } else {
            String s2 = s1.toLowerCase(Locale.ROOT);

            if (!Util.ALLOWED_UNTRUSTED_LINK_PROTOCOLS.contains(s2)) {
                throw new URISyntaxException(uri, "Unsupported protocol in URI: " + uri);
            } else {
                return uri1;
            }
        }
    }

    public static <T> T findNextInIterable(Iterable<T> collection, @Nullable T current) {
        Iterator<T> iterator = collection.iterator();
        T t1 = (T) iterator.next();

        if (current != null) {
            T t2 = t1;

            while (t2 != current) {
                if (iterator.hasNext()) {
                    t2 = (T) iterator.next();
                }
            }

            if (iterator.hasNext()) {
                return (T) iterator.next();
            }
        }

        return t1;
    }

    public static <T> T findPreviousInIterable(Iterable<T> collection, @Nullable T current) {
        Iterator<T> iterator = collection.iterator();

        T t1;
        T t2;

        for (t1 = null; iterator.hasNext(); t1 = t2) {
            t2 = (T) iterator.next();
            if (t2 == current) {
                if (t1 == null) {
                    t1 = iterator.hasNext() ? Iterators.getLast(iterator) : current;
                }
                break;
            }
        }

        return t1;
    }

    public static <T> T make(Supplier<T> factory) {
        return (T) factory.get();
    }

    public static <T> T make(T t, Consumer<? super T> consumer) {
        consumer.accept(t);
        return t;
    }

    public static <K extends Enum<K>, V> Map<K, V> makeEnumMap(Class<K> keyType, Function<K, V> function) {
        EnumMap<K, V> enummap = new EnumMap(keyType);

        for (K k0 : (Enum[]) keyType.getEnumConstants()) {
            enummap.put(k0, function.apply(k0));
        }

        return enummap;
    }

    public static <K, V1, V2> Map<K, V2> mapValues(Map<K, V1> map, Function<? super V1, V2> valueMapper) {
        return (Map) map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, (entry) -> {
            return valueMapper.apply(entry.getValue());
        }));
    }

    public static <K, V1, V2> Map<K, V2> mapValuesLazy(Map<K, V1> map, com.google.common.base.Function<V1, V2> valueMapper) {
        return Maps.transformValues(map, valueMapper);
    }

    public static <V extends @Nullable Object> CompletableFuture<List<V>> sequence(List<? extends CompletableFuture<V>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        } else if (futures.size() == 1) {
            return ((CompletableFuture) futures.getFirst()).thenApply(ObjectLists::singleton);
        } else {
            CompletableFuture<Void> completablefuture = CompletableFuture.allOf((CompletableFuture[]) futures.toArray(new CompletableFuture[0]));

            return completablefuture.thenApply((ovoid) -> {
                return futures.stream().map(CompletableFuture::join).toList();
            });
        }
    }

    public static <V extends @Nullable Object> CompletableFuture<List<V>> sequenceFailFast(List<? extends CompletableFuture<? extends V>> futures) {
        CompletableFuture<List<V>> completablefuture = new CompletableFuture();

        Objects.requireNonNull(completablefuture);
        return fallibleSequence(futures, completablefuture::completeExceptionally).applyToEither(completablefuture, Function.identity());
    }

    public static <V extends @Nullable Object> CompletableFuture<List<V>> sequenceFailFastAndCancel(List<? extends CompletableFuture<? extends V>> futures) {
        CompletableFuture<List<V>> completablefuture = new CompletableFuture();

        return fallibleSequence(futures, (throwable) -> {
            if (completablefuture.completeExceptionally(throwable)) {
                for (CompletableFuture<? extends V> completablefuture1 : futures) {
                    completablefuture1.cancel(true);
                }
            }

        }).applyToEither(completablefuture, Function.identity());
    }

    private static <V extends @Nullable Object> CompletableFuture<List<V>> fallibleSequence(List<? extends CompletableFuture<? extends V>> futures, Consumer<Throwable> failureHandler) {
        ObjectArrayList<V> objectarraylist = new ObjectArrayList();

        objectarraylist.size(futures.size());
        CompletableFuture<?>[] acompletablefuture = new CompletableFuture[futures.size()];

        for (int i = 0; i < futures.size(); ++i) {
            acompletablefuture[i] = ((CompletableFuture) futures.get(i)).whenComplete((object, throwable) -> {
                if (throwable != null) {
                    failureHandler.accept(throwable);
                } else {
                    objectarraylist.set(i, object);
                }

            });
        }

        return CompletableFuture.allOf(acompletablefuture).thenApply((ovoid) -> {
            return objectarraylist;
        });
    }

    public static <T> Optional<T> ifElse(Optional<T> input, Consumer<T> onTrue, Runnable onFalse) {
        if (input.isPresent()) {
            onTrue.accept(input.get());
        } else {
            onFalse.run();
        }

        return input;
    }

    public static <T> Supplier<T> name(final Supplier<T> task, Supplier<String> nameGetter) {
        if (SharedConstants.DEBUG_NAMED_RUNNABLES) {
            final String s = (String) nameGetter.get();

            return new Supplier<T>() {
                public T get() {
                    return (T) task.get();
                }

                public String toString() {
                    return s;
                }
            };
        } else {
            return task;
        }
    }

    public static Runnable name(final Runnable task, Supplier<String> nameGetter) {
        if (SharedConstants.DEBUG_NAMED_RUNNABLES) {
            final String s = (String) nameGetter.get();

            return new Runnable() {
                public void run() {
                    task.run();
                }

                public String toString() {
                    return s;
                }
            };
        } else {
            return task;
        }
    }

    public static void logAndPauseIfInIde(String message) {
        Util.LOGGER.error(message);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            doPause(message);
        }

    }

    public static void logAndPauseIfInIde(String message, Throwable throwable) {
        Util.LOGGER.error(message, throwable);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            doPause(message);
        }

    }

    public static <T extends Throwable> T pauseInIde(T t) {
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            Util.LOGGER.error("Trying to throw a fatal exception, pausing in IDE", t);
            doPause(t.getMessage());
        }

        return t;
    }

    public static void setPause(Consumer<String> pauseFunction) {
        Util.thePauser = pauseFunction;
    }

    private static void doPause(String message) {
        Instant instant = Instant.now();

        Util.LOGGER.warn("Did you remember to set a breakpoint here?");
        boolean flag = Duration.between(instant, Instant.now()).toMillis() > 500L;

        if (!flag) {
            Util.thePauser.accept(message);
        }

    }

    public static String describeError(Throwable err) {
        return err.getCause() != null ? describeError(err.getCause()) : (err.getMessage() != null ? err.getMessage() : err.toString());
    }

    public static <T> T getRandom(T[] array, RandomSource random) {
        return (T) array[random.nextInt(array.length)];
    }

    public static int getRandom(int[] array, RandomSource random) {
        return array[random.nextInt(array.length)];
    }

    public static <T> T getRandom(List<T> list, RandomSource random) {
        return (T) list.get(random.nextInt(list.size()));
    }

    public static <T> Optional<T> getRandomSafe(List<T> list, RandomSource random) {
        return list.isEmpty() ? Optional.empty() : Optional.of(getRandom(list, random));
    }

    private static BooleanSupplier createRenamer(final Path from, final Path to) {
        return new BooleanSupplier() {
            public boolean getAsBoolean() {
                try {
                    Files.move(from, to);
                    return true;
                } catch (IOException ioexception) {
                    Util.LOGGER.error("Failed to rename", ioexception);
                    return false;
                }
            }

            public String toString() {
                String s = String.valueOf(from);

                return "rename " + s + " to " + String.valueOf(to);
            }
        };
    }

    private static BooleanSupplier createDeleter(final Path target) {
        return new BooleanSupplier() {
            public boolean getAsBoolean() {
                try {
                    Files.deleteIfExists(target);
                    return true;
                } catch (IOException ioexception) {
                    Util.LOGGER.warn("Failed to delete", ioexception);
                    return false;
                }
            }

            public String toString() {
                return "delete old " + String.valueOf(target);
            }
        };
    }

    private static BooleanSupplier createFileDeletedCheck(final Path target) {
        return new BooleanSupplier() {
            public boolean getAsBoolean() {
                return !Files.exists(target, new LinkOption[0]);
            }

            public String toString() {
                return "verify that " + String.valueOf(target) + " is deleted";
            }
        };
    }

    private static BooleanSupplier createFileCreatedCheck(final Path target) {
        return new BooleanSupplier() {
            public boolean getAsBoolean() {
                return Files.isRegularFile(target, new LinkOption[0]);
            }

            public String toString() {
                return "verify that " + String.valueOf(target) + " is present";
            }
        };
    }

    private static boolean executeInSequence(BooleanSupplier... operations) {
        for (BooleanSupplier booleansupplier : operations) {
            if (!booleansupplier.getAsBoolean()) {
                Util.LOGGER.warn("Failed to execute {}", booleansupplier);
                return false;
            }
        }

        return true;
    }

    private static boolean runWithRetries(int numberOfRetries, String description, BooleanSupplier... operations) {
        for (int j = 0; j < numberOfRetries; ++j) {
            if (executeInSequence(operations)) {
                return true;
            }

            Util.LOGGER.error("Failed to {}, retrying {}/{}", new Object[]{description, j, numberOfRetries});
        }

        Util.LOGGER.error("Failed to {}, aborting, progress might be lost", description);
        return false;
    }

    public static void safeReplaceFile(Path targetPath, Path newPath, Path backupPath) {
        safeReplaceOrMoveFile(targetPath, newPath, backupPath, false);
    }

    public static boolean safeReplaceOrMoveFile(Path targetPath, Path newPath, Path backupPath, boolean noRollback) {
        if (Files.exists(targetPath, new LinkOption[0]) && !runWithRetries(10, "create backup " + String.valueOf(backupPath), createDeleter(backupPath), createRenamer(targetPath, backupPath), createFileCreatedCheck(backupPath))) {
            return false;
        } else if (!runWithRetries(10, "remove old " + String.valueOf(targetPath), createDeleter(targetPath), createFileDeletedCheck(targetPath))) {
            return false;
        } else if (!runWithRetries(10, "replace " + String.valueOf(targetPath) + " with " + String.valueOf(newPath), createRenamer(newPath, targetPath), createFileCreatedCheck(targetPath)) && !noRollback) {
            runWithRetries(10, "restore " + String.valueOf(targetPath) + " from " + String.valueOf(backupPath), createRenamer(backupPath, targetPath), createFileCreatedCheck(targetPath));
            return false;
        } else {
            return true;
        }
    }

    public static int offsetByCodepoints(String input, int pos, int offset) {
        int k = input.length();

        if (offset >= 0) {
            for (int l = 0; pos < k && l < offset; ++l) {
                if (Character.isHighSurrogate(input.charAt(pos++)) && pos < k && Character.isLowSurrogate(input.charAt(pos))) {
                    ++pos;
                }
            }
        } else {
            for (int i1 = offset; pos > 0 && i1 < 0; ++i1) {
                --pos;
                if (Character.isLowSurrogate(input.charAt(pos)) && pos > 0 && Character.isHighSurrogate(input.charAt(pos - 1))) {
                    --pos;
                }
            }
        }

        return pos;
    }

    public static Consumer<String> prefix(String prefix, Consumer<String> consumer) {
        return (s1) -> {
            consumer.accept(prefix + s1);
        };
    }

    public static DataResult<int[]> fixedSize(IntStream stream, int size) {
        int[] aint = stream.limit((long) (size + 1)).toArray();

        if (aint.length != size) {
            Supplier<String> supplier = () -> {
                return "Input is not a list of " + size + " ints";
            };

            return aint.length >= size ? DataResult.error(supplier, Arrays.copyOf(aint, size)) : DataResult.error(supplier);
        } else {
            return DataResult.success(aint);
        }
    }

    public static DataResult<long[]> fixedSize(LongStream stream, int size) {
        long[] along = stream.limit((long) (size + 1)).toArray();

        if (along.length != size) {
            Supplier<String> supplier = () -> {
                return "Input is not a list of " + size + " longs";
            };

            return along.length >= size ? DataResult.error(supplier, Arrays.copyOf(along, size)) : DataResult.error(supplier);
        } else {
            return DataResult.success(along);
        }
    }

    public static <T> DataResult<List<T>> fixedSize(List<T> list, int size) {
        if (list.size() != size) {
            Supplier<String> supplier = () -> {
                return "Input is not a list of " + size + " elements";
            };

            return list.size() >= size ? DataResult.error(supplier, list.subList(0, size)) : DataResult.error(supplier);
        } else {
            return DataResult.success(list);
        }
    }

    public static void startTimerHackThread() {
        Thread thread = new Thread("Timer hack thread") {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2147483647L);
                    } catch (InterruptedException interruptedexception) {
                        Util.LOGGER.warn("Timer hack thread interrupted, that really should not happen");
                        return;
                    }
                }
            }
        };

        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(Util.LOGGER));
        thread.start();
    }

    public static void copyBetweenDirs(Path sourceDir, Path targetDir, Path sourcePath) throws IOException {
        Path path3 = sourceDir.relativize(sourcePath);
        Path path4 = targetDir.resolve(path3);

        Files.copy(sourcePath, path4);
    }

    public static String sanitizeName(String value, CharPredicate isAllowedChar) {
        return (String) value.toLowerCase(Locale.ROOT).chars().mapToObj((i) -> {
            return isAllowedChar.test((char) i) ? Character.toString((char) i) : "_";
        }).collect(Collectors.joining());
    }

    public static <K, V> SingleKeyCache<K, V> singleKeyCache(Function<K, V> computeValueFunction) {
        return new SingleKeyCache<K, V>(computeValueFunction);
    }

    public static <T, R> Function<T, R> memoize(final Function<T, R> function) {
        return new Function<T, R>() {
            private final Map<T, R> cache = new ConcurrentHashMap();

            public R apply(T arg) {
                return (R) this.cache.computeIfAbsent(arg, function);
            }

            public String toString() {
                String s = String.valueOf(function);

                return "memoize/1[function=" + s + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T, U, R> BiFunction<T, U, R> memoize(final BiFunction<T, U, R> function) {
        return new BiFunction<T, U, R>() {
            private final Map<Pair<T, U>, R> cache = new ConcurrentHashMap();

            public R apply(T a, U b) {
                return (R) this.cache.computeIfAbsent(Pair.of(a, b), (pair) -> {
                    return function.apply(pair.getFirst(), pair.getSecond());
                });
            }

            public String toString() {
                String s = String.valueOf(function);

                return "memoize/2[function=" + s + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T> List<T> toShuffledList(Stream<T> stream, RandomSource random) {
        ObjectArrayList<T> objectarraylist = (ObjectArrayList) stream.collect(ObjectArrayList.toList());

        shuffle(objectarraylist, random);
        return objectarraylist;
    }

    public static IntArrayList toShuffledList(IntStream stream, RandomSource random) {
        IntArrayList intarraylist = IntArrayList.wrap(stream.toArray());
        int i = intarraylist.size();

        for (int j = i; j > 1; --j) {
            int k = random.nextInt(j);

            intarraylist.set(j - 1, intarraylist.set(k, intarraylist.getInt(j - 1)));
        }

        return intarraylist;
    }

    public static <T> List<T> shuffledCopy(T[] array, RandomSource random) {
        ObjectArrayList<T> objectarraylist = new ObjectArrayList(array);

        shuffle(objectarraylist, random);
        return objectarraylist;
    }

    public static <T> List<T> shuffledCopy(ObjectArrayList<T> list, RandomSource random) {
        ObjectArrayList<T> objectarraylist1 = new ObjectArrayList(list);

        shuffle(objectarraylist1, random);
        return objectarraylist1;
    }

    public static <T> void shuffle(List<T> list, RandomSource random) {
        int i = list.size();

        for (int j = i; j > 1; --j) {
            int k = random.nextInt(j);

            list.set(j - 1, list.set(k, list.get(j - 1)));
        }

    }

    public static <T> CompletableFuture<T> blockUntilDone(Function<Executor, CompletableFuture<T>> task) {
        return (CompletableFuture) blockUntilDone(task, CompletableFuture::isDone);
    }

    public static <T> T blockUntilDone(Function<Executor, T> task, Predicate<T> completionCheck) {
        BlockingQueue<Runnable> blockingqueue = new LinkedBlockingQueue();

        Objects.requireNonNull(blockingqueue);
        T t0 = (T) task.apply(blockingqueue::add);

        while (!completionCheck.test(t0)) {
            try {
                Runnable runnable = (Runnable) blockingqueue.poll(100L, TimeUnit.MILLISECONDS);

                if (runnable != null) {
                    runnable.run();
                }
            } catch (InterruptedException interruptedexception) {
                Util.LOGGER.warn("Interrupted wait");
                break;
            }
        }

        int i = blockingqueue.size();

        if (i > 0) {
            Util.LOGGER.warn("Tasks left in queue: {}", i);
        }

        return t0;
    }

    public static <T> ToIntFunction<T> createIndexLookup(List<T> values) {
        int i = values.size();

        if (i < 8) {
            Objects.requireNonNull(values);
            return values::indexOf;
        } else {
            Object2IntMap<T> object2intmap = new Object2IntOpenHashMap(i);

            object2intmap.defaultReturnValue(-1);

            for (int j = 0; j < i; ++j) {
                object2intmap.put(values.get(j), j);
            }

            return object2intmap;
        }
    }

    public static <T> ToIntFunction<T> createIndexIdentityLookup(List<T> values) {
        int i = values.size();

        if (i < 8) {
            ReferenceList<T> referencelist = new ReferenceImmutableList(values);

            Objects.requireNonNull(referencelist);
            return referencelist::indexOf;
        } else {
            Reference2IntMap<T> reference2intmap = new Reference2IntOpenHashMap(i);

            reference2intmap.defaultReturnValue(-1);

            for (int j = 0; j < i; ++j) {
                reference2intmap.put(values.get(j), j);
            }

            return reference2intmap;
        }
    }

    public static <A, B> Typed<B> writeAndReadTypedOrThrow(Typed<A> typed, Type<B> newType, UnaryOperator<Dynamic<?>> function) {
        Dynamic<?> dynamic = (Dynamic) typed.write().getOrThrow();

        return readTypedOrThrow(newType, (Dynamic) function.apply(dynamic), true);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> dynamic) {
        return readTypedOrThrow(type, dynamic, false);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> dynamic, boolean acceptPartial) {
        DataResult<Typed<T>> dataresult = type.readTyped(dynamic).map(Pair::getFirst);

        try {
            return acceptPartial ? (Typed) dataresult.getPartialOrThrow(IllegalStateException::new) : (Typed) dataresult.getOrThrow(IllegalStateException::new);
        } catch (IllegalStateException illegalstateexception) {
            CrashReport crashreport = CrashReport.forThrowable(illegalstateexception, "Reading type");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Info");

            crashreportcategory.setDetail("Data", dynamic);
            crashreportcategory.setDetail("Type", type);
            throw new ReportedException(crashreport);
        }
    }

    public static <T> List<T> copyAndAdd(List<T> list, T element) {
        return ImmutableList.builderWithExpectedSize(list.size() + 1).addAll(list).add(element).build();
    }

    public static <T> List<T> copyAndAdd(T element, List<T> list) {
        return ImmutableList.builderWithExpectedSize(list.size() + 1).add(element).addAll(list).build();
    }

    public static <K, V> Map<K, V> copyAndPut(Map<K, V> map, K key, V value) {
        return ImmutableMap.builderWithExpectedSize(map.size() + 1).putAll(map).put(key, value).buildKeepingLast();
    }

    public static enum OS {

        LINUX("linux"), SOLARIS("solaris"), WINDOWS("windows") {
            @Override
            protected String[] getOpenUriArguments(URI uri) {
                return new String[]{"rundll32", "url.dll,FileProtocolHandler", uri.toString()};
            }
        },
        OSX("mac") {
            @Override
            protected String[] getOpenUriArguments(URI uri) {
                return new String[]{"open", uri.toString()};
            }
        },
        UNKNOWN("unknown");

        private final String telemetryName;

        private OS(String telemetryName) {
            this.telemetryName = telemetryName;
        }

        public void openUri(URI uri) {
            try {
                Process process = Runtime.getRuntime().exec(this.getOpenUriArguments(uri));

                process.getInputStream().close();
                process.getErrorStream().close();
                process.getOutputStream().close();
            } catch (IOException ioexception) {
                Util.LOGGER.error("Couldn't open location '{}'", uri, ioexception);
            }

        }

        public void openFile(File file) {
            this.openUri(file.toURI());
        }

        public void openPath(Path path) {
            this.openUri(path.toUri());
        }

        protected String[] getOpenUriArguments(URI uri) {
            String s = uri.toString();

            if ("file".equals(uri.getScheme())) {
                s = s.replace("file:", "file://");
            }

            return new String[]{"xdg-open", s};
        }

        public void openUri(String uri) {
            try {
                this.openUri(new URI(uri));
            } catch (IllegalArgumentException | URISyntaxException urisyntaxexception) {
                Util.LOGGER.error("Couldn't open uri '{}'", uri, urisyntaxexception);
            }

        }

        public String telemetryName() {
            return this.telemetryName;
        }
    }
}
