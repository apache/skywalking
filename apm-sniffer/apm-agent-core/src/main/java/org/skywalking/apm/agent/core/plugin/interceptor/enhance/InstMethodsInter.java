package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

import net.bytebuddy.implementation.bind.annotation.*;
import org.skywalking.apm.agent.core.plugin.interceptor.loader.InterceptorInstanceLoader;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * The actual byte-buddy's interceptor to intercept class instance methods.
 * In this class, it provide a bridge between byte-buddy and sky-walking plugin.
 *
 * @author wusheng
 */
public class InstMethodsInter {
    private static final ILog logger = LogManager.getLogger(InstMethodsInter.class);

    /**
     * A class full name, and instanceof {@link InstanceMethodsAroundInterceptor}
     * This name should only stay in {@link String}, the real {@link Class} type will trigger classloader failure.
     * If you want to know more, please check on books about Classloader or Classloader appointment mechanism.
     */
    private String instanceMethodsAroundInterceptorClassName;

    /**
     * Set the name of {@link InstMethodsInter#instanceMethodsAroundInterceptorClassName}
     *
     * @param instanceMethodsAroundInterceptorClassName class full name.
     */
    public InstMethodsInter(String instanceMethodsAroundInterceptorClassName) {
        this.instanceMethodsAroundInterceptorClassName = instanceMethodsAroundInterceptorClassName;
    }

    /**
     * Intercept the target instance method.
     *
     * @param obj target class instance.
     * @param allArguments all method arguments
     * @param method method description.
     * @param dynamicFieldGetter a proxy to set the dynamic field
     * @param dynamicFieldSetter a proxy to get the dynamic field
     * @param zuper the origin call ref.
     * @return the return value of target instance method.
     * @throws Exception only throw exception because of zuper.call() or unexpected exception in sky-walking ( This is a
     * bug, if anything triggers this condition ).
     */
    @RuntimeType
    public Object intercept(@This Object obj,
        @AllArguments Object[] allArguments,
        @SuperCall Callable<?> zuper,
        @FieldProxy(ClassEnhancePluginDefine.CONTEXT_ATTR_NAME) FieldSetter dynamicFieldSetter,
        @FieldProxy(ClassEnhancePluginDefine.CONTEXT_ATTR_NAME) FieldGetter dynamicFieldGetter,
        @Origin Method method
    ) throws Throwable {
        InstanceMethodsAroundInterceptor interceptor = InterceptorInstanceLoader
            .load(instanceMethodsAroundInterceptorClassName, obj.getClass().getClassLoader());

        MethodInterceptResult result = new MethodInterceptResult();
        try {
            interceptor.beforeMethod(obj, method.getName(), allArguments, method.getParameterTypes(),
                dynamicFieldSetter,
                dynamicFieldGetter,
                result);
        } catch (Throwable t) {
            logger.error(t, "class[{}] before method[{}] intercept failure", obj.getClass(), method.getName());
        }

        Object ret = null;
        try {
            if (!result.isContinue()) {
                ret = result._ret();
            } else {
                ret = zuper.call();
            }
        } catch (Throwable t) {
            try {
                interceptor.handleMethodException(obj, method.getName(), allArguments, method.getParameterTypes(),
                    dynamicFieldSetter,
                    dynamicFieldGetter,
                    t);
            } catch (Throwable t2) {
                logger.error(t2, "class[{}] handle method[{}] exception failure", obj.getClass(), method.getName());
            }
            throw t;
        } finally {
            try {
                ret = interceptor.afterMethod(obj, method.getName(), allArguments, method.getParameterTypes(),
                    dynamicFieldSetter,
                    dynamicFieldGetter,
                    ret);
            } catch (Throwable t) {
                logger.error(t, "class[{}] after method[{}] intercept failure", obj.getClass(), method.getName());
            }
        }
        return ret;
    }
}
