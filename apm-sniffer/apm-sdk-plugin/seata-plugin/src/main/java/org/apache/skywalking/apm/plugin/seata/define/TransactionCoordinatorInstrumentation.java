package org.apache.skywalking.apm.plugin.seata.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

public class TransactionCoordinatorInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

  private static final String ENHANCE_CLASS_TC = "io.seata.server.coordinator.DefaultCoordinator";
  private static final String INTERCEPT_CLASS_TC = "org.apache.skywalking.apm.plugin.seata.interceptor.TransactionCoordinatorInterceptor";

  @Override
  protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
    return new ConstructorInterceptPoint[0];
  }

  @Override
  protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
    return new InstanceMethodsInterceptPoint[] {
        new InstanceMethodsInterceptPoint() {
          @Override
          public ElementMatcher<MethodDescription> getMethodsMatcher() {
            return named("doGlobalBegin")
                .or(named("doGlobalCommit"))
                .or(named("doGlobalRollback"))
                .or(named("doGlobalStatus"))
                ;
          }

          @Override
          public String getMethodsInterceptor() {
            return INTERCEPT_CLASS_TC;
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
    return byName(ENHANCE_CLASS_TC);
  }
}
