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
        if (!StringUtil.isEmpty(System.getProperty("es.cluster.name"))) {
            EsConfig.Es.Cluster.name = System.getProperty("es.cluster.name");
        }
        if (!StringUtil.isEmpty(System.getProperty("es.cluster.nodes"))) {
            EsConfig.Es.Cluster.nodes = System.getProperty("es.cluster.nodes");
        }
        if (!StringUtil.isEmpty(System.getProperty("es.cluster.transport.sniffer"))) {
            EsConfig.Es.Cluster.Transport.sniffer = System.getProperty("es.cluster.transport.sniffer");
        }

        if (!StringUtil.isEmpty(System.getProperty("es.index.shards.number"))) {
            EsConfig.Es.Index.Shards.number = System.getProperty("es.index.shards.number");
        }
        if (!StringUtil.isEmpty(System.getProperty("es.index.replicas.number"))) {
            EsConfig.Es.Index.Replicas.number = System.getProperty("es.index.replicas.number");
        }
    }
}
