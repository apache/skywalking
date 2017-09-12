package org.skywalking.apm.collector.boot;

import java.util.Map;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.CollectorException;
import org.skywalking.apm.collector.core.framework.Starter;
import org.skywalking.apm.collector.core.module.ModuleConfigLoader;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleDefineLoader;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleGroupDefineLoader;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class CollectorStarter implements Starter {

    private final Logger logger = LoggerFactory.getLogger(CollectorStarter.class);
    private Map<String, ModuleGroupDefine> moduleGroupDefineMap;

    @Override public void start() throws CollectorException {
        ModuleConfigLoader configLoader = new ModuleConfigLoader();
        Map<String, Map> configuration = configLoader.load();

        ModuleGroupDefineLoader groupDefineLoader = new ModuleGroupDefineLoader();
        moduleGroupDefineMap = groupDefineLoader.load();

        ModuleDefineLoader defineLoader = new ModuleDefineLoader();
        Map<String, Map<String, ModuleDefine>> moduleDefineMap = defineLoader.load();

        ServerHolder serverHolder = new ServerHolder();
        for (ModuleGroupDefine moduleGroupDefine : moduleGroupDefineMap.values()) {
            moduleGroupDefine.moduleInstaller().injectConfiguration(configuration.get(moduleGroupDefine.name()), moduleDefineMap.get(moduleGroupDefine.name()));
            moduleGroupDefine.moduleInstaller().injectServerHolder(serverHolder);
            moduleGroupDefine.moduleInstaller().preInstall();
        }

        moduleGroupDefineMap.get(ClusterModuleGroupDefine.GROUP_NAME).moduleInstaller().install();

        for (ModuleGroupDefine moduleGroupDefine : moduleGroupDefineMap.values()) {
            if (!(moduleGroupDefine instanceof ClusterModuleGroupDefine)) {
                moduleGroupDefine.moduleInstaller().install();
            }
        }

        serverHolder.getServers().forEach(server -> {
            try {
                server.start();
            } catch (ServerException e) {
                logger.error(e.getMessage(), e);
            }
        });

        dependenceAfterInstall();
    }

    private void dependenceAfterInstall() throws CollectorException {
        for (ModuleGroupDefine moduleGroupDefine : moduleGroupDefineMap.values()) {
            moduleInstall(moduleGroupDefine);
        }
    }

    private void moduleInstall(ModuleGroupDefine moduleGroupDefine) throws CollectorException {
        if (CollectionUtils.isNotEmpty(moduleGroupDefine.moduleInstaller().dependenceModules())) {
            for (String groupName : moduleGroupDefine.moduleInstaller().dependenceModules()) {
                moduleInstall(moduleGroupDefineMap.get(groupName));
            }
            logger.info("after install module group: {}", moduleGroupDefine.name());
            moduleGroupDefine.moduleInstaller().afterInstall();
        } else {
            logger.info("after install module group: {}", moduleGroupDefine.name());
            moduleGroupDefine.moduleInstaller().afterInstall();
        }
    }
}
