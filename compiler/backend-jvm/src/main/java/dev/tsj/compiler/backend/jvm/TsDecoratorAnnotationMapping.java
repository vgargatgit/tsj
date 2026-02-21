package dev.tsj.compiler.backend.jvm;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * TS decorator -> JVM annotation mapping contract for TSJ-32a subset.
 */
public final class TsDecoratorAnnotationMapping {
    private static final Map<String, String> CLASS_DECORATORS = Map.ofEntries(
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
    private static final Map<String, String> METHOD_DECORATORS = Map.ofEntries(
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
    private static final Map<String, String> PARAMETER_DECORATORS = Map.ofEntries(
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

    public Optional<String> mapClassDecorator(final String decoratorName) {
        Objects.requireNonNull(decoratorName, "decoratorName");
        return Optional.ofNullable(CLASS_DECORATORS.get(decoratorName));
    }

    public Optional<String> mapMethodDecorator(final String decoratorName) {
        Objects.requireNonNull(decoratorName, "decoratorName");
        return Optional.ofNullable(METHOD_DECORATORS.get(decoratorName));
    }

    public Optional<String> mapParameterDecorator(final String decoratorName) {
        Objects.requireNonNull(decoratorName, "decoratorName");
        return Optional.ofNullable(PARAMETER_DECORATORS.get(decoratorName));
    }

    public Set<String> supportedDecoratorNames() {
        final java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(CLASS_DECORATORS.keySet());
        names.addAll(METHOD_DECORATORS.keySet());
        names.addAll(PARAMETER_DECORATORS.keySet());
        return Set.copyOf(names);
    }
}
