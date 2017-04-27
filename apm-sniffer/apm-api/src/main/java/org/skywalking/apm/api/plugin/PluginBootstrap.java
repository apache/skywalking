package org.skywalking.apm.api.plugin;

import net.bytebuddy.pool.TypePool;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Plugins finder.
 * Use {@link PluginResourcesResolver} to find all plugins,
 * and ask {@link PluginCfg} to load all plugin definitions.
 *
 * @author wusheng
 */
public class PluginBootstrap {
    private static final ILog logger = LogManager.getLogger(PluginBootstrap.class);

    /**
     * load all plugins.
     *
     * @return plugin definition list.
     */
    public List<AbstractClassEnhancePluginDefine> loadPlugins() {
        TypePool classTypePool = TypePool.Default.ofClassPath();

        PluginResourcesResolver resolver = new PluginResourcesResolver();
        List<URL> resources = resolver.getResources();

        if (resources == null || resources.size() == 0) {
            logger.info("no plugin files (skywalking-plugin.properties) found, continue to start application.");
            return new ArrayList<AbstractClassEnhancePluginDefine>();
        }

        for (URL pluginUrl : resources) {
            try {
                PluginCfg.INSTANCE.load(pluginUrl.openStream());
            } catch (Throwable t) {
                logger.error(t, "plugin [{}] init failure.", pluginUrl);
            }
        }

        List<String> pluginClassList = PluginCfg.INSTANCE.getPluginClassList();

        List<AbstractClassEnhancePluginDefine> plugins = new ArrayList<AbstractClassEnhancePluginDefine>();
        for (String pluginClassName : pluginClassList) {
            try {
                logger.debug("loading plugin class {}.", pluginClassName);
                AbstractClassEnhancePluginDefine plugin =
                    (AbstractClassEnhancePluginDefine) Class.forName(pluginClassName).newInstance();
                plugin.setClassTypePool(classTypePool);
                plugins.add(plugin);
            } catch (Throwable t) {
                logger.error(t, "loade plugin [{}] failure.", pluginClassName);
            }
        }

        return plugins;

    }

}
