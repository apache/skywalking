package org.skywalking.apm.collector.storage.elasticsearch;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class StorageElasticSearchConfigParser implements ModuleConfigParser {

    private static final String CLUSTER_NAME = "cluster_name";
    private static final String CLUSTER_TRANSPORT_SNIFFER = "cluster_transport_sniffer";
    private static final String CLUSTER_NODES = "cluster_nodes";
    private static final String INDEX_SHARDS_NUMBER = "index_shards_number";
    private static final String INDEX_REPLICAS_NUMBER = "index_replicas_number";

    @Override public void parse(Map config) throws ConfigParseException {
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CLUSTER_NAME))) {
            StorageElasticSearchConfig.CLUSTER_NAME = (String)config.get(CLUSTER_NAME);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CLUSTER_TRANSPORT_SNIFFER))) {
            StorageElasticSearchConfig.CLUSTER_TRANSPORT_SNIFFER = (Boolean)config.get(CLUSTER_TRANSPORT_SNIFFER);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CLUSTER_NODES))) {
            StorageElasticSearchConfig.CLUSTER_NODES = (String)config.get(CLUSTER_NODES);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(INDEX_SHARDS_NUMBER))) {
            StorageElasticSearchConfig.INDEX_SHARDS_NUMBER = (Integer)config.get(INDEX_SHARDS_NUMBER);
        } else {
            StorageElasticSearchConfig.INDEX_SHARDS_NUMBER = 2;
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(INDEX_REPLICAS_NUMBER))) {
            StorageElasticSearchConfig.INDEX_REPLICAS_NUMBER = (Integer)config.get(INDEX_REPLICAS_NUMBER);
        } else {
            StorageElasticSearchConfig.INDEX_REPLICAS_NUMBER = 0;
        }
    }
}
