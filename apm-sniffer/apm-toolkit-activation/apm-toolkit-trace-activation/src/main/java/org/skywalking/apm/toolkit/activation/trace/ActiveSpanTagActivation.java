package org.skywalking.apm.toolkit.activation.trace;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link TraceAnnotationActivation} enhance the <code>tag</code> method of <code>org.skywalking.apm.toolkit.trace.ActiveSpan</code>
 * by <code>org.skywalking.apm.toolkit.activation.trace.ActiveSpanTagInterceptor</code>.
 *
 * @author zhangxin
 */
public class ActiveSpanTagActivation extends ClassStaticMethodsEnhancePluginDefine {

    public static final String ENHANCE_CLASS = "org.skywalking.apm.toolkit.trace.ActiveSpan";
    public static final String INTERCEPTOR_CLASS = "org.skywalking.apm.toolkit.activation.trace.ActiveSpanTagInterceptor";
    public static final String INTERCEPTOR_METHOD_NAME = "tag";

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[] {
            new StaticMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(INTERCEPTOR_METHOD_NAME);
                }

                @Override public String getMethodsInterceptor() {
                    return INTERCEPTOR_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override protected ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }
}
