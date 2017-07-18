package org.skywalking.apm.collector.boot;

import java.util.Map;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Starter;
import org.skywalking.apm.collector.core.module.ModuleConfigLoader;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleDefineLoader;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleGroupDefineLoader;
import org.skywalking.apm.collector.core.remote.SerializedDefineLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class CollectorStarter implements Starter {

    private final Logger logger = LoggerFactory.getLogger(CollectorStarter.class);

    @Override public void start() throws ConfigException, DefineException, ClientException {
        ModuleConfigLoader configLoader = new ModuleConfigLoader();
        Map<String, Map> configuration = configLoader.load();

        SerializedDefineLoader serializedDefineLoader = new SerializedDefineLoader();
        serializedDefineLoader.load();

        ModuleGroupDefineLoader groupDefineLoader = new ModuleGroupDefineLoader();
        Map<String, ModuleGroupDefine> moduleGroupDefineMap = groupDefineLoader.load();

        ModuleDefineLoader defineLoader = new ModuleDefineLoader();
        Map<String, Map<String, ModuleDefine>> moduleDefineMap = defineLoader.load();

        moduleGroupDefineMap.get(ClusterModuleGroupDefine.GROUP_NAME).moduleInstaller().install(configuration.get(ClusterModuleGroupDefine.GROUP_NAME), moduleDefineMap.get(ClusterModuleGroupDefine.GROUP_NAME));
        moduleGroupDefineMap.remove(ClusterModuleGroupDefine.GROUP_NAME);

        for (ModuleGroupDefine moduleGroupDefine : moduleGroupDefineMap.values()) {
            moduleGroupDefine.moduleInstaller().install(configuration.get(moduleGroupDefine.name()), moduleDefineMap.get(moduleGroupDefine.name()));
        }
    }
}
