package com.ai.cloud.skywalking.plugin;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PluginResourcesResolver {
	private static Logger logger = LogManager.getLogger(PluginResourcesResolver.class);
	
	public List<URL> getResources(){
		List<URL> cfgUrlPaths = new ArrayList<URL>();
		Enumeration<URL> urls;
		try {
			urls = getDefaultClassLoader().getResources("skywalking-plugin.def");
			
			if(!urls.hasMoreElements()){
				logger.info("no plugin files (skywalking-plugin.properties) found");
			}
			
			while(urls.hasMoreElements()){
				URL pluginUrl = urls.nextElement();
				cfgUrlPaths.add(pluginUrl);
				logger.info("find skywalking plugin define in {}", pluginUrl);
			}
			
			return cfgUrlPaths;
		} catch (IOException e) {
			logger.error("read resources failure.", e);
		}
		return null;
	}
	
	private ClassLoader getDefaultClassLoader() {
		ClassLoader cl = null;
		try {
			cl = Thread.currentThread().getContextClassLoader();
		}
		catch (Throwable ex) {
			// Cannot access thread context ClassLoader - falling back to system class loader...
		}
		if (cl == null) {
			// No thread context class loader -> use class loader of this class.
			cl = PluginResourcesResolver.class.getClassLoader();
		}
		return cl;
	}
	
}
