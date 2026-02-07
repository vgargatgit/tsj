package dev.tsj.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjInteropCodecTest {
    @Test
    void fromJavaConvertsPrimitiveFriendlyValues() {
        assertEquals(7, TsjInteropCodec.fromJava(Integer.valueOf(7)));
        assertEquals(3.5d, TsjInteropCodec.fromJava(Float.valueOf(3.5f)));
        assertEquals("x", TsjInteropCodec.fromJava(Character.valueOf('x')));
        assertEquals(9, TsjInteropCodec.fromJava(Long.valueOf(9L)));
    }

    @Test
    void fromJavaPreservesObjectReferences() {
        final Object marker = new Object();
        assertSame(marker, TsjInteropCodec.fromJava(marker));
    }

    @Test
    void toJavaConvertsToPrimitiveTargets() {
        assertEquals(12, TsjInteropCodec.toJava("12", int.class));
        assertEquals(2.25d, TsjInteropCodec.toJava("2.25", double.class));
        assertEquals(true, TsjInteropCodec.toJava(1, boolean.class));
    }

    @Test
    void toJavaReturnsNullForUndefinedWhenTargetIsReferenceType() {
        assertNull(TsjInteropCodec.toJava(TsjRuntime.undefined(), String.class));
    }

    @Test
    void toJavaKeepsAssignableObjectReference() {
        final TsjObject object = new TsjObject(null);
        assertSame(object, TsjInteropCodec.toJava(object, TsjObject.class));
    }

    @Test
    void toJavaArgumentsConvertsBySignatureAndChecksArity() {
        final Object[] converted = TsjInteropCodec.toJavaArguments(
                new Object[]{"3", 4},
                new Class<?>[]{int.class, double.class}
        );

        assertEquals(3, converted[0]);
        assertEquals(4.0d, converted[1]);

        final IllegalArgumentException arityException = assertThrows(
                IllegalArgumentException.class,
                () -> TsjInteropCodec.toJavaArguments(
                        new Object[]{1},
                        new Class<?>[]{int.class, int.class}
                )
        );
        assertTrue(arityException.getMessage().contains("arity"));
    }

    @Test
    void invokeStaticUsesCodecForArgumentsAndReturnValues() {
        assertEquals(5, TsjJavaInterop.invokeStatic("java.lang.Math", "max", "3", 5));

        final Object singleton = TsjJavaInterop.invokeStatic("java.util.Collections", "singletonList", "tsj");
        assertNotNull(singleton);
        assertTrue(singleton instanceof List<?>);
        assertEquals("tsj", ((List<?>) singleton).get(0));
    }

    @Test
    void invokeStaticRejectsMissingMethod() {
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TsjJavaInterop.invokeStatic("java.lang.Math", "definitelyMissing", 1)
        );
        assertTrue(exception.getMessage().contains("No compatible static method"));
    }
}
