package com.ai.cloud.skywalking.plugin;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import net.bytebuddy.pool.TypePool;

import java.net.URL;
import java.util.List;

public class PluginBootstrap {
    private static Logger logger = LogManager.getLogger(PluginBootstrap.class);

    public static TypePool CLASS_TYPE_POOL = null;

    public void start() {
        if (!AuthDesc.isAuth()) {
            return;
        }

        CLASS_TYPE_POOL = TypePool.Default.ofClassPath();

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
                logger.error("plugin [{}] init failure.", new Object[]{pluginUrl}, t);
            }
        }

        List<String> pluginClassList = PluginCfg.CFG
                .getPluginClassList();

        for (String pluginClassName : pluginClassList) {
            try {
                logger.debug("prepare to enhance class by plugin {}.",
                        pluginClassName);
                IPlugin plugin = (IPlugin) Class.forName(
                        pluginClassName).newInstance();
                plugin.define();
            } catch (Throwable t) {
                logger.error("prepare to enhance class by plugin [{}] failure.",
                        new Object[]{pluginClassName}, t);
            }
        }

    }
}
