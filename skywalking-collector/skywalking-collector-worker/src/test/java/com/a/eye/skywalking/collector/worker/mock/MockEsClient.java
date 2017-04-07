package com.a.eye.skywalking.collector.worker.mock;

import com.a.eye.skywalking.collector.worker.storage.EsClient;
import org.elasticsearch.client.Client;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.reflect.Whitebox;

import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
public class MockEsClient {

    public Client mock() {
        Client client = PowerMockito.mock(Client.class);
        EsClient esClient = PowerMockito.mock(EsClient.class);
        Whitebox.setInternalState(EsClient.class, "INSTANCE", esClient);
        when(esClient.getClient()).thenReturn(client);
        return client;
    }
}
