package com.a.eye.skywalking.toolkit.activation.log.log4j.v2.x;

import com.a.eye.skywalking.api.plugin.interceptor.StaticMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Active the toolkit class "com.a.eye.skywalking.toolkit.log.logback.v2.x.LogbackPatternConverter".
 * Should not dependency or import any class in "skywalking-toolkit-logback-2.x" module.
 * Activation's classloader is diff from "com.a.eye.skywalking.toolkit.log.logback.v2.x.LogbackPatternConverter",
 * using direct will trigger classloader issue.
 *
 * @author wusheng
 */
public class Log4j2OutputAppenderActivation extends ClassStaticMethodsEnhancePluginDefine {
    /**
     * @return the target class, which needs active.
     */
    @Override
    protected String enhanceClassName() {
        return "com.a.eye.skywalking.toolkit.log.log4j.v2.x.Log4j2OutputAppender";
    }

    /**
     * @return the collection of {@link StaticMethodsInterceptPoint}, represent the intercepted methods and their
     * interceptors.
     */
    @Override
    protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[] {
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("append");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "com.a.eye.skywalking.toolkit.activation.log.log4j.v2.x.PrintTraceIdInterceptor";
                }
            }
        };
    }
}
