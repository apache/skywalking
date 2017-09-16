package org.skywalking.apm.collector.agentstream.worker.serviceref.define;

import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author pengys5
 */
public class ServiceReferenceEsTableDefine extends ElasticSearchTableDefine {

    public ServiceReferenceEsTableDefine() {
        super(ServiceReferenceTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 2;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_AGG, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_S1_LTE, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_S3_LTE, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_S5_LTE, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_S5_GT, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_SUMMARY, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_ERROR, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_COST_SUMMARY, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
