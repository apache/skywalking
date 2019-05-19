package org.apache.skywalking.apm.plugin.seata.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

public class AbstractMessageInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

  private static final String ENHANCE_CLASS = "io.seata.core.protocol.AbstractMessage";
  private static final String INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.seata.interceptor.AbstractMessageInterceptor";

  @Override
  protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
    return new ConstructorInterceptPoint[0];
  }

  @Override
  protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
    return new StaticMethodsInterceptPoint[] {
        new StaticMethodsInterceptPoint() {
          @Override
          public ElementMatcher<MethodDescription> getMethodsMatcher() {
            return named("getMergeRequestInstanceByCode").and(takesArguments(1));
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
  protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
    return new InstanceMethodsInterceptPoint[0];
  }

  @Override
  protected ClassMatch enhanceClass() {
    return byName(ENHANCE_CLASS);
  }
}
