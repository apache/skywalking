package com.a.eye.skywalking.plugin.jdbc.define;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * JDBC plugin using {@link JDBCDriverInterceptor} to intercept all the class that it has extend {@link
 * java.sql.Driver#connect(String, java.util.Properties)}, and change the return object to {@link
 * com.a.eye.skywalking.plugin.jdbc.SWConnection}, All the method of {@link com.a.eye.skywalking.plugin.jdbc.SWConnection}
 * is delegate to the real JDBC Driver Connection object.
 *
 * @author zhangxin
 */
public abstract class AbstractDatabaseInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String INTERCEPT_CLASS = "com.a.eye.skywalking.plugin.jdbc.define.JDBCDriverInterceptor";

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
            }
        };
    }
}
