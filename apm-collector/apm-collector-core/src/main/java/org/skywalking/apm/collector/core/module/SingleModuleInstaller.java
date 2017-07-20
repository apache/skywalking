package org.skywalking.apm.collector.core.module;

import java.util.Iterator;
import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public abstract class SingleModuleInstaller implements ModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(SingleModuleInstaller.class);

    protected void installSingle(Map<String, Map> moduleConfig,
        Map<String, ModuleDefine> moduleDefineMap, ServerHolder serverHolder) throws DefineException, ClientException {
        ModuleDefine moduleDefine = null;
        if (CollectionUtils.isEmpty(moduleConfig)) {
            logger.info("could not configure module, use the default");
            Iterator<Map.Entry<String, ModuleDefine>> moduleDefineEntry = moduleDefineMap.entrySet().iterator();
            while (moduleDefineEntry.hasNext()) {
                moduleDefine = moduleDefineEntry.next().getValue();
                if (moduleDefine.defaultModule()) {
                    logger.info("module {} initialize", moduleDefine.getClass().getName());
                    moduleDefine.initialize(null, serverHolder);
                    break;
                }
            }
        } else {
            Map.Entry<String, Map> clusterConfigEntry = moduleConfig.entrySet().iterator().next();
            moduleDefine = moduleDefineMap.get(clusterConfigEntry.getKey());
            moduleDefine.initialize(clusterConfigEntry.getValue(), serverHolder);
        }
    }
}
