package org.skywalking.apm.collector.storage.elasticsearch;

/**
 * @author pengys5
 */
public class StorageElasticSearchConfig {
    public static String CLUSTER_NAME;
    public static Boolean CLUSTER_TRANSPORT_SNIFFER;
    public static String CLUSTER_NODES;
    public static Integer INDEX_SHARDS_NUMBER;
    public static Integer INDEX_REPLICAS_NUMBER;
}
