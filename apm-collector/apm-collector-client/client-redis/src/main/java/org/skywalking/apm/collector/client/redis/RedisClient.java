package org.skywalking.apm.collector.client.redis;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;

/**
 * @author pengys5
 */
public class RedisClient implements Client {

    @Override public void initialize() throws ClientException {
        
    }

    @Override public void insert(String path) throws ClientException {

    }

    @Override public void update() {

    }

    @Override public String select(String path) throws ClientException {
        return null;
    }

    @Override public void delete() {

    }

    @Override public boolean exist(String path) throws ClientException {
        return false;
    }

    @Override public void listen(String path) throws ClientException {

    }
}
