package org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor;

import org.apache.skywalking.apm.plugin.elasticsearch.v6.TransportAddressCache;
import org.elasticsearch.common.transport.TransportAddress;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class TransportAddressCacheTest {

    private TransportAddressCache transportAddressCache;

    @Before
    public void setUp() {
        transportAddressCache = new TransportAddressCache();
    }

    @Test
    public void transportAddressTest()
        throws UnknownHostException {

        transportAddressCache.addDiscoveryNode(
            new TransportAddress(InetAddress.getByName("172.1.1.1"), 9300),
            new TransportAddress(InetAddress.getByName("172.1.1.2"), 9200),
            new TransportAddress(InetAddress.getByName("172.1.1.3"), 9100)
        );

        assertThat(transportAddressCache.transportAddress(), is("172.1.1.1:9300,172.1.1.2:9200,172.1.1.3:9100"));
    }

}
