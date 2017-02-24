package com.a.eye.skywalking.plugin.jdbc.define;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.jdbc.SWConnection;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Properties;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * JDBC plugin using {@link JDBCDriverInterceptor} to intercept all the class that extend {@link java.sql.Driver#connect(String, Properties)},
 * and change the return object to {@link com.a.eye.skywalking.plugin.jdbc.SWConnection}, All the method of {@link com.a.eye.skywalking.plugin.jdbc.SWConnection}
 * is delete to the real JDBC Driver Connection object.
 * It will return {@link com.a.eye.skywalking.plugin.jdbc.SWStatement} when {@link java.sql.Driver} to create {@link java.sql.Statement}, return
 * {@link com.a.eye.skywalking.plugin.jdbc.SWPreparedStatement} when {@link java.sql.Driver} to create {@link java.sql.PreparedStatement} and return
 * {@link com.a.eye.skywalking.plugin.jdbc.SWCallableStatement} when {@link java.sql.Driver} to create {@link java.sql.CallableStatement}.
 * of course, {@link com.a.eye.skywalking.plugin.jdbc.SWStatement}, {@link com.a.eye.skywalking.plugin.jdbc.SWPreparedStatement} and
 * {@link com.a.eye.skywalking.plugin.jdbc.SWCallableStatement} are same as {@link SWConnection}.
 *
 * @author zhangxin
 */
public abstract class AbstractDatabaseInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    /**
     * Intercept class
     */
    private static final String INTERCEPT_CLASS = "com.a.eye.skywalking.plugin.jdbc.define.JDBCDriverInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("connect");
            }

            @Override
            public String getMethodsInterceptor() {
                return INTERCEPT_CLASS;
            }
        }};
    }
}
