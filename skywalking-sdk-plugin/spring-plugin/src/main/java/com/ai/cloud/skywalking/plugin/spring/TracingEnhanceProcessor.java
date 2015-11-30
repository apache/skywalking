package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.buriedpoint.LocalBuriedPointSender;
import com.ai.cloud.skywalking.model.Identification;
import javassist.*;
import javassist.bytecode.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TracingEnhanceProcessor implements BeanPostProcessor {

    private Logger logger = Logger.getLogger(TracingEnhanceProcessor.class.getName());

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        List<Method> methods = new ArrayList<Method>();
        for (Method method : bean.getClass().getMethods()) {
            if (method.isAnnotationPresent(Tracing.class)) {
                methods.add(method);
            }
        }
        if (methods.size() > 0) {
            try {
                ClassPool mPool = ClassPool.getDefault();
                mPool.appendClassPath(new ClassClassPath(bean.getClass()));
                //创建代理类
                CtClass mCtc = createProxyClass(bean, mPool);
                //拷贝类上注解
                copyClassesAnnotations(bean, mPool, mCtc);
                //代理类继承所有的被代理的接口
                inheritanceAllInterfaces(bean, mPool, mCtc);
                //
                mCtc.setSuperclass(mPool.get(bean.getClass().getName()));
                //添加字段
                mCtc.addField(CtField.make("private " + LocalBuriedPointSender.class.getName() + " buriedPoint;", mCtc));
                mCtc.addField(CtField.make("private " + bean.getClass().getName() + " realBean;", mCtc));
                // 校验方法
                for (Method method : methods) {
                    // 生成方法头
                    StringBuilder result = new StringBuilder();
                    result.append(generateMethodHead(method));
                    // 生成方法参数
                    result.append(generateMethodParameter(method));
                    // 生成抛出异常
                    result.append(generateException(method));
                    // 生成方法体
                    result.append(generateMethodBody(bean, method));
                    CtMethod dest = CtMethod.make(result.toString(), mCtc);

                    // 拷贝方法上的注解
                    CtMethod origin = convertMethod2CtMethod(bean, mPool, method);
                    copyAllAnnotation(origin.getMethodInfo(), dest.getMethodInfo());

                    mCtc.addMethod(dest);
                }
                mCtc.addConstructor(CtNewConstructor.make("public " + mCtc.getSimpleName() + "(" + LocalBuriedPointSender.class
                        .getName() + " buriedPoint, " + bean.getClass().getName() + " realBean){" +
                        "this.buriedPoint = buriedPoint;this.realBean = realBean;}", mCtc));
                mCtc.addConstructor(CtNewConstructor.defaultConstructor(mCtc));
                Class<?> classes = mCtc.toClass();
                Constructor<?> constructor = classes.getConstructor(LocalBuriedPointSender.class, bean.getClass());
                return constructor.newInstance(new LocalBuriedPointSender(), bean);
            } catch (CannotCompileException e) {
                logger.log(Level.ALL, "Failed to create the instance of the class[" + beanName + "]", e);
            } catch (InstantiationException e) {
                logger.log(Level.ALL, "Failed to create the instance of the class[" + beanName + "]", e);
            } catch (IllegalAccessException e) {
                logger.log(Level.ALL, "Failed to create the instance of the class[" + beanName + "]", e);
            } catch (NoSuchMethodException e) {
                logger.log(Level.ALL, "Failed to create the instance of the class[" + beanName + "]", e);
            } catch (InvocationTargetException e) {
                logger.log(Level.ALL, "Failed to create the instance of the class[" + beanName + "]", e);
            } catch (NotFoundException e) {
                logger.log(Level.ALL, "Failed to create the instance of the class[" + beanName + "]", e);
            }
        }

        return bean;
    }

    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    private CtMethod convertMethod2CtMethod(Object bean, ClassPool mPool, Method method) throws NotFoundException {
        int i = 0;
        CtClass ctClass = mPool.get(bean.getClass().getName());
        CtClass[] parameterClass = new CtClass[method.getParameterTypes().length];
        for (Class methodClass : method.getParameterTypes()) {
            parameterClass[i++] = mPool.get(methodClass.getName());
        }
        return ctClass.getDeclaredMethod(method.getName(), parameterClass);
    }

    private void copyClassesAnnotations(Object bean, ClassPool mPool, CtClass mCtc) throws NotFoundException {
        // 拷贝类上注解
        CtClass originCtClass = mPool.get(bean.getClass().getName());
        AnnotationsAttribute annotations = (AnnotationsAttribute) originCtClass.getClassFile().
                getAttribute(AnnotationsAttribute.visibleTag);
        AttributeInfo newAnnotations = annotations.copy(mCtc.getClassFile().getConstPool(), Collections.EMPTY_MAP);
        mCtc.getClassFile().addAttribute(newAnnotations);
    }

    private void copyAllAnnotation(MethodInfo origin, MethodInfo dest) {
        AnnotationsAttribute annotations = (AnnotationsAttribute) origin.getAttribute(AnnotationsAttribute.visibleTag);
        ParameterAnnotationsAttribute pannotations = (ParameterAnnotationsAttribute) origin.getAttribute(ParameterAnnotationsAttribute.visibleTag);
        ExceptionsAttribute exAt = (ExceptionsAttribute) origin.getAttribute(ExceptionsAttribute.tag);
        SignatureAttribute sigAt = (SignatureAttribute) origin.getAttribute(SignatureAttribute.tag);
        if (annotations != null) {
            AttributeInfo newAnnotations = annotations.copy(dest.getConstPool(), Collections.EMPTY_MAP);
            dest.addAttribute(newAnnotations);
        }
        if (pannotations != null) {
            AttributeInfo newAnnotations = pannotations.copy(dest.getConstPool(), Collections.EMPTY_MAP);
            dest.addAttribute(newAnnotations);
        }
        if (sigAt != null) {
            AttributeInfo newAnnotations = sigAt.copy(dest.getConstPool(), Collections.EMPTY_MAP);
            dest.addAttribute(newAnnotations);
        }
        if (exAt != null) {
            AttributeInfo newAnnotations = exAt.copy(dest.getConstPool(), Collections.EMPTY_MAP);
            dest.addAttribute(newAnnotations);
        }
    }

    private String generateMethodBody(Object bean, Method method) {
        StringBuilder result = new StringBuilder();
        result.append("{");
        result.append(" try{   this.buriedPoint.beforeSend");
        result.append(generateBeforeSendParamter(bean, method));
        if (!"void".equals(method.getReturnType().getSimpleName())) {
            result.append(" return ");
        }
        result.append("this.realBean." + method.getName());
        result.append(generateMethodInvokeParameters(method));
        result.append("}catch(Throwable e){");
        result.append("    this.buriedPoint.handleException(e);");
        result.append("    throw e;");
        result.append("}finally{");
        result.append(generateAfterTracing());
        result.append("}}");
        return result.toString();
    }

    private String generateBeforeSendParamter(Object bean, Method method) {
        StringBuilder builder = new StringBuilder("(" + Identification.class.getName() + ".newBuilder().viewPoint(\""
                + bean.getClass().getName() + "." + method.getName());
        builder.append("(");
        for (Class<?> param : method.getParameterTypes()) {
            builder.append(param.getSimpleName() + ",");
        }
        if (method.getGenericParameterTypes().length > 0) {
            builder = builder.delete(builder.length() - 1, builder.length());
        }
        builder.append(")");
        builder.append("\").spanType('M').build());");
        return builder.toString();
    }

    private String generateMethodInvokeParameters(Method method) {
        StringBuilder result = new StringBuilder();
        int index;
        result.append("(");
        index = 0;
        for (Class<?> parameter : method.getParameterTypes()) {
            result.append(parameter.getSimpleName().toLowerCase() + "$" + (index++) + ",");
        }
        if (method.getParameterTypes().length > 0) {
            result = result.delete(result.length() - 1, result.length());
        }
        result.append(");");
        return result.toString();
    }

    private String generateAfterTracing() {
        return " this.buriedPoint.afterSend();";
    }

    private String generateException(Method method) {
        StringBuilder resultB = new StringBuilder(" throws ");
        for (Class<?> exceptionClasses : method.getExceptionTypes()) {
            resultB.append(exceptionClasses.getName() + ",");
        }
        if (method.getExceptionTypes().length > 0) {
            resultB.delete(resultB.length() - 1, resultB.length());
        } else {
            resultB = new StringBuilder();
        }
        return resultB.toString();
    }

    private String generateMethodParameter(Method method) {
        StringBuilder result = new StringBuilder();
        result.append("(");
        int index = 0;
        for (Class<?> param : method.getParameterTypes()) {
            result.append(param.getName() + " " + param.getSimpleName().
                    toLowerCase() + "$" + (index++) + ",");
        }
        if (method.getGenericParameterTypes().length > 0) {
            result = result.delete(result.length() - 1, result.length());
        }
        result.append(")");
        return result.toString();
    }

    private String generateMethodHead(Method method) {
        return "public " + method.getReturnType().getName() + " " + method.getName();
    }

    private CtClass createProxyClass(Object bean, ClassPool mPool) {
        return mPool.makeClass(bean.getClass().getName() + "$EnhanceBySWTracing$" + ThreadLocalRandom.current().nextInt(100));
    }

    private void inheritanceAllInterfaces(Object bean, ClassPool mPool, CtClass mCtc) throws NotFoundException {
        for (Class<?> classes : bean.getClass().getInterfaces()) {
            mCtc.addInterface(mPool.get(classes.getClass().getName()));
        }
    }
}