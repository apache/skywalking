package org.skywalking.apm.collector.worker.mock;

import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.reflect.Whitebox;
import org.skywalking.apm.collector.worker.storage.EsClient;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
public class MockEsBulkClient {

    public IndexRequestBuilder indexRequestBuilder;

    public void createMock() {
        Client client = PowerMockito.mock(Client.class);
        EsClient esClient = PowerMockito.mock(EsClient.class);
        Whitebox.setInternalState(EsClient.class, "INSTANCE", esClient);
        when(esClient.getClient()).thenReturn(client);

        BulkRequestBuilder bulkRequestBuilder = mock(BulkRequestBuilder.class);
        when(client.prepareBulk()).thenReturn(bulkRequestBuilder);

        ListenableActionFuture listenableActionFuture = mock(ListenableActionFuture.class);
        when(bulkRequestBuilder.execute()).thenReturn(listenableActionFuture);

        BulkResponse responses = mock(BulkResponse.class);
        when(listenableActionFuture.actionGet()).thenReturn(responses);

        when(responses.hasFailures()).thenReturn(true);

        indexRequestBuilder = mock(IndexRequestBuilder.class);

        when(client.prepareIndex(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(indexRequestBuilder);

        when(indexRequestBuilder.setSource(Mockito.anyString())).thenReturn(indexRequestBuilder);
    }
}
