package org.skywalking.apm.collector.ui;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class UIModuleException extends ModuleException {
    public UIModuleException(String message) {
        super(message);
    }

    public UIModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
