package org.skywalking.apm.plugin.sjdbc.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.skywalking.apm.plugin.sjdbc.ExecuteEventListener;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link ExecutorInstrumentation} presents that skywalking intercepts {@link com.dangdang.ddframe.rdb.sharding.executor.ExecutorEngine}.
 * 
 * @author gaohongtao
 */
public class ExecutorInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
    
    private static final String ENHANCE_CLASS = "com.dangdang.ddframe.rdb.sharding.executor.ExecutorEngine";

    private static final String EXECUTE_INTERCEPTOR_CLASS = "org.skywalking.apm.plugin.sjdbc.define.ExecuteInterceptor";
    
    private static final String ASYNC_EXECUTE_INTERCEPTOR_CLASS = "org.skywalking.apm.plugin.sjdbc.define.AsyncExecuteInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }
    
    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("execute");
                }

                @Override
                public String getMethodsInterceptor() {
                    return EXECUTE_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("asyncExecute");
                }

                @Override
                public String getMethodsInterceptor() {
                    return ASYNC_EXECUTE_INTERCEPTOR_CLASS;
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
        ExecuteEventListener.init();
        return byName(ENHANCE_CLASS);
    }
}
