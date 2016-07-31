package com.ai.cloud.skywalking.plugin.boot;

import com.ai.cloud.skywalking.plugin.IPlugin;

public interface IBootPluginDefine extends IPlugin {

    void boot() throws BootException;

}
