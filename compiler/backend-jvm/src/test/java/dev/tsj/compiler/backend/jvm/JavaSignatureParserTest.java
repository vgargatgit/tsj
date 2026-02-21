package dev.tsj.compiler.backend.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSignatureParserTest {
    @Test
    void parsesParameterizedFieldSignatureWithWildcardBounds() {
        final JavaSignatureParser parser = new JavaSignatureParser();
        final JavaTypeModel.JFieldSig fieldSig = parser.parseFieldSignatureOrDescriptor(
                "Ljava/util/Map<Ljava/lang/String;Ljava/util/List<+Ljava/lang/Number;>;>;",
                "Ljava/util/Map;"
        );

        assertFalse(fieldSig.erasedFallback());
        final JavaTypeModel.ParameterizedType mapType =
                assertInstanceOf(JavaTypeModel.ParameterizedType.class, fieldSig.type());
        assertEquals("java/util/Map", mapType.internalName());
        assertEquals(2, mapType.typeArguments().size());

        final JavaTypeModel.ClassType keyType =
                assertInstanceOf(JavaTypeModel.ClassType.class, mapType.typeArguments().get(0));
        assertEquals("java/lang/String", keyType.internalName());

        final JavaTypeModel.ParameterizedType listType =
                assertInstanceOf(JavaTypeModel.ParameterizedType.class, mapType.typeArguments().get(1));
        final JavaTypeModel.WildcardType wildcard =
                assertInstanceOf(JavaTypeModel.WildcardType.class, listType.typeArguments().get(0));
        assertEquals(JavaTypeModel.WildcardVariance.EXTENDS, wildcard.variance());
        final JavaTypeModel.ClassType numberBound =
                assertInstanceOf(JavaTypeModel.ClassType.class, wildcard.bound());
        assertEquals("java/lang/Number", numberBound.internalName());
    }

    @Test
    void parsesMethodSignatureWithTypeParametersAndSuperWildcard() {
        final JavaSignatureParser parser = new JavaSignatureParser();
        final JavaTypeModel.JMethodSig methodSig = parser.parseMethodSignatureOrDescriptor(
                "<T:Ljava/lang/Number;R::Ljava/lang/Runnable;>(TT;Ljava/util/List<-Ljava/lang/Integer;>;)"
                        + "Ljava/util/List<TR;>;",
                "(Ljava/lang/Number;Ljava/util/List;)Ljava/util/List;"
        );

        assertFalse(methodSig.erasedFallback());
        assertEquals(2, methodSig.typeParameters().size());
        assertEquals("T", methodSig.typeParameters().get(0).identifier());
        assertEquals("R", methodSig.typeParameters().get(1).identifier());

        final JavaTypeModel.TypeVariableType firstParameter =
                assertInstanceOf(JavaTypeModel.TypeVariableType.class, methodSig.parameterTypes().get(0));
        assertEquals("T", firstParameter.identifier());

        final JavaTypeModel.ParameterizedType secondParameter =
                assertInstanceOf(JavaTypeModel.ParameterizedType.class, methodSig.parameterTypes().get(1));
        final JavaTypeModel.WildcardType wildcard =
                assertInstanceOf(JavaTypeModel.WildcardType.class, secondParameter.typeArguments().get(0));
        assertEquals(JavaTypeModel.WildcardVariance.SUPER, wildcard.variance());
        final JavaTypeModel.ClassType wildcardBound =
                assertInstanceOf(JavaTypeModel.ClassType.class, wildcard.bound());
        assertEquals("java/lang/Integer", wildcardBound.internalName());

        final JavaTypeModel.ParameterizedType returnType =
                assertInstanceOf(JavaTypeModel.ParameterizedType.class, methodSig.returnType());
        final JavaTypeModel.TypeVariableType returnArg =
                assertInstanceOf(JavaTypeModel.TypeVariableType.class, returnType.typeArguments().get(0));
        assertEquals("R", returnArg.identifier());
    }

    @Test
    void fallsBackToDescriptorWhenSignatureMissingOrUnsupported() {
        final JavaSignatureParser parser = new JavaSignatureParser();

        final JavaTypeModel.JFieldSig missingFieldSig = parser.parseFieldSignatureOrDescriptor(
                null,
                "[I"
        );
        assertTrue(missingFieldSig.erasedFallback());
        final JavaTypeModel.ArrayType intArray =
                assertInstanceOf(JavaTypeModel.ArrayType.class, missingFieldSig.type());
        final JavaTypeModel.PrimitiveType intType =
                assertInstanceOf(JavaTypeModel.PrimitiveType.class, intArray.elementType());
        assertEquals(JavaTypeModel.PrimitiveKind.INT, intType.kind());

        final JavaTypeModel.JMethodSig unsupportedMethodSig = parser.parseMethodSignatureOrDescriptor(
                "<T:>(TT;)TT;",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        assertTrue(unsupportedMethodSig.erasedFallback());
        assertTrue(unsupportedMethodSig.bestEffortNote().contains("descriptor fallback"));
    }

    @Test
    void parsesDescriptorFallbackForPrimitiveArrayAndVoid() {
        final JavaSignatureParser parser = new JavaSignatureParser();
        final JavaTypeModel.JMethodSig methodSig = parser.parseMethodSignatureOrDescriptor(
                null,
                "(I[Ljava/lang/String;)V"
        );

        assertTrue(methodSig.erasedFallback());
        assertEquals(2, methodSig.parameterTypes().size());
        final JavaTypeModel.PrimitiveType firstParameter =
                assertInstanceOf(JavaTypeModel.PrimitiveType.class, methodSig.parameterTypes().get(0));
        assertEquals(JavaTypeModel.PrimitiveKind.INT, firstParameter.kind());

        final JavaTypeModel.ArrayType secondParameter =
                assertInstanceOf(JavaTypeModel.ArrayType.class, methodSig.parameterTypes().get(1));
        final JavaTypeModel.ClassType arrayElement =
                assertInstanceOf(JavaTypeModel.ClassType.class, secondParameter.elementType());
        assertEquals("java/lang/String", arrayElement.internalName());

        final JavaTypeModel.PrimitiveType returnType =
                assertInstanceOf(JavaTypeModel.PrimitiveType.class, methodSig.returnType());
        assertEquals(JavaTypeModel.PrimitiveKind.VOID, returnType.kind());
    }

    @Test
    void parsesParameterizedOwnerSignaturesForNestedTypes() {
        final JavaSignatureParser parser = new JavaSignatureParser();
        final JavaTypeModel.JFieldSig fieldSig = parser.parseFieldSignatureOrDescriptor(
                "Ljava/util/Map<Ljava/lang/String;Ljava/lang/Integer;>.Entry<Ljava/lang/Long;>;",
                "Ljava/util/Map$Entry;"
        );

        assertFalse(fieldSig.erasedFallback());
        final JavaTypeModel.ParameterizedType entryType =
                assertInstanceOf(JavaTypeModel.ParameterizedType.class, fieldSig.type());
        assertEquals("java/util/Map$Entry", entryType.internalName());
        assertEquals(1, entryType.typeArguments().size());
        final JavaTypeModel.ClassType entryArg =
                assertInstanceOf(JavaTypeModel.ClassType.class, entryType.typeArguments().get(0));
        assertEquals("java/lang/Long", entryArg.internalName());

        final JavaTypeModel.ParameterizedType owner =
                assertInstanceOf(JavaTypeModel.ParameterizedType.class, entryType.ownerType());
        assertEquals("java/util/Map", owner.internalName());
        assertEquals(2, owner.typeArguments().size());
    }

    @Test
    void parsesIntersectionBoundsIntoExplicitIntersectionType() {
        final JavaSignatureParser parser = new JavaSignatureParser();
        final JavaTypeModel.JMethodSig methodSig = parser.parseMethodSignatureOrDescriptor(
                "<T:Ljava/lang/Number;:Ljava/lang/Comparable<TT;>;>(TT;)TT;",
                "(Ljava/lang/Number;)Ljava/lang/Number;"
        );

        assertFalse(methodSig.erasedFallback());
        assertEquals(1, methodSig.typeParameters().size());
        final JavaTypeModel.JType intersection =
                methodSig.typeParameters().get(0).bounds().get(0);
        final JavaTypeModel.IntersectionType bound =
                assertInstanceOf(JavaTypeModel.IntersectionType.class, intersection);
        assertEquals(2, bound.bounds().size());
        final JavaTypeModel.ClassType classBound =
                assertInstanceOf(JavaTypeModel.ClassType.class, bound.bounds().get(0));
        assertEquals("java/lang/Number", classBound.internalName());
        final JavaTypeModel.ParameterizedType interfaceBound =
                assertInstanceOf(JavaTypeModel.ParameterizedType.class, bound.bounds().get(1));
        assertEquals("java/lang/Comparable", interfaceBound.internalName());
    }
}
