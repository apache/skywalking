package org.apache.skywalking.apm.plugin.seata.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

public class TransactionManagerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

  private static final String ENHANCE_CLASS_TM = "io.seata.tm.DefaultTransactionManager";
  private static final String INTERCEPT_CLASS_TM = "org.apache.skywalking.apm.plugin.seata.interceptor.TransactionManagerInterceptor";

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
            return named("syncCall");
          }

          @Override
          public String getMethodsInterceptor() {
            return INTERCEPT_CLASS_TM;
          }

          @Override
          public boolean isOverrideArgs() {
            return true;
          }
        }
    };
  }

  @Override
  protected ClassMatch enhanceClass() {
    return byName(ENHANCE_CLASS_TM);
  }
}
