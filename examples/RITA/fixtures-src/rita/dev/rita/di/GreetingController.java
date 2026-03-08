package dev.rita.di;

@Component
public final class GreetingController {
    @Inject
    private GreetingService service;

    public String handle(final String name) {
        return service.greet(name);
    }
}
