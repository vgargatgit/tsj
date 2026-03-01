package dev.utta;

public final class Circle implements Shape {
    private final double radius;
    public Circle(double radius) { this.radius = radius; }
    public double getRadius() { return radius; }
    @Override public double area() { return Math.PI * radius * radius; }
    @Override public String describe() { return "Circle(r=" + radius + ")"; }
}
