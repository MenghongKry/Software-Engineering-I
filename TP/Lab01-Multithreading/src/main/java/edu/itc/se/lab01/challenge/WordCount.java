package edu.itc.se.lab01.challenge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public class WordCount {
    static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}]+");

    private final BlockingQueue<Path> queue = new ArrayBlockingQueue<>(16);
    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
    private final AtomicInteger filesDone = new AtomicInteger();
    private final AtomicInteger filesTotal = new AtomicInteger(-1); 
    private final LongAdder wordsDone = new LongAdder();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final CountDownLatch resultPrinted = new CountDownLatch(1); 
    private static final Path POISON = Path.of("__POISON__");

    private final Path dir; 
    private final int workers; 
    private final boolean virtual;
    private final int top; 
    private final long timeoutSec;

    private volatile ExecutorService pool;
    private volatile Thread scanner;
    private volatile ScheduledExecutorService reporter;
    private volatile long startNanos;

    Path csvFile = Path.of("result.csv"); 
    PrintStream out = System.out;

    WordCount(Path dir, int workers, boolean virtual, int top, long timeoutSec) {
        this.dir = dir; this.workers = workers; this.virtual = virtual; this.top = top; this.timeoutSec = timeoutSec;
    }

    void run() throws InterruptedException {
        startNanos = System.nanoTime();
        CountDownLatch workersDone = new CountDownLatch(workers);
        pool = virtual ? Executors.newVirtualThreadPerTaskExecutor()
                       : Executors.newFixedThreadPool(workers, Thread.ofPlatform().name("worker-", 1).factory());
        scanner = Thread.ofPlatform().name("scanner").start(this::scan);
        reporter = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("reporter").factory());
        reporter.scheduleAtFixedRate(this::report, 500, 500, TimeUnit.MILLISECONDS);
        Thread hook = new Thread(() -> shutdown("interrupted (Ctrl+C)"), "shutdown-hook");
        Runtime.getRuntime().addShutdownHook(hook);

        for (int i = 0; i < workers; i++) {
            pool.submit(() -> {
                try { work(); } finally { workersDone.countDown(); }
            });
        }

        boolean finished;
        if (timeoutSec > 0) {
            finished = workersDone.await(timeoutSec, TimeUnit.SECONDS);
        } else {
            workersDone.await();
            finished = true;
        }

        if (!finished) {
            shutdown("deadline reached");
        } else if (stopping.compareAndSet(false, true)) {
            reporter.shutdownNow();
            pool.shutdown();
            scanner.join();
            printResult(false);
            resultPrinted.countDown();
        } else {
            resultPrinted.await(3, TimeUnit.SECONDS);
        }
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException alreadyShuttingDown) {
        }
    }

    private void scan() {
        boolean interrupted = false;
        try {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(dir)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            filesTotal.set(files.size());
            for (Path p : files) queue.put(p);
        } catch (InterruptedException e) {
            interrupted = true;
            Thread.currentThread().interrupt();
        } catch (IOException | UncheckedIOException e) {
            System.err.println("scanner: cannot walk " + dir + ": " + e);
        } finally {
            if (!interrupted) {
                for (int i = 0; i < workers; i++) {
                    try {
                        queue.put(POISON);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void work() {
        try {
            while (!Thread.currentThread().isInterrupted()) { 
                Path p = queue.take();
                if (p == POISON) return;
                try {
                    countFile(p);
                } catch (UncheckedIOException e) {
                    System.err.println(Thread.currentThread().getName() + ": " + e.getMessage() + " (skipped)");
                }
                filesDone.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();                
        }
    }

    private void countFile(Path p) {
        try (BufferedReader in = new BufferedReader(new FileReader(p.toFile(), StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            while ((line = in.readLine()) != null) {
                int n = 0;
                for (String w : NON_LETTERS.split(line)) {
                    if (w.isEmpty()) continue;                   
                    String word = w.toLowerCase(Locale.ROOT);
                    LongAdder a = counts.get(word);
                    if (a == null) a = counts.computeIfAbsent(word, k -> new LongAdder());
                    a.increment();
                    n++;
                }
                wordsDone.add(n);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + p, e);
        }
    }

    private void report() {
        long words = wordsDone.sum();
        double secs = elapsedSec();
        int total = filesTotal.get();
        out.printf(Locale.US, "[%.1fs] files %d/%s  words %,d  %,d w/s  top: %s%n",
                secs, filesDone.get(), total < 0 ? "?" : total, words, (long) (words / secs), top3());
    }

    /** Top 3 without locking: CHM iteration is weakly consistent, LongAdder.sum() is a plain read. */
    private String top3() {
        String[] w = new String[3];
        long[] c = new long[3];
        counts.forEach((word, adder) -> {
            long n = adder.sum();
            for (int i = 0; i < 3; i++) {
                if (w[i] == null || n > c[i]) {
                    for (int j = 2; j > i; j--) { w[j] = w[j - 1]; c[j] = c[j - 1]; }
                    w[i] = word; c[i] = n;
                    return;
                }
            }
        });
        StringJoiner sj = new StringJoiner(" ");
        for (int i = 0; i < 3 && w[i] != null; i++) sj.add(String.format(Locale.US, "%s(%,d)", w[i], c[i]));
        return sj.toString();
    }

    void shutdown() { shutdown("shutdown requested"); }

    private void shutdown(String reason) {
        if (!stopping.compareAndSet(false, true)) {
            try { resultPrinted.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return;
        }
        try {
            out.printf(Locale.US, "[%.1fs] %s - stopping%n", elapsedSec(), reason);
            if (reporter != null) reporter.shutdownNow();
            if (scanner != null) scanner.interrupt();
            if (pool != null) pool.shutdownNow();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            try {
                boolean poolStopped = pool == null || pool.awaitTermination(2, TimeUnit.SECONDS);
                long leftMs = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
                if (scanner != null) scanner.join(leftMs);
                if (!poolStopped || (scanner != null && scanner.isAlive()))
                    System.err.println("warning: some threads did not stop within 2 s");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            printResult(true);
        } finally {
            resultPrinted.countDown();
        }
    }

    void printResult(boolean partial) {
        List<Map.Entry<String, Long>> sorted = sortedCounts();
        long total = 0;
        for (var e : sorted) total += e.getValue();

        out.printf(Locale.US, "%s files=%d words=%,d elapsed=%.2fs mode=%s%n",
                partial ? "[PARTIAL]" : "DONE ", filesDone.get(), total, elapsedSec(), mode());
        int width = 4;
        for (int i = 0; i < Math.min(top, sorted.size()); i++) width = Math.max(width, sorted.get(i).getKey().length());
        out.printf(Locale.US, " rank  %-" + width + "s %11s %8s%n", "word", "count", "share");
        for (int i = 0; i < Math.min(top, sorted.size()); i++) {
            var e = sorted.get(i);
            out.printf(Locale.US, "%5d  %-" + width + "s %,11d %6.2f %%%n",
                    i + 1, e.getKey(), e.getValue(), share(e.getValue(), total));
        }

        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(csvFile, StandardCharsets.UTF_8))) {
            csv.println("rank,word,count,share_percent");
            for (int i = 0; i < sorted.size(); i++) {
                var e = sorted.get(i);
                csv.printf(Locale.ROOT, "%d,%s,%d,%.4f%n", i + 1, e.getKey(), e.getValue(), share(e.getValue(), total));
            }
            out.println(csvFile.getFileName() + " written");
        } catch (IOException e) {
            System.err.println("cannot write " + csvFile + ": " + e);
        }
    }

    Map<String, Long> counts() {
        Map<String, Long> m = new HashMap<>();
        counts.forEach((w, a) -> m.put(w, a.sum()));
        return m;
    }

    private List<Map.Entry<String, Long>> sortedCounts() {
        List<Map.Entry<String, Long>> list = new ArrayList<>(counts().entrySet());
        list.sort(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        return list;
    }

    private static double share(long n, long total) { return total == 0 ? 0 : 100.0 * n / total; }

    private double elapsedSec() { return (System.nanoTime() - startNanos) / 1e9; }

    String mode() { return (virtual ? "virtual(" : "platform(") + workers + ")"; }

    public static void main(String[] args) throws Exception {
        Path dir = null;
        int top = 10, workers = Runtime.getRuntime().availableProcessors();
        long timeout = 0;
        boolean virtual = false;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--top" -> top = Integer.parseInt(args[++i]);
                    case "--timeout" -> timeout = Long.parseLong(args[++i]);
                    case "--workers" -> workers = Integer.parseInt(args[++i]);
                    case "--virtual" -> virtual = true;
                    default -> {
                        if (args[i].startsWith("--") || dir != null) throw new IllegalArgumentException(args[i]);
                        dir = Path.of(args[i]);
                    }
                }
            }
            if (dir == null || top < 1 || workers < 1 || timeout < 0) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            System.err.println("usage: WordCount <dir> [--top N] [--timeout SECONDS] [--virtual] [--workers N]");
            System.exit(1);
        }
        if (!Files.isDirectory(dir)) {
            System.err.println("not a directory: " + dir);
            System.exit(1);
        }
        new WordCount(dir, workers, virtual, top, timeout).run();
    }
}
