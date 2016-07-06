package com.ai.cloud.skywalking.reciever.processor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class ProcessorFactory {
    private static Logger                              logger                 = LogManager.getLogger(ProcessorFactory.class);
    private static Map<Integer, AbstractSpanProcessor> type_processor_mapping = new HashMap<Integer, AbstractSpanProcessor>();

    static {
        ServiceLoader<AbstractSpanProcessor> processors = ServiceLoader.load(AbstractSpanProcessor.class);

        for (AbstractSpanProcessor processor : processors) {
            logger.info("Init protocol type and processor mapping : {} --> {}.", processor.getType(), processor.getClass().getName());
            type_processor_mapping.put(processor.getType(), processor);
        }
    }

    public static AbstractSpanProcessor chooseProcessor(int dataType) {
        return type_processor_mapping.get(dataType);
    }
}
