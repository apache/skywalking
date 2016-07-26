package com.ai.cloud.skywalking.plugin.interceptor;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.IPlugin;
import com.ai.cloud.skywalking.plugin.exception.EnhanceClassEmptyException;
import com.ai.cloud.skywalking.plugin.exception.EnhanceClassNotFoundException;
import com.ai.cloud.skywalking.plugin.exception.PluginException;
import com.ai.cloud.skywalking.plugin.exception.WitnessClassesCannotFound;
import com.ai.cloud.skywalking.protocol.util.StringUtil;
import javassist.ClassPool;
import javassist.CtClass;

import static com.ai.cloud.skywalking.plugin.PluginBootstrap.CLASS_TYPE_POOL;

public abstract class AbstractClassEnhancePluginDefine implements IPlugin {
    private static Logger logger = LogManager.getLogger(AbstractClassEnhancePluginDefine.class);

    @Override
    public byte[] define() throws PluginException {
        String interceptorDefineClassName = this.getClass().getName();

        String enhanceOriginClassName = enhanceClassName();
        if (StringUtil.isEmpty(enhanceOriginClassName)) {
            logger.warn("classname of being intercepted is not defined by {}.", interceptorDefineClassName);
            throw new EnhanceClassEmptyException("class name of being is not deined by " + interceptorDefineClassName);
        }

        logger.debug("prepare to enhance class {} by {}.", enhanceOriginClassName, interceptorDefineClassName);

        CtClass ctClass = findEnhanceClasses(interceptorDefineClassName, enhanceOriginClassName);

        if (ctClass == null) {
            logger.warn("class {} can't be resolved, enhance by {} failue.", enhanceOriginClassName, interceptorDefineClassName);
            throw new EnhanceClassNotFoundException("class " + enhanceOriginClassName + " can't be resolved, enhance by " + interceptorDefineClassName + " failue.");
        }

        /**
         * find witness classes for enhance class
         */
        String[] witnessClasses = witnessClasses();
        if (witnessClasses != null) {
            for (String witnessClassName : witnessClasses) {
                try {
                    CtClass witnessClass = CLASS_TYPE_POOL.get(witnessClassName);
                    if (witnessClass != null) {
                        logger.warn("enhance class {} by plugin {} is not working. Because witness class {} is not existed.", enhanceOriginClassName, interceptorDefineClassName,
                                witnessClass);
                        throw new WitnessClassesCannotFound(
                                "enhance class " + enhanceOriginClassName + " by plugin " + interceptorDefineClassName + " is not working. Because witness class " + witnessClass
                                        + " is not existed.");
                    }
                } catch (Exception e) {

                }
            }
        }

        return enhance(ctClass);
    }

    private CtClass findEnhanceClasses(String interceptorDefineClassName, String enhanceOriginClassName) throws EnhanceClassNotFoundException {
        try {
            ClassPool classPool = ClassPool.getDefault();
            return classPool.get(enhanceOriginClassName);
        } catch (Exception e) {
            throw new EnhanceClassNotFoundException("class " + enhanceOriginClassName + " can't be resolved, enhance by " + interceptorDefineClassName + " failue.");
        }
    }

    protected abstract byte[] enhance(CtClass ctClass) throws PluginException;

    /**
     * 返回要被增强的类，应当返回类全名
     *
     * @return
     */
    protected abstract String enhanceClassName();

    /**
     * 返回一个类名的列表
     * 如果列表中的类在JVM中存在,则enhance可以会尝试生效
     *
     * @return
     */
    protected String[] witnessClasses() {
        return new String[] {};
    }
}
