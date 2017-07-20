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

    @Override public void parse(Map config) throws ConfigParseException {
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CLUSTER_NAME))) {
            StorageElasticSearchConfig.CLUSTER_NAME = (String)config.get(CLUSTER_NAME);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CLUSTER_TRANSPORT_SNIFFER))) {
            StorageElasticSearchConfig.CLUSTER_TRANSPORT_SNIFFER = (String)config.get(CLUSTER_TRANSPORT_SNIFFER);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CLUSTER_NODES))) {
            StorageElasticSearchConfig.CLUSTER_NODES = (String)config.get(CLUSTER_NODES);
        }
    }
}
