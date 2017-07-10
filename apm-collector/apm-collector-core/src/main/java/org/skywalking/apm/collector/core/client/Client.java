package org.skywalking.apm.collector.core.client;

/**
 * @author pengys5
 */
public interface Client {

    void initialize() throws ClientException;

    void insert(String path) throws ClientException;

    void update();

    String select(String path) throws ClientException;

    void delete();

    boolean exist(String path) throws ClientException;

    void listen(String path) throws ClientException;
}
