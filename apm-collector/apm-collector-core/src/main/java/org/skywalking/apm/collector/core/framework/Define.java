package org.skywalking.apm.collector.core.framework;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;

/**
 * @author pengys5
 */
public interface Define {

    void initialize(Map config) throws DefineException, ClientException;

    String name();
}
