package dev.tsj.compiler.backend.jvm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Parses TS decorator argument text into a typed annotation-attribute model (TSJ-32b subset).
 */
public final class TsAnnotationAttributeParser {
    private static final String FEATURE_ID = "TSJ32B-ANNOTATION-ATTRIBUTES";
    private static final String GUIDANCE =
            "Use supported decorator attribute values: string/number/boolean, "
                    + "enum(\"pkg.Type.CONSTANT\"), classOf(\"pkg.Type\"), and arrays of those.";

    public DecoratorAttributes parse(
            final String rawArgs,
            final Path sourceFile,
            final int line,
            final String decoratorName
    ) {
        Objects.requireNonNull(rawArgs, "rawArgs");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(decoratorName, "decoratorName");

        final Parser parser = new Parser(rawArgs, sourceFile, line, decoratorName);
        return parser.parse();
    }

    public sealed interface AnnotationValue permits
            StringValue, NumberValue, BooleanValue, EnumConstantValue, ClassLiteralValue, ArrayValue {
    }

    public record StringValue(String value) implements AnnotationValue {
    }

    public record NumberValue(String rawNumber) implements AnnotationValue {
        public int asInt() {
            return Integer.parseInt(rawNumber);
        }
    }

    public record BooleanValue(boolean value) implements AnnotationValue {
    }

    public record EnumConstantValue(String qualifiedConstant) implements AnnotationValue {
    }

    public record ClassLiteralValue(String qualifiedClassName) implements AnnotationValue {
    }

    public record ArrayValue(List<AnnotationValue> elements) implements AnnotationValue {
        public ArrayValue {
            elements = List.copyOf(elements);
        }
    }

    public static final class DecoratorAttributes {
        private final Map<String, AnnotationValue> attributes;
        private final Path sourceFile;
        private final int line;
        private final String decoratorName;

        private DecoratorAttributes(
                final Map<String, AnnotationValue> attributes,
                final Path sourceFile,
                final int line,
                final String decoratorName
        ) {
            this.attributes = Map.copyOf(attributes);
            this.sourceFile = sourceFile;
            this.line = line;
            this.decoratorName = decoratorName;
        }

        public boolean has(final String name) {
            return attributes.containsKey(name);
        }

        public Optional<AnnotationValue> get(final String name) {
            return Optional.ofNullable(attributes.get(name));
        }

        public AnnotationValue require(final String name) {
            final AnnotationValue value = attributes.get(name);
            if (value == null) {
                throw attributeError(
                        sourceFile,
                        line,
                        decoratorName,
                        "Missing required annotation attribute `" + name + "`."
                );
            }
            return value;
        }

        public String requireString(final String name) {
            final AnnotationValue value = require(name);
            if (value instanceof StringValue stringValue) {
                return stringValue.value();
            }
            throw typeMismatch(name, "string", value);
        }

        public int requireInt(final String name) {
            final AnnotationValue value = require(name);
            if (value instanceof NumberValue numberValue) {
                try {
                    return numberValue.asInt();
                } catch (final NumberFormatException numberFormatException) {
                    throw typeMismatch(name, "integer", value);
                }
            }
            throw typeMismatch(name, "integer", value);
        }

        public boolean requireBoolean(final String name) {
            final AnnotationValue value = require(name);
            if (value instanceof BooleanValue booleanValue) {
                return booleanValue.value();
            }
            throw typeMismatch(name, "boolean", value);
        }

        public String requireEnumConstant(final String name) {
            final AnnotationValue value = require(name);
            if (value instanceof EnumConstantValue enumConstantValue) {
                return enumConstantValue.qualifiedConstant();
            }
            throw typeMismatch(name, "enum constant", value);
        }

        public String requireClassLiteral(final String name) {
            final AnnotationValue value = require(name);
            if (value instanceof ClassLiteralValue classLiteralValue) {
                return classLiteralValue.qualifiedClassName();
            }
            throw typeMismatch(name, "class literal", value);
        }

        public List<String> requireStringArray(final String name) {
            final AnnotationValue value = require(name);
            if (value instanceof ArrayValue arrayValue) {
                final List<String> values = new ArrayList<>();
                for (AnnotationValue element : arrayValue.elements()) {
                    if (element instanceof StringValue stringValue) {
                        values.add(stringValue.value());
                    } else {
                        throw typeMismatch(name, "string[]", value);
                    }
                }
                return List.copyOf(values);
            }
            throw typeMismatch(name, "string[]", value);
        }

