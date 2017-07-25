package org.skywalking.apm.collector.stream;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.LocalAsyncWorkerProviderDefineLoader;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.RemoteWorkerProviderDefineLoader;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefineLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class StreamModuleInstaller implements ModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(StreamModuleInstaller.class);

    @Override public void install(Map<String, Map> moduleConfig, Map<String, ModuleDefine> moduleDefineMap,
        ServerHolder serverHolder) throws DefineException, ClientException {
        logger.info("beginning stream module install");
        StreamModuleContext context = new StreamModuleContext(StreamModuleGroupDefine.GROUP_NAME);
        CollectorContextHelper.INSTANCE.putContext(context);

        DataDefineLoader dataDefineLoader = new DataDefineLoader();
        Map<Integer, DataDefine> dataDefineMap = dataDefineLoader.load();
        context.putAllDataDefine(dataDefineMap);

        initializeWorker(context);

        logger.info("could not configure cluster module, use the default");
        Iterator<Map.Entry<String, ModuleDefine>> moduleDefineEntry = moduleDefineMap.entrySet().iterator();
        while (moduleDefineEntry.hasNext()) {
            ModuleDefine moduleDefine = moduleDefineEntry.next().getValue();
            logger.info("module {} initialize", moduleDefine.getClass().getName());
            moduleDefine.initialize((ObjectUtils.isNotEmpty(moduleConfig) && moduleConfig.containsKey(moduleDefine.name())) ? moduleConfig.get(moduleDefine.name()) : null, serverHolder);
        }
    }

    private void initializeWorker(StreamModuleContext context) throws DefineException {
        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext();
        context.setClusterWorkerContext(clusterWorkerContext);

        LocalAsyncWorkerProviderDefineLoader localAsyncProviderLoader = new LocalAsyncWorkerProviderDefineLoader();
        RemoteWorkerProviderDefineLoader remoteProviderLoader = new RemoteWorkerProviderDefineLoader();
        try {
            List<AbstractLocalAsyncWorkerProvider> localAsyncProviders = localAsyncProviderLoader.load();
            for (AbstractLocalAsyncWorkerProvider provider : localAsyncProviders) {
                provider.setClusterContext(clusterWorkerContext);
                provider.create();
                clusterWorkerContext.putRole(provider.role());
            }

            List<AbstractRemoteWorkerProvider> remoteProviders = remoteProviderLoader.load();
            for (AbstractRemoteWorkerProvider provider : remoteProviders) {
                provider.setClusterContext(clusterWorkerContext);
                provider.create();
                clusterWorkerContext.putRole(provider.role());
            }
        } catch (ProviderNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
