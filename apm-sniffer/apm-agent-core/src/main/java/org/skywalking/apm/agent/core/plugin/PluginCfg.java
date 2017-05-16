package org.skywalking.apm.agent.core.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.agent.core.plugin.exception.IllegalPluginDefineException;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

public enum PluginCfg {
    INSTANCE;

    private static final ILog logger = LogManager.getLogger(PluginCfg.class);

    private List<PluginDefine> pluginClassList = new ArrayList<PluginDefine>();

    void load(InputStream input) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String pluginDefine = null;
            while ((pluginDefine = reader.readLine()) != null) {
                try {
                    PluginDefine plugin = PluginDefine.build(pluginDefine);
                    if (plugin.enable()) {
                        pluginClassList.add(plugin);
                    }
                } catch (IllegalPluginDefineException e) {
                    logger.error("Failed to format plugin define.", e);
                }
            }
        } finally {
            input.close();
        }
    }

    public List<PluginDefine> getPluginClassList() {
        return pluginClassList;
    }

}
