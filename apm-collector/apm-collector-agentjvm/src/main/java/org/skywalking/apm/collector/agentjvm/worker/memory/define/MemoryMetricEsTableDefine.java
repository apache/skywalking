package org.skywalking.apm.collector.agentjvm.worker.memory.define;

import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;
import org.skywalking.apm.collector.storage.define.jvm.MemoryMetricTable;

/**
 * @author pengys5
 */
public class MemoryMetricEsTableDefine extends ElasticSearchTableDefine {

    public MemoryMetricEsTableDefine() {
        super(MemoryMetricTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 1;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(MemoryMetricTable.COLUMN_APPLICATION_INSTANCE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryMetricTable.COLUMN_IS_HEAP, ElasticSearchColumnDefine.Type.Boolean.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryMetricTable.COLUMN_INIT, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryMetricTable.COLUMN_MAX, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryMetricTable.COLUMN_USED, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryMetricTable.COLUMN_COMMITTED, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryMetricTable.COLUMN_TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