        public List<String> requireClassLiteralArray(final String name) {
            final AnnotationValue value = require(name);
            if (value instanceof ArrayValue arrayValue) {
                final List<String> values = new ArrayList<>();
                for (AnnotationValue element : arrayValue.elements()) {
                    if (element instanceof ClassLiteralValue classLiteralValue) {
                        values.add(classLiteralValue.qualifiedClassName());
                    } else {
                        throw typeMismatch(name, "class[]", value);
                    }
                }
                return List.copyOf(values);
            }
            throw typeMismatch(name, "class[]", value);
        }

        private JvmCompilationException typeMismatch(
                final String attributeName,
                final String expectedType,
                final AnnotationValue actual
        ) {
            return attributeError(
                    sourceFile,
                    line,
                    decoratorName,
                    "Annotation attribute `" + attributeName + "` expects " + expectedType
                            + " in TSJ-32b subset, but got " + actual.getClass().getSimpleName() + "."
            );
        }
    }

    private static JvmCompilationException attributeError(
            final Path sourceFile,
            final int line,
            final String decoratorName,
            final String message
    ) {
        return new JvmCompilationException(
                "TSJ-DECORATOR-ATTRIBUTE",
                "Decorator @" + decoratorName + ": " + message,
                line,
                1,
                sourceFile.toString(),
                FEATURE_ID,
                GUIDANCE
        );
    }

    private static final class Parser {
        private final String input;
        private final Path sourceFile;
        private final int line;
        private final String decoratorName;
        private int index;

        private Parser(
                final String input,
                final Path sourceFile,
                final int line,
                final String decoratorName
        ) {
            this.input = input;
            this.sourceFile = sourceFile;
            this.line = line;
            this.decoratorName = decoratorName;
            this.index = 0;
        }

        private DecoratorAttributes parse() {
            skipWhitespace();
            final Map<String, AnnotationValue> attributes;
            if (peek('{')) {
                attributes = parseObjectAttributes();
            } else {
                attributes = new LinkedHashMap<>();
                attributes.put("value", parseValue());
            }
            skipWhitespace();
            if (!isAtEnd()) {
                throw error("Unexpected trailing text in decorator attributes.");
            }
            return new DecoratorAttributes(attributes, sourceFile, line, decoratorName);
        }

        private Map<String, AnnotationValue> parseObjectAttributes() {
            final Map<String, AnnotationValue> attributes = new LinkedHashMap<>();
            consume('{');
            skipWhitespace();
            if (match('}')) {
                return attributes;
            }
            while (true) {
                final String key = parseObjectKey();
                skipWhitespace();
                consume(':');
                skipWhitespace();
                final AnnotationValue value = parseValue();
                attributes.put(key, value);
                skipWhitespace();
                if (match('}')) {
                    break;
                }
                consume(',');
                skipWhitespace();
            }
            return attributes;
        }

        private String parseObjectKey() {
            if (peek('"') || peek('\'')) {
                return parseStringLiteral();
            }
            return parseIdentifier();
        }

        private AnnotationValue parseValue() {
            skipWhitespace();
            if (isAtEnd()) {
                throw error("Unexpected end of decorator attributes.");
            }
            if (peek('"') || peek('\'')) {
                return new StringValue(parseStringLiteral());
            }
            if (peek('[')) {
                return parseArray();
            }
            if (peek('-') || isDigit(current())) {
                return parseNumber();
            }
            if (startsWithKeyword("true")) {
                index += 4;
                return new BooleanValue(true);
            }
            if (startsWithKeyword("false")) {
                index += 5;
                return new BooleanValue(false);
            }
            if (isIdentifierStart(current())) {
                final String identifier = parseIdentifier();
                skipWhitespace();
                if (match('(')) {
                    skipWhitespace();
                    final AnnotationValue helperValue;
                    if ("enum".equals(identifier)) {
                        final String constant = parseSingleStringArgument(identifier);
                        helperValue = new EnumConstantValue(constant);
                    } else if ("classOf".equals(identifier)) {
                        final String className = parseSingleStringArgument(identifier);
                        helperValue = new ClassLiteralValue(className);
                    } else {
                        throw error("Unsupported annotation helper `" + identifier + "`.");
                    }
                    skipWhitespace();
                    consume(')');
                    return helperValue;
                }
                throw error("Unsupported annotation attribute value: `" + identifier + "`.");
            }
            throw error("Unsupported annotation attribute value starting at `" + current() + "`.");
        }

