package org.apache.skywalking.oap.server.core.analysis.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exactly the same functionalities as {@link FilterMatcher} except for the value type of this matcher is {@code
 * boolean}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BooleanValueFilterMatcher {
    /**
     * @return see {@link FilterMatcher#value()}.
     */
    String[] value() default {};
}
