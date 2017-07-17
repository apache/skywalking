package org.skywalking.apm.collector.core.module;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterModuleInstaller;
import org.skywalking.apm.collector.core.framework.DefineException;

/**
 * @author pengys5
 */
public class ModuleInstallerAdapter implements ModuleInstaller {

    private ModuleInstaller moduleInstaller;

    public ModuleInstallerAdapter(ModuleGroup moduleGroup) {
        if (ModuleGroup.Cluster.equals(moduleGroup)) {
            moduleInstaller = new ClusterModuleInstaller();
        }
    }

    @Override public void install(Map<String, Map> moduleConfig,
        Map<String, ModuleDefine> moduleDefineMap) throws DefineException, ClientException {
        moduleInstaller.install(moduleConfig, moduleDefineMap);
    }
}
