package dev.tsj.compiler.backend.jvm.fixtures.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PARAMETER})
public @interface RichMark {
    String value() default "";

    String name() default "";

    int count() default 0;

    String[] tags() default {};
}
