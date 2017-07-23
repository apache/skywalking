package org.skywalking.apm.collector.remote.grpc.handler;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class RemoteHandlerDefineLoader implements Loader<List<Handler>> {

    private final Logger logger = LoggerFactory.getLogger(RemoteHandlerDefineLoader.class);

    @Override public List<Handler> load() throws ConfigException {
        List<Handler> handlers = new ArrayList<>();

        RemoteHandlerDefinitionFile definitionFile = new RemoteHandlerDefinitionFile();
        DefinitionLoader<Handler> definitionLoader = DefinitionLoader.load(Handler.class, definitionFile);
        for (Handler handler : definitionLoader) {
            logger.info("loaded remote handler definition class: {}", handler.getClass().getName());
            handlers.add(handler);
        }
        return handlers;
    }
}
