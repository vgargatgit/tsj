package dev.xtta.complex;

public interface Describable {
    String describe();

    default String summary() {
        return "Summary: " + describe();
    }
}
