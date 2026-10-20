package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Step 4, Fix A: always lock the account with the smaller id first.
 * Every thread then takes the locks in the same global order, so a cycle of waiting threads is impossible.
 */
public class OrderedBank {
    static final int ACCOUNTS = 4;
    static final int WORKERS = 8;
    static final int TRANSFERS_PER_WORKER = 100_000;

    static void transfer(Bank.Account from, Bank.Account to, long amount) {
        Bank.Account first  = from.id < to.id ? from : to;
        Bank.Account second = from.id < to.id ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Bank.Account[] accounts = new Bank.Account[ACCOUNTS];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Bank.Account(i, 1_000);

        long start = System.nanoTime();
        Thread[] workers = new Thread[WORKERS];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < TRANSFERS_PER_WORKER; i++) {
                    int a = rnd.nextInt(ACCOUNTS);
                    int b = (a + rnd.nextInt(1, ACCOUNTS)) % ACCOUNTS;   // any account except a
                    transfer(accounts[a], accounts[b], rnd.nextInt(1, 50));
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
        long ms = (System.nanoTime() - start) / 1_000_000;

        long total = 0;
        for (Bank.Account a : accounts) synchronized (a) { total += a.balance(); }
        System.out.printf("transfers=%d total=%d (expected %d) in %d ms%n",
                (long) WORKERS * TRANSFERS_PER_WORKER, total, ACCOUNTS * 1_000L, ms);
    }
}
