package dev.tsj.compiler.backend.jvm;

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates TSJ-37a validation constraints extracted from TS decorators.
 */
public final class TsjValidationSubsetEvaluator {
    static final String CONSTRAINT_CODE = "TSJ-VALIDATION-CONSTRAINT";
    static final String FEATURE_ID = "TSJ37A-VALIDATION";
    private static final String GUIDANCE =
            "Use @Validated with supported constraint decorators "
                    + "@NotNull, @NotBlank, @Size, @Min, and @Max.";
    private static final Set<String> CONSTRAINT_DECORATORS = Set.of(
            "NotNull",
            "NotBlank",
            "Size",
            "Min",
            "Max"
    );

    private final TsAnnotationAttributeParser attributeParser;

    public TsjValidationSubsetEvaluator() {
        this(new TsAnnotationAttributeParser());
    }

    TsjValidationSubsetEvaluator(final TsAnnotationAttributeParser attributeParser) {
        this.attributeParser = Objects.requireNonNull(attributeParser, "attributeParser");
    }

    public List<ValidatedMethod> collectValidatedMethods(final TsDecoratorModel model) {
        Objects.requireNonNull(model, "model");
        final List<ValidatedMethod> methods = new ArrayList<>();
        for (TsDecoratedClass decoratedClass : model.classes()) {
            final boolean classValidated = hasDecorator(decoratedClass.decorators(), "Validated");
            for (TsDecoratedMethod method : decoratedClass.methods()) {
                if (method.constructor()) {
                    continue;
                }
                if (!classValidated && !hasDecorator(method.decorators(), "Validated")) {
                    continue;
                }
                final List<ParameterConstraints> parameterConstraints = collectParameterConstraints(
                        decoratedClass.sourceFile(),
                        method
                );
                if (parameterConstraints.isEmpty()) {
                    continue;
                }
                methods.add(new ValidatedMethod(
                        decoratedClass.className(),
                        method.methodName(),
                        parameterConstraints
                ));
            }
        }
        return List.copyOf(methods);
    }

