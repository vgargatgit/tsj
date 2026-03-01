package dev.utta;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CompletableFuture for async interop testing.
 */
public final class AsyncLib {

    public static CompletableFuture<String> fetchGreeting(String name) {
        return CompletableFuture.supplyAsync(() -> "Hello, " + name + "!");
    }

    public static CompletableFuture<Integer> computeSquare(int n) {
        return CompletableFuture.supplyAsync(() -> n * n);
    }

    public static CompletableFuture<String> failingFuture() {
        CompletableFuture<String> f = new CompletableFuture<>();
        f.completeExceptionally(new RuntimeException("Intentional failure"));
        return f;
    }
}
