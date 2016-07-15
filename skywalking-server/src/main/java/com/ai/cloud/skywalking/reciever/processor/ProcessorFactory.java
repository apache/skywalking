package com.ai.cloud.skywalking.reciever.processor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class ProcessorFactory {
    private static Logger                   logger                 = LogManager.getLogger(ProcessorFactory.class);
    private static Map<Integer, IProcessor> type_processor_mapping = new HashMap<Integer, IProcessor>();

    static {
        ServiceLoader<IProcessor> processors = ServiceLoader.load(IProcessor.class);
        for (IProcessor processor : processors) {
            DefaultProcessor defaultProcessor = processor.getClass().getAnnotation(DefaultProcessor.class);
            IProcessor processor1 = type_processor_mapping.get(processor.getProtocolType());
            if (processor1 == null || (defaultProcessor != null && !defaultProcessor.defaultProcessor())) {
                logger.info("Init protocol type and processor mapping : {} --> {}.", processor.getProtocolType(),
                        processor.getClass().getName());
                type_processor_mapping.put(processor.getProtocolType(), processor);
            }
        }
    }

    public static IProcessor chooseProcessor(int dataType) {
        return type_processor_mapping.get(dataType);
    }
}
