package org.skywalking.apm.collector.core.framework;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.module.ModuleConfigLoader;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleDefineLoader;
import org.skywalking.apm.collector.core.module.ModuleGroup;
import org.skywalking.apm.collector.core.module.ModuleInstallerAdapter;
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

        ModuleDefineLoader defineLoader = new ModuleDefineLoader();
        Map<String, Map<String, ModuleDefine>> moduleDefineMap = defineLoader.load();

        ModuleInstallerAdapter moduleInstallerAdapter = new ModuleInstallerAdapter(ModuleGroup.Cluster);
        moduleInstallerAdapter.install(configuration.get(ModuleGroup.Cluster.name().toLowerCase()), moduleDefineMap.get(ModuleGroup.Cluster.name().toLowerCase()));

        ModuleGroup[] moduleGroups = ModuleGroup.values();
        for (ModuleGroup moduleGroup : moduleGroups) {
            if (!ModuleGroup.Cluster.equals(moduleGroup)) {
                moduleInstallerAdapter = new ModuleInstallerAdapter(moduleGroup);
                logger.info("module group {}, configuration {}", moduleGroup.name().toLowerCase(), configuration.get(moduleGroup.name().toLowerCase()));
                moduleInstallerAdapter.install(configuration.get(moduleGroup.name().toLowerCase()), moduleDefineMap.get(moduleGroup.name().toLowerCase()));
            }
        }
    }
}
