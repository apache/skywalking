package org.apache.skywalking.apm.plugin.seata.interceptor;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

public class TmRpcClientInterceptor implements InstanceMethodsAroundInterceptor {

  @Override
  public void beforeMethod(final EnhancedInstance objInst,
                           final Method method,
                           final Object[] allArguments,
                           final Class<?>[] argumentsTypes,
                           final MethodInterceptResult result) throws Throwable {
  }

  @Override
  public Object afterMethod(final EnhancedInstance objInst,
                            final Method method,
                            final Object[] allArguments,
                            final Class<?>[] argumentsTypes,
                            final Object ret) throws Throwable {
    final String remoteAddress = (String) ret;
    objInst.setSkyWalkingDynamicField(remoteAddress);
    return ret;
  }

  @Override
  public void handleMethodException(final EnhancedInstance objInst,
                                    final Method method,
                                    final Object[] allArguments,
                                    final Class<?>[] argumentsTypes,
                                    final Throwable t) {

  }
}
