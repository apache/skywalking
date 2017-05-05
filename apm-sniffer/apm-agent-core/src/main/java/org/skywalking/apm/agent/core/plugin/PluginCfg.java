package org.skywalking.apm.agent.core.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.agent.core.conf.Config;

public enum PluginCfg {
    INSTANCE;

    private List<PluginDefine> pluginClassList = new ArrayList<PluginDefine>();

    void load(InputStream input) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String pluginDefine = null;
            while ((pluginDefine = reader.readLine()) != null) {
                PluginDefine plugin = PluginDefine.build(pluginDefine);
                if (!plugin.disabled(Config.Plugin.DISABLED_PLUGINS)) {
                    pluginClassList.add(plugin);
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
