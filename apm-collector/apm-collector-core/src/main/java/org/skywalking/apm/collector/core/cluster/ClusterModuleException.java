package org.skywalking.apm.collector.core.cluster;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class ClusterModuleException extends ModuleException {

    public ClusterModuleException(String message) {
        super(message);
    }

    public ClusterModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
