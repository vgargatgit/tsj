package dev.tsj.runtime;

/**
 * Runtime helpers used by TSJ-generated JVM classes in TSJ-7 subset.
 */
public final class TsjRuntime {
    private TsjRuntime() {
    }

    public static void print(final Object value) {
        System.out.println(toDisplayString(value));
    }

    public static Object call(final Object callee, final Object... args) {
        if (callee instanceof TsjCallable callable) {
            return callable.call(args);
        }
        throw new IllegalArgumentException("Value is not callable: " + toDisplayString(callee));
    }

    public static Object add(final Object left, final Object right) {
        if (left instanceof String || right instanceof String) {
            return toDisplayString(left) + toDisplayString(right);
        }
        return narrowNumber(toNumber(left) + toNumber(right));
    }

    public static Object subtract(final Object left, final Object right) {
        return narrowNumber(toNumber(left) - toNumber(right));
    }

    public static Object multiply(final Object left, final Object right) {
        return narrowNumber(toNumber(left) * toNumber(right));
    }

    public static Object divide(final Object left, final Object right) {
        return Double.valueOf(toNumber(left) / toNumber(right));
    }

    public static Object negate(final Object value) {
        return narrowNumber(-toNumber(value));
    }

    public static boolean lessThan(final Object left, final Object right) {
        return toNumber(left) < toNumber(right);
    }

    public static boolean lessThanOrEqual(final Object left, final Object right) {
        return toNumber(left) <= toNumber(right);
    }

    public static boolean greaterThan(final Object left, final Object right) {
        return toNumber(left) > toNumber(right);
    }

    public static boolean greaterThanOrEqual(final Object left, final Object right) {
        return toNumber(left) >= toNumber(right);
    }

    public static boolean strictEquals(final Object left, final Object right) {
        if (left == right) {
            if (left instanceof Double leftDouble && Double.isNaN(leftDouble.doubleValue())) {
                return false;
            }
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof Number && right instanceof Number) {
            final double leftNumber = toNumber(left);
            final double rightNumber = toNumber(right);
            if (Double.isNaN(leftNumber) || Double.isNaN(rightNumber)) {
                return false;
            }
            return leftNumber == rightNumber;
        }
        return left.equals(right);
    }

    public static boolean truthy(final Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue.booleanValue();
        }
        if (value instanceof Number) {
            final double numberValue = toNumber(value);
            return !Double.isNaN(numberValue) && numberValue != 0.0d;
        }
        if (value instanceof String stringValue) {
            return !stringValue.isEmpty();
        }
        return true;
    }

    public static String toDisplayString(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue.booleanValue() ? "true" : "false";
        }
        if (value instanceof Number) {
            final double numericValue = toNumber(value);
            if (Double.isNaN(numericValue)) {
                return "NaN";
            }
            if (Double.isInfinite(numericValue)) {
                return numericValue > 0 ? "Infinity" : "-Infinity";
            }
            if (numericValue == Math.rint(numericValue)) {
                return Long.toString((long) numericValue);
            }
            return Double.toString(numericValue);
        }
        return value.toString();
    }

    public static double toNumber(final Object value) {
        if (value == null) {
            return 0.0d;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof Boolean boolValue) {
            return boolValue.booleanValue() ? 1.0d : 0.0d;
        }
        if (value instanceof String stringValue) {
            final String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return 0.0d;
            }
            try {
                return Double.parseDouble(trimmed);
            } catch (final NumberFormatException numberFormatException) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    private static Object narrowNumber(final double value) {
        if (!Double.isFinite(value)) {
            return Double.valueOf(value);
        }
        if (value == Math.rint(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return Integer.valueOf((int) value);
        }
        return Double.valueOf(value);
    }
}
