package org.skywalking.apm.collector.worker.mock;

import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
public class MockGetResponse {

    public GetResponse mockito() {
        MockEsClient mockEsClient = new MockEsClient();
        Client client = mockEsClient.mock();

        GetRequestBuilder builder = mock(GetRequestBuilder.class);
        GetResponse getResponse = mock(GetResponse.class);
        when(builder.get()).thenReturn(getResponse);

        when(client.prepareGet(anyString(), anyString(), anyString())).thenReturn(builder);

        return getResponse;
    }

    public GetResponse mockito(String index, String type, String id) {
        MockEsClient mockEsClient = new MockEsClient();
        Client client = mockEsClient.mock();

        GetRequestBuilder builder = mock(GetRequestBuilder.class);
        GetResponse getResponse = mock(GetResponse.class);
        when(builder.get()).thenReturn(getResponse);

        when(client.prepareGet(index, type, id)).thenReturn(builder);

        return getResponse;
    }
}
