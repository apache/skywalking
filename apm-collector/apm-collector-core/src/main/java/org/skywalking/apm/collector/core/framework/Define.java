package org.skywalking.apm.collector.core.framework;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.server.ServerHolder;

/**
 * @author pengys5
 */
public interface Define {

    void initialize(Map config, ServerHolder serverHolder) throws DefineException, ClientException;

    String name();
}
