package dev.tsj.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsjObjectTest {
    @Test
    void getPrefersOwnThenPrototypeThenUndefined() {
        final TsjObject prototype = new TsjObject(null);
        prototype.setOwn("shared", 7);

        final TsjObject object = new TsjObject(prototype);
        object.setOwn("own", 3);

        assertEquals(3, object.get("own"));
        assertEquals(7, object.get("shared"));
        assertEquals(TsjUndefined.INSTANCE, object.get("missing"));
    }

    @Test
    void getOwnReturnsUndefinedWhenMissing() {
        final TsjObject object = new TsjObject(null);
        assertEquals(TsjUndefined.INSTANCE, object.getOwn("missing"));
    }

    @Test
    void deleteOwnIsTrueAndRemovesPropertyWhenPresent() {
        final TsjObject object = new TsjObject(null);
        object.setOwn("value", 4);
        assertTrue(object.hasOwn("value"));

        assertTrue(object.deleteOwn("value"));
        assertFalse(object.hasOwn("value"));
        assertEquals(TsjUndefined.INSTANCE, object.get("value"));
        assertTrue(object.deleteOwn("value"));
    }

    @Test
    void setPrototypeSupportsReplacementAndNull() {
        final TsjObject first = new TsjObject(null);
        first.setOwn("a", 1);
        final TsjObject second = new TsjObject(null);
        second.setOwn("b", 2);
        final TsjObject object = new TsjObject(first);

        assertEquals(1, object.get("a"));
        object.setPrototype(second);
        assertEquals(2, object.get("b"));
        object.setPrototype(null);
        assertEquals(TsjUndefined.INSTANCE, object.get("b"));
    }

    @Test
    void setPrototypeRejectsCycles() {
        final TsjObject root = new TsjObject(null);
        final TsjObject child = new TsjObject(root);

        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> root.setPrototype(child)
        );
        assertTrue(exception.getMessage().contains("cycle"));
    }

    @Test
    void shapeTokenChangesOnWriteDeleteAndPrototypeChange() {
        final TsjObject firstPrototype = new TsjObject(null);
        final TsjObject secondPrototype = new TsjObject(null);
        final TsjObject object = new TsjObject(firstPrototype);

        final long initial = object.shapeToken();
        object.set("x", 1);
        final long afterSet = object.shapeToken();
        object.deleteOwn("x");
        final long afterDelete = object.shapeToken();
        object.setPrototype(secondPrototype);
        final long afterPrototype = object.shapeToken();

        assertTrue(afterSet > initial);
        assertTrue(afterDelete > afterSet);
        assertTrue(afterPrototype > afterDelete);
    }

    @Test
    void propertyAccessCacheCachesOwnPropertiesAndInvalidatesOnShapeChange() {
        final TsjObject object = new TsjObject(null);
        object.setOwn("count", 1);
        final TsjPropertyAccessCache cache = new TsjPropertyAccessCache("count");

        assertEquals(1, cache.read(object, "count"));
        object.set("count", 2);
        assertEquals(2, cache.read(object, "count"));
    }

    @Test
    void propertyAccessCacheDoesNotCachePrototypeReads() {
        final TsjObject prototype = new TsjObject(null);
        prototype.setOwn("x", 1);
        final TsjObject object = new TsjObject(prototype);
        final TsjPropertyAccessCache cache = new TsjPropertyAccessCache("x");

        assertEquals(1, cache.read(object, "x"));
        prototype.set("x", 5);
        assertEquals(5, cache.read(object, "x"));
    }
}
