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
 * {@link NutzHttpInstrumentation} enhance the <code>doExecute</code> method,<code>handleResponse</code> method and
 * <code>handleResponse</code> method of <code>org.springframework.web.client.RestTemplate</code> by
 * <code>org.skywalking.apm.plugin.spring.resttemplate.sync.RestExecuteInterceptor</code>,
 * <code>org.skywalking.apm.plugin.spring.resttemplate.sync.RestResponseInterceptor</code> and
 * <code>org.skywalking.apm.plugin.spring.resttemplate.sync.RestRequestInterceptor</code>.
 *
 * <code>org.skywalking.apm.plugin.spring.resttemplate.sync.RestResponseInterceptor</code> set context to  header for
 * propagate trace context after execute <code>createRequest</code>.
 *
 * @author wendal
 */
public class NutzHttpInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.nutz.http.Sender";
    private static final String DO_EXECUTE_METHOD_NAME = "send";
    private static final String DO_EXECUTE_INTERCEPTOR = "org.skywalking.apm.plugin.nutz.http.sync.SenderSendInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(DO_EXECUTE_METHOD_NAME);
                }

                @Override public String getMethodsInterceptor() {
                    return DO_EXECUTE_INTERCEPTOR;
                }

                @Override public boolean isOverrideArgs() {
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
