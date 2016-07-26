package com.ai.cloud.skywalking.plugin;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import javassist.ClassPool;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginBootstrap {
    private static Logger logger = LogManager.getLogger(PluginBootstrap.class);

    public static ClassPool CLASS_TYPE_POOL = null;

    public Map<String, ClassEnhancePluginDefine> loadPlugins() {
        if (!AuthDesc.isAuth()) {
            return null;
        }

        CLASS_TYPE_POOL = ClassPool.getDefault();

        PluginResourcesResolver resolver = new PluginResourcesResolver();
        List<URL> resources = resolver.getResources();

        if (resources == null || resources.size() == 0) {
            logger.info("no plugin files (skywalking-plugin.properties) found, continue to start application.");
            return new HashMap<String, ClassEnhancePluginDefine>();
        }

        for (URL pluginUrl : resources) {
            try {
                PluginCfg.CFG.load(pluginUrl.openStream());
            } catch (Throwable t) {
                logger.error("plugin [{}] init failure.", new Object[] {pluginUrl}, t);
            }
        }

        List<String> pluginClassList = PluginCfg.CFG.getPluginClassList();

        Map<String, ClassEnhancePluginDefine> pluginDefineMap = new HashMap<String, ClassEnhancePluginDefine>();
        for (String pluginClassName : pluginClassList) {
            try {
                logger.debug("prepare to enhance class by plugin {}.", pluginClassName);
                IPlugin plugin = (IPlugin) Class.forName(pluginClassName).newInstance();
                if (plugin instanceof ClassEnhancePluginDefine) {
                    pluginDefineMap.put(pluginClassName, (ClassEnhancePluginDefine) plugin);
                }
            } catch (Throwable t) {
                logger.error("prepare to enhance class by plugin [{}] failure.", new Object[] {pluginClassName}, t);
            }
        }

        return pluginDefineMap;
    }
}
