package dev.xtta.complex;

public final class Person implements Greetable, Describable {
    private final String personName;
    private final int age;

    public Person(String name, int age) {
        this.personName = name;
        this.age = age;
    }

    @Override
    public String name() {
        return personName;
    }

    @Override
    public String describe() {
        return personName + " (age " + age + ")";
    }

    public int getAge() {
        return age;
    }
}
