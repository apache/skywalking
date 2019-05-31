package org.apache.skywalking.apm.plugin.seata.interceptor;

import io.seata.server.session.SessionCondition;
import io.seata.server.store.TransactionStoreManager;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.seata.Constants.*;

public class TransactionStoreManagerInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
        final String methodName = method.getName();

        final AbstractSpan span = ContextManager.createLocalSpan(
            operationName(method)
        );

        if ("writeSession".equals(methodName)) {
            final TransactionStoreManager.LogOperation logOperation = (TransactionStoreManager.LogOperation) allArguments[0];
            span.tag(LOG_OPERATION, logOperation.name());
        } else if ("readSession".equals(methodName)) {
            final Object argument0 = allArguments[0];
            if (argument0 instanceof String) {
                span.tag(XID, (String) argument0);
            } else if (argument0 instanceof SessionCondition) {
                final SessionCondition sessionCondition = (SessionCondition) argument0;
                span.tag(XID, sessionCondition.getXid());
                span.tag(TRANSACTION_ID, String.valueOf(sessionCondition.getTransactionId()));
            }
        }

        span.setComponent(ComponentsDefine.SEATA);
        SpanLayer.asDB(span);
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
        return ComponentsDefine.SEATA.getName() + "/TransactionStoreManager/" + method.getName();
    }
}
