package org.skywalking.apm.collector.core.module;

import java.util.Map;

/**
 * @author pengys5
 */
public abstract class ModuleConfigContainer implements ModuleInstaller {

    private Map<String, Map> moduleConfig;
    private Map<String, ModuleDefine> moduleDefineMap;

    @Override
    public final void injectConfiguration(Map<String, Map> moduleConfig, Map<String, ModuleDefine> moduleDefineMap) {
        this.moduleConfig = moduleConfig;
        this.moduleDefineMap = moduleDefineMap;
    }

    public final Map<String, Map> getModuleConfig() {
        return moduleConfig;
    }

    public final Map<String, ModuleDefine> getModuleDefineMap() {
        return moduleDefineMap;
    }
}
