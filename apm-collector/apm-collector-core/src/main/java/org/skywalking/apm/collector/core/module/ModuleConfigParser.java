package org.skywalking.apm.collector.core.module;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;

/**
 * @author pengys5
 */
public interface ModuleConfigParser {
    void parse(Map config) throws ConfigParseException;
}
