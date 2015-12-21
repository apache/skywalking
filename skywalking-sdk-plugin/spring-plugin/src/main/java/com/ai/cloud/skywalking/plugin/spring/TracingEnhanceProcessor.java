package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.buriedpoint.LocalBuriedPointSender;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.plugin.spring.util.ConcurrentHashSet;

import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class TracingEnhanceProcessor implements DisposableBean,
		BeanPostProcessor, BeanFactoryPostProcessor, ApplicationContextAware {

	private final Set<TracingPattern> beanSet = new ConcurrentHashSet<TracingPattern>();

	
	@Override
	public void postProcessBeanFactory(
			ConfigurableListableBeanFactory beanFactory) throws BeansException {
		beanSet.addAll(applicationContext.getBeansOfType(TracingPattern.class)
				.values());
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

	public enum MatchType {
		METHOD, PACKAGE, CLASS;
	}

	private boolean checkMatch(String value, String pattern, MatchType matchType) {
		boolean result;
		if ("*".equals(pattern)) {
			return true;
		}
		if (matchType == MatchType.PACKAGE) {
			if (pattern.endsWith(".*")) {
				String newPattern = pattern.substring(0,
						pattern.lastIndexOf(".*"));
				result = value.startsWith(newPattern);
			} else {
				result = value.equals(pattern);
			}
		} else {
			if (pattern.endsWith("*")) {
				String newPattern = pattern.substring(0,
						pattern.lastIndexOf("*"));
				result = value.startsWith(newPattern);
			} else {
				result = value.equals(pattern);
			}
		}
		return result;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		String packageName = bean.getClass().getPackage().getName();
		String className = bean.getClass().getSimpleName();
		TracingPattern matchClassBean = null;
		boolean isMatch = false;
		for (TracingPattern tracingPattern : beanSet) {
			if (checkMatch(packageName, tracingPattern.getPackageName(),
					MatchType.PACKAGE)
					&& checkMatch(className, tracingPattern.getClassName(),
							MatchType.CLASS)) {
				isMatch = true;
				matchClassBean = tracingPattern;
				continue;
			}
		}
		if (!isMatch || matchClassBean == null) {
			return bean;
		}

		// 符合规范
		try {
			ClassPool pool = ClassPool.getDefault();
			CtClass ctSource = pool.get(bean.getClass().getName());
			CtClass ctDestination = pool.makeClass(
					generateProxyClassName(bean), ctSource);
			// 拷贝所有的方法，
			copyAllFields(ctSource, ctDestination);
			// 拷贝所有的注解
			copyClassAnnotation(ctSource, ctDestination);
			// 拷贝所有的方法，并增强
			ConstPool cp = ctDestination.getClassFile().getConstPool();
			for (CtMethod m : ctSource.getDeclaredMethods()) {
				CtMethod newm = CtNewMethod.delegator(m, ctDestination);
				copyMethodAnnotation(cp, m, newm);
				// 是否符合规范，符合则增强
				if (checkMatch(m.getName(), matchClassBean.getMethod(),
						MatchType.METHOD)) {
					enhanceMethod(bean, newm);
				}
				ctDestination.addMethod(newm);
			}

			Class<?> generateClass = ctDestination.toClass();
			Object newBean = generateClass.newInstance();
			BeanUtils.copyProperties(bean, newBean);
			return newBean;
		} catch (NotFoundException e) {
			throw new IllegalStateException("Class ["
					+ beanName.getClass().getName() + "] cannot be found", e);
		} catch (CannotCompileException e) {
			throw new IllegalStateException("Class ["
					+ beanName.getClass().getName() + "] cannot be compile", e);
		} catch (InstantiationException e) {
			throw new IllegalStateException("Failed to instance class["
					+ beanName.getClass().getName() + "]", e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Failed to access class["
					+ beanName.getClass().getName() + "]", e);
		}
	}

	

	private void copyMethodAnnotation(ConstPool cp, CtMethod m, CtMethod newm) {
		AnnotationsAttribute invAnn = (AnnotationsAttribute) m.getMethodInfo()
				.getAttribute(AnnotationsAttribute.invisibleTag);
		AnnotationsAttribute visAnn = (AnnotationsAttribute) m.getMethodInfo()
				.getAttribute(AnnotationsAttribute.visibleTag);
		if (invAnn != null) {
			newm.getMethodInfo().addAttribute(invAnn.copy(cp, null));
		}
		if (visAnn != null) {
			newm.getMethodInfo().addAttribute(visAnn.copy(cp, null));
		}
	}

	private String generateProxyClassName(Object bean) {
		return bean.getClass().getName() + "$EnhanceBySWTracing$"
				+ ThreadLocalRandom.current().nextInt(100);
	}

	private void copyAllFields(CtClass ctSource, CtClass ctDestination)
			throws CannotCompileException, NotFoundException {
		// copy fields
		ConstPool cp = ctDestination.getClassFile().getConstPool();
		for (CtField ctSourceField : ctSource.getDeclaredFields()) {
			CtClass fieldTypeClass = ClassPool.getDefault().get(
					ctSourceField.getType().getName());
			CtField ctField = new CtField(fieldTypeClass,
					ctSourceField.getName(), ctDestination);
			// with annotations
			copyAllFieldAnnotation(cp, ctSourceField, ctField);
			ctDestination.addField(ctField);
		}
	}

	private void copyAllFieldAnnotation(ConstPool cp, CtField ctSourceField,
			CtField ctDestinationField) throws CannotCompileException {
		AnnotationsAttribute invAnn = (AnnotationsAttribute) ctSourceField
				.getFieldInfo().getAttribute(AnnotationsAttribute.invisibleTag);
		AnnotationsAttribute visAnn = (AnnotationsAttribute) ctSourceField
				.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);

		if (invAnn != null) {
			ctDestinationField.getFieldInfo().addAttribute(
					invAnn.copy(cp, null));
		}
		if (visAnn != null) {
			ctDestinationField.getFieldInfo().addAttribute(
					visAnn.copy(cp, null));
		}
	}

	private void copyClassAnnotation(CtClass ctSource, CtClass ctDestination) {
		ConstPool cp = ctDestination.getClassFile().getConstPool();
		AnnotationsAttribute invAnn = (AnnotationsAttribute) ctSource
				.getClassFile().getAttribute(AnnotationsAttribute.invisibleTag);
		AnnotationsAttribute visAnn = (AnnotationsAttribute) ctSource
				.getClassFile().getAttribute(AnnotationsAttribute.visibleTag);
		if (invAnn != null) {
			ctDestination.getClassFile().addAttribute(invAnn.copy(cp, null));
		}
		if (visAnn != null) {
			ctDestination.getClassFile().addAttribute(visAnn.copy(cp, null));
		}
	}

	protected void enhanceMethod(Object bean, CtMethod method)
			throws CannotCompileException, NotFoundException {
		ClassPool cp = method.getDeclaringClass().getClassPool();
		method.addLocalVariable("___sender",
				cp.get(LocalBuriedPointSender.class.getName()));
		method.insertBefore("___sender = new "
				+ LocalBuriedPointSender.class.getName()
				+ "();\n___sender.beforeSend"
				+ generateBeforeSendParameter(bean, method) + "\n");
		method.addCatch("new " + LocalBuriedPointSender.class.getName()
				+ "().handleException(e);throw e;", ClassPool.getDefault()
				.getCtClass(Throwable.class.getName()), "e");
		method.insertAfter("new " + LocalBuriedPointSender.class.getName()
				+ "().afterSend();", true);
	}

	private String generateBeforeSendParameter(Object bean, CtMethod method)
			throws NotFoundException {
		StringBuilder builder = new StringBuilder("("
				+ Identification.class.getName() + ".newBuilder().viewPoint(\""
				+ bean.getClass().getName() + "." + method.getName());
		builder.append("(");
		for (CtClass param : method.getParameterTypes()) {
			builder.append(param.getSimpleName() + ",");
		}
		if (method.getParameterTypes().length > 0) {
			builder = builder.delete(builder.length() - 1, builder.length());
		}
		builder.append(")");
		builder.append("\").spanType('M').build());");
		return builder.toString();
	}

	@Override
	public void destroy() throws Exception {

	}
}
