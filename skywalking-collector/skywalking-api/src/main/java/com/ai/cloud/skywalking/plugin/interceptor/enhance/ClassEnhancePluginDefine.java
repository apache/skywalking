package com.ai.cloud.skywalking.plugin.interceptor.enhance;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.exception.PluginException;
import com.ai.cloud.skywalking.plugin.interceptor.AbstractClassEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import javassist.*;

import java.lang.reflect.Modifier;

public abstract class ClassEnhancePluginDefine extends AbstractClassEnhancePluginDefine {
    private static Logger logger = LogManager.getLogger(ClassEnhancePluginDefine.class);

    public static final String contextAttrName = "_$EnhancedClassInstanceContext";

    public byte[] enhance(CtClass ctClass) throws PluginException {
        try {
            CtMethod[] ctMethod = ctClass.getDeclaredMethods();
            for (CtMethod method : ctMethod) {
                if (Modifier.isStatic(method.getModifiers())) {
                    this.enhanceClass(ctClass, method);
                } else {
                    this.enhanceInstance(ctClass, method);
                }
            }

            return ctClass.toBytecode();
        } catch (Exception e) {
            throw new PluginException("Can not compile the class", e);
        }
    }

    private void enhanceClass(CtClass ctClass, CtMethod method) throws CannotCompileException, NotFoundException {
        boolean isMatch = false;
        for (MethodMatcher methodMatcher : getStaticMethodsMatchers()) {
            if (methodMatcher.match(method)) {
                isMatch = true;
                break;
            }
        }

        if (isMatch) {
            // 修改方法名,
            String methodName = method.getName();
            String newMethodName = methodName + "_$SkywalkingEnhance";
            method.setName(newMethodName);

            CtMethod newMethod = new CtMethod(method.getReturnType(), methodName, method.getParameterTypes(), method.getDeclaringClass());
            newMethod.setBody(
                    "{ new " + ClassStaticMethodsInterceptor.class.getName() + "(new " + getStaticMethodsInterceptor().getClass().getName() + ").intercept($class,$args,\""
                            + methodName + "\"," + OriginCallCodeGenerator.generateStaticMethodOriginCallCode(ctClass.getName(), newMethodName) + ");}");

            ctClass.addMethod(newMethod);
        }

    }

    private void enhanceInstance(CtClass ctClass, CtMethod method) throws CannotCompileException, NotFoundException {
        // 添加一个字段,并且带上get/set方法
        CtField ctField = CtField.make("{public " + EnhancedClassInstanceContext.class.getName() + " " + contextAttrName + ";}", ctClass);
        ctClass.addMethod(
                CtMethod.make("public " + EnhancedClassInstanceContext.class.getName() + " get" + contextAttrName + "(){ return this." + contextAttrName + ";}", ctClass));
        ctClass.addMethod(CtMethod.make(
                "public void set" + contextAttrName + "(" + EnhancedClassInstanceContext.class.getName() + " " + contextAttrName + "){this." + contextAttrName + "="
                        + contextAttrName + ";}", ctClass));


        // 初始化构造函数
        CtConstructor[] constructors = ctClass.getDeclaredConstructors();
        for (CtConstructor constructor : constructors) {
            constructor.insertAfter(" new " + ClassConstructorInterceptor.class.getName() + "(new " + getInstanceMethodsInterceptor().getClass().getName() + "()).intercept($0,$0."
                    + contextAttrName + ",$args);");
        }

        boolean isMatch = false;
        for (MethodMatcher methodMatcher : getInstanceMethodsMatchers()) {
            if (methodMatcher.match(method)) {
                isMatch = true;
                break;
            }
        }

        if (isMatch) {
            // 修改方法名,
            String methodName = method.getName();
            String newMethodName = methodName + "_$SkywalkingEnhance";
            method.setName(newMethodName);
            CtMethod newMethod = new CtMethod(method.getReturnType(), methodName, method.getParameterTypes(), method.getDeclaringClass());

            newMethod.setBody(
                    "{ new " + ClassInstanceMethodsInterceptor.class.getName() + "(new " + getInstanceMethodsInterceptor().getClass().getName() + "()).intercept($0,$args,\""
                            + methodName + "\"," + OriginCallCodeGenerator.generateInstanceMethodOriginCallCode("$0", methodName) + ",$0." + contextAttrName + ");}");

            ctClass.addMethod(newMethod);
        }
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
    protected abstract InstanceMethodsAroundInterceptor getInstanceMethodsInterceptor();

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
    protected abstract StaticMethodsAroundInterceptor getStaticMethodsInterceptor();
}
