package org.skywalking.apm.collector.agentstream.worker.service.entry.define;

import org.skywalking.apm.collector.storage.define.service.ServiceEntryTable;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author pengys5
 */
public class ServiceEntryEsTableDefine extends ElasticSearchTableDefine {

    public ServiceEntryEsTableDefine() {
        super(ServiceEntryTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 2;
    }

    @Override public int numberOfShards() {
        return 2;
    }

    @Override public int numberOfReplicas() {
        return 0;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(ServiceEntryTable.COLUMN_AGG, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceEntryTable.COLUMN_TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
