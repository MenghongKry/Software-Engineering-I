package edu.itc.se.lab01.challenge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class ReferenceWordCount {

    static Map<String, Long> count(Path dir) throws IOException {
        Map<String, Long> counts = new HashMap<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path p : files) {
            try (BufferedReader in = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                String line;
                while ((line = in.readLine()) != null) countLine(line, counts);
            }
        }
        return counts;
    }

    private static void countLine(String line, Map<String, Long> counts) {
        StringBuilder word = new StringBuilder();
        line.codePoints().forEach(cp -> {
            if (Character.isLetter(cp)) {
                word.appendCodePoint(cp);
            } else if (!word.isEmpty()) {
                counts.merge(word.toString().toLowerCase(Locale.ROOT), 1L, Long::sum);
                word.setLength(0);
            }
        });
        if (!word.isEmpty()) counts.merge(word.toString().toLowerCase(Locale.ROOT), 1L, Long::sum);
    }

    public static void main(String[] args) throws IOException {
        long t0 = System.nanoTime();
        Map<String, Long> counts = count(Path.of(args[0]));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        System.out.printf(Locale.US, "reference: %,d distinct words, %,d words in %.2fs%n",
                counts.size(), total, (System.nanoTime() - t0) / 1e9);
    }
}
