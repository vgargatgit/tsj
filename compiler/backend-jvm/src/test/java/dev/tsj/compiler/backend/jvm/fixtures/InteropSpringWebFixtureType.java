package dev.tsj.compiler.backend.jvm.fixtures;

import java.util.List;

/**
 * Spring-web oriented fixture targets used by TSJ-34 bridge generation tests.
 */
public final class InteropSpringWebFixtureType {
    private InteropSpringWebFixtureType() {
    }

    public static UserDto findUser(final String id) {
        return new UserDto(id, "user-" + id, List.of("alpha", "beta"));
    }

    public static UserDto validateUser(final String id) {
        if ("bad".equals(id)) {
            throw new IllegalArgumentException("bad user id");
        }
        return findUser(id);
    }

    public static UserDto instanceOnly(final String id) {
        throw new IllegalStateException("not used");
    }

    public static final class InstanceService {
        public UserDto find(final String id) {
            return new UserDto(id, "inst-" + id, List.of("x"));
        }
    }

    public static final class UserDto {
        private final String id;
        private final String name;
        private final List<String> tags;

        public UserDto(final String id, final String name, final List<String> tags) {
            this.id = id;
            this.name = name;
            this.tags = tags;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public List<String> tags() {
            return tags;
        }
    }
}
