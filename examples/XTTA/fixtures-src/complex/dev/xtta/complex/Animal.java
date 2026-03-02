package dev.xtta.complex;

public abstract class Animal {
    private final String species;

    protected Animal(String species) {
        this.species = species;
    }

    public String getSpecies() {
        return species;
    }

    public abstract String sound();

    public String describe() {
        return species + " says " + sound();
    }
}
