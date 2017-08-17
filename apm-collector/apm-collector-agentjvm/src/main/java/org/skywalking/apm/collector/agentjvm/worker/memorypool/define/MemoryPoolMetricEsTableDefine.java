package org.skywalking.apm.collector.agentjvm.worker.memorypool.define;

import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;
import org.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;

/**
 * @author pengys5
 */
public class MemoryPoolMetricEsTableDefine extends ElasticSearchTableDefine {

    public MemoryPoolMetricEsTableDefine() {
        super(MemoryPoolMetricTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 1;
    }

    @Override public int numberOfShards() {
        return 2;
    }

    @Override public int numberOfReplicas() {
        return 0;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.COLUMN_APPLICATION_INSTANCE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.COLUMN_POOL_TYPE, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.COLUMN_IS_HEAP, ElasticSearchColumnDefine.Type.Boolean.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.COLUMN_INIT, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.COLUMN_MAX, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.COLUMN_USED, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.COLUMN_COMMITTED, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
