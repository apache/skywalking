package org.skywalking.apm.collector.worker.config;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class EsConfigProviderTestCase {

    @Test
    public void test() {
        EsConfigProvider provider = new EsConfigProvider();

        Assert.assertEquals(EsConfig.class, provider.configClass());

        System.setProperty("es.cluster.NAME", "A");
        System.setProperty("es.cluster.NODES", "B");
        System.setProperty("es.cluster.transport.SNIFFER", "C");
        System.setProperty("es.index.shards.NUMBER", "10");
        System.setProperty("es.index.replicas.NUMBER", "20");
        provider.cliArgs();

        Assert.assertEquals("A", EsConfig.Es.Cluster.NAME);
        Assert.assertEquals("B", EsConfig.Es.Cluster.NODES);
        Assert.assertEquals("C", EsConfig.Es.Cluster.Transport.SNIFFER);
        Assert.assertEquals("10", EsConfig.Es.Index.Shards.NUMBER);
        Assert.assertEquals("20", EsConfig.Es.Index.Replicas.NUMBER);
    }
}
