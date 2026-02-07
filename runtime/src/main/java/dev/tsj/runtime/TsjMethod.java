package dev.tsj.runtime;

/**
 * Method callable that receives explicit receiver object.
 */
@FunctionalInterface
public interface TsjMethod {
    Object call(TsjObject thisObject, Object... args);
}
