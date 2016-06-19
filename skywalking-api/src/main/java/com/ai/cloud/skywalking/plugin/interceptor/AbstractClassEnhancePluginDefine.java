package com.ai.cloud.skywalking.plugin.interceptor;

import static com.ai.cloud.skywalking.plugin.PluginBootstrap.CLASS_TYPE_POOL;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.pool.TypePool.Resolution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.plugin.IPlugin;
import com.ai.cloud.skywalking.plugin.PluginException;
import com.ai.cloud.skywalking.util.StringUtil;

public abstract class AbstractClassEnhancePluginDefine implements IPlugin {
	private static Logger logger = LogManager.getLogger(AbstractClassEnhancePluginDefine.class);
	
	@Override
	public void define() throws PluginException {
		String interceptorDefineClassName = this.getClass().getName();
		
		String enhanceOriginClassName = getBeInterceptedClassName();
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
	protected abstract String getBeInterceptedClassName();
}
