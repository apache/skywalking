package com.ai.cloud.skywalking.plugin;

import java.net.URL;
import java.util.Enumeration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.plugin.interceptor.EnhanceClazz4Interceptor;

public class PluginBootstrap {
	private static Logger logger = LogManager.getLogger(PluginBootstrap.class);

	public void start() {
		if (!AuthDesc.isAuth()) {
			return;
		}

		PluginResourcesResolver resolver = new PluginResourcesResolver();
		Enumeration<URL> resources = resolver.getResources();

		if (resources == null || !resources.hasMoreElements()) {
			logger.info("no plugin files (skywalking-plugin.properties) found, continue to start application.");
			return;
		}

		while (resources.hasMoreElements()) {
			URL pluginUrl = resources.nextElement();
			try {
				PluginCfg.CFG.load(pluginUrl.openStream());
			} catch (Throwable t) {
				logger.error("plugin [{}] init failure.", pluginUrl, t);
			}
		}
		
		EnhanceClazz4Interceptor enhanceClazz4Interceptor = new EnhanceClazz4Interceptor();
		enhanceClazz4Interceptor.enhance();
	}
}
