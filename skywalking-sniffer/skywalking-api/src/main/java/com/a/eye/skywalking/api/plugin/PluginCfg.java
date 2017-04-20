package com.a.eye.skywalking.api.plugin;

import com.a.eye.skywalking.api.util.StringUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public enum PluginCfg {
    INSTANCE;

    private List<String> pluginClassList = new ArrayList<String>();

    void load(InputStream input) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String pluginDefineClassName = null;
            while ((pluginDefineClassName = reader.readLine()) != null) {
                if (!StringUtil.isEmpty(pluginDefineClassName)) {
                    pluginClassList.add(pluginDefineClassName.trim());
                }
            }
        } finally {
            input.close();
        }
    }

    public List<String> getPluginClassList() {
        return pluginClassList;
    }
}
