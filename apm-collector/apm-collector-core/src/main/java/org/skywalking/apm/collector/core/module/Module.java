package org.skywalking.apm.collector.core.module;

import java.util.Map;

/**
 * @author pengys5
 */
public interface Module {
    void install(Map configuration);
}
