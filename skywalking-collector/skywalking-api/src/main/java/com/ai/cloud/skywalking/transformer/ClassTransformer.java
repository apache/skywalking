package com.ai.cloud.skywalking.transformer;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

public class ClassTransformer implements ClassFileTransformer {

    private static Logger logger = LogManager.getLogger(ClassTransformer.class);

    private String interceptorPackage;

    public ClassTransformer(String interceptorPackage){
        this.interceptorPackage = interceptorPackage;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

        if (interceptorPackage == null || interceptorPackage.length() == 0) {
            return classfileBuffer;
        }

        if (!className.replaceAll("/", ".").startsWith(interceptorPackage)) {
            return classfileBuffer;
        }

        try {
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctClass = classPool.get(className.replaceAll("/", "."));

            if (ctClass.isInterface()) {
                return classfileBuffer;
            }

            CtMethod[] ctMethod = ctClass.getDeclaredMethods();
            for (CtMethod method : ctMethod) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }

                String methodName = method.getName();
                method.setName(methodName + "_skywalking_enhance");

                CtMethod newMethod =
                        new CtMethod(method.getReturnType(), methodName, method.getParameterTypes(),
                                method.getDeclaringClass());
                newMethod.setBody("{" +
                        MethodInterceptor.class.getName() + ".before($class,,$sig,$args,$0,\"" + methodName + "\");"
                        + "return " + methodName + "_skywalking_enhance($$);}");
                newMethod.addCatch("{ " + MethodInterceptor.class.getName() + ".handleException(e); throw e;}", classPool.get("java.lang.Throwable"), "e");
                newMethod.insertAfter("{" + MethodInterceptor.class.getName() + ".after($class,$type,$_);}", true);

                ctClass.addMethod(newMethod);
            }
            return ctClass.toBytecode();
        } catch (Exception e) {
            logger.error("Failed to transform class " + className, e);
            return classfileBuffer;
        }

    }
}
