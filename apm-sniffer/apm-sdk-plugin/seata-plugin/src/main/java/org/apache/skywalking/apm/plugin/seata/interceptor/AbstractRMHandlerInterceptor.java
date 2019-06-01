package org.apache.skywalking.apm.plugin.seata.interceptor;

import io.seata.core.protocol.transaction.AbstractBranchEndRequest;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedRequest;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.seata.Constants.RESOURCE_ID;
import static org.apache.skywalking.apm.plugin.seata.Constants.XID;

public class AbstractRMHandlerInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
        final ContextCarrier contextCarrier = new ContextCarrier();

        final EnhancedRequest enhancedRequest = (EnhancedRequest) allArguments[0];
        final String xid = ((AbstractBranchEndRequest) allArguments[0]).getXid();
        final String resourceId = ((AbstractBranchEndRequest) allArguments[0]).getResourceId();

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(enhancedRequest.get(next.getHeadKey()));
        }

        final AbstractSpan span = ContextManager.createEntrySpan(
            operationName(method),
            contextCarrier
        );

        span.tag(XID, xid);
        span.tag(RESOURCE_ID, resourceId);
        span.setComponent(ComponentsDefine.SEATA);
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst,
                              final Method method,
                              final Object[] allArguments,
                              final Class<?>[] argumentsTypes,
                              final Object ret) throws Throwable {
        if (ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst,
                                      final Method method,
                                      final Object[] allArguments,
                                      final Class<?>[] argumentsTypes,
                                      final Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().errorOccurred().log(t);
        }
    }

    private String operationName(final Method method) {
        return ComponentsDefine.SEATA.getName() + "/RM/" + method.getName();
    }
}
