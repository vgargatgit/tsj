package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsDecoratorAnnotationMappingTest {
    @Test
    void mapsSpringClassAndMethodDecoratorsToJvmAnnotations() {
        final TsDecoratorAnnotationMapping mapping = new TsDecoratorAnnotationMapping();

        assertEquals(
                "org.springframework.web.bind.annotation.RestController",
                mapping.mapClassDecorator("RestController").orElseThrow()
        );
        assertEquals(
                "org.springframework.web.bind.annotation.GetMapping",
                mapping.mapMethodDecorator("GetMapping").orElseThrow()
        );
        assertEquals(
                "org.springframework.web.bind.annotation.ResponseStatus",
                mapping.mapMethodDecorator("ResponseStatus").orElseThrow()
        );
        assertEquals(
                "jakarta.annotation.PostConstruct",
                mapping.mapMethodDecorator("PostConstruct").orElseThrow()
        );
        assertEquals(
                "org.springframework.transaction.annotation.Transactional",
                mapping.mapMethodDecorator("Transactional").orElseThrow()
        );
        assertEquals(
                "org.springframework.transaction.annotation.Transactional",
                mapping.mapClassDecorator("Transactional").orElseThrow()
        );
        assertEquals(
                "org.springframework.context.annotation.Primary",
                mapping.mapClassDecorator("Primary").orElseThrow()
        );
        assertEquals(
                "org.springframework.beans.factory.annotation.Qualifier",
                mapping.mapClassDecorator("Qualifier").orElseThrow()
        );
        assertEquals(
                "org.springframework.beans.factory.annotation.Qualifier",
                mapping.mapMethodDecorator("Qualifier").orElseThrow()
        );
        assertEquals(
                "org.springframework.boot.actuate.endpoint.annotation.Endpoint",
                mapping.mapClassDecorator("Endpoint").orElseThrow()
        );
        assertEquals(
                "org.springframework.boot.actuate.endpoint.annotation.ReadOperation",
                mapping.mapMethodDecorator("ReadOperation").orElseThrow()
        );
        assertEquals(
                "org.springframework.security.access.prepost.PreAuthorize",
                mapping.mapMethodDecorator("PreAuthorize").orElseThrow()
        );
        assertEquals(
                "org.springframework.validation.annotation.Validated",
                mapping.mapClassDecorator("Validated").orElseThrow()
        );
        assertEquals(
                "org.springframework.validation.annotation.Validated",
                mapping.mapMethodDecorator("Validated").orElseThrow()
        );
        assertEquals(
                "org.springframework.web.bind.annotation.RequestParam",
                mapping.mapParameterDecorator("RequestParam").orElseThrow()
        );
        assertEquals(
                "org.springframework.beans.factory.annotation.Qualifier",
                mapping.mapParameterDecorator("Qualifier").orElseThrow()
        );
        assertEquals(
                "jakarta.validation.constraints.NotBlank",
                mapping.mapParameterDecorator("NotBlank").orElseThrow()
        );
        assertEquals(
                "jakarta.validation.constraints.Size",
                mapping.mapParameterDecorator("Size").orElseThrow()
        );
        assertEquals(
                "jakarta.validation.constraints.Min",
                mapping.mapParameterDecorator("Min").orElseThrow()
        );
        assertEquals(
                "jakarta.validation.constraints.Max",
                mapping.mapParameterDecorator("Max").orElseThrow()
        );
        assertEquals(
                "jakarta.validation.constraints.NotNull",
                mapping.mapParameterDecorator("NotNull").orElseThrow()
        );
    }

    @Test
    void exposesSupportedDecoratorNameSetForValidation() {
        final TsDecoratorAnnotationMapping mapping = new TsDecoratorAnnotationMapping();

        assertTrue(mapping.supportedDecoratorNames().contains("Component"));
        assertTrue(mapping.supportedDecoratorNames().contains("RestController"));
        assertTrue(mapping.supportedDecoratorNames().contains("Bean"));
        assertTrue(mapping.supportedDecoratorNames().contains("PostConstruct"));
        assertTrue(mapping.supportedDecoratorNames().contains("Transactional"));
        assertTrue(mapping.supportedDecoratorNames().contains("Primary"));
        assertTrue(mapping.supportedDecoratorNames().contains("Qualifier"));
        assertTrue(mapping.supportedDecoratorNames().contains("Endpoint"));
        assertTrue(mapping.supportedDecoratorNames().contains("ReadOperation"));
        assertTrue(mapping.supportedDecoratorNames().contains("PreAuthorize"));
        assertTrue(mapping.supportedDecoratorNames().contains("PathVariable"));
        assertTrue(mapping.supportedDecoratorNames().contains("Validated"));
        assertTrue(mapping.supportedDecoratorNames().contains("NotBlank"));
        assertTrue(mapping.supportedDecoratorNames().contains("Size"));
        assertTrue(mapping.supportedDecoratorNames().contains("Min"));
        assertTrue(mapping.supportedDecoratorNames().contains("Max"));
        assertTrue(mapping.supportedDecoratorNames().contains("NotNull"));
        assertFalse(mapping.supportedDecoratorNames().contains("UnknownDecorator"));
    }
}