        private AnnotationValue parseArray() {
            consume('[');
            final List<AnnotationValue> elements = new ArrayList<>();
            skipWhitespace();
            if (match(']')) {
                return new ArrayValue(elements);
            }
            while (true) {
                elements.add(parseValue());
                skipWhitespace();
                if (match(']')) {
                    break;
                }
                consume(',');
                skipWhitespace();
            }
            return new ArrayValue(elements);
        }

        private AnnotationValue parseNumber() {
            final int start = index;
            if (match('-')) {
                if (isAtEnd() || !isDigit(current())) {
                    throw error("Invalid numeric attribute value.");
                }
            }
            while (!isAtEnd() && isDigit(current())) {
                index++;
            }
            if (!isAtEnd() && current() == '.') {
                index++;
                if (isAtEnd() || !isDigit(current())) {
                    throw error("Invalid numeric attribute value.");
                }
                while (!isAtEnd() && isDigit(current())) {
                    index++;
                }
            }
            return new NumberValue(input.substring(start, index));
        }

        private String parseSingleStringArgument(final String helperName) {
            final AnnotationValue value = parseValue();
            if (!(value instanceof StringValue stringValue)) {
                throw error("Helper `" + helperName + "` expects one string argument.");
            }
            skipWhitespace();
            if (peek(',')) {
                throw error("Helper `" + helperName + "` accepts exactly one argument.");
            }
            return stringValue.value();
        }

        private String parseIdentifier() {
            if (isAtEnd() || !isIdentifierStart(current())) {
                throw error("Expected identifier.");
            }
            final int start = index;
            index++;
            while (!isAtEnd() && isIdentifierPart(current())) {
                index++;
            }
            return input.substring(start, index);
        }

        private String parseStringLiteral() {
            final char quote = current();
            consume(quote);
            final StringBuilder builder = new StringBuilder();
            while (!isAtEnd() && current() != quote) {
                final char value = current();
                if (value == '\\') {
                    index++;
                    if (isAtEnd()) {
                        throw error("Unterminated escape sequence in string literal.");
                    }
                    final char escaped = current();
                    switch (escaped) {
                        case '\\', '"', '\'' -> builder.append(escaped);
                        case 'n' -> builder.append('\n');
                        case 't' -> builder.append('\t');
                        default -> builder.append(escaped);
                    }
                    index++;
                } else {
                    builder.append(value);
                    index++;
                }
            }
            if (isAtEnd()) {
                throw error("Unterminated string literal.");
            }
            consume(quote);
            return builder.toString();
        }

        private boolean startsWithKeyword(final String keyword) {
            if (!input.startsWith(keyword, index)) {
                return false;
            }
            final int end = index + keyword.length();
            return end >= input.length() || !isIdentifierPart(input.charAt(end));
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(current())) {
                index++;
            }
        }

        private boolean match(final char value) {
            if (!peek(value)) {
                return false;
            }
            index++;
            return true;
        }

        private void consume(final char value) {
            if (!match(value)) {
                throw error("Expected `" + value + "` in decorator attributes.");
            }
        }

        private boolean peek(final char value) {
            return !isAtEnd() && current() == value;
        }

        private boolean isAtEnd() {
            return index >= input.length();
        }

        private char current() {
            return input.charAt(index);
        }

        private JvmCompilationException error(final String message) {
            return attributeError(sourceFile, line, decoratorName, message);
        }
    }

    private static boolean isIdentifierStart(final char value) {
        return value == '_' || value == '$' || Character.isLetter(value);
    }

    private static boolean isIdentifierPart(final char value) {
        return isIdentifierStart(value) || Character.isDigit(value);
    }

    private static boolean isDigit(final char value) {
        return value >= '0' && value <= '9';
    }
}
