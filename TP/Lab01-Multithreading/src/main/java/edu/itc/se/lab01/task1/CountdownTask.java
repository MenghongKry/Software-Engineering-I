package edu.itc.se.lab01.task1;

public class CountdownTask implements Runnable {

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        try {
            for (int i = 3; i >= 1; i--) {
                System.out.println(name + ": " + i);
                Thread.sleep(200);
            }
            System.out.println(name + ": liftoff!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(name + ": aborted");
        }
    }
}