package dev.rita.di;

@Component
public final class GreetingRepository {
    public String prefix() {
        return "hello";
    }
}
