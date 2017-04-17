package com.a.eye.skywalking.plugin.mongodb.define;

import static net.bytebuddy.matcher.ElementMatchers.named;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

/**
 * {@link MongoDBInstrumentation} presents that skywalking intercepts {@link com.mongodb.Mongo#execute(ReadOperation, ReadPreference)},{@link com.mongodb.Mongo#execute(WriteOperation)}
 * by using {@link MongoDBMethodInterceptor}.
 *
 * @author baiyang
 */
public class MongoDBInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "com.mongodb.Mongo";

    private static final String MONGDB_READ_BINDING_CLASS = "com.a.eye.skywalking.plugin.mongodb.MongoDBReadBindingInterceptor";

    private static final String MONGDB_WRITE_BINDING_CLASS = "com.a.eye.skywalking.plugin.mongodb.MongoDBWriteBindingInterceptor";

    private static final String MONGDB_METHOD_INTERCET_CLASS = "com.a.eye.skywalking.plugin.mongodb.MongoDBMethodInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] { new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("execute");
            }

            @Override
            public String getMethodsInterceptor() {
                return MONGDB_METHOD_INTERCET_CLASS;
            }
        }, new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("getReadBinding");
            }

            @Override
            public String getMethodsInterceptor() {
                return MONGDB_READ_BINDING_CLASS;
            }
        }, new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("getWriteBinding");
            }

            @Override
            public String getMethodsInterceptor() {
                return MONGDB_WRITE_BINDING_CLASS;
            }
        } };
    }

    @Override
    protected String enhanceClassName() {
        return ENHANCE_CLASS;
    }

}
