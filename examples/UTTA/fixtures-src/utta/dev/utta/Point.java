package dev.utta;

/**
 * Java record for interop testing (Java 16+).
 */
public record Point(double x, double y) {
    public double distanceTo(Point other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public Point translate(double dx, double dy) {
        return new Point(x + dx, y + dy);
    }

    public static Point origin() {
        return new Point(0, 0);
    }
}
