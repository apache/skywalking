package com.a.eye.skywalking.toolkit.activation.log.logback.v1.x;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.StaticMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Active the toolkit class "com.a.eye.skywalking.toolkit.log.logback.v1.x.LogbackPatternConverter".
 * Should not dependency or import any class in "skywalking-toolkit-logback-1.x" module.
 * Activation's classloader is diff from "com.a.eye.skywalking.toolkit.log.logback.v1.x.LogbackPatternConverter",
 * using direct will trigger classloader issue.
 * <p>
 * Created by wusheng on 2016/12/7.
 */
public class LogbackPatternConverterActivation extends ClassInstanceMethodsEnhancePluginDefine {
    /**
     * @return the target class, which needs active.
     */
    @Override
    protected String enhanceClassName() {
        return "com.a.eye.skywalking.toolkit.log.logback.v1.x.LogbackPatternConverter";
    }

    /**
     * @return null, no need to intercept constructor of enhance class.
     */
    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    /**
     * @return the collection of {@link StaticMethodsInterceptPoint}, represent the intercepted methods and their
     * interceptors.
     */
    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("convert");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "com.a.eye.skywalking.toolkit.activation.log.logback.v1.x.PrintTraceIdInterceptor";
                }
            }
        };
    }
}
