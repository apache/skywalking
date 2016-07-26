package com.ai.cloud.skywalking.plugin;


import com.ai.cloud.skywalking.plugin.exception.PluginException;

public interface IPlugin {
    byte[] define() throws PluginException;
}
