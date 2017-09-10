package org.skywalking.apm.collector.core.module;

import java.util.Map;
import org.skywalking.apm.collector.core.CollectorException;

/**
 * @author pengys5
 */
public abstract class CommonModuleInstaller implements ModuleInstaller {

    private boolean isInstalled = false;
    private Map<String, Map> moduleConfig;
    private Map<String, ModuleDefine> moduleDefineMap;

    @Override
    public final void injectConfiguration(Map<String, Map> moduleConfig, Map<String, ModuleDefine> moduleDefineMap) {
        this.moduleConfig = moduleConfig;
        this.moduleDefineMap = moduleDefineMap;
    }

    protected final Map<String, Map> getModuleConfig() {
        return moduleConfig;
    }

    protected final Map<String, ModuleDefine> getModuleDefineMap() {
        return moduleDefineMap;
    }

    public abstract void onAfterInstall() throws CollectorException;

    @Override public final void afterInstall() throws CollectorException {
        if (!isInstalled) {
            onAfterInstall();
        }
        isInstalled = true;
    }
}
