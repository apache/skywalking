package org.apache.skywalking.apm.plugin.seata;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

public class TransactionManagerInterceptor implements InstanceMethodsAroundInterceptor {
  @Override
  public void beforeMethod(final EnhancedInstance objInst,
                           final Method method,
                           final Object[] allArguments,
                           final Class<?>[] argumentsTypes,
                           final MethodInterceptResult result) throws Throwable {
    final String applicationId = (String) allArguments[0];
    final String transactionServiceGroup = (String) allArguments[1];
    final String name = (String) allArguments[2];
    final Object timeout = allArguments[3];
    final String operationName = generateOperationName(applicationId, transactionServiceGroup, name, timeout);
    final ContextCarrier contextCarrier = new ContextCarrier();
    final AbstractSpan span = ContextManager.createEntrySpan(operationName, contextCarrier);

    span.setComponent(ComponentsDefine.SEATA);
    SpanLayer.asDB(span);
  }

  @Override
  public Object afterMethod(final EnhancedInstance objInst,
                            final Method method,
                            final Object[] allArguments,
                            final Class<?>[] argumentsTypes,
                            final Object ret) throws Throwable {
    ContextManager.stopSpan();
    return ret;
  }

  @Override
  public void handleMethodException(final EnhancedInstance objInst,
                                    final Method method,
                                    final Object[] allArguments,
                                    final Class<?>[] argumentsTypes,
                                    final Throwable t) {
    AbstractSpan span = ContextManager.activeSpan();
    span.errorOccurred();
    span.log(t);
  }

  @SuppressWarnings("unused")
  private String generateOperationName(final String applicationId,
                                       final String transactionServiceGroup,
                                       final String name,
                                       final Object timeout) {
    return applicationId + ":" + transactionServiceGroup + ":" + name;
  }
}
