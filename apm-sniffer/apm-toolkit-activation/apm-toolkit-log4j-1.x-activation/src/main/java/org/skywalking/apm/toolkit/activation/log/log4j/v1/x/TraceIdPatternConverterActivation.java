package org.skywalking.apm.toolkit.activation.log.log4j.v1.x;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.api.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Active the toolkit class "org.skywalking.apm.toolkit.log.log4j.v1.x.TraceIdPatternConverter".
 * Should not dependency or import any class in "skywalking-toolkit-log4j-1.x" module.
 * Activation's classloader is diff from "org.skywalking.apm.toolkit.log.log4j.v1.x.TraceIdPatternConverter",
 * using direct will trigger classloader issue.
 *
 * @author wusheng
 */
public class TraceIdPatternConverterActivation extends ClassInstanceMethodsEnhancePluginDefine {
    /**
     * @return the target class, which needs active.
     */
    @Override
    protected String enhanceClassName() {
        return "org.skywalking.apm.toolkit.log.log4j.v1.x.TraceIdPatternConverter";
    }

    /**
     * @return null, no need to intercept constructor of enhance class.
     */
    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    /**
     * @return the collection of {@link InstanceMethodsInterceptPoint}, represent the intercepted methods and their
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
