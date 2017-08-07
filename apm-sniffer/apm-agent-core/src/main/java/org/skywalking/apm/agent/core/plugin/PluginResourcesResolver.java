package org.skywalking.apm.agent.core.plugin;

import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Use the current classloader to read all plugin define file.
 * The file must be named 'skywalking-plugin.def'
 *
 * @author wusheng
 */
public class PluginResourcesResolver {
    private static final ILog logger = LogManager.getLogger(PluginResourcesResolver.class);

    public List<URL> getResources() {
        List<URL> cfgUrlPaths = new ArrayList<URL>();
        Enumeration<URL> urls;
        try {
            urls = getDefaultClassLoader().getResources("skywalking-plugin.def");

            while (urls.hasMoreElements()) {
                URL pluginUrl = urls.nextElement();
                cfgUrlPaths.add(pluginUrl);
                logger.info("findOrPrepare4Register skywalking plugin define in {}", pluginUrl);
            }

            return cfgUrlPaths;
        } catch (IOException e) {
            logger.error("read resources failure.", e);
        }
        return null;
    }

    /**
     * Get the classloader.
     * First get current thread's classloader,
     * if fail, get {@link PluginResourcesResolver}'s classloader.
     *
     * @return the classloader to findOrPrepare4Register plugin definitions.
     */
    private ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ex) {
            // Cannot access thread context ClassLoader - falling back to system class loader...
        }
        if (cl == null) {
            // No thread context class loader -> use class loader of this class.
            cl = PluginResourcesResolver.class.getClassLoader();
        }
        return cl;
    }

}
