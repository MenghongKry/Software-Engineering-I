package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

public final class Watchdog {
    private Watchdog() {}

    public static Thread start() {
        Thread t = new Thread(Watchdog::run, "watchdog");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void run() {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        String lastReport = "";
        while (true) {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                return;
            }
            long[] ids = mx.findDeadlockedThreads();
            if (ids == null) continue;
            String names = String.join(", ", Arrays.stream(mx.getThreadInfo(ids))
                    .filter(info -> info != null)
                    .map(ThreadInfo::getThreadName)
                    .sorted()
                    .toList());
            if (!names.equals(lastReport)) {
                System.out.println("[watchdog] DEADLOCK detected between: " + names);
                lastReport = names;
            }
        }
    }
}
