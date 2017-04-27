package org.skywalking.apm.toolkit.activation.log.logback.v1.x;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.api.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.StaticMethodsInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Active the toolkit class "org.skywalking.apm.toolkit.log.logback.v1.x.LogbackPatternConverter".
 * Should not dependency or import any class in "skywalking-toolkit-logback-1.x" module.
 * Activation's classloader is diff from "org.skywalking.apm.toolkit.log.logback.v1.x.LogbackPatternConverter",
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
        return "org.skywalking.apm.toolkit.log.logback.v1.x.LogbackPatternConverter";
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
                    return "PrintTraceIdInterceptor";
                }
            }
        };
    }
}
