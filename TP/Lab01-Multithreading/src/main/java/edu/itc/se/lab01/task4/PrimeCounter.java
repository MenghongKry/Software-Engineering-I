package edu.itc.se.lab01.task4;

import java.util.concurrent.Callable;

public record PrimeCounter(long from, long to) implements Callable<Long> {

    @Override
    public Long call() {
        return countPrimes(from, to);
    }

    static long countPrimes(long from, long to) {
        long count = 0;
        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;
            for (long d = 2; d * d <= n; d++) if (n % d == 0) { prime = false; break; }
            if (prime) count++;
        }
        return count;
    }
}
