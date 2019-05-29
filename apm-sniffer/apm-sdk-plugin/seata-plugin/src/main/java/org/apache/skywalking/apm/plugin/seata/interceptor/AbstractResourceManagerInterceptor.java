package org.apache.skywalking.apm.plugin.seata.interceptor;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

public class AbstractResourceManagerInterceptor implements InstanceMethodsAroundInterceptor {
  @Override
  public void beforeMethod(final EnhancedInstance objInst,
                           final Method method,
                           final Object[] allArguments,
                           final Class<?>[] argumentsTypes,
                           final MethodInterceptResult result) throws Throwable {
    final AbstractSpan span = ContextManager.createLocalSpan(
        operationName(method)
    );

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
    return ComponentsDefine.SEATA.getName() + "/RM/" + method.getName();
  }
}
