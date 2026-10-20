package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;

public class GracefulBank {
    static final int WORKERS = 8;
    static final long RUN_MS = 2_000;
    static final long JOIN_TIMEOUT_MS = 1_000;

    public static void main(String[] args) throws InterruptedException {
        TryLockBank.Account[] accounts = new TryLockBank.Account[TryLockBank.ACCOUNTS];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new TryLockBank.Account(i, 1_000);

        long[] done = new long[WORKERS];               // each worker writes only its own slot
        boolean[] flagRestored = new boolean[WORKERS];
        Thread[] workers = new Thread[WORKERS];
        for (int w = 0; w < workers.length; w++) {
            final int me = w;
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                long count = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    int a = rnd.nextInt(accounts.length);
                    int b = (a + rnd.nextInt(1, accounts.length)) % accounts.length;
                    try {
                        TryLockBank.transfer(accounts[a], accounts[b], rnd.nextInt(1, 50));
                        count++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                done[me] = count;
                flagRestored[me] = Thread.currentThread().isInterrupted();
            }, "transfer-" + w);
            workers[w].start();
        }

        Thread.sleep(RUN_MS);
        System.out.println("[main] interrupting " + WORKERS + " workers after " + RUN_MS + " ms");
        long stopStart = System.nanoTime();
        for (Thread t : workers) t.interrupt();

        // one shared 1-second deadline for all joins, not 1 s per worker
        long deadline = System.nanoTime() + JOIN_TIMEOUT_MS * 1_000_000;
        for (Thread t : workers) {
            long leftMs = Math.max(1, (deadline - System.nanoTime()) / 1_000_000);
            t.join(leftMs);
        }
        long stopMs = (System.nanoTime() - stopStart) / 1_000_000;

        boolean allStopped = true, allFlags = true;
        long totalTransfers = 0;
        for (int w = 0; w < WORKERS; w++) {
            boolean stopped = !workers[w].isAlive();
            allStopped &= stopped;
            allFlags &= flagRestored[w];
            totalTransfers += done[w];
            System.out.println(workers[w].getName() + (stopped ? " stopped after " + done[w] + " transfers" : " STILL RUNNING"));
        }
        System.out.printf("[main] stopped in %d ms, interrupt flags restored=%b, transfers completed=%d%n",
                stopMs, allFlags, totalTransfers);
        System.out.printf("all workers stopped=%b total=%d (expected %d)%n",
                allStopped, TryLockBank.total(accounts), accounts.length * 1_000L);
    }
}
