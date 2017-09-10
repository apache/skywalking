package org.skywalking.apm.collector.boot;

import org.skywalking.apm.collector.core.CollectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class CollectorBootStartUp {

    private static final Logger logger = LoggerFactory.getLogger(CollectorBootStartUp.class);

    public static void main(String[] args) throws CollectorException {
        logger.info("collector starting...");
        CollectorStarter starter = new CollectorStarter();
        starter.start();
        logger.info("collector start successful.");
    }
}
