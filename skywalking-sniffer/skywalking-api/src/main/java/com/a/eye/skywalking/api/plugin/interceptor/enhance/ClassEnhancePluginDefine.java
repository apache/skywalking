package com.a.eye.skywalking.api.plugin.interceptor.enhance;

import com.a.eye.skywalking.api.logging.api.ILog;
import com.a.eye.skywalking.api.logging.api.LogManager;
import com.a.eye.skywalking.api.plugin.AbstractClassEnhancePluginDefine;
import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.api.util.StringUtil;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.matcher.ElementMatchers;

import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * This class controls all enhance operations, including enhance constructors, instance methods and static methods.
 * All the enhances base on three types interceptor point: {@link ConstructorInterceptPoint}, {@link InstanceMethodsInterceptPoint} and {@link StaticMethodsInterceptPoint}
 * If plugin is going to enhance constructors, instance methods, or both,
 * {@link ClassEnhancePluginDefine} will add a field of {@link EnhancedClassInstanceContext} type.
 *
 * @author wusheng
 */
public abstract class ClassEnhancePluginDefine extends AbstractClassEnhancePluginDefine {
    private static ILog logger = LogManager.getLogger(ClassEnhancePluginDefine.class);

    /**
     * New field name.
     */
    public static final String contextAttrName = "_$EnhancedClassInstanceContext";

    /**
     * Begin to define how to enhance class.
     * After invoke this method, only means definition is finished.
     *
     * @param enhanceOriginClassName target class name
     * @param newClassBuilder        byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     * @throws PluginException
     */
    @Override
    protected DynamicType.Builder<?> enhance(String enhanceOriginClassName, DynamicType.Builder<?> newClassBuilder) throws PluginException {
        newClassBuilder = this.enhanceClass(enhanceOriginClassName, newClassBuilder);

        newClassBuilder = this.enhanceInstance(enhanceOriginClassName, newClassBuilder);

        return newClassBuilder;
    }

    /**
     * Enhance a class to intercept constructors and class instance methods.
     *
     * @param enhanceOriginClassName target class name
     * @param newClassBuilder        byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     * @throws PluginException
     */
    private DynamicType.Builder<?> enhanceInstance(String enhanceOriginClassName, DynamicType.Builder<?> newClassBuilder) throws PluginException {
        ConstructorInterceptPoint[] constructorInterceptPoints = getConstructorsInterceptPoints();
        InstanceMethodsInterceptPoint[] instanceMethodsInterceptPoints = getInstanceMethodsInterceptPoints();

        boolean existedConstructorInterceptPoint = false;
        if (constructorInterceptPoints != null && constructorInterceptPoints.length > 0) {
            existedConstructorInterceptPoint = true;
        }
        boolean existedMethodsInterceptPoints = false;
        if (instanceMethodsInterceptPoints != null && instanceMethodsInterceptPoints.length > 0) {
            existedMethodsInterceptPoints = true;
        }

        /**
         * nothing need to be enhanced in class instance, maybe need enhance static methods.
         */
        if (!existedConstructorInterceptPoint && !existedMethodsInterceptPoints) {
            return newClassBuilder;
        }


        /**
         * alter class source code.<br/>
         *
         * new class need:<br/>
         * 1.add field '_$EnhancedClassInstanceContext' of type
         * EnhancedClassInstanceContext <br/>
         *
         */
        newClassBuilder = newClassBuilder.defineField(contextAttrName, EnhancedClassInstanceContext.class, ACC_PRIVATE);

        /**
         * 2. enhance constructors
         */
        newClassBuilder = newClassBuilder.constructor(ElementMatchers.<MethodDescription>any()).intercept(SuperMethodCall.INSTANCE
                .andThen(MethodDelegation.to(new DefaultClassConstructorInterceptor()).appendParameterBinder(FieldProxy.Binder.install(FieldGetter.class, FieldSetter.class))));

        if (existedConstructorInterceptPoint) {
            for (ConstructorInterceptPoint constructorInterceptPoint : constructorInterceptPoints) {
                newClassBuilder = newClassBuilder.constructor(constructorInterceptPoint.getConstructorMatcher()).intercept(SuperMethodCall.INSTANCE.andThen(
                        MethodDelegation.to(new ClassConstructorInterceptor(constructorInterceptPoint.getConstructorInterceptor()))
                                .appendParameterBinder(FieldProxy.Binder.install(FieldGetter.class, FieldSetter.class))));
            }
        }


        /**
         * 3. enhance instance methods
         */
        if (existedMethodsInterceptPoints) {
            for (InstanceMethodsInterceptPoint instanceMethodsInterceptPoint : instanceMethodsInterceptPoints) {

                String interceptor = instanceMethodsInterceptPoint.getMethodsInterceptor();
                if (StringUtil.isEmpty(interceptor)) {
                    throw new EnhanceException("no InstanceMethodsAroundInterceptor define to enhance class " + enhanceOriginClassName);
                }
                ClassInstanceMethodsInterceptor classMethodInterceptor = new ClassInstanceMethodsInterceptor(interceptor);

                newClassBuilder =
                        newClassBuilder.method(not(isStatic()).and(instanceMethodsInterceptPoint.getMethodsMatcher())).intercept(MethodDelegation.to(classMethodInterceptor));
            }
        }

        return newClassBuilder;
    }

    /**
     * Constructor methods intercept point. See {@link ConstructorInterceptPoint}
     *
     * @return collections of {@link ConstructorInterceptPoint}
     */
    protected abstract ConstructorInterceptPoint[] getConstructorsInterceptPoints();

    /**
     * Instance methods intercept point. See {@link InstanceMethodsInterceptPoint}
     *
     * @return collections of {@link InstanceMethodsInterceptPoint}
     */
    protected abstract InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints();


    /**
     * Enhance a class to intercept class static methods.
     *
     * @param enhanceOriginClassName target class name
     * @param newClassBuilder        byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     * @throws PluginException
     */
    private DynamicType.Builder<?> enhanceClass(String enhanceOriginClassName, DynamicType.Builder<?> newClassBuilder) throws PluginException {
        StaticMethodsInterceptPoint[] staticMethodsInterceptPoints = getStaticMethodsInterceptPoints();

        if (staticMethodsInterceptPoints == null || staticMethodsInterceptPoints.length == 0) {
            return newClassBuilder;
        }

        for (StaticMethodsInterceptPoint staticMethodsInterceptPoint : staticMethodsInterceptPoints) {
            String interceptor = staticMethodsInterceptPoint.getMethodsInterceptor();
            if (StringUtil.isEmpty(interceptor)) {
                throw new EnhanceException("no StaticMethodsAroundInterceptor define to enhance class " + enhanceOriginClassName);
            }

            ClassStaticMethodsInterceptor classMethodInterceptor = new ClassStaticMethodsInterceptor(interceptor);

            newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher())).intercept(MethodDelegation.to(classMethodInterceptor));
        }

        return newClassBuilder;
    }

    /**
     * Static methods intercept point. See {@link StaticMethodsInterceptPoint}
     *
     * @return collections of {@link StaticMethodsInterceptPoint}
     */
    protected abstract StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints();
}
