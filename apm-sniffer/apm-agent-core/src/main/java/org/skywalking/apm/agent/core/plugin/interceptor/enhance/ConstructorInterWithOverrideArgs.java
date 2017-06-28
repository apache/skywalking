package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import org.skywalking.apm.agent.core.plugin.interceptor.loader.InterceptorInstanceLoader;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

/**
 * The actual byte-buddy's interceptor to intercept constructor methods.
 * In this class, it provide a bridge between byte-buddy and sky-walking plugin.
 *
 * @author wusheng
 */
public class ConstructorInterWithOverrideArgs {
    private static final ILog logger = LogManager.getLogger(ConstructorInterWithOverrideArgs.class);

    /**
     * A class full name, and instanceof {@link InstanceConstructorInterceptor}
     * This name should only stay in {@link String}, the real {@link Class} type will trigger classloader failure.
     * If you want to know more, please check on books about Classloader or Classloader appointment mechanism.
     */
    private String constructorInterceptorClassName;

    /**
     * Set the name of {@link ConstructorInterWithOverrideArgs#constructorInterceptorClassName}
     *
     * @param constructorInterceptorClassName class full name.
     */
    public ConstructorInterWithOverrideArgs(String constructorInterceptorClassName) {
        this.constructorInterceptorClassName = constructorInterceptorClassName;
    }

    /**
     * Intercept the target constructor.
     *
     * @param obj target class instance.
     * @param dynamicFieldGetter a proxy to set the dynamic field
     * @param dynamicFieldSetter a proxy to get the dynamic field
     * @param allArguments all constructor arguments
     */
    @RuntimeType
    public void intercept(@This Object obj,
        @FieldProxy(ClassEnhancePluginDefine.CONTEXT_ATTR_NAME) FieldSetter dynamicFieldSetter,
        @FieldProxy(ClassEnhancePluginDefine.CONTEXT_ATTR_NAME) FieldGetter dynamicFieldGetter,
        @AllArguments Object[] allArguments,
        @Morph(defaultMethod = true) Constructible zuper) {
        try {
            InstanceConstructorInterceptor interceptor = InterceptorInstanceLoader.load(constructorInterceptorClassName, obj.getClass().getClassLoader());

            interceptor.onConstruct(obj, allArguments, dynamicFieldSetter, dynamicFieldGetter);
            zuper.call(allArguments);
        } catch (Throwable t) {
            logger.error("ConstructorInter failure.", t);
        }

    }
}
