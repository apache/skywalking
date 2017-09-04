package org.skywalking.apm.collector.agentstream.worker.register.servicename;

import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;
import org.skywalking.apm.collector.storage.define.register.ServiceNameTable;

/**
 * @author pengys5
 */
public class ServiceNameEsTableDefine extends ElasticSearchTableDefine {

    public ServiceNameEsTableDefine() {
        super(ServiceNameTable.TABLE);
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
        addColumn(new ElasticSearchColumnDefine(ServiceNameTable.COLUMN_APPLICATION_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceNameTable.COLUMN_SERVICE_NAME, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceNameTable.COLUMN_SERVICE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
    }
}
