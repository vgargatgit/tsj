package dev.utta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Functional interface and streams torture testing.
 */
public final class FuncLib {

    // Accepts a Function<Integer, Integer> from TS
    public static List<Integer> mapList(List<Integer> list, Function<Integer, Integer> fn) {
        return list.stream().map(fn).collect(Collectors.toList());
    }

    // Accepts a Predicate<String> from TS
    public static List<String> filterStrings(List<String> list, Predicate<String> pred) {
        return list.stream().filter(pred).collect(Collectors.toList());
    }

    // Accepts a Consumer<String> from TS
    public static void forEachString(List<String> list, Consumer<String> action) {
        list.forEach(action);
    }

    // Accepts a Supplier<String> from TS
    public static String getFromSupplier(Supplier<String> supplier) {
        return supplier.get();
    }

    // Returns an Optional
    public static Optional<String> findFirst(List<String> list, Predicate<String> pred) {
        return list.stream().filter(pred).findFirst();
    }

    // Stream pipeline returning joined string
    public static String streamPipeline(List<Integer> nums) {
        return nums.stream()
            .filter(n -> n > 0)
            .map(n -> n * 2)
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    }

    // Returns Optional<Integer>
    public static Optional<Integer> safeDiv(int a, int b) {
        if (b == 0) return Optional.empty();
        return Optional.of(a / b);
    }
}
