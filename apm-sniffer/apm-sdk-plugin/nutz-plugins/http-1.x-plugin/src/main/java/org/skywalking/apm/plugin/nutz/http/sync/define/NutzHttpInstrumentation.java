package org.skywalking.apm.plugin.nutz.http.sync.define;

import static net.bytebuddy.matcher.ElementMatchers.named;

import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.skywalking.apm.agent.core.plugin.match.HierarchyMatch;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * {@link NutzHttpInstrumentation} enhance the <code>send</code>
 * method,<code>Constructor</code> of <code>org.nutz.http.Sender</code> by
 * <code>org.skywalking.apm.plugin.nutz.http.sync.SenderConstructorInterceptor</code>, and
 * <code>org.skywalking.apm.plugin.nutz.http.sync.SenderSendInterceptor</code>
 * set context to header for propagate trace context around execute
 * <code>send</code>.
 *
 * @author wendal
 */
public class NutzHttpInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.nutz.http.Sender";
    private static final String DO_SEND_METHOD_NAME = "send";
    private static final String DO_SEND_INTERCEPTOR = "org.skywalking.apm.plugin.nutz.http.sync.SenderSendInterceptor";
    private static final String DO_CONSTRUCTOR_INTERCEPTOR = "org.skywalking.apm.plugin.nutz.http.sync.SenderConstructorInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[]{
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return new ElementMatcher<MethodDescription>() {
                        @Override
                        public boolean matches(MethodDescription target) {
                            return target.isConstructor() && target.getParameters().size() > 0;
                        }
                    };
                }
                
                @Override
                public String getConstructorInterceptor() {
                    return DO_CONSTRUCTOR_INTERCEPTOR;
                }
            }
        };
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(DO_SEND_METHOD_NAME);
                }
                
                @Override
                public String getMethodsInterceptor() {
                    return DO_SEND_INTERCEPTOR;
                }
                
                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return HierarchyMatch.byHierarchyMatch(new String[]{ENHANCE_CLASS});
    }
}
