package org.skywalking.apm.plugin.mongodb.v3.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.plugin.mongodb.v3.MongoDBMethodInterceptor;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link MongoDBInstrumentation} presents that skywalking intercepts {@link com.mongodb.Mongo#execute(com.mongodb.operation.ReadOperation,
 * com.mongodb.ReadPreference)},{@link com.mongodb.Mongo#execute(com.mongodb.operation.WriteOperation)} by using {@link MongoDBMethodInterceptor}.
 *
 * @author baiyang
 */
public class MongoDBInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "com.mongodb.Mongo";

    private static final String MONGDB_READ_BINDING_CLASS = "org.skywalking.apm.plugin.mongodb.v3.MongoDBReadBindingInterceptor";

    private static final String MONGDB_WRITE_BINDING_CLASS = "org.skywalking.apm.plugin.mongodb.v3.MongoDBWriteBindingInterceptor";

    private static final String MONGDB_METHOD_INTERCET_CLASS = "org.skywalking.apm.plugin.mongodb.v3.MongoDBMethodInterceptor";

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
                }
            };
    }

    @Override
    protected String enhanceClassName() {
        return ENHANCE_CLASS;
    }

}
