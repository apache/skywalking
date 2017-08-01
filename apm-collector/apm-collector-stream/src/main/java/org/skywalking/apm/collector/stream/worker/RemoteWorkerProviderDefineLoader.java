package org.skywalking.apm.collector.stream.worker;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class RemoteWorkerProviderDefineLoader implements Loader<List<AbstractRemoteWorkerProvider>> {

    private final Logger logger = LoggerFactory.getLogger(RemoteWorkerProviderDefineLoader.class);

    @Override public List<AbstractRemoteWorkerProvider> load() throws DefineException {
        List<AbstractRemoteWorkerProvider> providers = new ArrayList<>();
        RemoteWorkerProviderDefinitionFile definitionFile = new RemoteWorkerProviderDefinitionFile();
        logger.info("remote worker provider definition file name: {}", definitionFile.fileName());

        DefinitionLoader<AbstractRemoteWorkerProvider> definitionLoader = DefinitionLoader.load(AbstractRemoteWorkerProvider.class, definitionFile);

        for (AbstractRemoteWorkerProvider provider : definitionLoader) {
            logger.info("loaded remote worker provider definition class: {}", provider.getClass().getName());
            providers.add(provider);
        }
        return providers;
    }
}
