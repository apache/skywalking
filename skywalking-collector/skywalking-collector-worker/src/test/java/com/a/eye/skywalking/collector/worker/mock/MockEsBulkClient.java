package com.a.eye.skywalking.collector.worker.mock;

import com.a.eye.skywalking.collector.worker.storage.EsClient;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.mockito.Mockito;

import static org.powermock.api.mockito.PowerMockito.*;

/**
 * @author pengys5
 */
public class MockEsBulkClient {

    public IndexRequestBuilder indexRequestBuilder;

    public void createMock() {
        Client client = mock(Client.class);
        mockStatic(EsClient.class);
        when(EsClient.getClient()).thenReturn(client);

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
