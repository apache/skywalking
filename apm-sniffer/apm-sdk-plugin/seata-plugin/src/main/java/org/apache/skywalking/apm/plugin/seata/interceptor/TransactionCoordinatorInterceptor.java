package org.apache.skywalking.apm.plugin.seata.interceptor;

import io.seata.core.protocol.transaction.GlobalBeginRequest;
import io.seata.core.protocol.transaction.GlobalCommitRequest;
import io.seata.core.protocol.transaction.GlobalRollbackRequest;
import io.seata.core.protocol.transaction.GlobalStatusRequest;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalBeginRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalCommitRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalGetStatusRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalRollbackRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedRequest;

import java.lang.reflect.Method;

public class TransactionCoordinatorInterceptor implements InstanceMethodsAroundInterceptor {
  @Override
  public void beforeMethod(final EnhancedInstance objInst,
                           final Method method,
                           final Object[] allArguments,
                           final Class<?>[] argumentsTypes,
                           final MethodInterceptResult result) throws Throwable {
    final Object request = allArguments[0];
    final ContextCarrier contextCarrier = new ContextCarrier();

    EnhancedRequest enhancedRequest = null;
    String methodName = null;

    if (request instanceof GlobalBeginRequest) {
      enhancedRequest = (EnhancedGlobalBeginRequest) request;

      methodName = "begin";
    } else if (request instanceof GlobalCommitRequest) {
      enhancedRequest = (EnhancedGlobalCommitRequest) request;

      methodName = "commit";
    } else if (request instanceof GlobalRollbackRequest) {
      enhancedRequest = (EnhancedGlobalRollbackRequest) request;

      methodName = "rollback";
    } else if (request instanceof GlobalStatusRequest) {
      enhancedRequest = (EnhancedGlobalGetStatusRequest) request;

      methodName = "getStatus";
    }

    if (enhancedRequest != null) {
      CarrierItem next = contextCarrier.items();
      while (next.hasNext()) {
        next = next.next();
        next.setHeadValue(enhancedRequest.get(next.getHeadKey()));
      }
    }

    if (methodName != null) {
      final AbstractSpan span = ContextManager.createEntrySpan(
          ComponentsDefine.SEATA.getName() + "/TC/" + methodName,
          contextCarrier
      );
      span.setComponent(ComponentsDefine.SEATA);
      SpanLayer.asDB(span);
    }
  }

  @Override
  public Object afterMethod(final EnhancedInstance objInst,
                            final Method method,
                            final Object[] allArguments,
                            final Class<?>[] argumentsTypes,
                            final Object ret) throws Throwable {
    if (ContextManager.activeSpan() != null) {
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
    AbstractSpan span = ContextManager.activeSpan();
    if (span != null) {
      span.errorOccurred();
      span.log(t);
    }
  }
}
