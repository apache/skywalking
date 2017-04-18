package com.a.eye.skywalking.collector.worker.config;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.config.ConfigProvider;

/**
 * @author pengys5
 */
public class EsConfigProvider implements ConfigProvider {

    @Override
    public Class configClass() {
        return EsConfig.class;
    }

    @Override
    public void cliArgs() {
        if (!StringUtil.isEmpty(System.getProperty("es.cluster.NAME"))) {
            EsConfig.Es.Cluster.NAME = System.getProperty("es.cluster.NAME");
        }
        if (!StringUtil.isEmpty(System.getProperty("es.cluster.NODES"))) {
            EsConfig.Es.Cluster.NODES = System.getProperty("es.cluster.NODES");
        }
        if (!StringUtil.isEmpty(System.getProperty("es.cluster.transport.SNIFFER"))) {
            EsConfig.Es.Cluster.Transport.SNIFFER = System.getProperty("es.cluster.transport.SNIFFER");
        }

        if (!StringUtil.isEmpty(System.getProperty("es.index.shards.NUMBER"))) {
            EsConfig.Es.Index.Shards.NUMBER = System.getProperty("es.index.shards.NUMBER");
        }
        if (!StringUtil.isEmpty(System.getProperty("es.index.replicas.NUMBER"))) {
            EsConfig.Es.Index.Replicas.NUMBER = System.getProperty("es.index.replicas.NUMBER");
        }
    }
}
