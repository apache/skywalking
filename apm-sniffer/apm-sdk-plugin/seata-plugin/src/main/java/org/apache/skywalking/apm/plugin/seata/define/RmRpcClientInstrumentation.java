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

public class RmRpcClientInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
  private static final String ENHANCE_CLASS = "io.seata.core.rpc.netty.RmRpcClient";
  private static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.seata.interceptor.RmRpcClientInterceptor";

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
            return named("sendMsgWithResponse").and(takesArguments(1));
          }

          @Override
          public String getMethodsInterceptor() {
            return INTERCEPTOR_CLASS;
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
    return byName(ENHANCE_CLASS);
  }
}
