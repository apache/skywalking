package org.skywalking.apm.collector.stream;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class StreamModuleException extends ModuleException {

    public StreamModuleException(String message) {
        super(message);
    }

    public StreamModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
