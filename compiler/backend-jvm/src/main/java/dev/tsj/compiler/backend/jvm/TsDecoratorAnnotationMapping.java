package dev.tsj.compiler.backend.jvm;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * TS decorator -> JVM annotation mapping contract for TSJ-32a subset.
 */
public final class TsDecoratorAnnotationMapping {
    private static final Map<String, String> DEFAULT_CLASS_DECORATORS = Map.ofEntries(
            Map.entry("Component", "org.springframework.stereotype.Component"),
            Map.entry("Service", "org.springframework.stereotype.Service"),
            Map.entry("Repository", "org.springframework.stereotype.Repository"),
            Map.entry("Controller", "org.springframework.stereotype.Controller"),
            Map.entry("RestController", "org.springframework.web.bind.annotation.RestController"),
            Map.entry("RequestMapping", "org.springframework.web.bind.annotation.RequestMapping"),
            Map.entry("Configuration", "org.springframework.context.annotation.Configuration"),
            Map.entry("Primary", "org.springframework.context.annotation.Primary"),
            Map.entry("Qualifier", "org.springframework.beans.factory.annotation.Qualifier"),
            Map.entry("Endpoint", "org.springframework.boot.actuate.endpoint.annotation.Endpoint"),
            Map.entry("Transactional", "org.springframework.transaction.annotation.Transactional"),
            Map.entry("Validated", "org.springframework.validation.annotation.Validated")
    );
    private static final Map<String, String> DEFAULT_METHOD_DECORATORS = Map.ofEntries(
            Map.entry("Bean", "org.springframework.context.annotation.Bean"),
            Map.entry("Autowired", "org.springframework.beans.factory.annotation.Autowired"),
            Map.entry("Qualifier", "org.springframework.beans.factory.annotation.Qualifier"),
            Map.entry("Primary", "org.springframework.context.annotation.Primary"),
            Map.entry("RequestMapping", "org.springframework.web.bind.annotation.RequestMapping"),
            Map.entry("GetMapping", "org.springframework.web.bind.annotation.GetMapping"),
            Map.entry("PostMapping", "org.springframework.web.bind.annotation.PostMapping"),
            Map.entry("PutMapping", "org.springframework.web.bind.annotation.PutMapping"),
            Map.entry("DeleteMapping", "org.springframework.web.bind.annotation.DeleteMapping"),
            Map.entry("PatchMapping", "org.springframework.web.bind.annotation.PatchMapping"),
            Map.entry("ExceptionHandler", "org.springframework.web.bind.annotation.ExceptionHandler"),
            Map.entry("ResponseStatus", "org.springframework.web.bind.annotation.ResponseStatus"),
            Map.entry("PreAuthorize", "org.springframework.security.access.prepost.PreAuthorize"),
            Map.entry("ReadOperation", "org.springframework.boot.actuate.endpoint.annotation.ReadOperation"),
            Map.entry("PostConstruct", "jakarta.annotation.PostConstruct"),
            Map.entry("PreDestroy", "jakarta.annotation.PreDestroy"),
            Map.entry("Transactional", "org.springframework.transaction.annotation.Transactional"),
            Map.entry("Validated", "org.springframework.validation.annotation.Validated")
    );
    private static final Map<String, String> DEFAULT_PARAMETER_DECORATORS = Map.ofEntries(
            Map.entry("RequestParam", "org.springframework.web.bind.annotation.RequestParam"),
            Map.entry("PathVariable", "org.springframework.web.bind.annotation.PathVariable"),
            Map.entry("RequestHeader", "org.springframework.web.bind.annotation.RequestHeader"),
            Map.entry("RequestBody", "org.springframework.web.bind.annotation.RequestBody"),
            Map.entry("Qualifier", "org.springframework.beans.factory.annotation.Qualifier"),
            Map.entry("NotNull", "jakarta.validation.constraints.NotNull"),
            Map.entry("NotBlank", "jakarta.validation.constraints.NotBlank"),
            Map.entry("Size", "jakarta.validation.constraints.Size"),
            Map.entry("Min", "jakarta.validation.constraints.Min"),
            Map.entry("Max", "jakarta.validation.constraints.Max"),
            Map.entry("Valid", "jakarta.validation.Valid")
    );
    private final Map<String, String> classDecorators;
    private final Map<String, String> methodDecorators;
    private final Map<String, String> parameterDecorators;

    public TsDecoratorAnnotationMapping() {
        this(DEFAULT_CLASS_DECORATORS, DEFAULT_METHOD_DECORATORS, DEFAULT_PARAMETER_DECORATORS);
    }

    public static TsDecoratorAnnotationMapping empty() {
        return new TsDecoratorAnnotationMapping(Map.of(), Map.of(), Map.of());
    }

    private TsDecoratorAnnotationMapping(
            final Map<String, String> classDecorators,
            final Map<String, String> methodDecorators,
            final Map<String, String> parameterDecorators
    ) {
        this.classDecorators = Map.copyOf(Objects.requireNonNull(classDecorators, "classDecorators"));
        this.methodDecorators = Map.copyOf(Objects.requireNonNull(methodDecorators, "methodDecorators"));
        this.parameterDecorators = Map.copyOf(Objects.requireNonNull(parameterDecorators, "parameterDecorators"));
    }

    public Optional<String> mapClassDecorator(final String decoratorName) {
        Objects.requireNonNull(decoratorName, "decoratorName");
        return Optional.ofNullable(classDecorators.get(decoratorName));
    }

    public Optional<String> mapMethodDecorator(final String decoratorName) {
        Objects.requireNonNull(decoratorName, "decoratorName");
        return Optional.ofNullable(methodDecorators.get(decoratorName));
    }

    public Optional<String> mapParameterDecorator(final String decoratorName) {
        Objects.requireNonNull(decoratorName, "decoratorName");
        return Optional.ofNullable(parameterDecorators.get(decoratorName));
    }

    public Set<String> supportedDecoratorNames() {
        final java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(classDecorators.keySet());
        names.addAll(methodDecorators.keySet());
        names.addAll(parameterDecorators.keySet());
        return Set.copyOf(names);
    }
}
