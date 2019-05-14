package org.apache.skywalking.apm.plugin.seata;

import io.seata.tm.DefaultTransactionManager;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.anyOf;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

public class TransactionManagerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

  private static final String ENHANCE_CLASS = DefaultTransactionManager.class.getName();

  private static final String INTERCEPT_CLASS = TransactionManagerInterceptor.class.getName();

  private static final ElementMatcher<MethodDescription> INTERCEPT_METHODS_MATCHER = anyOf(
      named("begin"),
      named("commit"),
      named("rollback"),
      named("getStatus")
  );

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
            return INTERCEPT_METHODS_MATCHER;
          }

          @Override
          public String getMethodsInterceptor() {
            return INTERCEPT_CLASS;
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
