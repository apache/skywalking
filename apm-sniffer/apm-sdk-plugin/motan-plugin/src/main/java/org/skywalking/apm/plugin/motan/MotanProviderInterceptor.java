package org.skywalking.apm.plugin.motan;

import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import java.lang.reflect.Method;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * Current trace segment will ref the trace segment if the serialized trace context that fetch from {@link
 * Request#getAttachments()} is not null.
 * <p>
 * {@link MotanConsumerInterceptor} intercept all constructor of {@link com.weibo.api.motan.rpc.AbstractProvider} for
 * record the request url from consumer side.
 *
 * @author zhangxin
 */
public class MotanProviderInterceptor implements InstanceMethodsAroundInterceptor {

    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        Request request = (Request)allArguments[0];
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(request.getAttachments().get(next.getHeadKey()));
        }

        AbstractSpan span = ContextManager.createEntrySpan(generateViewPoint(request), contextCarrier);
        SpanLayer.asRPCFramework(span);
        span.setComponent(ComponentsDefine.MOTAN);
    }

    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Response response = (Response)ret;
        if (response != null && response.getException() != null) {
            AbstractSpan span = ContextManager.activeSpan();
            span.log(response.getException());
            span.errorOccurred();
        }

        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.errorOccurred();
        activeSpan.log(t);
    }

    private static String generateViewPoint(Request request) {
        StringBuilder viewPoint = new StringBuilder(request.getInterfaceName());
        viewPoint.append("." + request.getMethodName());
        viewPoint.append("(" + request.getParamtersDesc() + ")");
        return viewPoint.toString();
    }
}
