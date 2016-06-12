package com.ai.cloud.skywalking.plugin.interceptor;

import static net.bytebuddy.matcher.ElementMatchers.any;

import java.util.List;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.pool.TypePool.Resolution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.plugin.PluginCfg;
import com.ai.cloud.skywalking.util.StringUtil;

public class EnhanceClazz4Interceptor {
    private static Logger logger = LogManager
            .getLogger(EnhanceClazz4Interceptor.class);

    private TypePool typePool;

    public static final String contextAttrName = "_$EnhancedClassInstanceContext";

    public EnhanceClazz4Interceptor() {
        typePool = TypePool.Default.ofClassPath();
    }

    public void enhance() {
        List<String> interceptorClassList = PluginCfg.CFG
                .getInterceptorClassList();

        for (String interceptorClassName : interceptorClassList) {
            try {
                enhance0(interceptorClassName);
            } catch (Throwable t) {
                logger.error("enhance class [{}] for intercept failure.",
                        interceptorClassName, t);
            }
        }
    }

    private void enhance0(String interceptorDefineClassName)
            throws InstantiationException, IllegalAccessException,
            ClassNotFoundException, EnhanceException {
        logger.debug("prepare to enhance class by {}.",
                interceptorDefineClassName);
        InterceptorDefine define = (InterceptorDefine) Class.forName(
                interceptorDefineClassName).newInstance();

        String enhanceOriginClassName = define.getBeInterceptedClassName();
        if (StringUtil.isEmpty(enhanceOriginClassName)) {
            logger.warn("classname of being intercepted is not defined by {}.",
                    interceptorDefineClassName);
            return;
        }

        logger.debug("prepare to enhance class {} by {}.",
                enhanceOriginClassName, interceptorDefineClassName);

        Resolution resolution = typePool.describe(enhanceOriginClassName);
        if (!resolution.isResolved()) {
            logger.warn("class {} can't be resolved, enhance by {} failue.",
                    enhanceOriginClassName, interceptorDefineClassName);
            return;
        }

        /**
         * find origin class source code for interceptor
         */
        DynamicType.Builder<?> newClassBuilder =  new ByteBuddy()
                .rebase(resolution.resolve(),
                        ClassFileLocator.ForClassLoader.ofClassPath());
                
        /**
         * alter class source code.<br/>
         *
         * new class need:<br/>
         * 1.add field '_$EnhancedClassInstanceContext' of type
         * EnhancedClassInstanceContext <br/>
         *
         * 2.intercept constructor by default, and intercept method which it's
         * required by interceptorDefineClass. <br/>
         */
        IAroundInterceptor interceptor = define.instance();
        if (interceptor == null) {
            throw new EnhanceException("no IAroundInterceptor instance. ");
        }

        newClassBuilder = newClassBuilder
                .defineField(contextAttrName,
                        EnhancedClassInstanceContext.class)
                .constructor(any())
                .intercept(
                        SuperMethodCall.INSTANCE.andThen(MethodDelegation.to(
                                new ClassConstructorInterceptor(interceptor))
                                .appendParameterBinder(
                                        FieldProxy.Binder.install(
                                                FieldGetter.class,
                                                FieldSetter.class))));

        MethodMatcher[] methodMatchers = define.getBeInterceptedMethodsMatchers();
        ClassMethodInterceptor classMethodInterceptor = new ClassMethodInterceptor(
                interceptor);
        
        StringBuilder enhanceRules = new StringBuilder("\nprepare to enhance class [" + enhanceOriginClassName + "] as following rules:\n"); 
        int ruleIdx = 1;
        for (MethodMatcher methodMatcher : methodMatchers) {
        	enhanceRules.append("\t" + ruleIdx++ + ". " +  methodMatcher + "\n");
        }
        logger.debug(enhanceRules);

        for (MethodMatcher methodMatcher : methodMatchers) {
            logger.debug("enhance class {} by rule: {}",
                    enhanceOriginClassName, methodMatcher);
            newClassBuilder = newClassBuilder.method(
            		methodMatcher.builderMatcher()).intercept(
                    MethodDelegation.to(classMethodInterceptor));
        }

        /**
         * naming class as origin class name, make and load class to
         * classloader.
         */
        newClassBuilder
                .name(enhanceOriginClassName)
                .make()
                .load(ClassLoader.getSystemClassLoader(),
                        ClassLoadingStrategy.Default.INJECTION).getLoaded();

        logger.debug("enhance class {} by {} completely.",
                enhanceOriginClassName, interceptorDefineClassName);
    }
}
