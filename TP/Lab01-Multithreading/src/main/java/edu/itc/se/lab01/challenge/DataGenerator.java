package edu.itc.se.lab01.challenge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.IntStream;

public class DataGenerator {
    static final int VOCABULARY_SIZE = 2_000;

    private static final String[] COMMON = ("the of and to a in is it you that he was for on are with as i his they "
            + "be at one have this from or had by not word but what some we can out other were all there when up use "
            + "your how said an each she which do their time if will way about many then them write would like so "
            + "these her long make thing see him two has look more day could go come did number sound no most people "
            + "my over know water than call first who may down side been now find").split(" ");
    private static final String[] SYLLABLES = ("ka lo mi ra ten vos bel dri sun po ex qua mor fin ga zu ler ish on ar "
            + "tor pel wen dra ki bo nu sa vel ird").split(" ");

    static List<String> vocabulary() {
        LinkedHashSet<String> words = new LinkedHashSet<>(Arrays.asList(COMMON));
        Random rnd = new Random(2_000);
        while (words.size() < VOCABULARY_SIZE) {
            StringBuilder w = new StringBuilder();
            int parts = 2 + rnd.nextInt(2);
            for (int i = 0; i < parts; i++) w.append(SYLLABLES[rnd.nextInt(SYLLABLES.length)]);
            words.add(w.toString());
        }
        return List.copyOf(words);
    }

    static void generate(Path dir, int files, int wordsPerFile, long seed) throws IOException {
        Files.createDirectories(dir);
        List<String> vocab = vocabulary();
        double[] cumulative = new double[vocab.size()];
        double acc = 0;
        for (int r = 0; r < cumulative.length; r++) cumulative[r] = acc += 1.0 / (r + 1);
        double sum = acc;

        String[] punctuation = {",", ".", ";", "!", "?", " -", "\""};
        IntStream.range(0, files).parallel().forEach(f -> {
            Random rnd = new Random(seed * 1_000_003 + f);
            Path file = dir.resolve(String.format("file-%05d.txt", f));
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                int left = wordsPerFile;
                while (left > 0) {
                    int lineWords = Math.min(left, 8 + rnd.nextInt(8));
                    for (int i = 0; i < lineWords; i++) {
                        int idx = Arrays.binarySearch(cumulative, rnd.nextDouble() * sum);
                        String w = vocab.get(Math.min(idx < 0 ? -idx - 1 : idx, vocab.size() - 1));
                        if (i == 0 || rnd.nextInt(20) == 0) w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
                        if (i > 0) out.write(' ');
                        out.write(w);
                        if (rnd.nextInt(12) == 0) out.write(punctuation[rnd.nextInt(punctuation.length)]);
                    }
                    out.write('\n');
                    left -= lineWords;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: DataGenerator <dir> [files=200] [wordsPerFile=50000] [seed=42]");
            System.exit(1);
        }
        Path dir = Path.of(args[0]);
        int files = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        int words = args.length > 2 ? Integer.parseInt(args[2]) : 50_000;
        long seed = args.length > 3 ? Long.parseLong(args[3]) : 42;
        long t0 = System.nanoTime();
        generate(dir, files, words, seed);
        System.out.printf(Locale.US, "wrote %d files x %,d words to %s in %.1fs%n",
                files, words, dir, (System.nanoTime() - t0) / 1e9);
    }
}
