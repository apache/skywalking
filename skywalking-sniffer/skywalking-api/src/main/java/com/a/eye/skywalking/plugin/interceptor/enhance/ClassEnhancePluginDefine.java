package com.a.eye.skywalking.plugin.interceptor.enhance;

import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.EasyLogger;
import com.a.eye.skywalking.plugin.AbstractClassEnhancePluginDefine;
import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.protocol.util.StringUtil;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import com.a.eye.skywalking.plugin.interceptor.EnhanceException;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;

public abstract class ClassEnhancePluginDefine extends AbstractClassEnhancePluginDefine {
	private static EasyLogger easyLogger = LogManager
			.getLogger(ClassEnhancePluginDefine.class);

	public static final String contextAttrName = "_$EnhancedClassInstanceContext";

	@Override
	protected DynamicType.Builder<?> enhance(String enhanceOriginClassName,
			DynamicType.Builder<?> newClassBuilder) throws PluginException {
		newClassBuilder = this.enhanceClass(enhanceOriginClassName, newClassBuilder);
		
		newClassBuilder = this.enhanceInstance(enhanceOriginClassName, newClassBuilder);
		
		return newClassBuilder;
	}

	private DynamicType.Builder<?> enhanceInstance(String enhanceOriginClassName,
			DynamicType.Builder<?> newClassBuilder) throws PluginException {
		MethodMatcher[] methodMatchers = getInstanceMethodsMatchers();
		if(methodMatchers == null){
			return newClassBuilder;
		}
		
		
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
		String interceptor = getInstanceMethodsInterceptor();
		if (StringUtil.isEmpty(interceptor)) {
			throw new EnhanceException("no InstanceMethodsAroundInterceptor define. ");
		}

		newClassBuilder = newClassBuilder
				.defineField(contextAttrName,
						EnhancedClassInstanceContext.class, ACC_PRIVATE)
				.constructor(any())
				.intercept(
						SuperMethodCall.INSTANCE.andThen(MethodDelegation.to(
								new ClassConstructorInterceptor(interceptor))
								.appendParameterBinder(
										FieldProxy.Binder.install(
												FieldGetter.class,
												FieldSetter.class))));

		ClassInstanceMethodsInterceptor classMethodInterceptor = new ClassInstanceMethodsInterceptor(
				interceptor);

		StringBuilder enhanceRules = new StringBuilder(
				"\nprepare to enhance class [" + enhanceOriginClassName
						+ "] instance methods as following rules:\n");
		int ruleIdx = 1;
		for (MethodMatcher methodMatcher : methodMatchers) {
			enhanceRules.append("\t" + ruleIdx++ + ". " + methodMatcher + "\n");
		}
		easyLogger.debug(enhanceRules);
		ElementMatcher.Junction<MethodDescription> matcher = null;
		for (MethodMatcher methodMatcher : methodMatchers) {
			easyLogger.debug("enhance class {} instance methods by rule: {}",
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

		return newClassBuilder;
	}
	
	/**
	 * 返回需要被增强的方法列表
	 * 
	 * @return
	 */
	protected abstract MethodMatcher[] getInstanceMethodsMatchers();

	/**
	 * 返回增强拦截器的实现<br/>
	 * 每个拦截器在同一个被增强类的内部，保持单例
	 * 
	 * @return
	 */
	protected abstract String getInstanceMethodsInterceptor();
	
	private DynamicType.Builder<?> enhanceClass(String enhanceOriginClassName,
			DynamicType.Builder<?> newClassBuilder) throws PluginException {
		MethodMatcher[] methodMatchers = getStaticMethodsMatchers();
		if(methodMatchers == null){
			return newClassBuilder;
		}

		String interceptor = getStaticMethodsInterceptor();
		if (StringUtil.isEmpty(interceptor)) {
			throw new EnhanceException("no StaticMethodsAroundInterceptor define. ");
		}
		
		
		ClassStaticMethodsInterceptor classMethodInterceptor = new ClassStaticMethodsInterceptor(
				interceptor);

		StringBuilder enhanceRules = new StringBuilder(
				"\nprepare to enhance class [" + enhanceOriginClassName
						+ "] static methods as following rules:\n");
		int ruleIdx = 1;
		for (MethodMatcher methodMatcher : methodMatchers) {
			enhanceRules.append("\t" + ruleIdx++ + ". " + methodMatcher + "\n");
		}
		easyLogger.debug(enhanceRules);
		ElementMatcher.Junction<MethodDescription> matcher = null;
		for (MethodMatcher methodMatcher : methodMatchers) {
			easyLogger.debug("enhance class {} static methods by rule: {}",
					enhanceOriginClassName, methodMatcher);
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
		newClassBuilder = newClassBuilder.method(matcher).intercept(
				MethodDelegation.to(classMethodInterceptor));

		return newClassBuilder;
	}
	
	/**
	 * 返回需要被增强的方法列表
	 * 
	 * @return
	 */
	protected abstract MethodMatcher[] getStaticMethodsMatchers();

	/**
	 * 返回增强拦截器的实现<br/>
	 * 每个拦截器在同一个被增强类的内部，保持单例
	 * 
	 * @return
	 */
	protected abstract String getStaticMethodsInterceptor();
}
