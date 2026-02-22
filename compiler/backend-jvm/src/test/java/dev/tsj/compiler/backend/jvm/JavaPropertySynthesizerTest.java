package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaPropertySynthesizerTest {
    private final JavaPropertySynthesizer synthesizer = new JavaPropertySynthesizer();

    @Test
    void synthesizesGetSetAndIsPropertiesWithDeterministicOrdering() {
        final JavaPropertySynthesizer.SynthesisResult result =
                synthesizer.synthesize(PropertyFixture.class, true);

        assertEquals(3, result.properties().size());
        assertEquals("active", result.properties().get(0).name());
        assertEquals("count", result.properties().get(1).name());
        assertEquals("name", result.properties().get(2).name());
        assertEquals("getName", result.properties().get(2).getterName());
        assertEquals("setName", result.properties().get(2).setterName());
    }

    @Test
    void prefersIsGetterForBooleanPropertiesWhenGetAndIsAreBothPresent() {
        final JavaPropertySynthesizer.SynthesisResult result =
                synthesizer.synthesize(PropertyFixture.class, true);
        final JavaPropertySynthesizer.SynthesizedProperty property = result.properties()
                .stream()
                .filter(candidate -> candidate.name().equals("active"))
                .findFirst()
                .orElseThrow();

        assertEquals("isActive", property.getterName());
        assertEquals("()Z", property.getterDescriptor());
    }

    @Test
    void skipsAmbiguousPropertyShapesWithExplainableDiagnostics() {
        final JavaPropertySynthesizer.SynthesisResult result =
                synthesizer.synthesize(AmbiguousFixture.class, true);

        assertTrue(result.properties().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(message -> message.contains("conflicting get/is")));
        assertTrue(
                result.diagnostics().stream().anyMatch(
                        message -> message.contains("ambiguous setter overloads [setMode(I)V, setMode(J)V]")
                )
        );
    }

    @Test
    void skipsGetterAliasCasingConflictsWithDeterministicReason() {
        final JavaPropertySynthesizer.SynthesisResult result =
                synthesizer.synthesize(UrlAliasFixture.class, true);

        assertTrue(result.properties().isEmpty());
        assertTrue(
                result.diagnostics().stream().anyMatch(
                        message -> message.equals(
                                "Skipped property `url`: conflicting accessor casing aliases [URL, url]."
                        )
                )
        );
    }

    @Test
    void supportsFeatureFlagToDisableSynthesis() {
        final JavaPropertySynthesizer.SynthesisResult result =
                synthesizer.synthesize(PropertyFixture.class, false);

        assertTrue(result.properties().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(message -> message.contains("disabled")));
    }

    public static final class PropertyFixture {
        public String getName() {
            return "x";
        }

        public void setName(final String value) {
        }

        public boolean isActive() {
            return true;
        }

        public boolean getActive() {
            return false;
        }

        public int getCount() {
            return 1;
        }

        public void setCount(final int value) {
        }

        public void setCount(final long value) {
        }
    }

    public static final class AmbiguousFixture {
        public String getMode() {
            return "a";
        }

        public boolean isMode() {
            return true;
        }

        public void setMode(final int value) {
        }

        public void setMode(final long value) {
        }
    }

    public static final class UrlAliasFixture {
        public String getURL() {
            return "https://example.test";
        }

        public String getUrl() {
            return "https://example.test";
        }

        public void setURL(final String value) {
        }

        public void setUrl(final String value) {
        }
    }
}
