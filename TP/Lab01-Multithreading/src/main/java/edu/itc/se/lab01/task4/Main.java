package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    static final int DOWNLOADS = 500;
    static final int SLOW_INDEX = 7;

    public static void main(String[] args) throws Exception {
        primes();
        System.out.println();
        downloads();
        System.out.println();
        downloadsWithCompletableFuture();
    }

    static void primes() throws InterruptedException {
        final long LIMIT = 4_000_000, CHUNKS = 16;
        int threads = Runtime.getRuntime().availableProcessors();

        long t0 = System.nanoTime();
        long single = PrimeCounter.countPrimes(0, LIMIT);
        System.out.printf("single-threaded: %d primes in %d ms%n", single, ms(t0));

        t0 = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Long>> futures = new ArrayList<>();
            long chunk = LIMIT / CHUNKS;
            for (int i = 0; i < CHUNKS; i++) {
                long from = i * chunk;
                long to = (i == CHUNKS - 1) ? LIMIT : from + chunk;
                futures.add(pool.submit(new PrimeCounter(from, to)));
            }
            long total = 0;
            for (Future<Long> f : futures) {
                try {
                    total += f.get();
                } catch (ExecutionException e) {
                    throw new IllegalStateException("prime chunk failed", e.getCause());
                }
            }
            System.out.printf("pool (%d threads): %d primes in %d ms%s%n", threads, total, ms(t0),
                    total == single ? "  [matches single-threaded]" : "  [MISMATCH!]");
        }
    }

    static void downloads() {
        try (ExecutorService platform = Executors.newFixedThreadPool(20)) {
            runDownloads(platform, "fixed(20) platform threads");
        }
        try (ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            runDownloads(virtual, "virtual threads           ");
        }
    }
    static List<FakeDownload> makeTasks() {
        List<FakeDownload> tasks = new ArrayList<>();
        for (int i = 0; i < DOWNLOADS; i++) {
            String url = "https://example.com/file" + i;
            boolean fail = (i + 1) % 50 == 0;
            long sleep = (i == SLOW_INDEX) ? 5_000 : 200;
            tasks.add(new FakeDownload(url, sleep, fail));
        }
        return tasks;
    }

    static void runDownloads(ExecutorService executor, String label) {
        long t0 = System.nanoTime();
        List<FakeDownload> tasks = makeTasks();
        List<Future<String>> futures = new ArrayList<>();
        for (FakeDownload task : tasks) futures.add(executor.submit(task));

        int ok = 0, failed = 0, timedOut = 0;
        for (int i = 0; i < futures.size(); i++) {
            Future<String> f = futures.get(i);
            try {
                f.get(1, TimeUnit.SECONDS);
                ok++;
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof IOException)) System.out.println("unexpected: " + e.getCause());
                failed++;
            } catch (TimeoutException e) {
                f.cancel(true);
                System.out.println("  " + tasks.get(i).url() + " timed out");
                timedOut++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.printf("downloads on %s : ok=%d failed=%d timedOut=%d in %d ms%n",
                label, ok, failed, timedOut, ms(t0));
    }

    static void downloadsWithCompletableFuture() {
        long t0 = System.nanoTime();
        long[] closedAt = new long[1];
        List<String> results;
        try (ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<String>> cfs = new ArrayList<>();
            for (FakeDownload task : makeTasks()) {
                CompletableFuture<String> cf = CompletableFuture
                        .supplyAsync(() -> {
                            try { return task.call(); }
                            catch (Exception e) { throw new CompletionException(e); }
                        }, virtual)
                        .orTimeout(1, TimeUnit.SECONDS)
                        .handle((value, error) -> {
                            if (error == null) return value;
                            Throwable cause = (error instanceof CompletionException) ? error.getCause() : error;
                            if (cause instanceof TimeoutException) return "TIMEOUT " + task.url();
                            return "FALLBACK " + task.url() + " (" + cause.getMessage() + ")";
                        });
                cfs.add(cf);
            }
            CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0])).join();
            results = cfs.stream().map(CompletableFuture::join).toList();
            closedAt[0] = System.nanoTime();
        }
        long ok = results.stream().filter(s -> !s.startsWith("FALLBACK") && !s.startsWith("TIMEOUT")).count();
        long failed = results.stream().filter(s -> s.startsWith("FALLBACK")).count();
        long timedOut = results.stream().filter(s -> s.startsWith("TIMEOUT")).count();
        System.out.printf("bonus CompletableFuture on virtual threads: ok=%d failed=%d timedOut=%d in %d ms%n",
                ok, failed, timedOut, (closedAt[0] - t0) / 1_000_000);
        System.out.printf("  (closing the executor then waited %d ms more: orTimeout does not interrupt the slow task)%n",
                ms(closedAt[0]));
        System.out.println("  sample: " + results.get(0) + " | " + results.get(49) + " | " + results.get(SLOW_INDEX));
    }

    static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
