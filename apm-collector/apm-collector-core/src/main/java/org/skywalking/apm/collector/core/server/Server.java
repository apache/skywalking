package org.skywalking.apm.collector.core.server;

import org.skywalking.apm.collector.core.framework.Handler;

/**
 * @author pengys5
 */
public interface Server {

    void initialize() throws ServerException;

    void start() throws ServerException;

    void addHandler(Handler handler);
}
