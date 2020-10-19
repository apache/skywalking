package org.apache.skywalking.apm.plugin.asyncClient.v1.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

public class NettyRequestSenderInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.asynchttpclient.netty.request.NettyRequestSender";

    private static final String NETTY_REQUEST_SENDER_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.asyncClient.v1.NettyRequestSenderInterceptor";

    private static final String PREPARE_REQUEST_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.asyncClient.v1.PrepareRequestInterceptor";

    private static final String NETTY_REQUEST_METHOD = "writeRequest";

    private static final String PREPARE_REQUEST_METHOD = "sendRequest";


    @Override
    protected ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named(NETTY_REQUEST_METHOD);
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return NETTY_REQUEST_SENDER_INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
                ,
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named(PREPARE_REQUEST_METHOD);
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return PREPARE_REQUEST_INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };
    }
}
