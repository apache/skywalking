package org.skywalking.apm.collector.core.cluster;

import java.util.Iterator;
import java.util.Map;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterModuleInstaller implements ModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(ClusterModuleInstaller.class);

    @Override public void install(Map<String, Map> moduleConfig,
        Map<String, ModuleDefine> moduleDefineMap) throws DefineException {
        logger.info("beginning cluster module install");
        if (CollectionUtils.isEmpty(moduleConfig)) {
            logger.info("could not configure cluster module, use the default");
            Iterator<Map.Entry<String, ModuleDefine>> moduleDefineEntry = moduleDefineMap.entrySet().iterator();
            while (moduleDefineEntry.hasNext()) {
                ModuleDefine moduleDefine = moduleDefineEntry.next().getValue();
                if (moduleDefine.defaultModule()) {
                    logger.info("module {} initialize", moduleDefine.getClass().getName());
                    moduleDefine.initialize(null);
                }
            }
        } else {
            Map.Entry<String, Map> clusterConfigEntry = moduleConfig.entrySet().iterator().next();
            ModuleDefine moduleDefine = moduleDefineMap.get(clusterConfigEntry.getKey());
            moduleDefine.initialize(clusterConfigEntry.getValue());
        }
    }
}
