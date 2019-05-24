
package cxf;

import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.description.OperationDesc;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;

import javax.xml.soap.MimeHeaders;
import java.lang.reflect.Method;

public class CxfInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {

        MessageContext msgContext = (MessageContext) allArguments[0];
        Message rm = msgContext.getRequestMessage();
        MimeHeaders mimeHeaders = rm.getMimeHeaders();
        OperationDesc od = msgContext.getOperation();

        AbstractSpan span;
        final ContextCarrier contextCarrier = new ContextCarrier();
        span = ContextManager.createExitSpan(od.getName(), contextCarrier,
                msgContext.getProperty("transport.url").toString());
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            mimeHeaders.addHeader(next.getHeadKey(), next.getHeadValue());
        }
        Tags.URL.set(span, od.getName());
        SpanLayer.asHttp(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        Object result = ret;
        ContextManager.stopSpan();
        return result;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        dealException(t);
    }


    private void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        span.errorOccurred();
        span.log(throwable);
    }


}
