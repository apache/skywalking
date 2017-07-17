package org.skywalking.apm.toolkit.opentracing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The <code>NeedSnifferActivation</code> annotation is flag for reader and maintainers,
 * which represents this method should be activated/intercepted in sniffer.
 *
 * @author wusheng
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.SOURCE)
public @interface NeedSnifferActivation {
    String value() default "What should interceptor do?";
}
