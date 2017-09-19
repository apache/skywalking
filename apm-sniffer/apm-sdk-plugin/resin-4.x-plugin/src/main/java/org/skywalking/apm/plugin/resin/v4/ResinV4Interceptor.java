package org.skywalking.apm.plugin.resin.v4;

import com.caucho.server.http.CauchoRequest;
import java.lang.reflect.Method;
import javax.servlet.http.HttpServletResponse;
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

/**
 * Created by Baiyang on 2017/5/2.
 */
public class ResinV4Interceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        CauchoRequest request = (CauchoRequest)allArguments[0];
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(request.getHeader(next.getHeadKey()));
        }
        AbstractSpan span = ContextManager.createEntrySpan(request.getPageURI(), contextCarrier);
        span.setComponent(ComponentsDefine.RESIN);
        Tags.URL.set(span, appendRequestURL(request));
        SpanLayer.asHttp(span);

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        HttpServletResponse response = (HttpServletResponse)allArguments[1];
        AbstractSpan span = ContextManager.activeSpan();

        if (response.getStatus() >= 400) {
            Tags.STATUS_CODE.set(span, Integer.toString(response.getStatus()));
            span.errorOccurred();
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.log(t);
        activeSpan.errorOccurred();
    }

    /**
     * Append request URL.
     *
     * @param request
     * @return
     */
    private String appendRequestURL(CauchoRequest request) {
        StringBuffer sb = new StringBuffer();
        sb.append(request.getScheme());
        sb.append("://");
        sb.append(request.getServerName());
        sb.append(":");
        sb.append(request.getServerPort());
        sb.append(request.getPageURI());
        return sb.toString();
    }
}
