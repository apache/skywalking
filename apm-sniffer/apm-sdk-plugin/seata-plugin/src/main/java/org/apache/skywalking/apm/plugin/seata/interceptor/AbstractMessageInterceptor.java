package org.apache.skywalking.apm.plugin.seata.interceptor;

import io.seata.core.protocol.transaction.GlobalBeginRequest;
import io.seata.core.protocol.transaction.GlobalCommitRequest;
import io.seata.core.protocol.transaction.GlobalRollbackRequest;
import io.seata.core.protocol.transaction.GlobalStatusRequest;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalBeginRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalCommitRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalGetStatusRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalRollbackRequest;

import java.lang.reflect.Method;

public class AbstractMessageInterceptor implements StaticMethodsAroundInterceptor {

  @Override
  public void beforeMethod(final Class clazz,
                           final Method method,
                           final Object[] allArguments,
                           final Class<?>[] parameterTypes,
                           final MethodInterceptResult result) {

  }

  @Override
  public Object afterMethod(final Class clazz,
                            final Method method,
                            final Object[] allArguments,
                            final Class<?>[] parameterTypes,
                            final Object ret) {
    if (ret instanceof GlobalBeginRequest) {
      return new EnhancedGlobalBeginRequest((GlobalBeginRequest) ret);
    }
    if (ret instanceof GlobalCommitRequest) {
      return new EnhancedGlobalCommitRequest((GlobalCommitRequest) ret);
    }
    if (ret instanceof GlobalRollbackRequest) {
      return new EnhancedGlobalRollbackRequest((GlobalRollbackRequest) ret);
    }
    if (ret instanceof GlobalStatusRequest) {
      return new EnhancedGlobalGetStatusRequest((GlobalStatusRequest) ret);
    }
    return ret;
  }

  @Override
  public void handleMethodException(final Class clazz,
                                    final Method method,
                                    final Object[] allArguments,
                                    final Class<?>[] parameterTypes,
                                    final Throwable t) {

  }
}
