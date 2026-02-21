package dev.tsj.compiler.backend.jvm;

import java.util.ArrayList;
import java.util.List;

final class JavaSignatureParser {
    JavaTypeModel.JFieldSig parseFieldSignatureOrDescriptor(
            final String signature,
            final String descriptor
    ) {
        if (signature == null || signature.isBlank()) {
            return new JavaTypeModel.JFieldSig(
                    parseDescriptorType(descriptor, 0).type(),
                    true,
                    "Missing generic Signature attribute; descriptor fallback applied."
            );
        }
        try {
            final SignatureCursor cursor = new SignatureCursor(signature);
            final JavaTypeModel.JType type = parseFieldTypeSignature(cursor);
            cursor.expectEnd();
            return new JavaTypeModel.JFieldSig(type, false, "");
        } catch (final RuntimeException parseFailure) {
            return new JavaTypeModel.JFieldSig(
                    parseDescriptorType(descriptor, 0).type(),
                    true,
                    "Unsupported generic Signature `" + signature + "`; descriptor fallback applied."
            );
        }
    }

    JavaTypeModel.JMethodSig parseMethodSignatureOrDescriptor(
            final String signature,
            final String descriptor
    ) {
        if (signature == null || signature.isBlank()) {
            return parseMethodFromDescriptor(
                    descriptor,
                    "Missing generic Signature attribute; descriptor fallback applied."
            );
        }
        try {
            final SignatureCursor cursor = new SignatureCursor(signature);
            final List<JavaTypeModel.JTypeParameter> typeParameters = parseTypeParameterSection(cursor);
            cursor.expect('(');
            final List<JavaTypeModel.JType> parameters = new ArrayList<>();
            while (!cursor.consumeIf(')')) {
                parameters.add(parseTypeSignature(cursor));
            }
            final JavaTypeModel.JType returnType = parseReturnType(cursor);
            while (cursor.consumeIf('^')) {
                if (cursor.consumeIf('T')) {
                    cursor.readIdentifierUntil(';');
                    cursor.expect(';');
                } else if (cursor.consumeIf('L')) {
                    skipClassTypeSuffix(cursor);
                    cursor.expect(';');
                } else {
                    throw new IllegalArgumentException("Unsupported throws signature");
                }
            }
            cursor.expectEnd();
            return new JavaTypeModel.JMethodSig(
                    List.copyOf(typeParameters),
                    List.copyOf(parameters),
                    returnType,
                    false,
                    ""
            );
        } catch (final RuntimeException parseFailure) {
            return parseMethodFromDescriptor(
                    descriptor,
                    "Unsupported generic Signature `" + signature + "`; descriptor fallback applied."
            );
        }
    }

    private static List<JavaTypeModel.JTypeParameter> parseTypeParameterSection(final SignatureCursor cursor) {
        final List<JavaTypeModel.JTypeParameter> typeParameters = new ArrayList<>();
        if (!cursor.consumeIf('<')) {
            return typeParameters;
        }
        while (!cursor.consumeIf('>')) {
            final String identifier = cursor.readIdentifierUntil(':');
            cursor.expect(':');
            final List<JavaTypeModel.JType> bounds = new ArrayList<>();
            if (!cursor.peekIs(':')) {
                bounds.add(parseFieldTypeSignature(cursor));
            }
            while (cursor.consumeIf(':')) {
                bounds.add(parseFieldTypeSignature(cursor));
            }
            if (bounds.size() > 1) {
                typeParameters.add(new JavaTypeModel.JTypeParameter(
                        identifier,
                        List.of(new JavaTypeModel.IntersectionType(List.copyOf(bounds)))
                ));
            } else {
                typeParameters.add(new JavaTypeModel.JTypeParameter(identifier, List.copyOf(bounds)));
            }
        }
        return typeParameters;
    }

