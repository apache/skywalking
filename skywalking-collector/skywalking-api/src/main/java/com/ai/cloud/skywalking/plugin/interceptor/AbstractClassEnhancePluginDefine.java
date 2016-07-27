package com.ai.cloud.skywalking.plugin.interceptor;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.IPlugin;
import com.ai.cloud.skywalking.plugin.PluginException;
import com.ai.cloud.skywalking.protocol.util.StringUtil;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.pool.TypePool.Resolution;

import static com.ai.cloud.skywalking.plugin.PluginBootstrap.CLASS_TYPE_POOL;

public abstract class AbstractClassEnhancePluginDefine implements IPlugin {
    private static Logger logger = LogManager.getLogger(AbstractClassEnhancePluginDefine.class);

    @Override
    public void define() throws PluginException {
        String interceptorDefineClassName = this.getClass().getName();

        String enhanceOriginClassName = enhanceClassName();
        if (StringUtil.isEmpty(enhanceOriginClassName)) {
            logger.warn("classname of being intercepted is not defined by {}.",
                    interceptorDefineClassName);
            return;
        }

        logger.debug("prepare to enhance class {} by {}.",
                enhanceOriginClassName, interceptorDefineClassName);

        Resolution resolution = CLASS_TYPE_POOL.describe(enhanceOriginClassName);
        if (!resolution.isResolved()) {
            logger.warn("class {} can't be resolved, enhance by {} failue.",
                    enhanceOriginClassName, interceptorDefineClassName);
            return;
        }

        /**
         * find witness classes for enhance class
         */
        String[] witnessClasses = witnessClasses();
        if(witnessClasses != null) {
            for (String witnessClass : witnessClasses) {
                Resolution witnessClassResolution = CLASS_TYPE_POOL.describe(witnessClass);
                if (!witnessClassResolution.isResolved()) {
                    logger.warn("enhance class {} by plugin {} is not working. Because witness class {} is not existed.", enhanceOriginClassName, interceptorDefineClassName, witnessClass);
                    return;
                }
            }
        }

        /**
         * find origin class source code for interceptor
         */
        DynamicType.Builder<?> newClassBuilder = new ByteBuddy()
                .rebase(resolution.resolve(),
                        ClassFileLocator.ForClassLoader.ofClassPath());

        newClassBuilder = this.enhance(enhanceOriginClassName, newClassBuilder);

        /**
         * naming class as origin class name, make and load class to
         * classloader.
         */
        newClassBuilder
                .name(enhanceOriginClassName)
                .make()
                .load(ClassLoader.getSystemClassLoader(),
                        ClassLoadingStrategy.Default.INJECTION).getLoaded();

        logger.debug("enhance class {} by {} completely.",
                enhanceOriginClassName, interceptorDefineClassName);
    }

    protected abstract DynamicType.Builder<?> enhance(String enhanceOriginClassName, DynamicType.Builder<?> newClassBuilder) throws PluginException;

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
    protected String[] witnessClasses(){
        return new String[]{};
    }
}
