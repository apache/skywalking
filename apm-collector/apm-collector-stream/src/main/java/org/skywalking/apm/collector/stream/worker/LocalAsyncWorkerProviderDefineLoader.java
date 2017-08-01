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
public class LocalAsyncWorkerProviderDefineLoader implements Loader<List<AbstractLocalAsyncWorkerProvider>> {

    private final Logger logger = LoggerFactory.getLogger(LocalAsyncWorkerProviderDefineLoader.class);

    @Override public List<AbstractLocalAsyncWorkerProvider> load() throws DefineException {
        List<AbstractLocalAsyncWorkerProvider> providers = new ArrayList<>();
        LocalAsyncWorkerProviderDefinitionFile definitionFile = new LocalAsyncWorkerProviderDefinitionFile();
        logger.info("local async worker provider definition file name: {}", definitionFile.fileName());

        DefinitionLoader<AbstractLocalAsyncWorkerProvider> definitionLoader = DefinitionLoader.load(AbstractLocalAsyncWorkerProvider.class, definitionFile);

        for (AbstractLocalAsyncWorkerProvider provider : definitionLoader) {
            logger.info("loaded local async worker provider definition class: {}", provider.getClass().getName());
            providers.add(provider);
        }
        return providers;
    }
}
