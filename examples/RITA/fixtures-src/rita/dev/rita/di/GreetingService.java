package dev.rita.di;

@Component
public final class GreetingService {
    @Inject
    private GreetingRepository repository;

    public String greet(final String name) {
        return repository.prefix() + " " + name;
    }
}
