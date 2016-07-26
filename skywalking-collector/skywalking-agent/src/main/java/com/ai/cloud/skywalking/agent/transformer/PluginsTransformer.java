package com.ai.cloud.skywalking.agent.transformer;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import javassist.ClassPool;
import javassist.CtClass;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Map;

public class PluginsTransformer implements ClassFileTransformer {

    private Logger logger = LogManager.getLogger(PluginsTransformer.class);

    private Map<String, ClassEnhancePluginDefine> pluginDefineMap;


    public PluginsTransformer(Map<String, ClassEnhancePluginDefine> pluginDefineMap) {
        this.pluginDefineMap = pluginDefineMap;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (pluginDefineMap.isEmpty()) {
            return classfileBuffer;
        }

        if (className.startsWith("com/ai/cloud/skywalking")){
            return classfileBuffer;
        }

        ClassEnhancePluginDefine pluginDefine = pluginDefineMap.get(className.replaceAll("/", "."));
        if (pluginDefine != null) {
            ClassPool classPool = ClassPool.getDefault();
            try {
                CtClass ctClass = classPool.get(className.replaceAll("/", "."));
                if (ctClass.isInterface()) {
                    return classfileBuffer;
                }

                pluginDefine.enhance(ctClass);

                return ctClass.toBytecode();
            } catch (Exception e) {
                logger.error("Failed to enhance class[" + className + "]", e);
                return classfileBuffer;
            }
        }

        return classfileBuffer;
    }
}
