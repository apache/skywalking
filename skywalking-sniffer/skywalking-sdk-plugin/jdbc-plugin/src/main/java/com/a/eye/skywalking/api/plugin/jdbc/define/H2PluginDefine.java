package com.a.eye.skywalking.api.plugin.jdbc.define;

public class H2PluginDefine extends AbstractDatabasePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "org.h2.Driver";
    }
}
