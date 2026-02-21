package dev.tsj.compiler.backend.jvm.fixtures;

import java.util.List;

/**
 * Spring-oriented fixture targets used by TSJ-33 interop bridge generation tests.
 */
public final class InteropSpringFixtureType {
    private InteropSpringFixtureType() {
    }

    public static final class DependencyA {
        private final String label;

        public DependencyA(final String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final class DependencyB {
        private final int value;

        public DependencyB(final int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }

    public static final class Service {
        private final DependencyA dependencyA;
        private final DependencyB dependencyB;

        public Service(final DependencyA dependencyA, final DependencyB dependencyB) {
            this.dependencyA = dependencyA;
            this.dependencyB = dependencyB;
        }

        public String describe() {
            return dependencyA.label() + "-" + dependencyB.value();
        }
    }

    public static final class MultiConstructorService {
        public MultiConstructorService() {
        }

        public MultiConstructorService(final DependencyA dependencyA) {
        }
    }

    public static Service createService(final DependencyA dependencyA, final DependencyB dependencyB) {
        return new Service(dependencyA, dependencyB);
    }

    public static List<String> copyLabels(final List<String> labels) {
        return List.copyOf(labels);
    }

    public static <T> T identity(final T value) {
        return value;
    }
}
