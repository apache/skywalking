package com.ai.cloud.skywalking.plugin;

import java.net.URL;
import java.util.Enumeration;
import java.util.List;

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
		List<URL> resources = resolver.getResources();

		if (resources == null || resources.size() == 0) {
			logger.info("no plugin files (skywalking-plugin.properties) found, continue to start application.");
			return;
		}

		for (URL pluginUrl : resources) {
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
