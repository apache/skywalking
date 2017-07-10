package org.skywalking.apm.collector.core.framework;

import org.skywalking.apm.collector.core.config.ConfigException;

/**
 * @author pengys5
 */
public interface Loader {

    void load() throws ConfigException;
}
