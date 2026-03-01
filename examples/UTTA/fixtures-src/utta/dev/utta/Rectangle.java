package dev.utta;

public final class Rectangle implements Shape {
    private final double width;
    private final double height;
    public Rectangle(double width, double height) { this.width = width; this.height = height; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    @Override public double area() { return width * height; }
    @Override public String describe() { return "Rectangle(" + width + "x" + height + ")"; }
}
