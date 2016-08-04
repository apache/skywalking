package com.ai.cloud.skywalking.plugin;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.protocol.util.StringUtil;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.pool.TypePool.Resolution;

import static com.ai.cloud.skywalking.plugin.PluginBootstrap.CLASS_TYPE_POOL;

public abstract class AbstractClassEnhancePluginDefine{
    private static Logger logger = LogManager.getLogger(AbstractClassEnhancePluginDefine.class);

    public DynamicType.Builder<?> define(String transformClassName, DynamicType.Builder<?> builder) throws PluginException {
        String interceptorDefineClassName = this.getClass().getName();

        if (StringUtil.isEmpty(transformClassName)) {
            logger.warn("classname of being intercepted is not defined by {}.", interceptorDefineClassName);
            return builder;
        }

        logger.debug("prepare to enhance class {} by {}.", transformClassName, interceptorDefineClassName);

        /**
         * find witness classes for enhance class
         */
        String[] witnessClasses = witnessClasses();
        if (witnessClasses != null) {
            for (String witnessClass : witnessClasses) {
                Resolution witnessClassResolution = CLASS_TYPE_POOL.describe(witnessClass);
                if (!witnessClassResolution.isResolved()) {
                    logger.warn("enhance class {} by plugin {} is not working. Because witness class {} is not existed.", transformClassName, interceptorDefineClassName,
                            witnessClass);
                    return builder;
                }
            }
        }

        /**
         * find origin class source code for interceptor
         */
        DynamicType.Builder<?> newClassBuilder = this.enhance(transformClassName, builder);


        logger.debug("enhance class {} by {} completely.", transformClassName, interceptorDefineClassName);

        return newClassBuilder;
    }

    protected abstract DynamicType.Builder<?> enhance(String enhanceOriginClassName, DynamicType.Builder<?> newClassBuilder) throws PluginException;

    /**
     * 返回要被增强的类，应当返回类全名或前匹配(返回*号结尾)
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
