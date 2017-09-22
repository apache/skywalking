package org.skywalking.apm.plugin.struts2;

import java.lang.reflect.Method;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

public class Struts2Interceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        HttpServletRequest request = ServletActionContext.getRequest();
        ContextCarrier contextCarrier = new ContextCarrier();

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(request.getHeader(next.getHeadKey()));
        }

        AbstractSpan span = ContextManager.createEntrySpan(request.getRequestURI(), contextCarrier);
        Tags.URL.set(span, request.getRequestURL().toString());
        Tags.HTTP.METHOD.set(span, request.getMethod());
        span.setComponent(ComponentsDefine.STRUTS2);
        SpanLayer.asHttp(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        HttpServletResponse response = ServletActionContext.getResponse();

        AbstractSpan span = ContextManager.activeSpan();
        if (response.getStatus() >= 400) {
            span.errorOccurred();
            Tags.STATUS_CODE.set(span, Integer.toString(response.getStatus()));
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
