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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TracingEnhanceProcessor implements DisposableBean, BeanPostProcessor, BeanFactoryPostProcessor, ApplicationContextAware {

    private Logger logger = Logger.getLogger(TracingEnhanceProcessor.class.getName());

    private final Set<TracingClassBean> beanSet = new ConcurrentHashSet<TracingClassBean>();
    private ApplicationContext applicationContext;


    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        applicationContext.getBeansOfType(TracingClassBean.class);
        //beanSet.addAll(beanFactory.getBeansOfType(TracingClassBean.class).values());
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        boolean isMatch = false;
        TracingClassBean matchClassBean = null;
        for (TracingClassBean tracingClassBean : beanSet) {
            //不符合符合规则
            //报名只支持精确匹配，类名支持*号模糊
            if (isPackageMatch(bean.getClass().getPackage().getName(), tracingClassBean.getPackageName())
                    || isClassNameMatch(bean, tracingClassBean.getClassName())) {
                isMatch = true;
                matchClassBean = tracingClassBean;
                continue;
            }
        }
        if (!isMatch || matchClassBean == null) {
            return bean;
        }

        //符合规范
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctSource = pool.get(bean.getClass().getName());
            CtClass ctDestination = pool.makeClass(generateProxyClassName(bean), ctSource);
            //拷贝所有的方法，所有的属性以及注解
            copy(ctSource, ctDestination);

            String methodPrefix = matchClassBean.getMethod();
            if (matchClassBean.getMethod().indexOf("*") != -1) {
                methodPrefix = methodPrefix.substring(0, matchClassBean.getMethod().indexOf("*"));
            }

            List<CtMethod> methods = new ArrayList<CtMethod>();
            for (CtMethod method : ctDestination.getDeclaredMethods()) {
                if (methodPrefix.length() <= 0 || method.getName().startsWith(methodPrefix)) {
                    methods.add(method);
                }
            }

            for (CtMethod method : methods) {
                enhanceMethod(bean, method);
            }

            // 判断是否存在无参的构造函数，如果没有，则
            // 生成实例，构造函数
            Class generateClass = ctDestination.toClass();

            Object newBean = generateClass.newInstance();
            BeanUtils.copyProperties(bean, newBean);
            return newBean;
        } catch (NotFoundException e) {
            logger.log(Level.ALL, "Class [" + beanName.getClass().getName() + "] cannot be found");
            throw new IllegalStateException("Class [" + beanName.getClass().getName() + "] cannot be found", e);
        } catch (CannotCompileException e) {
            throw new IllegalStateException("Class [" + beanName.getClass().getName() + "] cannot be compile", e);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return bean;
    }

    private String generateProxyClassName(Object bean) {
        return bean.getClass().getName() + "$EnhanceBySWTracing$" + ThreadLocalRandom.current().nextInt(100);
    }

    private void copy(CtClass ctSource, CtClass ctDestination) {
        try {
            // copy fields
            ConstPool cp = ctDestination.getClassFile().getConstPool();
            for (CtField f : ctSource.getDeclaredFields()) {
                CtClass fieldTypeClass = ClassPool.getDefault().get(f.getType().getName());
                //with annotations
                AnnotationsAttribute invAnn = (AnnotationsAttribute) f.getFieldInfo().getAttribute(
                        AnnotationsAttribute.invisibleTag);
                AnnotationsAttribute visAnn = (AnnotationsAttribute) f.getFieldInfo().getAttribute(
                        AnnotationsAttribute.visibleTag);
                CtField ctField = new CtField(fieldTypeClass, f.getName(), ctDestination);
                if (invAnn != null) {
                    ctField.getFieldInfo().addAttribute(invAnn.copy(cp, null));
                }
                if (visAnn != null) {
                    ctField.getFieldInfo().addAttribute(visAnn.copy(cp, null));
                }

                ctDestination.addField(ctField);
            }
            // copy methods
            for (CtMethod m : ctSource.getDeclaredMethods()) {
                //copy the method prefixing it with the source class name
                CtMethod newm = CtNewMethod.copy(m, /*ctSource.getSimpleName() + "_" + */m.getName(), ctDestination, null);
                // with annotations
                AnnotationsAttribute invAnn = (AnnotationsAttribute) m.getMethodInfo().getAttribute(
                        AnnotationsAttribute.invisibleTag);
                AnnotationsAttribute visAnn = (AnnotationsAttribute) m.getMethodInfo().getAttribute(
                        AnnotationsAttribute.visibleTag);
                if (invAnn != null) {
                    newm.getMethodInfo().addAttribute(invAnn.copy(cp, null));
                }
                if (visAnn != null) {
                    newm.getMethodInfo().addAttribute(visAnn.copy(cp, null));
                }

                ctDestination.addMethod(newm);
            }

            // copy annotation
            AnnotationsAttribute invAnn = (AnnotationsAttribute) ctSource.getClassFile().getAttribute(
                    AnnotationsAttribute.invisibleTag);
            AnnotationsAttribute visAnn = (AnnotationsAttribute) ctSource.getClassFile().getAttribute(
                    AnnotationsAttribute.visibleTag);
            if (invAnn != null) {
                ctDestination.getClassFile().addAttribute(invAnn.copy(cp, null));
            }
            if (visAnn != null) {
                ctDestination.getClassFile().addAttribute(visAnn.copy(cp, null));
            }

        } catch (Exception e) {
            logger.log(Level.ALL, "Failed to generate proxy class ");
            throw new IllegalStateException("Failed to generate proxy class");
        }
    }

    protected void enhanceMethod(Object bean, CtMethod method) throws CannotCompileException, NotFoundException {
        ClassPool cp = method.getDeclaringClass().getClassPool();
        method.addLocalVariable("___sender", cp.get(LocalBuriedPointSender.class.getName()));
        method.insertBefore("___sender = new " + LocalBuriedPointSender.class.getName() + "();___sender.beforeSend"
                + generateBeforeSendParameter(bean, method));
        method.addCatch("new " + LocalBuriedPointSender.class.getName() + "().handleException(e);throw e;",
                ClassPool.getDefault().getCtClass(Throwable.class.getName()), "e");
        method.insertAfter("new " + LocalBuriedPointSender.class.getName() + "().afterSend();", true);
    }

    private String generateBeforeSendParameter(Object bean, CtMethod method) throws NotFoundException {
        StringBuilder builder = new StringBuilder("(" + Identification.class.getName() + ".newBuilder().viewPoint(\""
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


    private boolean isClassNameMatch(Object bean, String tracingClassBean) {
        //
        String classNamePrefix = tracingClassBean;
        if (tracingClassBean.endsWith("*")) {
            classNamePrefix = tracingClassBean.substring(0, tracingClassBean.indexOf('*'));
        }
        return bean.getClass().getSimpleName().startsWith(classNamePrefix);
    }

    private boolean isPackageMatch(Object bean, String packageName) {
        return bean.getClass().getPackage().equals(packageName);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void destroy() throws Exception {

    }
}
