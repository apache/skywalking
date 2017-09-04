package org.skywalking.apm.toolkit.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The agent create local span if the method that annotation with {@link Trace}. The value of span operation name will
 * fetch by {@link #operationName()}.  if the value of {@link #operationName()} is blank string. the operation name will
 * be set the class name + method name.
 *
 * @author zhangxin
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Trace {
    /**
     * @return operation name, the default value is blank string.
     */
    String operationName() default "";
}
