package org.apache.skywalking.apm.testcase.jedis.controller;

import redis.clients.jedis.Jedis;

public class RedisCommandExecutor implements AutoCloseable{
    private Jedis jedis;

    public RedisCommandExecutor(String host, Integer port) {
        jedis = new Jedis(host, port);
        jedis.echo("Test");
    }

    public void set(String key, String value) {
        jedis.set(key, value);
    }

    public void get(String key) {
        jedis.get(key);
    }

    public void del(String key) {
        jedis.del(key);
    }

    public void close() throws Exception {
        jedis.close();
    }
}
