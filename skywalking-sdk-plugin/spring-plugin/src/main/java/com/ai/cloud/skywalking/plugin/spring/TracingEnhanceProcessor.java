package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.plugin.spring.util.ConcurrentHashSet;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.aop.aspectj.AspectInstanceFactory;
import org.springframework.aop.aspectj.AspectJAroundAdvice;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.SimpleAspectInstanceFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.util.Set;

public class TracingEnhanceProcessor implements DisposableBean,
        BeanPostProcessor, BeanFactoryPostProcessor, ApplicationContextAware {
    private final Set<TracingPattern> beanSet = new ConcurrentHashSet<TracingPattern>();

    @Override
    public void postProcessBeanFactory(
            ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (TracingPattern tracingPattern : applicationContext.getBeansOfType(TracingPattern.class)
                .values()) {

            AspectJExpressionPointcut packageMatcher = new AspectJExpressionPointcut();
            packageMatcher.setExpression("within(" + tracingPattern.getPackageExpression() + ")");
            tracingPattern.setPackageMatcher(packageMatcher);

            AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
            pointcut.setExpression("execution(* " + tracingPattern.getPackageExpression().substring(0, tracingPattern.getPackageExpression().length() - 1) + tracingPattern.getClassExpression() + ".*(..))");
            tracingPattern.setPointcut(pointcut);

            beanSet.add(tracingPattern);
        }

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

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        if (!AuthDesc.isAuth()) {
            return bean;
        }

        for (TracingPattern tracingPattern : beanSet) {
            if (tracingPattern.getPackageMatcher().matches(bean.getClass()) && matchClassName(tracingPattern.getClassExpression(), bean
                    .getClass().getSimpleName())) {
                ProxyFactory proxyFactory = new ProxyFactory(bean);
                proxyFactory.setProxyTargetClass(true);
                AspectInstanceFactory aspectInstanceFactory = new SimpleAspectInstanceFactory(TracingAspect.class);
                Method method = null;
                try {
                    method = TracingAspect.class.getMethod("doTracing", ProceedingJoinPoint.class);
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException("Failed to find doTracing method", e);
                }
                AspectJAroundAdvice advised = new AspectJAroundAdvice(method,
                        tracingPattern.getPointcut(), aspectInstanceFactory);
                proxyFactory.addAdvice(advised);

                return proxyFactory.getProxy();
            }
        }
        return bean;
    }

    private boolean matchClassName(String className, String simpleName) {
        if ("*".equals(className)) {
            return true;
        } else if (className.endsWith("*")) {
            return simpleName.startsWith(className);
        }
        return false;
    }

    @Override
    public void destroy() throws Exception {

    }

}
