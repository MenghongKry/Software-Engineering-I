package edu.itc.se.lab01.task3;

public record Order(int id, String item, int qty) {

    public static final Order POISON_PILL = new Order(-1, "STOP", 0);
    public boolean isPoisonPill() {return id == -1;}
}
