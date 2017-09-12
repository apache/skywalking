package org.skywalking.apm.collector.client.redis;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import redis.clients.jedis.Jedis;

/**
 * @author pengys5
 */
public class RedisClient implements Client {

    private Jedis jedis;

    private final String host;
    private final int port;

    public RedisClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override public void initialize() throws ClientException {
        jedis = new Jedis(host, port);
    }

    @Override public void shutdown() {

    }

    public void setex(String key, int seconds, String value) {
        jedis.setex(key, seconds, value);
    }
}
