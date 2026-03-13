package dev.tsj.compiler.backend.jvm.fixtures.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface NestedInnerMark {
    String name();

    int count() default 0;
}
