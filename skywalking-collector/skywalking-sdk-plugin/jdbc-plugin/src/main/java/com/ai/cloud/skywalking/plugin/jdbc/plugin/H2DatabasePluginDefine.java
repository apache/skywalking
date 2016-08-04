package com.ai.cloud.skywalking.plugin.jdbc.plugin;

import com.ai.cloud.skywalking.plugin.jdbc.define.AbstractDatabasePluginDefine;

public class H2DatabasePluginDefine extends AbstractDatabasePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "org.h2.Driver";
    }
}
