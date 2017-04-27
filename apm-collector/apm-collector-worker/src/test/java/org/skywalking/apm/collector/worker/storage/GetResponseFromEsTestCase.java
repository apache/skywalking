package org.skywalking.apm.collector.worker.storage;

import org.elasticsearch.action.get.GetResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.collector.worker.mock.MockGetResponse;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {EsClient.class})
@PowerMockIgnore( {"javax.management.*"})
public class GetResponseFromEsTestCase {

    private GetResponse getResponse;

    @Before
    public void init() {
        MockGetResponse mockGetResponse = new MockGetResponse();
        getResponse = mockGetResponse.mockito();
    }

    @Test
    public void testGet() {
        GetResponse response = GetResponseFromEs.INSTANCE.get("INDEX", "TYPE", "1");
        Assert.assertEquals(getResponse, response);
    }
}
