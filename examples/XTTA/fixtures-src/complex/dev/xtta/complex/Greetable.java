package dev.xtta.complex;

public interface Greetable {
    String name();

    default String greet() {
        return "Hello, " + name() + "!";
    }

    default String shout() {
        return greet().toUpperCase();
    }

    static String defaultGreeting() {
        return "Hello, World!";
    }
}
