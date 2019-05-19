package org.apache.skywalking.apm.plugin.seata.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

public class TmRpcClientInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

  private static final String ENHANCE_CLASS = "io.seata.core.rpc.netty.TmRpcClient";
  private static final String INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.seata.interceptor.TmRpcClientInterceptor";
  private static final String INTERCEPT_CTOR_CLASS = "org.apache.skywalking.apm.plugin.seata.interceptor.TmRpcClientCtorInterceptor";

  @Override
  protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
    return new ConstructorInterceptPoint[]{
        new ConstructorInterceptPoint() {
          @Override
          public ElementMatcher<MethodDescription> getConstructorMatcher() {
            return takesArguments(0);
          }

          @Override
          public String getConstructorInterceptor() {
            return INTERCEPT_CTOR_CLASS;
          }
        }
    };
  }

  @Override
  protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
    return new InstanceMethodsInterceptPoint[]{
        new InstanceMethodsInterceptPoint() {
          @Override
          public ElementMatcher<MethodDescription> getMethodsMatcher() {
            return named("loadBalance");
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
