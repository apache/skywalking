package org.skywalking.apm.collector.cluster;

import java.util.Iterator;
import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterModuleInstaller implements ModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(ClusterModuleInstaller.class);

    @Override public void install(Map<String, Map> moduleConfig,
        Map<String, ModuleDefine> moduleDefineMap, ServerHolder serverHolder) throws DefineException, ClientException {
        logger.info("beginning cluster module install");

        ClusterModuleContext context = new ClusterModuleContext(ClusterModuleGroupDefine.GROUP_NAME);
        CollectorContextHelper.INSTANCE.putContext(context);

        ModuleDefine moduleDefine = null;
        if (CollectionUtils.isEmpty(moduleConfig)) {
            logger.info("could not configure cluster module, use the default");
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
