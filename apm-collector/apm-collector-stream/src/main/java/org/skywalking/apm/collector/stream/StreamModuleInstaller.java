package org.skywalking.apm.collector.stream;

import java.util.List;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.SingleModuleInstaller;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.LocalAsyncWorkerProviderDefineLoader;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.RemoteWorkerProviderDefineLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class StreamModuleInstaller extends SingleModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(StreamModuleInstaller.class);

    @Override public String groupName() {
        return StreamModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new StreamModuleContext(groupName());
    }

    @Override public void install() throws ClientException, DefineException, ConfigException, ServerException {
        super.install();
        initializeWorker((StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(groupName()));
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
                clusterWorkerContext.putRole(provider.role());
                clusterWorkerContext.putProvider(provider);
            }
        } catch (ProviderNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