    public List<ConstraintViolation> validate(final ValidatedMethod method, final Object[] args) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(args, "args");
        final List<ConstraintViolation> violations = new ArrayList<>();
        for (ParameterConstraints parameter : method.parameters()) {
            final Object value = parameter.index() < args.length ? args[parameter.index()] : null;
            for (ConstraintDefinition constraint : parameter.constraints()) {
                if (constraint.isViolation(value)) {
                    violations.add(new ConstraintViolation(
                            parameter.name(),
                            constraint.message(),
                            constraint.name()
                    ));
                }
            }
        }
        return List.copyOf(violations);
    }

    public Object validValue(final ParameterConstraints parameter) {
        Objects.requireNonNull(parameter, "parameter");
        Integer minNumber = null;
        Integer maxNumber = null;
        Integer minSize = 0;
        Integer maxSize = Integer.MAX_VALUE;
        boolean notBlank = false;
        boolean notNull = false;
        for (ConstraintDefinition constraint : parameter.constraints()) {
            switch (constraint.name()) {
                case "Min":
                    minNumber = constraint.minValue();
                    break;
                case "Max":
                    maxNumber = constraint.maxValue();
                    break;
                case "Size":
                    minSize = Math.max(minSize, constraint.minValue());
                    maxSize = Math.min(maxSize, constraint.maxValue());
                    break;
                case "NotBlank":
                    notBlank = true;
                    break;
                case "NotNull":
                    notNull = true;
                    break;
                default:
                    throw new IllegalStateException("Unsupported validation constraint: " + constraint.name());
            }
        }
        if (minNumber != null || maxNumber != null) {
            final int lower = minNumber == null ? Integer.MIN_VALUE : minNumber;
            final int upper = maxNumber == null ? Integer.MAX_VALUE : maxNumber;
            if (lower > upper) {
                throw new IllegalArgumentException("Invalid numeric validation range for parameter " + parameter.name());
            }
            if (lower == Integer.MIN_VALUE && upper == Integer.MAX_VALUE) {
                return 0;
            }
            if (lower == Integer.MIN_VALUE) {
                return upper;
            }
            return lower;
        }
        if (notBlank || notNull || maxSize != Integer.MAX_VALUE || minSize > 0) {
            final int length = chooseValidStringLength(minSize, maxSize);
            return "x".repeat(length);
        }
        return null;
    }

    public Object invalidValue(final ParameterConstraints parameter, final ConstraintDefinition constraint) {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(constraint, "constraint");
        return switch (constraint.name()) {
            case "NotNull" -> null;
            case "NotBlank" -> "   ";
            case "Size" -> invalidSizeValue(constraint);
            case "Min" -> (long) constraint.minValue() - 1L;
            case "Max" -> (long) constraint.maxValue() + 1L;
            default -> throw new IllegalStateException("Unsupported validation constraint: " + constraint.name());
        };
    }

    private static Object invalidSizeValue(final ConstraintDefinition constraint) {
        final int min = constraint.minValue();
        final int max = constraint.maxValue();
        if (min > 0) {
            return "x".repeat(Math.max(0, min - 1));
        }
        if (max == Integer.MAX_VALUE) {
            return "";
        }
        return "x".repeat(max + 1);
    }

    private static int chooseValidStringLength(final int minSize, final int maxSize) {
        if (maxSize <= 0) {
            return 1;
        }
        int length = Math.max(1, minSize);
        if (length > maxSize) {
            length = maxSize;
        }
        return Math.max(1, length);
    }

    private List<ParameterConstraints> collectParameterConstraints(final Path sourceFile, final TsDecoratedMethod method) {
        final List<ParameterConstraints> parameters = new ArrayList<>();
        for (TsDecoratedParameter parameter : method.parameters()) {
            final List<ConstraintDefinition> constraints = new ArrayList<>();
            for (TsDecoratorUse decorator : parameter.decorators()) {
                if (!CONSTRAINT_DECORATORS.contains(decorator.name())) {
                    continue;
                }
                constraints.add(parseConstraintDefinition(sourceFile, decorator, parameter.name()));
            }
            if (!constraints.isEmpty()) {
                parameters.add(new ParameterConstraints(parameter.index(), parameter.name(), constraints));
            }
        }
        return List.copyOf(parameters);
    }

    private ConstraintDefinition parseConstraintDefinition(
            final Path sourceFile,
            final TsDecoratorUse decorator,
            final String parameterName
    ) {
        return switch (decorator.name()) {
            case "NotNull" -> parseNotNullConstraint(sourceFile, decorator, parameterName);
            case "NotBlank" -> parseNotBlankConstraint(sourceFile, decorator, parameterName);
            case "Size" -> parseSizeConstraint(sourceFile, decorator, parameterName);
            case "Min" -> parseMinConstraint(sourceFile, decorator, parameterName);
            case "Max" -> parseMaxConstraint(sourceFile, decorator, parameterName);
            default -> throw constraintDiagnostic(
                    sourceFile,
                    decorator.line(),
                    "Unsupported validation constraint decorator @" + decorator.name()
                            + " on parameter `" + parameterName + "`."
            );
        };
    }

    private ConstraintDefinition parseNotNullConstraint(
            final Path sourceFile,
            final TsDecoratorUse decorator,
            final String parameterName
    ) {
        final String message = parseMessage(
                sourceFile,
                decorator,
                parameterName,
                "must not be null"
        );
        return new ConstraintDefinition("NotNull", message, null, null);
    }

    private ConstraintDefinition parseNotBlankConstraint(
            final Path sourceFile,
            final TsDecoratorUse decorator,
            final String parameterName
    ) {
        final String message = parseMessage(
                sourceFile,
                decorator,
                parameterName,
                "must not be blank"
        );
        return new ConstraintDefinition("NotBlank", message, null, null);
    }

    private ConstraintDefinition parseSizeConstraint(
            final Path sourceFile,
            final TsDecoratorUse decorator,
            final String parameterName
    ) {
        final TsAnnotationAttributeParser.DecoratorAttributes attributes = parseAttributes(sourceFile, decorator);
        int min = 0;
        int max = Integer.MAX_VALUE;
        if (attributes != null) {
            if (attributes.has("min")) {
                min = attributes.requireInt("min");
            }
            if (attributes.has("max")) {
                max = attributes.requireInt("max");
            }
        }
        if (min < 0) {
            throw constraintDiagnostic(
                    sourceFile,
                    decorator.line(),
                    "@Size min must be >= 0 for parameter `" + parameterName + "`."
            );
        }
        if (max < 0) {
            throw constraintDiagnostic(
                    sourceFile,
                    decorator.line(),
                    "@Size max must be >= 0 for parameter `" + parameterName + "`."
            );
        }
        if (min > max) {
            throw constraintDiagnostic(
                    sourceFile,
                    decorator.line(),
                    "@Size min must be <= max for parameter `" + parameterName + "`."
            );
        }
        final String defaultMessage = "size must be between " + min + " and " + max;
        final String message = parseMessage(sourceFile, decorator, parameterName, defaultMessage);
        return new ConstraintDefinition("Size", message, min, max);
    }

    private ConstraintDefinition parseMinConstraint(
            final Path sourceFile,
            final TsDecoratorUse decorator,
            final String parameterName
    ) {
        final TsAnnotationAttributeParser.DecoratorAttributes attributes = parseRequiredAttributes(
                sourceFile,
                decorator,
                parameterName
        );
        final int min = attributes.requireInt("value");
        final String message = parseMessage(
                sourceFile,
                decorator,
                parameterName,
                "must be greater than or equal to " + min
        );
        return new ConstraintDefinition("Min", message, min, null);
    }

    private ConstraintDefinition parseMaxConstraint(
            final Path sourceFile,
            final TsDecoratorUse decorator,
            final String parameterName
    ) {
        final TsAnnotationAttributeParser.DecoratorAttributes attributes = parseRequiredAttributes(
                sourceFile,
                decorator,
                parameterName
        );
        final int max = attributes.requireInt("value");
        final String message = parseMessage(
                sourceFile,
                decorator,
                parameterName,
                "must be less than or equal to " + max
        );
        return new ConstraintDefinition("Max", message, null, max);
    }

    private TsAnnotationAttributeParser.DecoratorAttributes parseRequiredAttributes(
            final Path sourceFile,
            final TsDecoratorUse decorator,
            final String parameterName
    ) {
        final TsAnnotationAttributeParser.DecoratorAttributes attributes = parseAttributes(sourceFile, decorator);
        if (attributes == null || !attributes.has("value")) {
            throw constraintDiagnostic(
                    sourceFile,
                    decorator.line(),
                    "Decorator @" + decorator.name() + " requires a numeric `value` for parameter `"
                            + parameterName + "`."
            );
        }
        return attributes;
    }

    private String parseMessage(
            final Path sourceFile,
            final TsDecoratorUse decorator,
            final String parameterName,
            final String defaultMessage
    ) {
        final TsAnnotationAttributeParser.DecoratorAttributes attributes = parseAttributes(sourceFile, decorator);
        if (attributes == null) {
            return defaultMessage;
        }
        if (!attributes.has("message")) {
            return defaultMessage;
        }
        final String message = attributes.requireString("message").trim();
        if (message.isEmpty()) {
            throw constraintDiagnostic(
                    sourceFile,
                    decorator.line(),
                    "Decorator @" + decorator.name() + " message must not be blank for parameter `"
                            + parameterName + "`."
            );
        }
        return message;
    }

    private TsAnnotationAttributeParser.DecoratorAttributes parseAttributes(
            final Path sourceFile,
            final TsDecoratorUse decorator
    ) {
        if (decorator.rawArgs() == null || decorator.rawArgs().isBlank()) {
            return null;
        }
        try {
            return attributeParser.parse(
                    decorator.rawArgs(),
                    sourceFile,
                    decorator.line(),
                    decorator.name()
            );
        } catch (final JvmCompilationException compilationException) {
            throw new JvmCompilationException(
                    CONSTRAINT_CODE,
                    "Invalid @" + decorator.name() + " attributes in TSJ-37a subset: "
                            + compilationException.getMessage(),
                    decorator.line(),
                    1,
                    sourceFile.toString(),
                    FEATURE_ID,
                    GUIDANCE,
                    compilationException
            );
        }
    }

    private static boolean hasDecorator(final List<TsDecoratorUse> decorators, final String name) {
        for (TsDecoratorUse decorator : decorators) {
            if (name.equals(decorator.name())) {
                return true;
            }
        }
        return false;
    }

    private static JvmCompilationException constraintDiagnostic(
            final Path sourceFile,
            final int line,
            final String message
    ) {
        return new JvmCompilationException(
                CONSTRAINT_CODE,
                message,
                line,
                1,
                sourceFile.toString(),
                FEATURE_ID,
                GUIDANCE
        );
    }

    public record ValidatedMethod(
            String className,
            String methodName,
            List<ParameterConstraints> parameters
    ) {
        public ValidatedMethod {
            className = Objects.requireNonNull(className, "className");
            methodName = Objects.requireNonNull(methodName, "methodName");
            parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        }
    }

    public record ParameterConstraints(
            int index,
            String name,
            List<ConstraintDefinition> constraints
    ) {
        public ParameterConstraints {
            name = Objects.requireNonNull(name, "name");
            constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        }
    }

    public record ConstraintDefinition(
            String name,
            String message,
            Integer minValue,
            Integer maxValue
    ) {
        public ConstraintDefinition {
            name = Objects.requireNonNull(name, "name");
            message = Objects.requireNonNull(message, "message");
        }

        boolean isViolation(final Object value) {
            return switch (name) {
                case "NotNull" -> value == null;
                case "NotBlank" -> value == null || String.valueOf(value).trim().isEmpty();
                case "Size" -> isSizeViolation(value);
                case "Min" -> isMinViolation(value);
                case "Max" -> isMaxViolation(value);
                default -> throw new IllegalStateException("Unsupported validation constraint: " + name);
            };
        }

        private boolean isSizeViolation(final Object value) {
            if (value == null) {
                return false;
            }
            final int length = valueLength(value);
            return length < minValue || length > maxValue;
        }

        private boolean isMinViolation(final Object value) {
            if (value == null) {
                return false;
            }
            final Double numeric = asDouble(value);
            return numeric == null || numeric < minValue;
        }

        private boolean isMaxViolation(final Object value) {
            if (value == null) {
                return false;
            }
            final Double numeric = asDouble(value);
            return numeric == null || numeric > maxValue;
        }

        private static int valueLength(final Object value) {
            if (value instanceof CharSequence charSequence) {
                return charSequence.length();
            }
            if (value instanceof java.util.Collection<?> collection) {
                return collection.size();
            }
            if (value.getClass().isArray()) {
                return Array.getLength(value);
            }
            return String.valueOf(value).length();
        }

        private static Double asDouble(final Object value) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String text) {
                try {
                    return Double.parseDouble(text);
                } catch (final NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    public record ConstraintViolation(String field, String message, String constraint) {
        public ConstraintViolation {
            field = Objects.requireNonNull(field, "field");
            message = Objects.requireNonNull(message, "message");
            constraint = Objects.requireNonNull(constraint, "constraint");
        }
    }
}
