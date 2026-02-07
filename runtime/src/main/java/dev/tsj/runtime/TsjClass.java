package dev.tsj.runtime;

/**
 * Runtime class model for TSJ subset.
 */
public final class TsjClass {
    private final String name;
    private final TsjClass superClass;
    private final TsjObject prototype;
    private TsjMethod constructorMethod;

    public TsjClass(final String name, final TsjClass superClass) {
        this.name = name;
        this.superClass = superClass;
        this.prototype = new TsjObject(superClass != null ? superClass.prototype : null);
        this.constructorMethod = null;
    }

    public String name() {
        return name;
    }

    public TsjClass superClass() {
        return superClass;
    }

    public TsjObject prototype() {
        return prototype;
    }

    public void setConstructor(final TsjMethod constructorMethod) {
        this.constructorMethod = constructorMethod;
    }

    public void defineMethod(final String methodName, final TsjMethod method) {
        prototype.setOwn(methodName, method);
    }

    public Object construct(final Object... args) {
        final TsjObject instance = new TsjObject(prototype);
        invokeConstructor(instance, args);
        return instance;
    }

    public void invokeConstructor(final TsjObject instance, final Object... args) {
        if (constructorMethod != null) {
            constructorMethod.call(instance, args);
        } else if (superClass != null) {
            superClass.invokeConstructor(instance, args);
        }
    }
}
