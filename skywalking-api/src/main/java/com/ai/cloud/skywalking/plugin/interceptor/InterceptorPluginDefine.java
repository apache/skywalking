package com.ai.cloud.skywalking.plugin.interceptor;

import static com.ai.cloud.skywalking.plugin.PluginBootstrap.CLASS_TYPE_POOL;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.not;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool.Resolution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.plugin.IPlugin;
import com.ai.cloud.skywalking.plugin.PluginException;
import com.ai.cloud.skywalking.util.StringUtil;

public abstract class InterceptorPluginDefine implements IPlugin {
	private static Logger logger = LogManager.getLogger(InterceptorPluginDefine.class);
	
    public static final String contextAttrName = "_$EnhancedClassInstanceContext";
    

	@Override
	public void define() throws PluginException {
		String interceptorDefineClassName = this.getClass().getName();
		
		String enhanceOriginClassName = getBeInterceptedClassName();
        if (StringUtil.isEmpty(enhanceOriginClassName)) {
            logger.warn("classname of being intercepted is not defined by {}.",
                    interceptorDefineClassName);
            return;
        }

        logger.debug("prepare to enhance class {} by {}.",
                enhanceOriginClassName, interceptorDefineClassName);

        Resolution resolution = CLASS_TYPE_POOL.describe(enhanceOriginClassName);
        if (!resolution.isResolved()) {
            logger.warn("class {} can't be resolved, enhance by {} failue.",
                    enhanceOriginClassName, interceptorDefineClassName);
            return;
        }

        /**
         * find origin class source code for interceptor
         */
        DynamicType.Builder<?> newClassBuilder = new ByteBuddy()
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
        IAroundInterceptor interceptor = instance();
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

        MethodMatcher[] methodMatchers = getBeInterceptedMethodsMatchers();
        ClassMethodInterceptor classMethodInterceptor = new ClassMethodInterceptor(
                interceptor);

        StringBuilder enhanceRules = new StringBuilder("\nprepare to enhance class [" + enhanceOriginClassName + "] as following rules:\n");
        int ruleIdx = 1;
        for (MethodMatcher methodMatcher : methodMatchers) {
            enhanceRules.append("\t" + ruleIdx++ + ". " + methodMatcher + "\n");
        }
        logger.debug(enhanceRules);
        ElementMatcher.Junction<MethodDescription> matcher = null;
        for (MethodMatcher methodMatcher : methodMatchers) {
            logger.debug("enhance class {} by rule: {}",
                    enhanceOriginClassName, methodMatcher);
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
        newClassBuilder = newClassBuilder.method(matcher).intercept(
                MethodDelegation.to(classMethodInterceptor));

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

	/**
	 * 返回要被增强的类，应当返回类全名
	 * 
	 * @return
	 */
	public abstract String getBeInterceptedClassName();

	/**
	 * 返回需要被增强的方法列表
	 * 
	 * @return
	 */
	public abstract MethodMatcher[] getBeInterceptedMethodsMatchers();

	/**
	 * 返回增强拦截器的实现<br/>
	 * 每个拦截器在同一个被增强类的内部，保持单例
	 * 
	 * @return
	 */
	public abstract IAroundInterceptor instance();
}
