package com.a.eye.skywalking.toolkit.activation.log.log4j.v1.x;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.StaticMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Active the toolkit class "com.a.eye.skywalking.toolkit.log.log4j.v1.x.TraceIdPatternConverter".
 * Should not dependency or import any class in "skywalking-toolkit-log4j-1.x" module.
 * Activation's classloader is diff from "com.a.eye.skywalking.toolkit.log.log4j.v1.x.TraceIdPatternConverter",
 * using direct will trigger classloader issue.
 *
 * Created by wusheng on 2016/12/7.
 */
public class TraceIdPatternConverterActivation extends ClassInstanceMethodsEnhancePluginDefine {
    /**
     * @return the target class, which needs active.
     */
    @Override
    protected String enhanceClassName() {
        return "com.a.eye.skywalking.toolkit.log.log4j.v1.x.TraceIdPatternConverter";
    }

    /**
     * @return null, no need to intercept constructor of enhance class.
     */
    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    /**
     * @return the collection of {@link InstanceMethodsInterceptPoint}, represent the intercepted methods and their interceptors.
     */
    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("convert");
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.toolkit.activation.log.log4j.v1.x.PrintTraceIdInterceptor";
            }
        }};
    }
}
