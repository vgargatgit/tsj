package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsAnnotationAttributeParserTest {
    @Test
    void parsesPrimitiveStringEnumClassLiteralAndArrayValues() {
        final TsAnnotationAttributeParser.DecoratorAttributes attributes = new TsAnnotationAttributeParser().parse(
                "{ value: \"hello\", retries: 3, enabled: true, "
                        + "status: enum(\"org.example.Status.OK\"), "
                        + "type: classOf(\"java.lang.String\"), "
                        + "roles: [\"admin\", \"user\"], "
                        + "types: [classOf(\"java.lang.String\"), classOf(\"java.lang.Integer\")] }",
                Path.of("/tmp/model.ts"),
                7,
                "Sample"
        );

        assertEquals("hello", attributes.requireString("value"));
        assertEquals(3, attributes.requireInt("retries"));
        assertTrue(attributes.requireBoolean("enabled"));
        assertEquals("org.example.Status.OK", attributes.requireEnumConstant("status"));
        assertEquals("java.lang.String", attributes.requireClassLiteral("type"));
        assertEquals(List.of("admin", "user"), attributes.requireStringArray("roles"));
        assertEquals(
                List.of("java.lang.String", "java.lang.Integer"),
                attributes.requireClassLiteralArray("types")
        );
    }

    @Test
    void supportsSinglePositionalValueAsImplicitValueAttribute() {
        final TsAnnotationAttributeParser.DecoratorAttributes attributes = new TsAnnotationAttributeParser().parse(
                "\"/api\"",
                Path.of("/tmp/model.ts"),
                11,
                "RequestMapping"
        );

        assertEquals("/api", attributes.requireString("value"));
    }

    @Test
    void rejectsUnsupportedIdentifierAttributeValueWithTargetedDiagnostic() {
        final JvmCompilationException exception = assertThrows(
                JvmCompilationException.class,
                () -> new TsAnnotationAttributeParser().parse(
                        "{ value: SOME_CONST }",
                        Path.of("/tmp/model.ts"),
                        19,
                        "RequestMapping"
                )
        );

        assertEquals("TSJ-DECORATOR-ATTRIBUTE", exception.code());
        assertEquals("TSJ32B-ANNOTATION-ATTRIBUTES", exception.featureId());
        assertTrue(exception.getMessage().contains("Unsupported annotation attribute value"));
    }
}
