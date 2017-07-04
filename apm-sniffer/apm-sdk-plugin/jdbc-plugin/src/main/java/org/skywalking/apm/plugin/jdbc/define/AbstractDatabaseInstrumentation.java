package org.skywalking.apm.plugin.jdbc.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.plugin.jdbc.SWConnection;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * JDBC plugin using {@link JDBCDriverInterceptor} to intercept all the class that it has extend {@link
 * java.sql.Driver#connect(String, java.util.Properties)}, and change the return object to {@link
 * SWConnection}, All the method of {@link SWConnection}
 * is delegate to the real JDBC Driver Connection object.
 *
 * @author zhangxin
 */
public abstract class AbstractDatabaseInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String INTERCEPT_CLASS = "org.skywalking.apm.plugin.jdbc.define.JDBCDriverInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("connect");
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPT_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
