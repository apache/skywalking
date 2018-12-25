package org.apache.skywalking.apm.plugin.reactor.netty.http.client.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.*;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * @author jian.tan
 */
public class HttpClientOperationsInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private final static String ENHANCE_CLASS = "reactor.ipc.netty.http.client.HttpClientOperations";

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return any();
                }

                @Override public String getConstructorInterceptor() {
                    return "org.apache.skywalking.apm.plugin.reactor.netty.http.client.HttpClientOperationsInterceptor";
                }
            }
        };
    }

    @Override protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("status");
                }

                @Override public String getMethodsInterceptor() {
                    return "org.apache.skywalking.apm.plugin.reactor.netty.http.client.ResponseStatusInterceptor";
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("onInboundNext");
                }

                @Override public String getMethodsInterceptor() {
                    return "org.apache.skywalking.apm.plugin.reactor.netty.http.client.OnInboundNextInterceptor";
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("onOutboundComplete");
                }

                @Override public String getMethodsInterceptor() {
                    return "org.apache.skywalking.apm.plugin.reactor.netty.http.client.OnOutboundCompleteInterceptor";
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("onOutboundError");
                }

                @Override public String getMethodsInterceptor() {
                    return "org.apache.skywalking.apm.plugin.reactor.netty.http.client.OnOutboundErrorInterceptor";
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
