package dev.utta;

/**
 * Java sealed interface hierarchy for interop testing (Java 17+).
 */
public sealed interface Shape permits Circle, Rectangle {
    double area();
    String describe();
}
