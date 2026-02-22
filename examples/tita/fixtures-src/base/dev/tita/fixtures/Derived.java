package dev.tita.fixtures;

public final class Derived extends Base<String> {
    @Override
    public String id(final String value) {
        return value;
    }
}
