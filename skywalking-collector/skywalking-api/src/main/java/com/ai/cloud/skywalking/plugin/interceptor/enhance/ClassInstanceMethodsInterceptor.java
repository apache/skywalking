package com.ai.cloud.skywalking.plugin.interceptor.enhance;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.loader.InterceptorInstanceLoader;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.bind.annotation.*;
import sun.tools.jar.resources.jar;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.concurrent.Callable;

/**
 * 类方法拦截、控制器
 *
 * @author wusheng
 */
public class ClassInstanceMethodsInterceptor {
    private static Logger logger = LogManager.getLogger(ClassInstanceMethodsInterceptor.class);

    private String instanceMethodsAroundInterceptorClassName;

    public ClassInstanceMethodsInterceptor(String instanceMethodsAroundInterceptorClassName) {
        this.instanceMethodsAroundInterceptorClassName = instanceMethodsAroundInterceptorClassName;
    }

    @RuntimeType
    public Object intercept(@This Object obj, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> zuper,
            @FieldValue(ClassEnhancePluginDefine.contextAttrName) EnhancedClassInstanceContext instanceContext) throws Exception {
        InstanceMethodsAroundInterceptor interceptor = InterceptorInstanceLoader.load(instanceMethodsAroundInterceptorClassName, obj.getClass().getClassLoader());

        InstanceMethodInvokeContext interceptorContext = new InstanceMethodInvokeContext(obj, method.getName(), allArguments);
        MethodInterceptResult result = new MethodInterceptResult();
        try {
            interceptor.beforeMethod(instanceContext, interceptorContext, result);
        } catch (Throwable t) {
            logger.error("class[{}] before method[{}] intercept failue:{}", new Object[] {obj.getClass(), method.getName(), t.getMessage()}, t);
        }
        if (!result.isContinue()) {
            return result._ret();
        }

        Object ret = null;
        try {
            ret = zuper.call();
        } catch (Throwable t) {
            try {
                interceptor.handleMethodException(t, instanceContext, interceptorContext, ret);
            } catch (Throwable t2) {
                logger.error("class[{}] handle method[{}] exception failue:{}", new Object[] {obj.getClass(), method.getName(), t2.getMessage()}, t2);
            }
            throw t;
        } finally {
            try {
                ret = interceptor.afterMethod(instanceContext, interceptorContext, ret);
            } catch (Throwable t) {
                logger.error("class[{}] after method[{}] intercept failue:{}", new Object[] {obj.getClass(), method.getName(), t.getMessage()}, t);
            }
        }
        return ret;
    }
}
