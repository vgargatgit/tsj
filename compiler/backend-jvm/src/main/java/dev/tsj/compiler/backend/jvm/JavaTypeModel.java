package dev.tsj.compiler.backend.jvm;

import java.util.List;

final class JavaTypeModel {
    private JavaTypeModel() {
    }

    enum PrimitiveKind {
        BOOLEAN,
        BYTE,
        CHAR,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        VOID
    }

    enum WildcardVariance {
        UNBOUNDED,
        EXTENDS,
        SUPER
    }

    sealed interface JType permits PrimitiveType, ClassType, ParameterizedType, ArrayType, TypeVariableType,
            WildcardType, IntersectionType {
    }

    record PrimitiveType(PrimitiveKind kind) implements JType {
    }

    record ClassType(String internalName) implements JType {
    }

    record ParameterizedType(String internalName, List<JType> typeArguments, JType ownerType) implements JType {
        ParameterizedType(String internalName, List<JType> typeArguments) {
            this(internalName, typeArguments, null);
        }
    }

    record ArrayType(JType elementType) implements JType {
    }

    record TypeVariableType(String identifier) implements JType {
    }

    record WildcardType(WildcardVariance variance, JType bound) implements JType {
    }

    record IntersectionType(List<JType> bounds) implements JType {
    }

    record JTypeParameter(String identifier, List<JType> bounds) {
    }

    record JFieldSig(JType type, boolean erasedFallback, String bestEffortNote) {
    }

    record JMethodSig(
            List<JTypeParameter> typeParameters,
            List<JType> parameterTypes,
            JType returnType,
            boolean erasedFallback,
            String bestEffortNote
    ) {
    }
}
