package com.a.eye.skywalking.plugin.interceptor.enhance;

import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.plugin.AbstractClassEnhancePluginDefine;
import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.plugin.interceptor.*;
import com.a.eye.skywalking.util.StringUtil;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public abstract class ClassEnhancePluginDefine extends AbstractClassEnhancePluginDefine {
    private static ILog logger = LogManager.getLogger(ClassEnhancePluginDefine.class);

    public static final String contextAttrName = "_$EnhancedClassInstanceContext";

    @Override
    protected DynamicType.Builder<?> enhance(String enhanceOriginClassName, DynamicType.Builder<?> newClassBuilder) throws PluginException {
        newClassBuilder = this.enhanceClass(enhanceOriginClassName, newClassBuilder);

        newClassBuilder = this.enhanceInstance(enhanceOriginClassName, newClassBuilder);

        return newClassBuilder;
    }

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
        if (existedConstructorInterceptPoint) {
            for (ConstructorInterceptPoint constructorInterceptPoint : constructorInterceptPoints) {
                newClassBuilder = newClassBuilder.constructor(constructorInterceptPoint.getConstructorMatcher())
                        .intercept(SuperMethodCall.INSTANCE.andThen(
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
                    throw new EnhanceException("no InstanceMethodsAroundInterceptor define. ");
                }
                ClassInstanceMethodsInterceptor classMethodInterceptor = new ClassInstanceMethodsInterceptor(interceptor);

                MethodMatcher[] methodMatchers = instanceMethodsInterceptPoint.getMethodsMatchers();

                StringBuilder enhanceRules = new StringBuilder("\nprepare to enhance class [" + enhanceOriginClassName + "] instance methods as following rules:\n");
                int ruleIdx = 1;
                for (MethodMatcher methodMatcher : methodMatchers) {
                    enhanceRules.append("\t" + ruleIdx++ + ". " + methodMatcher + "\n");
                }
                logger.debug(enhanceRules.toString());
                ElementMatcher.Junction<MethodDescription> matcher = null;
                for (MethodMatcher methodMatcher : methodMatchers) {
                    logger.debug("enhance class {} instance methods by rule: {}", enhanceOriginClassName, methodMatcher);
                    if (matcher == null) {
                        matcher = methodMatcher.buildMatcher();
                        continue;
                    }

                    matcher = matcher.or(methodMatcher.buildMatcher());

                }

                /**
                 * exclude static methods.
                 */
                matcher = matcher.and(not(ElementMatchers.isStatic()));
                newClassBuilder = newClassBuilder.method(matcher).intercept(MethodDelegation.to(classMethodInterceptor));
            }
        }

        return newClassBuilder;
    }

    protected abstract ConstructorInterceptPoint[] getConstructorsInterceptPoints();

    protected abstract InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints();


    private DynamicType.Builder<?> enhanceClass(String enhanceOriginClassName, DynamicType.Builder<?> newClassBuilder) throws PluginException {
        StaticMethodsInterceptPoint[] staticMethodsInterceptPoints = getStaticMethodsInterceptPoints();

        if (staticMethodsInterceptPoints == null || staticMethodsInterceptPoints.length == 0) {
            return newClassBuilder;
        }

        for (StaticMethodsInterceptPoint staticMethodsInterceptPoint : staticMethodsInterceptPoints) {
            MethodMatcher[] methodMatchers = staticMethodsInterceptPoint.getMethodsMatchers();

            String interceptor = staticMethodsInterceptPoint.getMethodsInterceptor();
            if (StringUtil.isEmpty(interceptor)) {
                throw new EnhanceException("no StaticMethodsAroundInterceptor define. ");
            }

            ClassStaticMethodsInterceptor classMethodInterceptor = new ClassStaticMethodsInterceptor(interceptor);

            StringBuilder enhanceRules = new StringBuilder("\nprepare to enhance class [" + enhanceOriginClassName + "] static methods as following rules:\n");
            int ruleIdx = 1;
            for (MethodMatcher methodMatcher : methodMatchers) {
                enhanceRules.append("\t" + ruleIdx++ + ". " + methodMatcher + "\n");
            }
            logger.debug(enhanceRules.toString());
            ElementMatcher.Junction<MethodDescription> matcher = null;
            for (MethodMatcher methodMatcher : methodMatchers) {
                logger.debug("enhance class {} static methods by rule: {}", enhanceOriginClassName, methodMatcher);
                if (matcher == null) {
                    matcher = methodMatcher.buildMatcher();
                    continue;
                }

                matcher = matcher.or(methodMatcher.buildMatcher());

            }

            /**
             * restrict static methods.
             */
            matcher = matcher.and(ElementMatchers.isStatic());
            newClassBuilder = newClassBuilder.method(matcher).intercept(MethodDelegation.to(classMethodInterceptor));
        }

        return newClassBuilder;
    }

    /**
     * 返回需要被增强的方法列表
     *
     * @return
     */
    protected abstract StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints();
}
