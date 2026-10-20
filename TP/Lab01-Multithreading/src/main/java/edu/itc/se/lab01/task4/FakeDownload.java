package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.concurrent.Callable;
public record FakeDownload(String url, long sleepMs, boolean fail) implements Callable<String> {

    public FakeDownload(String url) {
        this(url, 200, false);
    }

    @Override
    public String call() throws IOException, InterruptedException {
        Thread.sleep(sleepMs);
        if (fail) throw new IOException("boom");
        int length = url.length() * 1000;
        return url + " -> " + length;
    }
}
