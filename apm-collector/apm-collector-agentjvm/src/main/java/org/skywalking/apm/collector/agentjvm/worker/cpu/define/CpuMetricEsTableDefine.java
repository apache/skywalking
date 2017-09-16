package org.skywalking.apm.collector.agentjvm.worker.cpu.define;

import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;
import org.skywalking.apm.collector.storage.define.jvm.CpuMetricTable;

/**
 * @author pengys5
 */
public class CpuMetricEsTableDefine extends ElasticSearchTableDefine {

    public CpuMetricEsTableDefine() {
        super(CpuMetricTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 1;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(CpuMetricTable.COLUMN_INSTANCE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(CpuMetricTable.COLUMN_USAGE_PERCENT, ElasticSearchColumnDefine.Type.Double.name()));
        addColumn(new ElasticSearchColumnDefine(CpuMetricTable.COLUMN_TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
