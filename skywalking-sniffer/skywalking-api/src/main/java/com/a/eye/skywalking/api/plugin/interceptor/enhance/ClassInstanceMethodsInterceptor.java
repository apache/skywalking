package com.a.eye.skywalking.api.plugin.interceptor.enhance;

import com.a.eye.skywalking.api.logging.api.ILog;
import com.a.eye.skywalking.api.logging.api.LogManager;
import com.a.eye.skywalking.api.plugin.interceptor.loader.InterceptorInstanceLoader;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * The actual byte-buddy's interceptor to intercept class instance methods.
 * In this class, it provide a bridge between byte-buddy and sky-walking plugin.
 *
 * @author wusheng
 */
public class ClassInstanceMethodsInterceptor {
    private static ILog logger = LogManager.getLogger(ClassInstanceMethodsInterceptor.class);

    /**
     * A class full name, and instanceof {@link InstanceMethodsAroundInterceptor}
     * This name should only stay in {@link String}, the real {@link Class} type will trigger classloader failure.
     * If you want to know more, please check on books about Classloader or Classloader appointment mechanism.
     */
    private String instanceMethodsAroundInterceptorClassName;

    /**
     * Set the name of {@link ClassInstanceMethodsInterceptor#instanceMethodsAroundInterceptorClassName}
     * @param instanceMethodsAroundInterceptorClassName class full name.
     */
    public ClassInstanceMethodsInterceptor(String instanceMethodsAroundInterceptorClassName) {
        this.instanceMethodsAroundInterceptorClassName = instanceMethodsAroundInterceptorClassName;
    }

    /**
     * Intercept the target instance method.
     * @param obj target class instance.
     * @param allArguments all method arguments
     * @param method method description.
     * @param zuper the origin call ref.
     * @param instanceContext the added field of enhanced class.
     * @return the return value of target instance method.
     * @throws Exception only throw exception because of zuper.call()
     *          or unexpected exception in sky-walking ( This is a bug, if anything triggers this condition ).
     */
    @RuntimeType
    public Object intercept(@This Object obj, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> zuper,
            @FieldValue(ClassEnhancePluginDefine.contextAttrName) EnhancedClassInstanceContext instanceContext) throws Exception {
        InstanceMethodsAroundInterceptor interceptor = InterceptorInstanceLoader
                .load(instanceMethodsAroundInterceptorClassName, obj.getClass().getClassLoader());

        InstanceMethodInvokeContext interceptorContext = new InstanceMethodInvokeContext(obj, method.getName(), allArguments, method.getParameterTypes());
        MethodInterceptResult result = new MethodInterceptResult();
        try {
            interceptor.beforeMethod(instanceContext, interceptorContext, result);
        } catch (Throwable t) {
            logger.error("class[{}] before method[{}] intercept failue:{}", new Object[] {obj.getClass(), method.getName(), t.getMessage()}, t);
        }

        Object ret = null;
        try {
            if (!result.isContinue()) {
                ret = result._ret();
            }else {
                ret = zuper.call();
            }
        } catch (Throwable t) {
            try {
                interceptor.handleMethodException(t, instanceContext, interceptorContext);
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
