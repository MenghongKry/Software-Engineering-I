package edu.itc.se.lab01.challenge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class MapStrategies {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : Runtime.getRuntime().availableProcessors();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }

        for (int round = 1; round <= 2; round++) {
            Map<String, Long> locked = new HashMap<>();
            time(round, "synchronized HashMap         ", files, threads, w -> {
                synchronized (locked) { locked.merge(w, 1L, Long::sum); }
            });
            ConcurrentHashMap<String, Long> merged = new ConcurrentHashMap<>();
            time(round, "ConcurrentHashMap.merge(Long)", files, threads, w -> merged.merge(w, 1L, Long::sum));
            ConcurrentHashMap<String, LongAdder> adders = new ConcurrentHashMap<>();
            time(round, "ConcurrentHashMap<LongAdder> ", files, threads, w -> {
                LongAdder a = adders.get(w);
                if (a == null) a = adders.computeIfAbsent(w, k -> new LongAdder());
                a.increment();
            });
        }
    }

    static void time(int round, String label, List<Path> files, int threads, Consumer<String> update) throws Exception {
        long t0 = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (Path p : files) {
                pool.submit(() -> {
                    try (BufferedReader in = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        String line;
                        while ((line = in.readLine()) != null)
                            for (String w : WordCount.NON_LETTERS.split(line))
                                if (!w.isEmpty()) update.accept(w.toLowerCase(Locale.ROOT));
                    }
                    return null;
                });
            }
        }
        if (round > 1) System.out.printf(Locale.US, "%s  %d threads  %.2fs%n", label, threads, (System.nanoTime() - t0) / 1e9);
    }
}
