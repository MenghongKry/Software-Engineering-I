package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockBank {
    static final int ACCOUNTS = 4;
    static final int WORKERS = 8;
    static final int TRANSFERS_PER_WORKER = 100_000;

    static final class Account {
        final int id; private long balance;
        final ReentrantLock lock = new ReentrantLock();
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }

    static final LongAdder retries = new LongAdder();

    static void transfer(Account from, Account to, long amount) throws InterruptedException {
        var rnd = ThreadLocalRandom.current();
        while (true) {
            if (from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                        try {
                            if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
                            return;
                        } finally {
                            to.lock.unlock();
                        }
                    }
                } finally {
                    from.lock.unlock();
                }
            }
            retries.increment();
            TimeUnit.MICROSECONDS.sleep(rnd.nextInt(1, 100));
        }
    }

    static long total(Account[] accounts) {
        long total = 0;
        for (Account a : accounts) {
            a.lock.lock();
            try { total += a.balance(); } finally { a.lock.unlock(); }
        }
        return total;
    }

    public static void main(String[] args) throws InterruptedException {
        Account[] accounts = new Account[ACCOUNTS];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);

        long start = System.nanoTime();
        Thread[] workers = new Thread[WORKERS];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                try {
                    for (int i = 0; i < TRANSFERS_PER_WORKER; i++) {
                        int a = rnd.nextInt(ACCOUNTS);
                        int b = (a + rnd.nextInt(1, ACCOUNTS)) % ACCOUNTS;
                        transfer(accounts[a], accounts[b], rnd.nextInt(1, 50));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
        long ms = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("transfers=%d retries=%d total=%d (expected %d) in %d ms%n",
                (long) WORKERS * TRANSFERS_PER_WORKER, retries.sum(), total(accounts), ACCOUNTS * 1_000L, ms);
    }
}
