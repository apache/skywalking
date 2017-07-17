package org.skywalking.apm.collector.core.client;

import java.util.List;

/**
 * @author pengys5
 */
public interface DataListener {
    List<String> items();

    void listen() throws ClientException;
}
