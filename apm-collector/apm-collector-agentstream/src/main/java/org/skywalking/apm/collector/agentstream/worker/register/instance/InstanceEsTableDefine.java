package org.skywalking.apm.collector.agentstream.worker.register.instance;

import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author pengys5
 */
public class InstanceEsTableDefine extends ElasticSearchTableDefine {

    public InstanceEsTableDefine() {
        super(InstanceTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 0;
    }

    @Override public int numberOfShards() {
        return 2;
    }

    @Override public int numberOfReplicas() {
        return 0;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(InstanceTable.COLUMN_APPLICATION_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(InstanceTable.COLUMN_AGENT_UUID, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(InstanceTable.COLUMN_REGISTER_TIME, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(InstanceTable.COLUMN_INSTANCE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(InstanceTable.COLUMN_HEARTBEAT_TIME, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
