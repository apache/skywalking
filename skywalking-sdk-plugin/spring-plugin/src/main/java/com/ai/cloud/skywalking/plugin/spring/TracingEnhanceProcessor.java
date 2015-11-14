package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.buriedpoint.LocalBuriedPointSender;
import com.ai.cloud.skywalking.model.Identification;
import javassist.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TracingEnhanceProcessor implements BeanPostProcessor {

    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        List<Method> methods = new ArrayList<Method>();
        for (Method method : bean.getClass().getMethods()) {
            if (method.isAnnotationPresent(Tracing.class)) {
                methods.add(method);
            }
        }
        if (methods.size() > 0) {
            try {
                ClassPool mPool = new ClassPool(true);
                //创建代理类
                CtClass mCtc = createProxyClass(bean, mPool);
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
                    // TODO 注解
                    // TODO 参数注解
                    mCtc.addMethod(CtMethod.make(result.toString(), mCtc));
                }
                mCtc.addConstructor(CtNewConstructor.make("public " + mCtc.getSimpleName() + "(" + LocalBuriedPointSender.class
                        .getName() + " buriedPoint, " + bean.getClass().getName() + " realBean){" +
                        "this.buriedPoint = buriedPoint;this.realBean = realBean;}", mCtc));
                mCtc.addConstructor(CtNewConstructor.defaultConstructor(mCtc));
                Class<?> classes = mCtc.toClass();
                Constructor<?> constructor = classes.getConstructor(LocalBuriedPointSender.class, bean.getClass());
                return constructor.newInstance(new LocalBuriedPointSender(), bean);
            } catch (CannotCompileException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }

        return bean;
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
        return mPool.makeClass(bean.getClass().getSimpleName() + "$EnhanceBySWTracing$" + ThreadLocalRandom.current().nextInt(100));
    }

    private void inheritanceAllInterfaces(Object bean, ClassPool mPool, CtClass mCtc) throws NotFoundException {
        for (Class<?> classes : bean.getClass().getInterfaces()) {
            mCtc.addInterface(mPool.get(classes.getClass().getName()));
        }
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
}