package org.apache.skywalking.apm.plugin.seata.define;

import io.seata.core.protocol.transaction.BranchCommitRequest;
import io.seata.core.protocol.transaction.BranchRollbackRequest;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

public class AbstractRMHandlerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
  private static final String ENHANCE_CLASS = "io.seata.rm.AbstractRMHandler";
  private static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.seata.interceptor.AbstractRMHandlerInterceptor";

  @Override
  protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
    return new ConstructorInterceptPoint[0];
  }

  @Override
  protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
    return new InstanceMethodsInterceptPoint[]{
        new InstanceMethodsInterceptPoint() {
          @Override
          public ElementMatcher<MethodDescription> getMethodsMatcher() {
            return (named("handle").and(takesArguments(BranchCommitRequest.class))
                .or(named("handle").and(takesArguments(BranchRollbackRequest.class))));
          }

          @Override
          public String getMethodsInterceptor() {
            return INTERCEPTOR_CLASS;
          }

          @Override
          public boolean isOverrideArgs() {
            return false;
          }
        }
    };
  }

  @Override
  protected ClassMatch enhanceClass() {
    return byName(ENHANCE_CLASS);
  }
}