    private static JavaTypeModel.JType parseReturnType(final SignatureCursor cursor) {
        if (cursor.consumeIf('V')) {
            return new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.VOID);
        }
        return parseTypeSignature(cursor);
    }

    private static JavaTypeModel.JType parseTypeSignature(final SignatureCursor cursor) {
        final char marker = cursor.peek();
        if (marker == 'L' || marker == 'T' || marker == '[') {
            return parseFieldTypeSignature(cursor);
        }
        return parseBaseType(cursor.next());
    }

    private static JavaTypeModel.JType parseFieldTypeSignature(final SignatureCursor cursor) {
        final char marker = cursor.next();
        return switch (marker) {
            case 'L' -> parseClassTypeSignature(cursor);
            case 'T' -> {
                final String identifier = cursor.readIdentifierUntil(';');
                cursor.expect(';');
                yield new JavaTypeModel.TypeVariableType(identifier);
            }
            case '[' -> new JavaTypeModel.ArrayType(parseTypeSignature(cursor));
            default -> throw new IllegalArgumentException("Unsupported field type signature marker: " + marker);
        };
    }

    private static JavaTypeModel.JType parseClassTypeSignature(final SignatureCursor cursor) {
        final StringBuilder internalName = new StringBuilder();
        JavaTypeModel.JType ownerType = null;
        while (true) {
            internalName.append(cursor.readIdentifierUntilAny('<', ';', '.'));
            final List<JavaTypeModel.JType> typeArguments;
            if (cursor.consumeIf('<')) {
                typeArguments = List.copyOf(parseTypeArguments(cursor));
            } else {
                typeArguments = List.of();
            }
            final JavaTypeModel.JType currentType;
            if (typeArguments.isEmpty() && ownerType == null) {
                currentType = new JavaTypeModel.ClassType(internalName.toString());
            } else {
                currentType = new JavaTypeModel.ParameterizedType(
                        internalName.toString(),
                        typeArguments,
                        ownerType
                );
            }
            if (cursor.consumeIf('.')) {
                ownerType = currentType;
                internalName.append('$');
                continue;
            }
            cursor.expect(';');
            return currentType;
        }
    }

    private static void skipClassTypeSuffix(final SignatureCursor cursor) {
        int nestedTypeArguments = 0;
        while (!cursor.peekIs(';')) {
            if (cursor.consumeIf('<')) {
                nestedTypeArguments++;
                continue;
            }
            if (cursor.consumeIf('>')) {
                nestedTypeArguments--;
                continue;
            }
            cursor.next();
        }
        if (nestedTypeArguments != 0) {
            throw new IllegalArgumentException("Unbalanced class type signature.");
        }
    }

    private static List<JavaTypeModel.JType> parseTypeArguments(final SignatureCursor cursor) {
        final List<JavaTypeModel.JType> arguments = new ArrayList<>();
        while (!cursor.consumeIf('>')) {
            if (cursor.consumeIf('*')) {
                arguments.add(new JavaTypeModel.WildcardType(JavaTypeModel.WildcardVariance.UNBOUNDED, null));
                continue;
            }
            if (cursor.consumeIf('+')) {
                arguments.add(new JavaTypeModel.WildcardType(
                        JavaTypeModel.WildcardVariance.EXTENDS,
                        parseFieldTypeSignature(cursor)
                ));
                continue;
            }
            if (cursor.consumeIf('-')) {
                arguments.add(new JavaTypeModel.WildcardType(
                        JavaTypeModel.WildcardVariance.SUPER,
                        parseFieldTypeSignature(cursor)
                ));
                continue;
            }
            arguments.add(parseFieldTypeSignature(cursor));
        }
        return arguments;
    }

    private static JavaTypeModel.JMethodSig parseMethodFromDescriptor(
            final String descriptor,
            final String fallbackNote
    ) {
        final SignatureCursor cursor = new SignatureCursor(descriptor);
        cursor.expect('(');
        final List<JavaTypeModel.JType> parameters = new ArrayList<>();
        while (!cursor.consumeIf(')')) {
            final DescriptorTypeResult result = parseDescriptorType(cursor.source(), cursor.position());
            parameters.add(result.type());
            cursor.moveTo(result.nextIndex());
        }
        final DescriptorTypeResult returnType = parseDescriptorType(cursor.source(), cursor.position());
        cursor.moveTo(returnType.nextIndex());
        cursor.expectEnd();
        return new JavaTypeModel.JMethodSig(
                List.of(),
                List.copyOf(parameters),
                returnType.type(),
                true,
                fallbackNote
        );
    }

    private static DescriptorTypeResult parseDescriptorType(final String descriptor, final int startIndex) {
        final char marker = descriptor.charAt(startIndex);
        return switch (marker) {
            case 'B' -> new DescriptorTypeResult(new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.BYTE), startIndex + 1);
            case 'C' -> new DescriptorTypeResult(new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.CHAR), startIndex + 1);
            case 'D' -> new DescriptorTypeResult(new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.DOUBLE), startIndex + 1);
            case 'F' -> new DescriptorTypeResult(new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.FLOAT), startIndex + 1);
            case 'I' -> new DescriptorTypeResult(new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.INT), startIndex + 1);
            case 'J' -> new DescriptorTypeResult(new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.LONG), startIndex + 1);
            case 'S' -> new DescriptorTypeResult(new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.SHORT), startIndex + 1);
            case 'Z' -> new DescriptorTypeResult(new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.BOOLEAN), startIndex + 1);
            case 'V' -> new DescriptorTypeResult(new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.VOID), startIndex + 1);
            case 'L' -> {
                final int endIndex = descriptor.indexOf(';', startIndex);
                if (endIndex < 0) {
                    throw new IllegalArgumentException("Invalid descriptor: " + descriptor);
                }
                yield new DescriptorTypeResult(
                        new JavaTypeModel.ClassType(descriptor.substring(startIndex + 1, endIndex)),
                        endIndex + 1
                );
            }
            case '[' -> {
                final DescriptorTypeResult element = parseDescriptorType(descriptor, startIndex + 1);
                yield new DescriptorTypeResult(new JavaTypeModel.ArrayType(element.type()), element.nextIndex());
            }
            default -> throw new IllegalArgumentException("Unsupported descriptor marker: " + marker);
        };
    }

    private static JavaTypeModel.JType parseBaseType(final char marker) {
        return switch (marker) {
            case 'B' -> new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.BYTE);
            case 'C' -> new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.CHAR);
            case 'D' -> new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.DOUBLE);
            case 'F' -> new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.FLOAT);
            case 'I' -> new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.INT);
            case 'J' -> new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.LONG);
            case 'S' -> new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.SHORT);
            case 'Z' -> new JavaTypeModel.PrimitiveType(JavaTypeModel.PrimitiveKind.BOOLEAN);
            default -> throw new IllegalArgumentException("Unsupported base type marker: " + marker);
        };
    }

    private record DescriptorTypeResult(JavaTypeModel.JType type, int nextIndex) {
    }

    private static final class SignatureCursor {
        private final String source;
        private int position;

        private SignatureCursor(final String source) {
            this.source = source;
            this.position = 0;
        }

        private String source() {
            return source;
        }

        private int position() {
            return position;
        }

        private void moveTo(final int nextPosition) {
            this.position = nextPosition;
        }

        private char next() {
            if (position >= source.length()) {
                throw new IllegalArgumentException("Unexpected end of signature: " + source);
            }
            return source.charAt(position++);
        }

        private char peek() {
            if (position >= source.length()) {
                throw new IllegalArgumentException("Unexpected end of signature: " + source);
            }
            return source.charAt(position);
        }

        private boolean peekIs(final char expected) {
            return position < source.length() && source.charAt(position) == expected;
        }

        private boolean consumeIf(final char expected) {
            if (!peekIs(expected)) {
                return false;
            }
            position++;
            return true;
        }

        private void expect(final char expected) {
            final char actual = next();
            if (actual != expected) {
                throw new IllegalArgumentException(
                        "Expected `" + expected + "` but found `" + actual + "` in " + source
                );
            }
        }

        private void expectEnd() {
            if (position != source.length()) {
                throw new IllegalArgumentException("Unexpected trailing signature content: " + source);
            }
        }

        private String readIdentifierUntil(final char delimiter) {
            final int start = position;
            while (!peekIs(delimiter)) {
                next();
            }
            return source.substring(start, position);
        }

        private String readIdentifierUntilAny(
                final char firstDelimiter,
                final char secondDelimiter,
                final char thirdDelimiter
        ) {
            final int start = position;
            while (position < source.length()) {
                final char marker = source.charAt(position);
                if (marker == firstDelimiter || marker == secondDelimiter || marker == thirdDelimiter) {
                    break;
                }
                position++;
            }
            return source.substring(start, position);
        }
    }
}
