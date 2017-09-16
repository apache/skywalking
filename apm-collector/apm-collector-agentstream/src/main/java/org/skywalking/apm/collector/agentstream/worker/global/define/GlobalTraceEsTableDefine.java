package org.skywalking.apm.collector.agentstream.worker.global.define;

import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;
import org.skywalking.apm.collector.storage.define.global.GlobalTraceTable;

/**
 * @author pengys5
 */
public class GlobalTraceEsTableDefine extends ElasticSearchTableDefine {

    public GlobalTraceEsTableDefine() {
        super(GlobalTraceTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 5;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(GlobalTraceTable.COLUMN_SEGMENT_ID, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(GlobalTraceTable.COLUMN_TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
