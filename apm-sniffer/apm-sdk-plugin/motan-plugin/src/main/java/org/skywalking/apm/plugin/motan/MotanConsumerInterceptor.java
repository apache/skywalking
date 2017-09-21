package org.skywalking.apm.plugin.motan;

import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;
import java.lang.reflect.Method;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link MotanProviderInterceptor} create span by fetch request url from
 * {@link EnhancedInstance#getSkyWalkingDynamicField()} and transport serialized context
 * data to provider side through {@link Request#setAttachment(String, String)}.
 *
 * @author zhangxin
 */
public class MotanConsumerInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(allArguments[1]);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        URL url = (URL)objInst.getSkyWalkingDynamicField();
        Request request = (Request)allArguments[0];
        if (url != null) {
            ContextCarrier contextCarrier = new ContextCarrier();
            String remotePeer = url.getHost() + ":" + url.getPort();
            AbstractSpan span = ContextManager.createExitSpan(generateOperationName(url, request), contextCarrier, remotePeer);
            span.setComponent(ComponentsDefine.MOTAN);
            Tags.URL.set(span, url.getIdentity());
            SpanLayer.asRPCFramework(span);
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                request.setAttachment(next.getHeadKey(), next.getHeadValue());
            }
        }
    }

    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Response response = (Response)ret;
        if (response != null && response.getException() != null) {
            AbstractSpan span = ContextManager.activeSpan();
            span.errorOccurred();
            span.log(response.getException());
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        span.errorOccurred();
        span.log(t);
    }

    /**
     * Generate operation name.
     *
     * @return operation name.
     */
    private static String generateOperationName(URL serviceURI, Request request) {
        return new StringBuilder(serviceURI.getPath()).append(".").append(request.getMethodName()).append("(")
            .append(request.getParamtersDesc()).append(")").toString();
    }
}
