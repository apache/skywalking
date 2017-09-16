package org.skywalking.apm.collector.agentregister.worker.application;

import org.skywalking.apm.collector.storage.define.register.ApplicationTable;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author pengys5
 */
public class ApplicationEsTableDefine extends ElasticSearchTableDefine {

    public ApplicationEsTableDefine() {
        super(ApplicationTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 2;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(ApplicationTable.COLUMN_APPLICATION_CODE, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ApplicationTable.COLUMN_APPLICATION_ID, ElasticSearchColumnDefine.Type.Integer.name()));
    }
}
